package com.smartapply.ida.ai.plugin.deterministic;

import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 确定性字段匹配器：在调用 LLM 之前，通过 clientFieldKey 直接映射和标签启发式匹配，
 * 将能确定性匹配的表单字段即时赋值，仅将无法匹配的字段交给 LLM 处理。
 * <p>
 * 匹配策略优先级：
 * <ol>
 *   <li>clientFieldKey 直接映射（confidence=0.98）——前端扩展标注了标准化的字段 key</li>
 *   <li>标签启发式匹配（confidence=0.85）——基于 labelText/placeholder 在别名表中查找</li>
 *   <li>未命中任何策略 → 交给 LLM</li>
 * </ol>
 * 整个匹配过程被 try-catch 包裹，任何异常均降级为「全部交给 LLM」，不影响主流程。
 */
@Slf4j
@Component
public class DeterministicFieldMatcher {

    /** textarea 和长文本字段需要 LLM 做多字段拼接，不适合确定性匹配 */
    private static final Set<String> LONG_TEXT_INTERACTION_MODES = Set.of("textarea");

    /**
     * 长文本类字段路径——这些字段即使能提取到值，也应交给 LLM 处理，
     * 因为网申表单中这类字段通常需要将简历中多个子字段（职责、成果、描述）拼接成完整段落。
     */
    private static final Set<String> LONG_TEXT_FIELD_SUFFIXES = Set.of(
            "selfIntro", "strengths", "workDescription", "dutyDescription",
            "achievement", "description", "majorCourses"
    );

    /**
     * 对页面快照中的全部字段执行确定性匹配。
     *
     * @param fields 经截断后的表单字段列表
     * @param draft  用户简历草稿
     * @return 匹配结果分区：已匹配 + 未匹配 + blocked
     */
    public DeterministicMatchResult matchAll(List<PluginPageFormFieldDto> fields, ResumeParseDraftDto draft) {
        List<PluginFieldAssignmentDto> matched = new ArrayList<>();
        List<PluginPageFormFieldDto> unmatched = new ArrayList<>();

        // 诊断用：打印前 5 个字段的原始数据，便于排查前端采集格式
        int sampleLimit = Math.min(fields.size(), 5);
        for (int i = 0; i < sampleLimit; i++) {
            PluginPageFormFieldDto f = fields.get(i);
            log.info("确定性匹配[字段样本{}] selector={}, tagName={}, inputType={}, interactionMode={}, "
                            + "compoundSegment={}, clientFieldKey={}, labelText={}, placeholder={}, name={}, "
                            + "currentValue={}, optionLabelsSample.size={}",
                    i, f.getSelector(), f.getTagName(), f.getInputType(), f.getInteractionMode(),
                    f.getCompoundSegment(), f.getClientFieldKey(), f.getLabelText(), f.getPlaceholder(),
                    f.getName(), f.getCurrentValue(),
                    f.getOptionLabelsSample() != null ? f.getOptionLabelsSample().size() : 0);
        }

        // 统计各跳过原因
        int skipCurrentValue = 0;
        int skipLongText = 0;
        int skipCompound = 0;
        int strategy1Fail = 0;
        int strategy2Fail = 0;
        int strategy1HasKey = 0;   // 有 clientFieldKey 但未知
        int strategy1NoValue = 0;  // key 已知但简历值为空
        int strategy2NoLabel = 0;  // 标签在别名表未命中
        int strategy2Collection = 0; // 集合字段无法定索引
        int strategy2NoValue = 0;  // 标签命中但简历值为空

        int order = 1;
        for (PluginPageFormFieldDto field : fields) {
            String sel = field.getSelector();

            // 已有预填值的字段不覆盖
            if (StringUtils.hasText(field.getCurrentValue())) {
                skipCurrentValue++;
                unmatched.add(field);
                continue;
            }

            // textarea / 长文本字段一律交 LLM（需多字段拼接）
            if (isLongTextField(field)) {
                skipLongText++;
                unmatched.add(field);
                continue;
            }

            // compound 字段交 LLM 处理 subAssignments
            if (StringUtils.hasText(field.getCompoundSegment())) {
                skipCompound++;
                unmatched.add(field);
                continue;
            }

            // 策略 1：clientFieldKey 直接映射
            String ck = field.getClientFieldKey();
            boolean hasKey = StringUtils.hasText(ck);
            boolean knownKey = hasKey && ResumeFieldAccessor.isKnownKey(ck);
            if (knownKey) {
                String rawValue = ResumeFieldAccessor.extractValue(draft, ck);
                if (rawValue != null) {
                    // key 已知且有值 → 尝试构建 assignment
                    PluginFieldAssignmentDto assignment = tryClientFieldKeyMatch(field, draft, order);
                    if (assignment != null) {
                        matched.add(assignment);
                        order++;
                        continue;
                    }
                    // tryClientFieldKeyMatch 返回 null 说明下拉选项匹配失败
                    strategy2Fail++; // 归入策略2统计（下拉匹配失败本质上是未命中）
                } else {
                    strategy1NoValue++;
                }
            } else if (hasKey) {
                strategy1HasKey++; // 有 key 但注册表不识别
            }

            // 策略 2：标签启发式匹配（策略 1 未命中时无条件尝试）
            PluginFieldAssignmentDto assignment = tryLabelMatch(field, draft, order);
            if (assignment != null) {
                matched.add(assignment);
                order++;
                continue;
            }

            // 诊断策略2失败原因
            String labelPath = resolveByLabels(field);
            if (labelPath == null) {
                strategy2NoLabel++;
            } else if (labelPath.contains("[?]")) {
                strategy2Collection++;
            } else {
                String rawValue = ResumeFieldAccessor.extractValue(draft, labelPath);
                if (rawValue == null) {
                    strategy2NoValue++;
                } else {
                    strategy2Fail++; // 有值但下拉匹配失败
                }
            }

            unmatched.add(field);
        }

        log.info("确定性匹配完成: 总字段={}, 已匹配={}, 未匹配={} | "
                        + "原因分布: [已有值跳过={}, 长文本跳过={}, 复合跳过={}, "
                        + "策略1-key未知={}, 策略1-值为空={}, "
                        + "策略2-标签未命中={}, 策略2-集合字段={}, 策略2-值为空={}, 策略2-下拉失败={}]",
                fields.size(), matched.size(), unmatched.size(),
                skipCurrentValue, skipLongText, skipCompound,
                strategy1HasKey, strategy1NoValue,
                strategy2NoLabel, strategy2Collection, strategy2NoValue, strategy2Fail);
        return DeterministicMatchResult.builder()
                .matchedAssignments(matched)
                .unmatchedFields(unmatched)
                .build();
    }

