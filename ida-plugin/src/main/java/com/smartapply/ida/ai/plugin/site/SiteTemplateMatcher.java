package com.smartapply.ida.ai.plugin.site;

import com.smartapply.ida.ai.plugin.deterministic.ChineseFieldAliasTable;
import com.smartapply.ida.ai.plugin.deterministic.OptionMatcher;
import com.smartapply.ida.ai.plugin.deterministic.ResumeFieldAccessor;
import com.smartapply.ida.common.domain.plugin.autofill.PluginBlockedFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.site.FieldAnchor;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplate;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplateMatchResult;
import com.smartapply.ida.common.domain.plugin.autofill.site.TemplateFieldMapping;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 站点模板匹配器：对命中指纹的模板逐字段执行金标映射，产出可直接填写的 assignments。
 * <p>
 * 处理规则：
 * <ul>
 *   <li>已有预填值（currentValue）的字段 → unmatched（与确定性层一致，不覆盖）。</li>
 *   <li>复合字段（compoundSegment 非空）/长文本（textarea 等）→ unmatched，交 LLM 处理多字段拼接与子选择器。</li>
 *   <li>命中 {@link TemplateFieldMapping#isSkip()} → blockedFields（reason=other）。</li>
 *   <li>命中金标且简历有值：下拉字段经 {@link OptionMatcher} 选项匹配，成功→assignment；失败→unmatched 降级交 LLM。
 *       非下拉字段→assignment（confidence=0.99, matchType=template）。</li>
 *   <li>未命中任何映射行 → unmatched。</li>
 * </ul>
 * 产出的 assignment 仍会经 {@code PluginFormAutofillAiService#sanitizePlan} 的 selector 白名单与锚点合并，安全网不变。
 */
@Slf4j
@Component
public class SiteTemplateMatcher {

    /** textarea / 长文本类字段交 LLM 做多字段拼接，模板层不直接填 */
    private static final Set<String> LONG_TEXT_INTERACTION_MODES = Set.of("textarea");
    private static final Set<String> LONG_TEXT_FIELD_SUFFIXES = Set.of(
            "selfIntro", "strengths", "workDescription", "dutyDescription",
            "achievement", "description", "majorCourses"
    );

    /**
     * 执行模板匹配。
     *
     * @param template 命中的站点模板（非空）
     * @param fields   经截断后的页面字段列表
     * @param draft    用户简历 draft
     * @return 匹配结果（matched / blocked / unmatched）
     */
    public SiteTemplateMatchResult apply(SiteTemplate template,
                                         List<PluginPageFormFieldDto> fields,
                                         ResumeParseDraftDto draft) {
        List<PluginFieldAssignmentDto> matched = new ArrayList<>();
        List<PluginBlockedFieldDto> blocked = new ArrayList<>();
        List<PluginPageFormFieldDto> unmatched = new ArrayList<>();

        List<TemplateFieldMapping> mappings = template.getFieldMappings();
        int order = 1;

        for (PluginPageFormFieldDto field : fields) {
            // 已有预填值不覆盖
            if (StringUtils.hasText(field.getCurrentValue())) {
                unmatched.add(field);
                continue;
            }
            // 复合字段 / 长文本交 LLM
            if (isLongTextOrCompound(field)) {
                unmatched.add(field);
                continue;
            }

            TemplateFieldMapping mapping = findMapping(mappings, field);
            if (mapping == null) {
                unmatched.add(field);
                continue;
            }

            // 模板标注 skip → 直接进 blocked
            if (mapping.isSkip()) {
                blocked.add(buildBlocked(field, template));
                continue;
            }

            String resumePath = mapping.getResumePath();
            if (!StringUtils.hasText(resumePath) || !ResumeFieldAccessor.isKnownKey(resumePath)) {
                // 模板配置了无法解析的路径 → 降级交下层
                unmatched.add(field);
                continue;
            }
            String rawValue = ResumeFieldAccessor.extractValue(draft, resumePath);
            if (rawValue == null) {
                // 简历无值 → 交下层（下层也会判空，但保持流转一致）
                unmatched.add(field);
                continue;
            }

            // 下拉字段做选项匹配
            String enumCategory = StringUtils.hasText(mapping.getEnumCategory())
                    ? mapping.getEnumCategory()
                    : ChineseFieldAliasTable.inferEnumCategory(resumePath);
            if (hasOptions(field)) {
                String optionMatch = OptionMatcher.matchOption(rawValue, field.getOptionLabelsSample(), enumCategory);
                if (optionMatch == null) {
                    // 选项匹配失败 → 交 LLM，它可能有更好的语义理解
                    unmatched.add(field);
                    continue;
                }
                matched.add(buildAssignment(field, optionMatch, buildFallbacks(rawValue, optionMatch), order));
                order++;
                continue;
            }

            matched.add(buildAssignment(field, rawValue, List.of(), order));
            order++;
        }

        log.info("站点模板匹配完成: siteId={} 总字段={}, 已匹配={}, blocked={}, 未匹配={}",
                template.getSiteId(), fields.size(), matched.size(), blocked.size(), unmatched.size());

        return SiteTemplateMatchResult.builder()
                .siteId(template.getSiteId())
                .matchedAssignments(matched)
                .blockedFields(blocked)
                .unmatchedFields(unmatched)
                .build();
    }

    private TemplateFieldMapping findMapping(List<TemplateFieldMapping> mappings, PluginPageFormFieldDto field) {
        if (mappings == null) return null;
        for (TemplateFieldMapping m : mappings) {
            FieldAnchor anchor = m.getAnchor();
            if (anchor != null && SiteTemplateRegistry.anchorMatches(field, anchor)) {
                return m;
            }
        }
        return null;
    }

    private boolean isLongTextOrCompound(PluginPageFormFieldDto field) {
        if (StringUtils.hasText(field.getCompoundSegment())) {
            return true;
        }
        String mode = field.getInteractionMode();
        if (mode != null && LONG_TEXT_INTERACTION_MODES.contains(mode.toLowerCase())) {
            return true;
        }
        if ("textarea".equalsIgnoreCase(field.getTagName())) {
            return true;
        }
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

    private List<String> buildFallbacks(String rawValue, String matchedOption) {
        if (!rawValue.equals(matchedOption)) {
            return List.of(rawValue);
        }
        return List.of();
    }

    private PluginFieldAssignmentDto buildAssignment(PluginPageFormFieldDto field, String value,
                                                     List<String> fallbacks, int order) {
        return PluginFieldAssignmentDto.builder()
                .selector(field.getSelector())
                .value(value)
                .order(order)
                .confidence(0.99)
                .matchType("template")
                .fallbackValues(fallbacks)
                .dataFormFieldId(field.getDataFormFieldId())
                .i18nLabel(field.getI18nLabel())
                .clientFieldKey(field.getClientFieldKey())
                .build();
    }

    private PluginBlockedFieldDto buildBlocked(PluginPageFormFieldDto field, SiteTemplate template) {
        return PluginBlockedFieldDto.builder()
                .selector(field.getSelector())
                .reason("other")
                .userHint("模板标注需人工处理")
                .dataFormFieldId(field.getDataFormFieldId())
                .i18nLabel(field.getI18nLabel())
                .clientFieldKey(field.getClientFieldKey())
                .build();
    }
}
