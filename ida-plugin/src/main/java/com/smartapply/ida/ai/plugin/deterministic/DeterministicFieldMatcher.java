package com.smartapply.ida.ai.plugin.deterministic;

import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 确定性字段匹配器：在调用 LLM 之前，通过 clientFieldKey 直接映射和标签启发式匹配，
 * 将能确定性匹配的表单字段即时赋值，仅将无法匹配的字段交给 LLM 处理。
 * <p>
 * 匹配策略优先级：
 * <ol>
 *   <li>clientFieldKey 直接映射（confidence=0.98, matchType=deterministic）——前端扩展标注了标准化的字段 key</li>
 *   <li>标签启发式匹配——基于 labelText/placeholder/name/id 依次查找：
 *     <ol>
 *       <li>静态别名表精确命中（confidence=0.85, matchType=deterministic）</li>
 *       <li>静态别名表最长包含命中（confidence=0.85, matchType=deterministic）</li>
 *       <li>动态别名表精确命中（confidence=0.80, matchType=dynamic_alias）——来自
 *           {@link DynamicFieldAliasService}，admin 审核登记的 DB 别名，置信度略低于静态表</li>
 *       <li>动态别名表最长包含命中（confidence=0.80, matchType=dynamic_alias）</li>
 *     </ol>
 *     包含规则：取被 label 包含的最长 key，key 长度 &lt; 2 不参与，并列最长视为歧义返回 null</li>
 *   <li>未命中任何策略 → 交给 LLM</li>
 * </ol>
 * 调用方 {@code PluginFormAutofillAiService} 对本匹配过程做了 try-catch 包裹，
 * 任何异常均降级为「全部交给 LLM」，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeterministicFieldMatcher {

    /** textarea 和长文本字段需要 LLM 做多字段拼接，不适合确定性匹配 */
    private static final Set<String> LONG_TEXT_INTERACTION_MODES = Set.of("textarea");

    /** 选择类交互模式：无选项样本时不能按纯文本直填原始值（否则性别会填成 "1" 而不是 "男"） */
    private static final Set<String> SELECT_LIKE_INTERACTION_MODES =
            Set.of("single_select", "combobox_typeahead", "composite_left_select", "select");

    private final DynamicFieldAliasService dynamicFieldAliasService;

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
        int strategy1Fail = 0;     // 策略1下拉选项匹配失败
        int strategy2Fail = 0;
        int strategy1HasKey = 0;   // 有 clientFieldKey 但未知
        int strategy1NoValue = 0;  // key 已知但简历值为空
        int strategy1Conflict = 0; // clientFieldKey 与标签语义冲突（前端映射疑似有误）
        int strategy2NoLabel = 0;  // 标签在别名表未命中
        int strategy2NoValue = 0;  // 标签命中但简历值为空

        int order = 1;
        // 集合路径出现次序计数：多块表单中同一标签（如两个项目块的「项目名称」）第 N 次出现
        // 对应简历第 N 条条目，避免全部恒取索引 0 导致后块被第一条经历覆盖
        Map<String, Integer> pathOccurrence = new HashMap<>();
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
            // strategy1Counted 标记该字段失败原因已由策略1统计，诊断块不再重复计数
            boolean strategy1Counted = false;
            String effKey = null; // 策略1按出现次序改写后的集合路径（供策略2同路径复用）
            String ck = field.getClientFieldKey();
            boolean hasKey = StringUtils.hasText(ck);
            boolean knownKey = hasKey && ResumeFieldAccessor.isKnownKey(ck);
            if (knownKey) {
                // 冲突防护：clientFieldKey 与标签推断路径不一致时，说明前端映射有误
                // （如「项目名称」行的 dataFormFieldId=name 被前端错映射为 basic.fullName），
                // 不信任任何一侧的高置信直填，交 LLM 结合语义判断
                LabelMatch labelProbe = resolveByLabels(field);
                String labelPath = labelProbe == null ? null : labelProbe.path();
                if (labelPath != null && !labelPath.equals(ck)) {
                    strategy1Conflict++;
                    strategy1Counted = true;
                    log.warn("clientFieldKey 与标签语义冲突，交 LLM 处理: selector={}, clientFieldKey={}, 标签推断={}",
                            field.getSelector(), ck, labelPath);
                } else {
                    effKey = resolveIndexedPath(ck, pathOccurrence);
                    String rawValue = ResumeFieldAccessor.extractValue(draft, effKey);
                    if (rawValue != null) {
                        // key 已知且有值 → 尝试构建 assignment
                        PluginFieldAssignmentDto assignment = tryClientFieldKeyMatch(field, draft, effKey, order);
                        if (assignment != null) {
                            matched.add(assignment);
                            order++;
                            continue;
                        }
                        // tryClientFieldKeyMatch 返回 null 说明下拉选项匹配失败
                        strategy1Fail++;
                        strategy1Counted = true;
                    } else {
                        strategy1NoValue++;
                        strategy1Counted = true;
                    }
                }
            } else if (hasKey) {
                strategy1HasKey++; // 有 key 但注册表不识别
                strategy1Counted = true;
            }

            // 策略 2：标签启发式匹配（策略 1 未命中时无条件尝试）
            LabelMatch labelMatch = resolveByLabels(field);
            String effPath = null;
            if (labelMatch != null) {
                // 与策略1同路径时复用已计数的索引，避免同一字段把块序号推进两次
                effPath = (effKey != null && labelMatch.path().equals(ck)) ? effKey
                        : resolveIndexedPath(labelMatch.path(), pathOccurrence);
            }
            PluginFieldAssignmentDto assignment = tryLabelMatch(field, draft, labelMatch, effPath, order);
            if (assignment != null) {
                matched.add(assignment);
                order++;
                continue;
            }

            // 诊断策略2失败原因（策略1已统计过的字段不重复计数）
            if (!strategy1Counted) {
                String labelPath = labelMatch == null ? null : labelMatch.path();
                if (labelPath == null) {
                    strategy2NoLabel++;
                } else {
                    String rawValue = ResumeFieldAccessor.extractValue(draft, effPath);
                    if (rawValue == null) {
                        strategy2NoValue++;
                    } else {
                        strategy2Fail++; // 有值但下拉匹配失败
                    }
                }
            }

            unmatched.add(field);
        }

        log.info("确定性匹配完成: 总字段={}, 已匹配={}, 未匹配={} | "
                        + "原因分布: [已有值跳过={}, 长文本跳过={}, 复合跳过={}, "
                        + "策略1-key未知={}, 策略1-值为空={}, 策略1-下拉失败={}, 策略1-标签冲突={}, "
                        + "策略2-标签未命中={}, 策略2-值为空={}, 策略2-下拉失败={}]",
                fields.size(), matched.size(), unmatched.size(),
                skipCurrentValue, skipLongText, skipCompound,
                strategy1HasKey, strategy1NoValue, strategy1Fail, strategy1Conflict,
                strategy2NoLabel, strategy2NoValue, strategy2Fail);
        return DeterministicMatchResult.builder()
                .matchedAssignments(matched)
                .unmatchedFields(unmatched)
                .build();
    }

    /**
     * 策略 1：通过 clientFieldKey（已按出现次序改写索引）直接从简历 draft 中提取值。
     */
    private PluginFieldAssignmentDto tryClientFieldKeyMatch(
            PluginPageFormFieldDto field, ResumeParseDraftDto draft, String key, int order) {

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
            OptionResolution resolution = resolveOptionValue(field, key, rawValue);
            if (resolution == null) {
                // 下拉选项匹配失败 → 交给 LLM，它可能有更好的语义理解
                return null;
            }
            finalValue = resolution.value();
            fallbacks = resolution.fallbacks();
        } else if (isSelectLikeField(field)) {
            // 选择类控件无选项样本：枚举类别转展示文案（"1"→"男"），备选为整组别名
            String fieldName = ResumeFieldAccessor.extractFieldName(key);
            List<String> values = resolveSelectLikeValue(
                    rawValue, ChineseFieldAliasTable.inferEnumCategory(fieldName));
            finalValue = values.get(0);
            fallbacks = values.subList(1, values.size());
        }

        return buildAssignment(field, finalValue, fallbacks, order, 0.98, "deterministic");
    }

    /**
     * 策略 2：通过 labelText / placeholder 在别名表中查找。
     * 仅用于 clientFieldKey 缺失的场景（兼容旧版前端）。
     *
     * @param labelMatch 外层已解析的标签匹配结果（含路径与来源），为 null 直接返回 null
     * @param fieldPath  按出现次序改写索引后的集合路径，随 labelMatch 为 null 时为 null
     */
    private PluginFieldAssignmentDto tryLabelMatch(
            PluginPageFormFieldDto field, ResumeParseDraftDto draft,
            LabelMatch labelMatch, String fieldPath, int order) {

        if (labelMatch == null) {
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
            OptionResolution resolution = resolveOptionValue(field, fieldPath, rawValue);
            if (resolution == null) {
                return null;
            }
            finalValue = resolution.value();
            fallbacks = resolution.fallbacks();
        } else if (isSelectLikeField(field)) {
            // 选择类控件无选项样本：枚举类别转展示文案（"1"→"男"），备选为整组别名
            List<String> values = resolveSelectLikeValue(
                    rawValue, ChineseFieldAliasTable.inferEnumCategory(fieldPath));
            finalValue = values.get(0);
            fallbacks = values.subList(1, values.size());
        }

        // 动态别名（DB 审核登记）置信度略低于内置静态表，便于统计区分来源
        double confidence = labelMatch.dynamic() ? 0.80 : 0.85;
        String matchType = labelMatch.dynamic() ? "dynamic_alias" : "deterministic";
        return buildAssignment(field, finalValue, fallbacks, order, confidence, matchType);
    }

    /**
     * 多块表单的出现次序索引：集合路径（如 project.0.projectName）在同一页面第 N 次出现时，
     * 改写为简历第 N 条条目（project.(N-1).projectName）——页面块通常与简历条目同序排列。
     * 简历条目数不足时 extractValue 返回 null，该字段自然降级交 LLM；单块页面首次出现恒为索引 0，行为不变。
     */
    private String resolveIndexedPath(String path, Map<String, Integer> pathOccurrence) {
        if (path == null || !path.matches("^\\w+\\.\\d+\\.\\w+$")) {
            return path;
        }
        int occurrence = pathOccurrence.merge(path, 1, Integer::sum) - 1;
        if (occurrence == 0) {
            return path;
        }
        return path.replaceFirst("\\.\\d+\\.", "." + occurrence + ".");
    }

    /**
     * 依次尝试 labelText、placeholder、name、id；
     * 查找顺序是「每个文本候选内四级」——静态精确 → 静态 contains → 动态精确 → 动态 contains，
     * 即 labelText 的静态 contains 优先于 placeholder 的动态精确。
     */
    private LabelMatch resolveByLabels(PluginPageFormFieldDto field) {
        for (String text : new String[]{field.getLabelText(), field.getPlaceholder(), field.getName(), field.getId()}) {
            if (!StringUtils.hasText(text)) continue;
            String normalized = normalizeLabel(text);
            String path = ChineseFieldAliasTable.resolveByLabel(normalized);
            if (path != null) return new LabelMatch(path, false);
            path = ChineseFieldAliasTable.resolveByLabelContains(normalized);
            if (path != null) return new LabelMatch(path, false);
            path = dynamicFieldAliasService.resolveExact(normalized);
            if (path != null) return new LabelMatch(path, true);
            path = dynamicFieldAliasService.resolveContains(normalized);
            if (path != null) return new LabelMatch(path, true);
        }
        return null;
    }

    /** 标签匹配结果：字段路径 + 是否命中动态（DB）别名 */
    private record LabelMatch(String path, boolean dynamic) {}

    /** 标签归一化：去首尾空格、常见后缀和前缀指令词，末尾统一小写（中文不受影响；英文 name/id 属性大小写不敏感） */
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
        return (s.isEmpty() ? label.trim() : s).toLowerCase();
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

    /** 是否为选择类控件（下拉/联想框），按 tagName=select 或 interactionMode 判断 */
    private boolean isSelectLikeField(PluginPageFormFieldDto field) {
        if ("select".equalsIgnoreCase(field.getTagName())) {
            return true;
        }
        String mode = field.getInteractionMode();
        return mode != null && SELECT_LIKE_INTERACTION_MODES.contains(mode.trim().toLowerCase());
    }

    /**
     * 选择类控件无选项样本时的取值：枚举类别（性别/学历/政治面貌等）把原始值转展示文案，
     * 返回列表首个为主值（原始值本身已是展示文案如 "本科" 则沿用，否则用组内首选别名如 "1"→"男"），
     * 其余别名作为 fallbackValues 供前端 typeahead 逐个尝试；非枚举类别保留原始值（键入即搜索）。
     */
    private List<String> resolveSelectLikeValue(String rawValue, String enumCategory) {
        List<String> aliases = enumCategory == null ? null
                : ChineseFieldAliasTable.enumAliasesOf(enumCategory, rawValue);
        if (aliases == null || aliases.isEmpty()) {
            return List.of(rawValue);
        }
        boolean rawIsDisplay = aliases.stream().anyMatch(a -> a.equalsIgnoreCase(rawValue));
        String primary = rawIsDisplay ? rawValue : aliases.get(0);
        List<String> out = new ArrayList<>();
        out.add(primary);
        for (String a : aliases) {
            if (!a.equalsIgnoreCase(primary)) {
                out.add(a);
            }
        }
        return out;
    }

    private List<String> buildFallbackValues(String rawValue, String matchedOption) {
        List<String> fallbacks = new ArrayList<>();
        // 若匹配值和原始值不同，将原始值作为备选
        if (!rawValue.equals(matchedOption)) {
            fallbacks.add(rawValue);
        }
        return fallbacks;
    }

    /** 选项匹配的产出：最终值 + 备选值 */
    private record OptionResolution(String value, List<String> fallbacks) {}

    /**
     * 有选项样本时的取值：
     * <ul>
     *   <li>封闭下拉（select/single_select 等）：精确 → 枚举别名 → contains 打分，全不中返回 null 交 LLM；</li>
     *   <li>开放联想框（combobox_typeahead 且非枚举类别，如学校/公司/专业）：optionLabelsSample 只是
     *       未键入时采到的部分候选而非封闭值域，禁止 contains 打分改写（否则「江苏科技大学」会被样本里的
     *       残缺项「大学」命中截断）；精确/枚举别名不中时直填简历原值，由前端键入后在完整选项中匹配。</li>
     * </ul>
     */
    private OptionResolution resolveOptionValue(PluginPageFormFieldDto field, String fieldPath, String rawValue) {
        String enumCategory = ChineseFieldAliasTable.inferEnumCategory(fieldPath);
        boolean openTypeahead = OptionMatcher.isOpenTypeahead(field.getInteractionMode(), enumCategory);
        String optionMatch = OptionMatcher.matchOption(
                rawValue, field.getOptionLabelsSample(), enumCategory, !openTypeahead);
        if (optionMatch != null) {
            // 生成备选值：原始值 + 选项匹配值（如果不同的话）
            return new OptionResolution(optionMatch, buildFallbackValues(rawValue, optionMatch));
        }
        if (openTypeahead) {
            return new OptionResolution(rawValue, List.of());
        }
        return null;
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
                .labelText(field.getLabelText())
                .build();
    }
}
