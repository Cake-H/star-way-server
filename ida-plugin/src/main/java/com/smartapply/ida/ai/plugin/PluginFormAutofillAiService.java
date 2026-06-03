package com.smartapply.ida.ai.plugin;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartapply.ida.ai.volcengine.client.ArkChatCompletionClient;
import com.smartapply.ida.ai.volcengine.config.VolcengineArkProperties;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillProgressPhase;
import com.smartapply.ida.common.domain.plugin.autofill.PluginBlockedFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginSubAssignmentDto;
import com.smartapply.ida.common.domain.resume.support.ResumeDraftModuleCountsExtractor;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.resume.service.AppResumeQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 插件端「一键填表」AI：独立系统提示词身份（见 {@link PluginAutofillPrompts}），与简历解析链路解耦。
 * <p>
 * <b>与浏览器扩展的分工：</b>扩展必须在 content script 中采集 DOM 生成 {@link com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormSnapshotDto}，
 * 调用本服务拿到 {@link PluginAutofillPlanResultVO} 后，再由扩展按 {@code selector} 写回 DOM；服务端无法直接操作用户浏览器。
 * 异步任务形态见 {@code POST .../autofill-plan/tasks} 与 {@link PluginAutofillProgressSink}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginFormAutofillAiService {

    /** 单页控件过多时截断，避免提示词爆炸与方舟超时；与清洗逻辑无关的上限 */
    private static final int MAX_FIELDS = 120;

    /** 火山方舟 {@code /chat/completions} 同步 HTTP 客户端（Bean 注入，与简历解析共用实现类） */
    private final ArkChatCompletionClient arkChatCompletionClient;
    /** {@code ida.volcengine-ark.*} 绑定：解析 API Key、baseUrl、plugin-autofill 专用模型/思考/ max_tokens 等 */
    private final VolcengineArkProperties arkProperties;
    /** Spring 主 ObjectMapper：简历 draft 序列化、页面快照 JSON、模型返回 JSON 均走同一套配置 */
    private final ObjectMapper objectMapper;
    /** 按 userId + resumeId 拉取简历详情（含 draft）；与 App 端简历接口共用查询服务 */
    private final AppResumeQueryService appResumeQueryService;

    /**
     * 同步生成填表计划（原 {@code POST /autofill-plan} 路径）：不向外报告细粒度进度，避免重复日志。
     */
    public PluginAutofillPlanResultVO planAutofill(long userId, PluginAutofillPlanRequest req) {
        return planAutofill(userId, req, PluginAutofillProgressSink.noop());
    }

    /**
     * 生成填表计划并在关键步骤回调 {@code progress}。
     * <p>
     * <b>进度语义：</b>{@link PluginAutofillProgressPhase#CALLING_MODEL} 对应一次阻塞式 HTTP 调用方舟，
     * 在思考链 + 长 JSON 场景下可能持续数十秒至数分钟；此间 percent 可停留在 35～55 一带，属预期行为。
     * 真正「流式 token」需后续对接方舟 SSE，再在本阶段内拆分子事件。
     */
    public PluginAutofillPlanResultVO planAutofill(long userId, PluginAutofillPlanRequest req, PluginAutofillProgressSink progress) {
        // 异步任务可传 registry 的 sink；同步接口传 null 时退化为空操作，避免分支判断
        PluginAutofillProgressSink p = progress != null ? progress : PluginAutofillProgressSink.noop();

        if (req == null || req.getPage() == null) {
            throw new BusinessException("请求体不完整");
        }
        p.onProgress(PluginAutofillProgressPhase.RECEIVED, "请求体与 page 快照已校验", 8);

        // 扩展采集的 DOM 控件列表；可能为 null（上面已保证 page 非 null）
        List<PluginPageFormFieldDto> rawFields = req.getPage().getFields();
        if (rawFields == null || rawFields.isEmpty()) {
            p.onProgress(PluginAutofillProgressPhase.SANITIZING_PLAN, "控件列表为空，跳过大模型，直接返回不可填说明", 90);
            PluginAutofillPlanResultVO quick = PluginAutofillPlanResultVO.builder()
                    .fillable(false)
                    .hint("当前页面未采集到任何可编辑控件。请在招聘/网申/简历投递表单页打开本功能，并确保扩展有权限读取该页 DOM。")
                    .assignments(List.of())
                    .blockedFields(List.of())
                    .build();
            p.onProgress(PluginAutofillProgressPhase.COMPLETED, "短路径结束（无需调用模型）", 100);
            return quick;
        }
        log.info("前端采集的DOM控件列表：{}", rawFields);

        p.onProgress(PluginAutofillProgressPhase.LOADING_RESUME, "正在加载简历 draft，resumeId=" + req.getResumeId(), 14);
        // 会校验简历归属 userId；无权限或不存在时由下层抛业务异常
        ResumeDetailVO detail = appResumeQueryService.getResumeDetail(userId, req.getResumeId());
        if (detail.getDraft() == null) {
            throw new BusinessException("简历内容为空，无法生成填表计划");
        }
        String resumeJson;
        try {
            // PART_A：嵌入用户消息，供模型对照填写
            resumeJson = objectMapper.writeValueAsString(detail.getDraft());
        } catch (JsonProcessingException e) {
            throw new BusinessException("简历数据序列化失败");
        }

        // 截断后列表用于白名单校验与 toPageFieldsJson
        List<PluginPageFormFieldDto> fields = truncateFields(rawFields);
        // PART_B：pageUrl/pageTitle + fields 数组（含扩展可选提供的 compoundSegment / interactionMode / optionLabelsSample）
        String pageJson = toPageFieldsJson(req, fields);
        // PART_C：与云端简历子表条数一致，提示模型「应有多少段经历/项目」并辅助 hint；与前端 GET autofill-context 同源统计
        String moduleCountsJson;
        try {
            moduleCountsJson = objectMapper.writeValueAsString(ResumeDraftModuleCountsExtractor.fromDraft(detail.getDraft()));
        } catch (JsonProcessingException e) {
            throw new BusinessException("简历模块统计序列化失败");
        }
        p.onProgress(PluginAutofillProgressPhase.BUILDING_PROMPT,
                "已序列化简历、页面字段与模块条数 PART_C（控件数=" + fields.size() + "），准备构造方舟消息体", 24);

        // 读环境变量 / 系统属性，与简历解析共用同一 Key
        String apiKey = arkProperties.resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("未配置火山方舟 API Key，请设置环境变量 " + arkProperties.getEnvApiKey());
        }

        // 填表专用 model 未配置时回落到简历解析模型，便于单机只配一个 endpoint
        String model = resolveAutofillModel();
        double temperature = arkProperties.getPluginAutofill().getTemperature();
        // pf：thinking、reasoning、max_tokens、response_format 等均取自该嵌套配置块
        var pf = arkProperties.getPluginAutofill();

        // OpenAI 兼容 messages：单轮 user 内拼 PART_A + PART_B + PART_C（模块条数）
        String userContent = PluginAutofillPrompts.USER_PREFIX + resumeJson + PluginAutofillPrompts.USER_MIDDLE + pageJson
                + PluginAutofillPrompts.USER_PARTC_PREFIX + moduleCountsJson;
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", PluginAutofillPrompts.SYSTEM.trim()),
                Map.of("role", "user", "content", userContent)
        );

        // 0 表示不下发 max_tokens，交给方舟默认
        Integer maxTokens = pf.getMaxTokens() > 0 ? pf.getMaxTokens() : null;

        /*
         * 以下 chatCompletions 为同步 HTTP：当前线程（Tomcat 或 plugin-autofill-* 线程池）会阻塞到方舟返回全文。
         * 异步模式下阻塞的是线程池线程，故网关/浏览器不会因本连接过长而超时；进度百分比在此阶段可长时间不变，属预期。
         */
        p.onProgress(PluginAutofillProgressPhase.CALLING_MODEL,
                "正在调用火山方舟 model=" + model + "（阻塞直至整段响应返回，请耐心等待）", 38);

        JsonNode root;
        try {
            // 重载：带填表专用 thinking/reasoning/max_tokens/json_object，与简历解析那路参数分离
            root = arkChatCompletionClient.chatCompletions(
                    apiKey, model, temperature, messages,
                    pf.getThinkingType(), pf.getReasoningEffort(), maxTokens,
                    pf.isResponseFormatJsonObject());
        } catch (ResourceAccessException e) {
            // 超时、DNS、连接拒绝等 → 转成用户可读中文，不暴露堆栈
            throw translateArkNetworkFailure(e);
        }

        if (root == null) {
            throw new BusinessException("方舟接口返回空响应");
        }
        // 方舟错误时 HTTP 仍 200，body 里带 error 对象
        JsonNode err = root.get("error");
        if (err != null && !err.isNull()) {
            String msg = err.path("message").asText("方舟接口错误");
            log.warn("插件填表方舟错误: {}", root);
            throw new BusinessException("一键填表失败：" + msg);
        }

        p.onProgress(PluginAutofillProgressPhase.CALLING_MODEL, "方舟 HTTP 已返回，正在读取 choices[0].message.content", 55);

        // 标准 chat.completions 正文路径；若模型只返回 tool_calls 此处可能为空（当前提示词未用工具）
        String content = root.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BusinessException("模型未返回可解析内容");
        }

        // 部分模型仍包 ```json 围栏，去掉后再 parse
        String json = stripMarkdownFence(content.trim());

        log.info("模型返回json: \n{}", json);


        p.onProgress(PluginAutofillProgressPhase.PARSING_MODEL_JSON, "开始 Jackson 解析模型 JSON，长度=" + json.length(), 65);

        PluginAutofillPlanResultVO parsed;
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            if (!rootNode.isObject()) {
                throw new BusinessException("模型返回的根节点不是 JSON 对象");
            }
            ObjectNode obj = (ObjectNode) rootNode;
            // JSON 模式/部分模型会漏掉 fillable；boolean 缺省反序列化为 false 会误丢 assignments，故在此补全
            if (!obj.has("fillable") || obj.get("fillable").isNull()) {
                int n = obj.path("assignments").isArray() ? obj.get("assignments").size() : 0;
                boolean infer = n > 0;
                obj.put("fillable", infer);
                if (infer) {
                    log.info("填表模型 JSON 未包含 fillable，已根据 assignments 条数={} 推断为 true", n);
                }
            }
            parsed = objectMapper.treeToValue(obj, PluginAutofillPlanResultVO.class);
        } catch (JsonProcessingException e) {
            // 辅助判断：length 截断时优先提示加 max_tokens
            String finish = root.path("choices").path(0).path("finish_reason").asText("");
            log.warn("填表模型输出非合法 JSON，finish_reason={}，JSON 长度={}，前 200 字: {}；{}",
                    finish, json.length(), json.length() > 200 ? json.substring(0, 200) : json,
                    jsonParseErrorSnippet(json, e), e);
            if ("length".equalsIgnoreCase(finish)) {
                throw new BusinessException("模型输出因长度上限被截断，无法解析为 JSON。请提高 ida.volcengine-ark.plugin-autofill.max-tokens 后重试。");
            }
            throw new BusinessException("模型返回的 JSON 无法解析（多为某条 assignment 的 value 中含未转义的双引号或裸换行）。请重试；若方舟报参数错误可关闭 ida.volcengine-ark.plugin-autofill.response-format-json-object。");
        }

        p.onProgress(PluginAutofillProgressPhase.SANITIZING_PLAN, "正在执行 selector 白名单、锚点合并与 blockedFields 清洗", 82);
        // parsed 中 selector 可能不在快照内，此处统一过滤并合并锚点
        PluginAutofillPlanResultVO out = sanitizePlan(parsed, fields);
        p.onProgress(PluginAutofillProgressPhase.COMPLETED, "填表计划生成完毕", 100);
        log.info("填表计划完成 userId={} resumeId={} fillable={} assignments={} blockedFields={}",
                userId, req.getResumeId(), out.isFillable(),
                out.getAssignments() == null ? 0 : out.getAssignments().size(),
                out.getBlockedFields() == null ? 0 : out.getBlockedFields().size());
        return out;
    }

    private List<PluginPageFormFieldDto> truncateFields(List<PluginPageFormFieldDto> rawFields) {
        if (rawFields.size() <= MAX_FIELDS) {
            return rawFields; // 保持原列表引用，避免无谓拷贝
        }
        log.info("插件填表：控件数={} 超过上限 {}，已截断", rawFields.size(), MAX_FIELDS);
        return new ArrayList<>(rawFields.subList(0, MAX_FIELDS)); // 只保留前 N 个，后续白名单也只认这批
    }

    private String toPageFieldsJson(PluginAutofillPlanRequest req, List<PluginPageFormFieldDto> fields) {
        try {
            // 与提示词 PART_B 约定一致：扁平对象；fields 为 PluginPageFormFieldDto 列表（Jackson 序列化可选字段 compoundSegment、interactionMode、optionLabelsSample 等）
            Map<String, Object> wrap = Map.of(
                    "pageUrl", req.getPage().getPageUrl() != null ? req.getPage().getPageUrl() : "",
                    "pageTitle", req.getPage().getPageTitle() != null ? req.getPage().getPageTitle() : "",
                    "fields", fields
            );
            return objectMapper.writeValueAsString(wrap);
        } catch (JsonProcessingException e) {
            throw new BusinessException("页面快照序列化失败");
        }
    }

    /**
     * 安全裁剪：selector/subSelector 必须在快照内；合并客户端锚点；规范化 reason/matchType；剔除无值的无效 assignment。
     * <p>
     * 使用 {@link #normalizeSelectorKey(String)} 建索引：模型回传的 CSS 路径可能与扩展快照仅有空白差异，
     * 匹配成功后把 {@link PluginFieldAssignmentDto#setSelector(String)} 回写为快照中的「权威」字符串，保证扩展 querySelector 可用。
     */
    private PluginAutofillPlanResultVO sanitizePlan(PluginAutofillPlanResultVO parsed, List<PluginPageFormFieldDto> fields) {
        // key = 规范化后的 selector，value = 原始快照字段（含扩展采集的锚点等）
        Map<String, PluginPageFormFieldDto> fieldByNormKey = new LinkedHashMap<>();
        for (PluginPageFormFieldDto f : fields) {
            if (f != null && StringUtils.hasText(f.getSelector())) {
                String nk = normalizeSelectorKey(f.getSelector());
                // 同一规范化 key 只保留先出现的快照行，避免重复 selector 歧义
                fieldByNormKey.putIfAbsent(nk, f);
            }
        }

        if (parsed == null) {
            return PluginAutofillPlanResultVO.builder()
                    .fillable(false)
                    .hint("模型未返回有效计划，请重试。")
                    .assignments(List.of())
                    .blockedFields(List.of())
                    .build();
        }

        // 同样按白名单过滤 blockedFields，并补默认 userHint
        List<PluginBlockedFieldDto> blockedSafe = sanitizeBlockedFields(parsed.getBlockedFields(), fieldByNormKey);

        if (!parsed.isFillable()) {
            // 模型判定整页不可填：仍返回清洗后的 blocked，assignments 清空
            String h = StringUtils.hasText(parsed.getHint())
                    ? parsed.getHint()
                    : "当前页面不适合自动填写简历类信息，请在网申/投递表单页使用。";
            return PluginAutofillPlanResultVO.builder()
                    .fillable(false)
                    .hint(h)
                    .assignments(List.of())
                    .blockedFields(blockedSafe)
                    .build();
        }

        List<PluginFieldAssignmentDto> safe = new ArrayList<>();
        if (parsed.getAssignments() != null) {
            for (PluginFieldAssignmentDto a : parsed.getAssignments()) {
                if (a == null || !StringUtils.hasText(a.getSelector())) {
                    continue;
                }
                String sel = a.getSelector().trim();
                PluginPageFormFieldDto matched = fieldByNormKey.get(normalizeSelectorKey(sel));
                if (matched == null) {
                    continue; // 模型幻觉的 selector 直接丢弃，防止扩展 querySelector 失败
                }
                // 回写快照里的权威 selector 字符串（空白与模型输出对齐）
                a.setSelector(matched.getSelector().trim());
                List<PluginSubAssignmentDto> subs = sanitizeSubAssignments(a.getSubAssignments(), fieldByNormKey);
                if (a.getSubAssignments() != null) {
                    a.setSubAssignments(subs);
                }
                boolean hasValue = StringUtils.hasText(a.getValue());
                boolean hasSubs = !subs.isEmpty();
                if (!hasValue && !hasSubs) {
                    continue; // 纯复合字段必须至少有一条子赋值
                }
                mergeAnchorsOntoAssignment(a, matched);
                normalizeAssignmentMeta(a);
                safe.add(a);
            }
        }

        // order 缺省排最后，便于前端按序高亮
        safe.sort(Comparator.comparingInt(a -> a.getOrder() != null ? a.getOrder() : Integer.MAX_VALUE));

        if (safe.isEmpty()) {
            return PluginAutofillPlanResultVO.builder()
                    .fillable(false)
                    .hint("当前页面虽有控件，但未能与简历字段可靠对齐；请确认处于网申/简历表单页，或手动映射后再试。")
                    .assignments(List.of())
                    .blockedFields(blockedSafe)
                    .build();
        }

        return PluginAutofillPlanResultVO.builder()
                .fillable(true)
                .hint(parsed.getHint())
                .assignments(safe)
                .blockedFields(blockedSafe)
                .build();
    }

    private static List<PluginBlockedFieldDto> sanitizeBlockedFields(
            List<PluginBlockedFieldDto> raw,
            Map<String, PluginPageFormFieldDto> fieldByNormKey) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<PluginBlockedFieldDto> out = new ArrayList<>();
        for (PluginBlockedFieldDto b : raw) {
            if (b == null || !StringUtils.hasText(b.getSelector())) {
                continue;
            }
            PluginPageFormFieldDto matched = fieldByNormKey.get(normalizeSelectorKey(b.getSelector()));
            if (matched == null) {
                continue;
            }
            String canonical = matched.getSelector().trim(); // 与 assignment 分支相同：用快照 selector
            String normReason = normalizeBlockedReason(b.getReason());
            // 新建 DTO，避免改到模型反序列化出来的可变对象在别处被误用
            PluginBlockedFieldDto copy = PluginBlockedFieldDto.builder()
                    .selector(canonical)
                    .reason(normReason)
                    .userHint(StringUtils.hasText(b.getUserHint()) ? b.getUserHint().trim() : defaultBlockedHint(normReason))
                    .dataFormFieldId(b.getDataFormFieldId())
                    .i18nLabel(b.getI18nLabel())
                    .clientFieldKey(b.getClientFieldKey())
                    .build();
            mergeAnchorsOntoBlocked(copy, matched);
            out.add(copy);
        }
        return out;
    }

    private static String normalizeBlockedReason(String r) {
        if (!StringUtils.hasText(r)) {
            return "other";
        }
        String x = r.trim().toLowerCase(Locale.ROOT);
        // 模型若发明新 reason，统一归为 other，避免前端 switch 炸开
        return switch (x) {
            case "readonly", "captcha", "attachment", "no_mapping", "ambiguous", "compound_unresolved", "policy", "other" -> x;
            default -> "other";
        };
    }

    private static String defaultBlockedHint(String reason) {
        String r = reason == null ? "other" : reason.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "readonly" -> "只读项，请手动确认";
            case "captcha" -> "验证码需人工输入";
            case "attachment" -> "附件需本地上传";
            case "no_mapping" -> "无法与简历字段对应";
            case "ambiguous" -> "语义不明确，请手动选择";
            case "compound_unresolved" -> "复合字段未能拆分，请分步填写";
            case "policy" -> "策略限制，请手动填写";
            default -> "需人工处理";
        };
    }

    private static List<PluginSubAssignmentDto> sanitizeSubAssignments(
            List<PluginSubAssignmentDto> raw,
            Map<String, PluginPageFormFieldDto> fieldByNormKey) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<PluginSubAssignmentDto> out = new ArrayList<>();
        for (PluginSubAssignmentDto s : raw) {
            if (s == null || !StringUtils.hasText(s.getSelector())) {
                continue;
            }
            PluginPageFormFieldDto matched = fieldByNormKey.get(normalizeSelectorKey(s.getSelector()));
            if (matched == null) {
                continue;
            }
            if (!StringUtils.hasText(s.getValue())) {
                continue;
            }
            // targetRole 限枚举，非法则置 null，扩展可忽略未知角色
            out.add(PluginSubAssignmentDto.builder()
                    .selector(matched.getSelector().trim())
                    .value(s.getValue().trim())
                    .targetRole(normalizeTargetRole(s.getTargetRole()))
                    .build());
        }
        return out;
    }

    /**
     * 与快照、模型回传的 selector 对齐：去首尾空白并压缩连续空白（模型偶发在 {@code >} 两侧多打空格导致白名单不匹配）。
     */
    private static String normalizeSelectorKey(String selector) {
        if (selector == null) {
            return "";
        }
        return selector.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeTargetRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String x = role.trim().toLowerCase(Locale.ROOT);
        // 与提示词中复合字段约定一致（含起止时间拆成两格）
        return switch (x) {
            case "dial_code", "local_number", "id_type", "id_number", "date_start", "date_end", "part" -> x;
            default -> null;
        };
    }

    private static void mergeAnchorsOntoAssignment(PluginFieldAssignmentDto a, PluginPageFormFieldDto field) {
        if (field == null) {
            return;
        }
        // 模型漏抄锚点时，从快照补全，仅填空不覆盖模型已填
        if (!StringUtils.hasText(a.getDataFormFieldId()) && StringUtils.hasText(field.getDataFormFieldId())) {
            a.setDataFormFieldId(field.getDataFormFieldId());
        }
        if (!StringUtils.hasText(a.getI18nLabel()) && StringUtils.hasText(field.getI18nLabel())) {
            a.setI18nLabel(field.getI18nLabel());
        }
        if (!StringUtils.hasText(a.getClientFieldKey()) && StringUtils.hasText(field.getClientFieldKey())) {
            a.setClientFieldKey(field.getClientFieldKey());
        }
    }

    private static void mergeAnchorsOntoBlocked(PluginBlockedFieldDto b, PluginPageFormFieldDto field) {
        if (field == null) {
            return;
        }
        // 与 mergeAnchorsOntoAssignment 对称，保证 blocked 行也能带锚点给前端
        if (!StringUtils.hasText(b.getDataFormFieldId()) && StringUtils.hasText(field.getDataFormFieldId())) {
            b.setDataFormFieldId(field.getDataFormFieldId());
        }
        if (!StringUtils.hasText(b.getI18nLabel()) && StringUtils.hasText(field.getI18nLabel())) {
            b.setI18nLabel(field.getI18nLabel());
        }
        if (!StringUtils.hasText(b.getClientFieldKey()) && StringUtils.hasText(field.getClientFieldKey())) {
            b.setClientFieldKey(field.getClientFieldKey());
        }
    }

    private static void normalizeAssignmentMeta(PluginFieldAssignmentDto a) {
        if (a.getMatchType() != null && StringUtils.hasText(a.getMatchType())) {
            String m = a.getMatchType().trim().toLowerCase(Locale.ROOT);
            if (!List.of("exact", "fuzzy", "guess").contains(m)) {
                a.setMatchType(null); // 非法枚举去掉，前端只展示合法三态
            } else {
                a.setMatchType(m);
            }
        }
        if (a.getConfidence() != null) {
            double c = a.getConfidence();
            if (c < 0) {
                a.setConfidence(0d);
            } else if (c > 1) {
                a.setConfidence(1d);
            }
        }
        if (a.getSkipReason() != null && StringUtils.hasText(a.getSkipReason())) {
            a.setSkipReason(a.getSkipReason().trim().toLowerCase(Locale.ROOT));
        }
    }

    private String resolveAutofillModel() {
        String m = arkProperties.getPluginAutofill().getModel();
        if (StringUtils.hasText(m)) {
            return m.trim();
        }
        return arkProperties.getResumeParse().getModel(); // 回落
    }

    private BusinessException translateArkNetworkFailure(ResourceAccessException e) {
        String baseUrl = arkProperties.getBaseUrl();
        String host;
        try {
            host = URI.create(baseUrl).getHost(); // 用户可读：指出解析失败的域名
        } catch (Exception ex) {
            host = baseUrl;
        }
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof UnknownHostException) {
            return new BusinessException("无法访问火山方舟（域名无法解析）：" + host + "。请检查网络/DNS 或 VPN。base-url=" + baseUrl);
        }
        if (cause instanceof ConnectException) {
            return new BusinessException("无法连接火山方舟（连接失败）。base-url=" + baseUrl);
        }
        String detail = cause.getMessage() != null ? cause.getMessage() : e.getMessage();
        return new BusinessException("调用火山方舟失败：" + detail);
    }

    /** 从 Jackson 异常中取出解析位置，截取原文片段便于确认是否未转义引号等 */
    private static String jsonParseErrorSnippet(String json, JsonProcessingException e) {
        JsonLocation loc = null;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof JsonParseException jpe) {
                loc = jpe.getLocation();
                break;
            }
        }
        if (loc == null || json == null || json.isEmpty()) {
            return "解析位置不可用";
        }
        int pos = -1;
        long charOff = loc.getCharOffset();
        if (charOff >= 0 && charOff < json.length()) {
            pos = (int) charOff;
        } else if (loc.getLineNr() == 1 && loc.getColumnNr() >= 0) {
            // 单行 JSON 时 column 可当作字符偏移近似
            pos = Math.min(json.length() - 1, Math.max(0, loc.getColumnNr() - 1));
        }
        if (pos < 0 || pos >= json.length()) {
            return String.format("line=%d col=%d", loc.getLineNr(), loc.getColumnNr());
        }
        int from = Math.max(0, pos - 100);
        int to = Math.min(json.length(), pos + 100);
        String excerpt = json.substring(from, to).replace('\r', ' ').replace('\n', '↵');
        return String.format("错误附近 pos≈%d: …%s…", pos, excerpt);
    }

    private static String stripMarkdownFence(String raw) {
        String s = raw;
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1); // 去掉首行 ``` 或 ```json
            }
            int end = s.lastIndexOf("```");
            if (end > 0) {
                s = s.substring(0, end); // 去掉尾部闭合围栏
            }
        }
        return s.trim();
    }
}
