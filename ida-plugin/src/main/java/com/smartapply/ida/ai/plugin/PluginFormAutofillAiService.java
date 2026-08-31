package com.smartapply.ida.ai.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.langchain4j.assistant.PluginAutofillAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.PluginAutofillPromptProvider;
import com.smartapply.ida.ai.plugin.deterministic.ChineseFieldAliasTable;
import com.smartapply.ida.ai.plugin.deterministic.DeterministicFieldMatcher;
import com.smartapply.ida.ai.plugin.deterministic.DeterministicMatchResult;
import com.smartapply.ida.ai.plugin.deterministic.OptionMatcher;
import com.smartapply.ida.ai.plugin.site.SiteTemplateMatcher;
import com.smartapply.ida.ai.plugin.site.SiteTemplateRegistry;
import com.smartapply.ida.ai.usage.AiUsageBizType;
import com.smartapply.ida.common.domain.plugin.autofill.BlockedFieldReasons;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplate;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplateMatchResult;
import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillProgressPhase;
import com.smartapply.ida.common.domain.plugin.autofill.PluginBlockedFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginSubAssignmentDto;
import com.smartapply.ida.common.domain.resume.support.ResumeDraftModuleCountsExtractor;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.module.app.resume.service.AppResumeQueryService;
import com.smartapply.ida.module.plugin.resume.autofill.PluginAutofillResultCacheService;
import com.smartapply.ida.module.plugin.resume.autofill.ResumePreloadCacheService;
import com.smartapply.ida.security.mask.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 插件端「一键填表」AI（基于 LangChain4j）：独立系统提示词身份（见 {@link PluginAutofillPromptProvider}），与简历解析链路解耦。
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

    /** LangChain4j AI Service：直接返回结构化对象，无需手写 JSON 解析 */
    private final PluginAutofillAssistant assistant;
    /** 提示词 Provider：提供系统提示词和用户消息构建 */
    private final PluginAutofillPromptProvider promptProvider;
    /** Spring 主 ObjectMapper：简历 draft 序列化、页面快照 JSON 均走同一套配置 */
    private final ObjectMapper objectMapper;
    /** 按 userId + resumeId 拉取简历详情（含 draft）；与 App 端简历接口共用查询服务 */
    private final AppResumeQueryService appResumeQueryService;
    /** 确定性字段匹配器：clientFieldKey 直接映射 + 标签启发式，覆盖 ~80% 常见字段 */
    private final DeterministicFieldMatcher deterministicFieldMatcher;
    /** PART_A 简历 JSON 相关性裁剪器：按未匹配字段移除无关模块，降低 token 消耗（召回优先） */
    private final ResumeDraftPromptPruner resumeDraftPromptPruner;
    /** 站点模板注册表：按 URL/锚点指纹匹配已标注的 ATS 模板，命中即走金标直填 */
    private final SiteTemplateRegistry siteTemplateRegistry;
    /** 站点模板匹配器：对命中的模板逐字段执行金标映射，产出高置信 assignment */
    private final SiteTemplateMatcher siteTemplateMatcher;
    /** 结果缓存：相同用户+简历+页面结构 → 命中缓存跳过 LLM */
    private final PluginAutofillResultCacheService resultCacheService;
    /** 简历预加载缓存：优先从 Redis 读取，miss 再查 DB */
    private final ResumePreloadCacheService resumePreloadCacheService;
    private final BillingService billingService;

    /**
     * 同步生成填表计划（原 {@code POST /autofill-plan} 路径）：不向外报告细粒度进度，避免重复日志。
     */
    public PluginAutofillPlanResultVO planAutofill(long userId, PluginAutofillPlanRequest req) {
        return planAutofill(userId, req, PluginAutofillProgressSink.noop());
    }

    /**
     * 生成填表计划并在关键步骤回调 {@code progress}。
     * <p>
     * <b>三阶段降级链：</b>
     * <ol>
     *   <li>站点模板匹配（命中 ATS 模板即金标直填）—— 毫秒级、~100% 准确，优先级最高</li>
     *   <li>确定性匹配（clientFieldKey 直接映射 + 标签启发式）—— ~80% 字段，< 1s 完成</li>
     *   <li>LLM 推理（仅处理仍未匹配的字段）—— prompt 体积大幅缩减，推理更快</li>
     * </ol>
     * 每阶段产出的 assignment 通过 partialResult 推送前端，RUNNING 状态轮询到即可立即开始填写，无需等待 LLM 返回。
     */
    public PluginAutofillPlanResultVO planAutofill(long userId, PluginAutofillPlanRequest req, PluginAutofillProgressSink progress) {
        PluginAutofillProgressSink p = progress != null ? progress : PluginAutofillProgressSink.noop();

        if (req == null || req.getPage() == null) {
            throw new BusinessException("请求体不完整");
        }
        p.onProgress(PluginAutofillProgressPhase.RECEIVED, "请求体与 page 快照已校验", 8);

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

        // ========== 阶段 1：加载简历（优先 Redis 预加载缓存，miss 查 DB） ==========
        p.onProgress(PluginAutofillProgressPhase.LOADING_RESUME, "正在加载简历 draft，resumeId=" + req.getResumeId(), 12);
        ResumeDetailVO detail = resumePreloadCacheService.get(userId, req.getResumeId());
        if (detail == null) {
            detail = appResumeQueryService.getResumeDetail(userId, req.getResumeId());
        }
        if (detail.getDraft() == null) {
            throw new BusinessException("简历内容为空，无法生成填表计划");
        }

        List<PluginPageFormFieldDto> fields = truncateFields(rawFields);

        // ========== 阶段 2：查结果缓存（同用户+同简历版本+同页面结构 → 直接返回） ==========
        long resumeUpdatedAtMs = detail.getUpdatedAt() != null
                ? detail.getUpdatedAt().toInstant(ZoneOffset.of("+8")).toEpochMilli()
                : 0L;
        String pageUrl = req.getPage().getPageUrl();
        PluginAutofillPlanResultVO cached = resultCacheService.get(
                userId, req.getResumeId(), resumeUpdatedAtMs, pageUrl, fields);
        if (cached != null) {
            p.onProgress(PluginAutofillProgressPhase.COMPLETED, "命中结果缓存，直接返回", 100);
            log.info("填表计划命中缓存 userId={} resumeId={}", userId, req.getResumeId());
            return cached;
        }

        // ========== 阶段 3：站点模板匹配（最高优先级，命中即金标直填，毫秒级 ~100% 准确） ==========
        p.onProgress(PluginAutofillProgressPhase.DETERMINISTIC_MATCHING, "正在执行站点模板匹配", 15);
        SiteTemplateMatchResult templateResult;
        try {
            java.util.Optional<SiteTemplate> tpl = siteTemplateRegistry.match(req.getPage());
            if (tpl.isPresent()) {
                templateResult = siteTemplateMatcher.apply(tpl.get(), fields, detail.getDraft());
            } else {
                templateResult = SiteTemplateMatchResult.empty(fields);
            }
        } catch (Exception e) {
            // 模板层任何异常 → 降级为全部字段交确定性匹配
            log.warn("站点模板匹配异常，降级全部交确定性匹配: {}", e.getMessage(), e);
            templateResult = SiteTemplateMatchResult.empty(fields);
        }

        // 推送 partialResult：模板已匹配部分可立即让前端开始填写
        if (templateResult.getMatchedAssignments() != null && !templateResult.getMatchedAssignments().isEmpty()) {
            PluginAutofillPlanResultVO partial = PluginAutofillPlanResultVO.builder()
                    .fillable(true)
                    .hint("站点模板已命中并完成部分字段直填，剩余字段继续处理")
                    .assignments(templateResult.getMatchedAssignments())
                    .blockedFields(templateResult.getBlockedFields())
                    .build();
            p.onPartialResult(partial);
            p.onProgress(PluginAutofillProgressPhase.DETERMINISTIC_MATCHING,
                    "站点模板匹配完成: 模板命中=" + templateResult.getMatchedAssignments().size()
                            + " 剩余=" + templateResult.getUnmatchedFields().size(), 18);
        }

        // ========== 阶段 4：确定性匹配（仅处理模板层未匹配的字段） ==========
        p.onProgress(PluginAutofillProgressPhase.DETERMINISTIC_MATCHING, "正在执行确定性字段匹配", 22);
        DeterministicMatchResult detResult;
        try {
            detResult = deterministicFieldMatcher.matchAll(templateResult.getUnmatchedFields(), detail.getDraft());
        } catch (Exception e) {
            // 确定性层任何异常 → 降级为全部字段交 LLM
            log.warn("确定性匹配异常，降级全部交 LLM: {}", e.getMessage(), e);
            detResult = DeterministicMatchResult.allUnmatched(templateResult.getUnmatchedFields());
        }

        // 推送 partialResult：模板 + 确定性已匹配部分，前端可在 RUNNING 状态立即开始填写
        if (!detResult.getMatchedAssignments().isEmpty()) {
            List<PluginFieldAssignmentDto> partialAssignments = new ArrayList<>(templateResult.getMatchedAssignments());
            partialAssignments.addAll(detResult.getMatchedAssignments());
            PluginAutofillPlanResultVO partial = PluginAutofillPlanResultVO.builder()
                    .fillable(true)
                    .hint("确定性匹配已完成，剩余字段正在调用 AI 处理")
                    .assignments(partialAssignments)
                    .blockedFields(mergeBlocked(templateResult.getBlockedFields(), detResult.getBlockedFields()))
                    .build();
            p.onPartialResult(partial);
            p.onProgress(PluginAutofillProgressPhase.DETERMINISTIC_MATCHING,
                    "确定性匹配完成: 已匹配=" + detResult.getMatchedAssignments().size()
                            + " 未匹配=" + detResult.getUnmatchedFields().size(), 26);
        }

        // ========== 阶段 5：已有值字段不进 LLM（不覆盖用户已填内容）；剩余为空则跳过 LLM ==========
        // 模板/确定性层会把已有 currentValue 的字段归入 unmatched（它们只负责不匹配，不负责剔除），
        // 这里在发 LLM 前统一过滤，避免模型返回覆盖用户已填内容的 assignment。
        List<PluginPageFormFieldDto> llmFields = detResult.getUnmatchedFields().stream()
                .filter(f -> !StringUtils.hasText(f.getCurrentValue()))
                .toList();
        int skippedWithValue = detResult.getUnmatchedFields().size() - llmFields.size();
        if (skippedWithValue > 0) {
            log.info("已有值字段不交给 LLM: 跳过={} 个（unmatched={} → LLM 待处理={}）",
                    skippedWithValue, detResult.getUnmatchedFields().size(), llmFields.size());
        }
        if (llmFields.isEmpty()) {
            log.info("全部字段已由模板+确定性匹配覆盖或已有值，跳过 LLM userId={} resumeId={} siteId={}",
                    userId, req.getResumeId(), templateResult.getSiteId());
            PluginAutofillPlanResultVO fullResult = PluginAutofillPlanResultVO.builder()
                    .fillable(true)
                    .siteId(templateResult.getSiteId())
                    .assignments(concatAssignments(templateResult.getMatchedAssignments(),
                            detResult.getMatchedAssignments()))
                    .blockedFields(mergeBlocked(templateResult.getBlockedFields(),
                            detResult.getBlockedFields()))
                    .build();
            // 写入结果缓存
            resultCacheService.put(userId, req.getResumeId(), resumeUpdatedAtMs, pageUrl, fields, fullResult);
            p.onProgress(PluginAutofillProgressPhase.COMPLETED, "全部字段模板/确定性匹配完成，无需调用 LLM", 100);
            return fullResult;
        }

        // ========== 阶段 6：构建缩减版 prompt（仅未匹配字段） ==========
        String resumeJson;
        try {
            resumeJson = objectMapper.writeValueAsString(detail.getDraft());
        } catch (JsonProcessingException e) {
            throw new BusinessException("简历数据序列化失败");
        }

        // PART_B 仅包含未匹配且无已有值的字段，大幅缩减 prompt 体积；allFields 用于页面结构目录
        List<PluginPageFormFieldDto> unmatchedFields = llmFields;
        String pageJson = toPageFieldsJson(req, unmatchedFields, fields);
        String moduleCountsJson;
        try {
            moduleCountsJson = objectMapper.writeValueAsString(ResumeDraftModuleCountsExtractor.fromDraft(detail.getDraft()));
        } catch (JsonProcessingException e) {
            throw new BusinessException("简历模块统计序列化失败");
        }
        p.onProgress(PluginAutofillProgressPhase.BUILDING_PROMPT,
                "已序列化简历与未匹配字段（LLM 待处理控件数=" + unmatchedFields.size()
                        + "，确定性已匹配=" + detResult.getMatchedAssignments().size() + "）", 28);

        // PART_A 相关性裁剪：按未匹配字段推断保留的简历模块，减少 token 消耗（召回优先，失败静默降级）
        String prunedResumeJson = resumeDraftPromptPruner.prune(resumeJson, unmatchedFields);
        log.info("PART_A 简历 JSON 相关性裁剪: 裁剪前={}字符 裁剪后={}字符 未匹配字段数={}",
                resumeJson.length(), prunedResumeJson.length(), unmatchedFields.size());

        // 发送 LLM 前先把简历 PII（手机号/身份证/邮箱）替换为占位符；映射仅存本次请求内存
        SensitiveDataMasker.MaskSession maskSession = SensitiveDataMasker.mask(prunedResumeJson);

        // ========== 阶段 7：调用 LangChain4j AI Service（字段少 → 推理更快） ==========
        String userMessage = promptProvider.buildUserMessage(maskSession.maskedText(), pageJson, moduleCountsJson);

        p.onProgress(PluginAutofillProgressPhase.CALLING_MODEL,
                "正在调用大模型（LLM 仅处理 " + unmatchedFields.size() + " 个未匹配字段）", 38);

        PluginAutofillPlanResultVO parsed;
        try {
            // 结果不可解析（parsed==null）视为服务未成功，不扣点不记用量
            parsed = billingService.execute(
                    userId, AiUsageBizType.PLUGIN_AUTOFILL, req.getResumeId(),
                    () -> assistant.planAutofill(PluginAutofillPromptProvider.SYSTEM, userMessage),
                    r -> r != null);
        } catch (BusinessException e) {
            // 额度不足等业务异常直接透传，不再包装为“大模型失败”
            throw e;
        } catch (Exception e) {
            // LLM 超时/网关异常：降级返回模板+确定性结果，不再让整个任务失败
            log.error("填表调用大模型异常，降级为模板+确定性结果 userId={} resumeId={}",
                    userId, req.getResumeId(), e);
            return degradeWithoutLlm(templateResult, detResult, p,
                    "AI 处理超时或暂时不可用，已先填入可确定性匹配的字段；其余字段可点击重试（将重新调用 AI 生成完整填表计划，不重复扣费），或手动补充");
        }

        if (parsed == null) {
            // 模型输出不可解析（不扣点）：同样降级为部分结果，给用户可用产出
            log.warn("模型未返回有效结果，降级为模板+确定性结果 userId={} resumeId={}", userId, req.getResumeId());
            return degradeWithoutLlm(templateResult, detResult, p,
                    "AI 暂时未返回有效结果，已先填入可确定性匹配的字段；其余字段可点击重试（将重新调用 AI 生成完整填表计划，不重复扣费），或手动补充");
        }

        // 隐私还原：把 LLM 输出中的 [PHONE_1] 等占位符换回真实简历值，保证填表写入的是真值
        restorePlanPlaceholders(parsed, maskSession);

        // 补全 fillable（如果模型未返回）
        if (!parsed.isFillable() && parsed.getAssignments() != null && !parsed.getAssignments().isEmpty()) {
            parsed.setFillable(true);
            log.info("填表模型 JSON 未包含 fillable，已根据 assignments 条数推断为 true");
        }

        p.onProgress(PluginAutofillProgressPhase.PARSING_MODEL_JSON, "模型结果已解析", 65);

        // ========== 阶段 8：清洗 LLM 结果（selector 白名单基于全量字段，确保 LLM 返回的 selector 均在快照中） ==========
        p.onProgress(PluginAutofillProgressPhase.SANITIZING_PLAN, "正在执行 selector 白名单、锚点合并与 blockedFields 清洗", 78);
        PluginAutofillPlanResultVO llmResult = sanitizePlan(parsed, fields);

        // ========== 阶段 9：合并模板 + 确定性 + LLM 结果（优先级：模板 > 确定性 > LLM） ==========
        PluginAutofillPlanResultVO merged = mergeResults(templateResult, detResult, llmResult);

        // ========== 阶段 10：写入结果缓存 ==========
        resultCacheService.put(userId, req.getResumeId(), resumeUpdatedAtMs, pageUrl, fields, merged);

        p.onProgress(PluginAutofillProgressPhase.COMPLETED, "填表计划生成完毕", 100);
        log.info("填表计划完成 userId={} resumeId={} siteId={} fillable={} assignments={} blockedFields={} "
                        + "(模板={} 确定性={} LLM={})",
                userId, req.getResumeId(), templateResult.getSiteId(), merged.isFillable(),
                merged.getAssignments() == null ? 0 : merged.getAssignments().size(),
                merged.getBlockedFields() == null ? 0 : merged.getBlockedFields().size(),
                templateResult.getMatchedAssignments() == null ? 0 : templateResult.getMatchedAssignments().size(),
                detResult.getMatchedAssignments().size(),
                llmResult.getAssignments() == null ? 0 : llmResult.getAssignments().size());
        return merged;
    }

    /**
     * 隐私还原：LLM 链路发送前用 {@link SensitiveDataMasker} 把简历 PII 替换为占位符，
     * 这里把模型返回 plan 中 value/fallbackValues/subAssignments/hint 里的占位符还原为真实值。
     */
    private void restorePlanPlaceholders(PluginAutofillPlanResultVO plan, SensitiveDataMasker.MaskSession session) {
        if (session == null || session.mapping().isEmpty()) {
            return;
        }
        plan.setHint(SensitiveDataMasker.restore(plan.getHint(), session));
        if (plan.getAssignments() != null) {
            for (PluginFieldAssignmentDto a : plan.getAssignments()) {
                if (a == null) {
                    continue;
                }
                a.setValue(SensitiveDataMasker.restore(a.getValue(), session));
                if (a.getFallbackValues() != null) {
                    a.setFallbackValues(a.getFallbackValues().stream()
                            .map(v -> SensitiveDataMasker.restore(v, session))
                            .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
                }
                if (a.getSubAssignments() != null) {
                    for (PluginSubAssignmentDto sub : a.getSubAssignments()) {
                        if (sub != null) {
                            sub.setValue(SensitiveDataMasker.restore(sub.getValue(), session));
                        }
                    }
                }
            }
        }
    }

    /**
     * 三层合并：模板匹配 > 确定性匹配 > LLM。高优先级层已覆盖的 selector，低优先级层中重复的 assignment 被丢弃。
     * order 从 1 开始重新分配（模板字段在前，确定性次之，LLM 在后）。
     */
    /**
     * LLM 不可用时的降级结果：只保留模板+确定性匹配的产出。
     * 刻意<b>不写结果缓存</b>——降级是临时态，下次重试应重新走完整 LLM 链路。
     */
    private PluginAutofillPlanResultVO degradeWithoutLlm(SiteTemplateMatchResult templateResult,
                                                         DeterministicMatchResult detResult,
                                                         PluginAutofillProgressSink p, String hint) {
        PluginAutofillPlanResultVO emptyLlm = PluginAutofillPlanResultVO.builder()
                .fillable(false)
                .hint(hint)
                .assignments(List.of())
                .blockedFields(List.of())
                .build();
        PluginAutofillPlanResultVO degraded = mergeResults(templateResult, detResult, emptyLlm);
        p.onProgress(PluginAutofillProgressPhase.COMPLETED, "LLM 不可用，已降级返回确定性匹配结果", 100);
        return degraded;
    }

    // 包私有以便单测直接验证三层合并与 assignment/blocked 互斥
    PluginAutofillPlanResultVO mergeResults(SiteTemplateMatchResult templateResult,                                                    DeterministicMatchResult detResult,
                                                    PluginAutofillPlanResultVO llmResult) {
        List<PluginFieldAssignmentDto> merged = new ArrayList<>();
        Set<String> coveredSelectors = new HashSet<>();

        // 第 1 层：模板匹配（最高优先级）
        if (templateResult.getMatchedAssignments() != null) {
            for (PluginFieldAssignmentDto a : templateResult.getMatchedAssignments()) {
                coveredSelectors.add(normalizeSelectorKey(a.getSelector()));
                merged.add(a);
            }
        }

        // 第 2 层：确定性匹配（跳过模板已覆盖的 selector）
        for (PluginFieldAssignmentDto a : detResult.getMatchedAssignments()) {
            String normSel = normalizeSelectorKey(a.getSelector());
            if (!coveredSelectors.contains(normSel)) {
                coveredSelectors.add(normSel);
                merged.add(a);
            }
        }

        // 第 3 层：LLM（跳过上层已覆盖的 selector）
        if (llmResult.getAssignments() != null) {
            for (PluginFieldAssignmentDto a : llmResult.getAssignments()) {
                String normSel = normalizeSelectorKey(a.getSelector());
                if (!coveredSelectors.contains(normSel)) {
                    coveredSelectors.add(normSel);
                    merged.add(a);
                }
            }
        }

        // 重新编号 order（模板 → 确定性 → LLM）
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).setOrder(i + 1);
        }

        // 合并 blockedFields（三层去重）
        List<PluginBlockedFieldDto> allBlocked = mergeBlocked(
                templateResult.getBlockedFields(),
                detResult.getBlockedFields());
        Set<String> blockedSelectors = new HashSet<>();
        for (PluginBlockedFieldDto b : allBlocked) {
            blockedSelectors.add(normalizeSelectorKey(b.getSelector()));
        }
        if (llmResult.getBlockedFields() != null) {
            for (PluginBlockedFieldDto b : llmResult.getBlockedFields()) {
                if (!blockedSelectors.contains(normalizeSelectorKey(b.getSelector()))) {
                    allBlocked.add(b);
                }
            }
        }

        // 互斥：同一 selector 已有 assignment 时剔除其 blocked 项——模型偶发把同一控件同时写进
        // assignments 与 blockedFields（如区号下拉既给值又报 compound_unresolved），以填入为准
        allBlocked.removeIf(b -> coveredSelectors.contains(normalizeSelectorKey(b.getSelector())));

        boolean fillable = !merged.isEmpty() || (llmResult.isFillable());
        return PluginAutofillPlanResultVO.builder()
                .fillable(fillable)
                .siteId(templateResult.getSiteId())
                .hint(llmResult.getHint())
                .assignments(merged)
                .blockedFields(allBlocked)
                .build();
    }

    /** 拼接两层 assignment 列表（不去重，调用方保证无重复或后续 merge 去重） */
    private List<PluginFieldAssignmentDto> concatAssignments(List<PluginFieldAssignmentDto> first,
                                                             List<PluginFieldAssignmentDto> second) {
        List<PluginFieldAssignmentDto> out = new ArrayList<>(
                first != null ? first : List.of());
        if (second != null) {
            out.addAll(second);
        }
        return out;
    }

    /** 合并两个 blockedFields 列表，按规范化 selector 去重，保留先出现者 */
    private List<PluginBlockedFieldDto> mergeBlocked(List<PluginBlockedFieldDto> first,
                                                     List<PluginBlockedFieldDto> second) {
        List<PluginBlockedFieldDto> out = new ArrayList<>(first != null ? first : List.of());
        Set<String> seen = new HashSet<>();
        for (PluginBlockedFieldDto b : out) {
            seen.add(normalizeSelectorKey(b.getSelector()));
        }
        if (second != null) {
            for (PluginBlockedFieldDto b : second) {
                if (seen.add(normalizeSelectorKey(b.getSelector()))) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    private List<PluginPageFormFieldDto> truncateFields(List<PluginPageFormFieldDto> rawFields) {
        if (rawFields.size() <= MAX_FIELDS) {
            return rawFields; // 保持原列表引用，避免无谓拷贝
        }
        log.info("插件填表：控件数={} 超过上限 {}，已截断", rawFields.size(), MAX_FIELDS);
        return new ArrayList<>(rawFields.subList(0, MAX_FIELDS)); // 只保留前 N 个，后续白名单也只认这批
    }

    private String toPageFieldsJson(PluginAutofillPlanRequest req, List<PluginPageFormFieldDto> fields,
                                    List<PluginPageFormFieldDto> allFields) {
        try {
            // 与提示词 PART_B 约定一致：扁平对象；fields 为 PluginPageFormFieldDto 列表（Jackson 序列化可选字段 compoundSegment、interactionMode、optionLabelsSample 等）
            // pageStructure：全页面字段的轻量目录（仅标签+是否已匹配，按 DOM 顺序），供模型判断未匹配字段所属模块块，
            // 解决「PART_B 只含未匹配字段后，模型丢失块归属上下文，把多模块内容拼进同一描述框」的问题
            Map<String, Object> wrap = Map.of(
                    "pageUrl", req.getPage().getPageUrl() != null ? req.getPage().getPageUrl() : "",
                    "pageTitle", req.getPage().getPageTitle() != null ? req.getPage().getPageTitle() : "",
                    "fields", fields,
                    "pageStructure", buildPageStructure(allFields, fields)
            );
            return objectMapper.writeValueAsString(wrap);
        } catch (JsonProcessingException e) {
            throw new BusinessException("页面快照序列化失败");
        }
    }

    /**
     * 构建页面结构目录：每个字段一行 {i, label, matched}，label 取标签/锚点/占位符中首个非空值。
     * 体积刻意极小（无 selector、无值），只承担「块归属定位」职责。
     */
    private List<Map<String, Object>> buildPageStructure(List<PluginPageFormFieldDto> allFields,
                                                         List<PluginPageFormFieldDto> unmatchedFields) {
        Set<String> unmatchedKeys = new HashSet<>();
        for (PluginPageFormFieldDto f : unmatchedFields) {
            unmatchedKeys.add(normalizeSelectorKey(f.getSelector()));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int i = 1;
        for (PluginPageFormFieldDto f : allFields) {
            String label = firstNonBlank(f.getLabelText(), f.getI18nLabel(), f.getPlaceholder(),
                    f.getCompoundSegment(), f.getName(), f.getId());
            rows.add(Map.of(
                    "i", i++,
                    "label", label != null ? label : "",
                    "matched", !unmatchedKeys.contains(normalizeSelectorKey(f.getSelector()))
            ));
        }
        return rows;
    }

    private static String firstNonBlank(String... texts) {
        for (String t : texts) {
            if (StringUtils.hasText(t)) {
                return t.trim();
            }
        }
        return null;
    }

    /**
     * 安全裁剪：selector/subSelector 必须在快照内；合并客户端锚点；规范化 reason/matchType；
     * 出口枚举校验（快照带选项样本且非开放联想框时 value 必须命中选项，否则转 blocked）；
     * 无值 assignment 带 skipReason 时转 blocked，否则剔除。
     * <p>
     * 使用 {@link #normalizeSelectorKey(String)} 建索引：模型回传的 CSS 路径可能与扩展快照仅有空白差异，
     * 匹配成功后把 {@link PluginFieldAssignmentDto#setSelector(String)} 回写为快照中的「权威」字符串，保证扩展 querySelector 可用。
     */
    // 包私有以便单测直接验证清洗与复合字段归一
    PluginAutofillPlanResultVO sanitizePlan(PluginAutofillPlanResultVO parsed, List<PluginPageFormFieldDto> fields) {
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

        // 同样按白名单过滤 blockedFields，并补默认 userHint；可编辑列表：出口校验会追加新的 blocked 项
        List<PluginBlockedFieldDto> blockedSafe = new ArrayList<>(sanitizeBlockedFields(parsed.getBlockedFields(), fieldByNormKey));

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
                // 复合字段归一：快照标了 compoundSegment 而模型把值写在了主 value（未用 subAssignments）时，
                // 转为带 targetRole 的子赋值（date_start/date_end 等），前端据此走对应的控件交互，
                // 并清空主 value，避免前端用主 value 覆盖复合字段整行容器
                if (StringUtils.hasText(a.getValue()) && subs.isEmpty()
                        && StringUtils.hasText(matched.getCompoundSegment())) {
                    subs = List.of(PluginSubAssignmentDto.builder()
                            .selector(matched.getSelector().trim())
                            .value(a.getValue().trim())
                            .targetRole(matched.getCompoundSegment().trim().toLowerCase())
                            .build());
                    a.setSubAssignments(subs);
                    a.setValue(null);
                }
                boolean hasValue = StringUtils.hasText(a.getValue());
                boolean hasSubs = !subs.isEmpty();
                if (!hasValue && !hasSubs) {
                    // 模型按提示词规则对无法匹配选项/无法映射的字段会输出空 value + skipReason：
                    // 不静默丢弃，转 blocked 让前端提示用户手动处理
                    if (StringUtils.hasText(a.getSkipReason())) {
                        addBlockedIfAbsent(blockedSafe, matched, a.getSkipReason(), null);
                    }
                    continue;
                }
                // 出口枚举校验：快照带选项样本且非开放联想框时，value 必须落在选项名录内，
                // 防模型输出「精通」这类不在选项内的值直接透传到页面；命中但文案不一致时对齐为选项原文
                if (hasValue && matched.getOptionLabelsSample() != null
                        && !matched.getOptionLabelsSample().isEmpty()) {
                    String category = ChineseFieldAliasTable.inferEnumCategory(matched.getClientFieldKey());
                    if (!OptionMatcher.isOpenTypeahead(matched.getInteractionMode(), category)) {
                        String optionMatch = OptionMatcher.matchOption(
                                a.getValue(), matched.getOptionLabelsSample(), category);
                        if (optionMatch == null) {
                            log.info("出口枚举校验未通过，assignment 转 blocked: selector={}, value={}, options={}",
                                    matched.getSelector(), a.getValue(), matched.getOptionLabelsSample());
                            addBlockedIfAbsent(blockedSafe, matched,
                                    BlockedFieldReasons.AMBIGUOUS, "未匹配到合适的下拉选项，请手动选择");
                            continue;
                        }
                        a.setValue(optionMatch);
                    }
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
            return BlockedFieldReasons.OTHER;
        }
        String x = r.trim().toLowerCase(Locale.ROOT);
        // 模型若发明新 reason，统一归为 other，避免前端 switch 炸开；值域以 BlockedFieldReasons 为准
        return BlockedFieldReasons.isValid(x) ? x : BlockedFieldReasons.OTHER;
    }

    /**
     * assignment 无法安全下发时转为 blocked：从快照带回锚点；同 selector 已有 blocked 项则不重复添加。
     */
    private static void addBlockedIfAbsent(List<PluginBlockedFieldDto> blockedSafe,
                                           PluginPageFormFieldDto field, String reason, String userHint) {
        String normSel = normalizeSelectorKey(field.getSelector());
        for (PluginBlockedFieldDto b : blockedSafe) {
            if (normalizeSelectorKey(b.getSelector()).equals(normSel)) {
                return;
            }
        }
        String normReason = normalizeBlockedReason(reason);
        PluginBlockedFieldDto blocked = PluginBlockedFieldDto.builder()
                .selector(field.getSelector().trim())
                .reason(normReason)
                .userHint(StringUtils.hasText(userHint) ? userHint : defaultBlockedHint(normReason))
                .build();
        mergeAnchorsOntoBlocked(blocked, field);
        blockedSafe.add(blocked);
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
            if (!List.of("exact", "fuzzy", "guess", "deterministic").contains(m)) {
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
}