    /**
     * 策略 1：通过 clientFieldKey 直接从简历 draft 中提取值。
     */
    private PluginFieldAssignmentDto tryClientFieldKeyMatch(
            PluginPageFormFieldDto field, ResumeParseDraftDto draft, int order) {

        String key = field.getClientFieldKey();
        if (!StringUtils.hasText(key) || !ResumeFieldAccessor.isKnownKey(key)) {
            return null;
        }

        String rawValue = ResumeFieldAccessor.extractValue(draft, key);
        if (rawValue == null) {
            return null;
        }

        // 下拉字段需要将简历值映射到选项文案
        String finalValue = rawValue;
        List<String> fallbacks = List.of();
        if (hasOptions(field)) {
            String fieldName = ResumeFieldAccessor.extractFieldName(key);
            String enumCategory = ChineseFieldAliasTable.inferEnumCategory(fieldName);
            String optionMatch = OptionMatcher.matchOption(rawValue, field.getOptionLabelsSample(), enumCategory);
            if (optionMatch == null) {
                // 下拉选项匹配失败 → 交给 LLM，它可能有更好的语义理解
                return null;
            }
            finalValue = optionMatch;
            // 生成备选值：原始值 + 选项匹配值（如果不同的话）
            fallbacks = buildFallbackValues(rawValue, optionMatch);
        }

        return buildAssignment(field, finalValue, fallbacks, order, 0.98, "deterministic");
    }

