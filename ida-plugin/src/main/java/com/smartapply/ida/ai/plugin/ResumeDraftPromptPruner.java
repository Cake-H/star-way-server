package com.smartapply.ida.ai.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartapply.ida.ai.plugin.deterministic.ChineseFieldAliasTable;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PART_A 简历 JSON 相关性裁剪器：根据「未匹配字段」推断本次 LLM 调用真正需要的简历模块，
 * 移除无关的顶层模块节点，降低 token 消耗并避免长简历稀释模型注意力。
 * <p>
 * 裁剪原则为<b>召回优先</b>：
 * <ul>
 *   <li>basic、location 模块（顶层标量字段）始终保留；</li>
 *   <li>其余模块按未匹配字段的 clientFieldKey 前缀、labelText 别名命中、compoundSegment 语义推断保留集合。
 *       模块推断<b>仅用静态别名表的精确匹配</b>（{@code ChineseFieldAliasTable#resolveByLabel}），
 *       不含 contains 匹配与动态（DB）别名——与 DeterministicFieldMatcher 的「每个文本候选内四级」查找不同；
 *       推断不到即保守保留全量，不做裁剪；</li>
 *   <li>存在 textarea 未匹配字段时保守保留 profile 及 work/project 模块（长文本需多字段拼接）；</li>
 *   <li>任一未匹配字段无法推断所属模块 → 返回原始 JSON，不做裁剪；</li>
 *   <li>解析/序列化任何异常 → 静默降级返回原始 JSON（log.warn）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeDraftPromptPruner {

    /**
     * 模块名（与 {@code clientFieldKey} / 别名表路径前缀一致）→ 序列化后 draft JSON 的顶层 key 集合。
     * basic/location/mailing/emergency/job/profile 为顶层标量字段分组，其余为子表 List 节点。
     */
    private static final Map<String, Set<String>> MODULE_TO_JSON_KEYS = Map.ofEntries(
            Map.entry("basic", Set.of("title", "fullName", "gender", "birthDate", "idDocType",
                    "idDocNumber", "phone", "email", "qq", "wechat")),
            Map.entry("location", Set.of("countryRegion", "nativePlace", "ethnicity", "politicalStatus",
                    "hukouType", "province", "city", "district", "currentCity", "detailAddress")),
            Map.entry("mailing", Set.of("mailingSameAsCurrent", "mailingAddress")),
            Map.entry("emergency", Set.of("emergencyContactName", "emergencyContactPhone", "emergencyContactRelation")),
            Map.entry("job", Set.of("jobSeekingStatus", "isFreshGraduate", "workYears", "expectedPositions",
                    "expectedCities", "expectedIndustries", "salaryExpectation", "availability", "acceptTransfer")),
            Map.entry("profile", Set.of("selfIntro", "strengths", "portfolioLinks")),
            Map.entry("education", Set.of("educations")),
            Map.entry("work", Set.of("works")),
            Map.entry("project", Set.of("projects")),
            Map.entry("campus", Set.of("campusExperiences")),
            Map.entry("skill", Set.of("skills")),
            Map.entry("language", Set.of("languages")),
            Map.entry("certificate", Set.of("certificates")),
            Map.entry("award", Set.of("awards")),
            Map.entry("publication", Set.of("publications"))
    );

    /** 始终保留的模块：基本信息与地域信息是几乎所有表单的公共上下文 */
    private static final Set<String> ALWAYS_KEPT_MODULES = Set.of("basic", "location");

    /** compoundSegment=id_type/id_number → 证件信息在 basic 模块 */
    private static final Set<String> COMPOUND_ID_SEGMENTS = Set.of("id_type", "id_number");
    /** compoundSegment=dial_code/local_number → 手机号在 basic 模块 */
    private static final Set<String> COMPOUND_PHONE_SEGMENTS = Set.of("dial_code", "local_number");
    /** compoundSegment=date_start/date_end → 起止时间无法定位具体模块，保守保留三类经历 */
    private static final Set<String> COMPOUND_DATE_SEGMENTS = Set.of("date_start", "date_end");
    private static final Set<String> COMPOUND_DATE_MODULES = Set.of("education", "work", "project");

    /** 存在 textarea 未匹配字段时保守保留的模块（长文本拼接来源，见 DeterministicFieldMatcher 长文本语义） */
    private static final Set<String> TEXTAREA_MODULES = Set.of("profile", "work", "project");

    /**
     * 无法映射到具体简历字段、但能推断所属模块的常见表单标签 → 模块。
     * 仅用于裁剪推断（减少「推断不到即全量保留」的绕过），不参与字段赋值。
     */
    private static final Map<String, Set<String>> LABEL_MODULE_HINTS = Map.of(
            "实验室", Set.of("education"),
            "领域方向", Set.of("education"),
            "研究方向", Set.of("education"),
            "导师", Set.of("education"),
            "指导老师", Set.of("education"),
            "项目链接", Set.of("project"),
            "项目地址", Set.of("project"),
            // 起止时间无 compoundSegment 标注时无法定位具体模块，保守保留三类经历（与 date 复合段同策略）
            "起止时间", Set.of("education", "work", "project")
    );

    private final ObjectMapper objectMapper;

    /**
     * 按未匹配字段相关性裁剪 draft JSON。
     *
     * @param resumeJson      简历 draft 序列化后的 JSON 字符串
     * @param unmatchedFields 模板 + 确定性匹配后仍未匹配的页面字段
     * @return 裁剪后的 JSON 字符串；无法安全裁剪时返回原始 JSON
     */
    public String prune(String resumeJson, List<PluginPageFormFieldDto> unmatchedFields) {
        if (!StringUtils.hasText(resumeJson) || unmatchedFields == null || unmatchedFields.isEmpty()) {
            return resumeJson;
        }
        try {
            JsonNode root = objectMapper.readTree(resumeJson);
            if (!(root instanceof ObjectNode rootObject)) {
                return resumeJson;
            }

            Set<String> keptModules = inferKeptModules(unmatchedFields);
            if (keptModules == null) {
                // 存在无法推断模块的未匹配字段 → 召回优先，全量保留
                log.info("PART_A 裁剪跳过：存在无法推断模块的未匹配字段，保留全部简历模块");
                return resumeJson;
            }

            Set<String> keysToRemove = new HashSet<>();
            MODULE_TO_JSON_KEYS.forEach((module, keys) -> {
                if (!keptModules.contains(module)) {
                    keysToRemove.addAll(keys);
                }
            });
            keysToRemove.forEach(rootObject::remove);
            return objectMapper.writeValueAsString(rootObject);
        } catch (Exception e) {
            // 静默降级：裁剪失败绝不影响主流程
            log.warn("PART_A 简历 JSON 裁剪失败，降级为全量发送: {}", e.getMessage());
            return resumeJson;
        }
    }

    /**
     * 推断需要保留的模块集合。
     *
     * @return 保留集合（含 basic/location）；任一字段无法推断时返回 null 表示放弃裁剪
     */
    private Set<String> inferKeptModules(List<PluginPageFormFieldDto> unmatchedFields) {
        Set<String> kept = new HashSet<>(ALWAYS_KEPT_MODULES);
        for (PluginPageFormFieldDto field : unmatchedFields) {
            Set<String> inferred = inferFieldModules(field);
            if (inferred.isEmpty() && !isTextareaField(field)) {
                return null;
            }
            kept.addAll(inferred);
            if (isTextareaField(field)) {
                // textarea 长文本需拼接自我介绍/工作描述/项目职责等，保守保留来源模块
                kept.addAll(TEXTAREA_MODULES);
            }
        }
        return kept;
    }

    /**
     * 从单个未匹配字段推断所属模块：compoundSegment → clientFieldKey 前缀 → labelText/placeholder/name/id
     * 别名命中 → 模块提示表（{@link #LABEL_MODULE_HINTS}，覆盖「实验室/导师/项目链接」这类无对应简历字段
     * 但语义可定位模块的标签）。
     * <p>
     * 注意：别名命中仅用静态精确匹配（{@code ChineseFieldAliasTable#resolveByLabel}），
     * 不做 contains / 动态别名查找——推断不到即返回空集合，由上层保守保留全量。
     */
    private Set<String> inferFieldModules(PluginPageFormFieldDto field) {
        Set<String> modules = new HashSet<>();

        String segment = field.getCompoundSegment();
        if (StringUtils.hasText(segment)) {
            String seg = segment.trim().toLowerCase();
            if (COMPOUND_ID_SEGMENTS.contains(seg) || COMPOUND_PHONE_SEGMENTS.contains(seg)) {
                modules.add("basic");
            } else if (COMPOUND_DATE_SEGMENTS.contains(seg)) {
                modules.addAll(COMPOUND_DATE_MODULES);
            }
        }

        String key = field.getClientFieldKey();
        if (StringUtils.hasText(key)) {
            String module = moduleFromFieldKey(key.trim());
            if (module != null) {
                modules.add(module);
            }
        }

        // 与 DeterministicFieldMatcher 相同的标签来源顺序，提升召回
        for (String text : new String[]{field.getLabelText(), field.getPlaceholder(), field.getName(), field.getId()}) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String normalized = normalizeLabel(text);
            String path = ChineseFieldAliasTable.resolveByLabel(normalized);
            if (path != null) {
                String module = path.substring(0, path.indexOf('.'));
                if (MODULE_TO_JSON_KEYS.containsKey(module)) {
                    modules.add(module);
                }
                break;
            }
            // 别名表 miss 后查模块提示表：标签语义能定位模块（如无对应简历字段的「实验室」「导师」）
            Set<String> hinted = LABEL_MODULE_HINTS.get(normalized);
            if (hinted != null) {
                modules.addAll(hinted);
                break;
            }
        }
        return modules;
    }

    /**
     * 从 clientFieldKey 推断模块：形如 {@code work.0.companyName} 取首段；
     * 无点号的扁平 key（如 {@code phone}）在各模块的 JSON key 中反查。
     */
    private String moduleFromFieldKey(String key) {
        int dot = key.indexOf('.');
        if (dot > 0) {
            String prefix = key.substring(0, dot);
            return MODULE_TO_JSON_KEYS.containsKey(prefix) ? prefix : null;
        }
        for (Map.Entry<String, Set<String>> entry : MODULE_TO_JSON_KEYS.entrySet()) {
            if (entry.getValue().contains(key)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isTextareaField(PluginPageFormFieldDto field) {
        if ("textarea".equalsIgnoreCase(field.getTagName())) {
            return true;
        }
        String mode = field.getInteractionMode();
        return mode != null && "textarea".equalsIgnoreCase(mode.trim());
    }

    /** 与 DeterministicFieldMatcher 一致的标签归一化：去首尾空格、常见后缀与前缀指令词 */
    private String normalizeLabel(String label) {
        if (label == null) {
            return "";
        }
        String s = label.trim()
                .replaceAll("[：:*＊]$", "")
                .replaceAll("\\(必填\\)$", "")
                .replaceAll("（必填）$", "")
                .replaceAll("^请输入", "")
                .replaceAll("^请选择", "")
                .replaceAll("^请填写", "")
                .replaceAll("^选择", "")
                .trim();
        return s.isEmpty() ? label.trim() : s;
    }
}