    /**
     * 策略 2：通过 labelText / placeholder 在别名表中查找。
     * 仅用于 clientFieldKey 缺失的场景（兼容旧版前端）。
     */
    private PluginFieldAssignmentDto tryLabelMatch(
            PluginPageFormFieldDto field, ResumeParseDraftDto draft, int order) {

        String fieldPath = resolveByLabels(field);
        if (fieldPath == null) {
            return null;
        }

        // 集合字段（含 [?]）无法确定索引，交给 LLM
        if (fieldPath.contains("[?]")) {
            return null;
        }

        // 用解析出的 fieldPath 构造一个虚拟 clientFieldKey 进行值提取
        String rawValue = ResumeFieldAccessor.extractValue(draft, fieldPath);
        if (rawValue == null) {
            return null;
        }

        // 下拉匹配
        String finalValue = rawValue;
        List<String> fallbacks = List.of();
        if (hasOptions(field)) {
            String enumCategory = ChineseFieldAliasTable.inferEnumCategory(fieldPath);
            String optionMatch = OptionMatcher.matchOption(rawValue, field.getOptionLabelsSample(), enumCategory);
            if (optionMatch == null) {
                return null;
            }
            finalValue = optionMatch;
            fallbacks = buildFallbackValues(rawValue, optionMatch);
        }

        return buildAssignment(field, finalValue, fallbacks, order, 0.85, "deterministic");
    }

    /**
     * 依次尝试 labelText、placeholder、name、id 在别名表中查找。
     */
    private String resolveByLabels(PluginPageFormFieldDto field) {
        for (String text : new String[]{field.getLabelText(), field.getPlaceholder(), field.getName(), field.getId()}) {
            if (!StringUtils.hasText(text)) continue;
            String normalized = normalizeLabel(text);
            String path = ChineseFieldAliasTable.resolveByLabel(normalized);
            if (path != null) return path;
        }
        return null;
    }

    /** 标签归一化：去首尾空格、常见后缀和前缀指令词 */
    private String normalizeLabel(String label) {
        if (label == null) return "";
        String s = label.trim()
                // 去常见后缀
                .replaceAll("[：:*＊]$", "")
                .replaceAll("\\(必填\\)$", "")
                .replaceAll("（必填）$", "")
                // 去常见前缀指令词（"请输入"、"请选择"、"请填写" 等）
                .replaceAll("^请输入", "")
                .replaceAll("^请选择", "")
                .replaceAll("^请填写", "")
                .replaceAll("^选择", "")
                .trim();
        // 归一化后若为空（比如原始标签就是"请输入"），保留原始值
        return s.isEmpty() ? label.trim() : s;
    }

    private boolean isLongTextField(PluginPageFormFieldDto field) {
        String mode = field.getInteractionMode();
        if (mode != null && LONG_TEXT_INTERACTION_MODES.contains(mode.toLowerCase())) {
            return true;
        }
        if ("textarea".equalsIgnoreCase(field.getTagName())) {
            return true;
        }
        // 通过 clientFieldKey 判断是否为长文本字段
        String key = field.getClientFieldKey();
        if (StringUtils.hasText(key)) {
            String fieldName = ResumeFieldAccessor.extractFieldName(key);
            return LONG_TEXT_FIELD_SUFFIXES.contains(fieldName);
        }
        return false;
    }

    private boolean hasOptions(PluginPageFormFieldDto field) {
        return field.getOptionLabelsSample() != null && !field.getOptionLabelsSample().isEmpty();
    }

    private List<String> buildFallbackValues(String rawValue, String matchedOption) {
        List<String> fallbacks = new ArrayList<>();
        // 若匹配值和原始值不同，将原始值作为备选
        if (!rawValue.equals(matchedOption)) {
            fallbacks.add(rawValue);
        }
        return fallbacks;
    }

    private PluginFieldAssignmentDto buildAssignment(
            PluginPageFormFieldDto field, String value, List<String> fallbackValues,
            int order, double confidence, String matchType) {
        return PluginFieldAssignmentDto.builder()
                .selector(field.getSelector())
                .value(value)
                .order(order)
                .confidence(confidence)
                .matchType(matchType)
                .fallbackValues(fallbackValues != null ? fallbackValues : List.of())
                .dataFormFieldId(field.getDataFormFieldId())
                .i18nLabel(field.getI18nLabel())
                .clientFieldKey(field.getClientFieldKey())
                .build();
    }
}
