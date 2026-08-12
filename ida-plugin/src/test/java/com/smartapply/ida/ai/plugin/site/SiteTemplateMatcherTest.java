package com.smartapply.ida.ai.plugin.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.site.FieldAnchor;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplate;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplateMatchResult;
import com.smartapply.ida.common.domain.plugin.autofill.site.TemplateFieldMapping;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeEducationDraftLine;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 站点模板匹配器单测：验证金标命中、下拉选项匹配、skip 进 blocked、复合/长文本降级、未命中流转。
 */
class SiteTemplateMatcherTest {

    private final SiteTemplateMatcher matcher = new SiteTemplateMatcher();

    @Test
    void shouldProduceTemplateAssignmentForPlainField() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("姓名").build(),
                        "basic.fullName", null, false)
        ));
        PluginPageFormFieldDto field = field("#name", "姓名", null, null, null);

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(field), draft());

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("张三");
        assertThat(result.getMatchedAssignments().get(0).getMatchType()).isEqualTo("template");
        assertThat(result.getMatchedAssignments().get(0).getConfidence()).isEqualTo(0.99);
        assertThat(result.getUnmatchedFields()).isEmpty();
    }

    @Test
    void shouldMatchDropdownOptionViaOptionMatcher() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("学历").build(),
                        "education.0.degree", "degree", false)
        ));
        PluginPageFormFieldDto field = field("#degree", "学历", null, null,
                List.of("大学本科", "硕士研究生", "博士研究生"));

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(field), draft());

        assertThat(result.getMatchedAssignments()).hasSize(1);
        // 简历值为 "本科"，应匹配到选项 "大学本科"
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("大学本科");
        assertThat(result.getMatchedAssignments().get(0).getFallbackValues()).contains("本科");
    }

    @Test
    void shouldDowngradeToUnmatchedWhenOptionNotMatched() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("学历").build(),
                        "education.0.degree", "degree", false)
        ));
        // 选项与简历值完全无关
        PluginPageFormFieldDto field = field("#degree", "学历", null, null,
                List.of("幼儿园", "小学"));

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(field), draft());

        assertThat(result.getMatchedAssignments()).isEmpty();
        assertThat(result.getUnmatchedFields()).contains(field);
    }

    @Test
    void shouldSendSkipFieldToBlocked() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("附件").build(),
                        null, null, true)
        ));
        PluginPageFormFieldDto field = field("#resume_upload", "附件", null, null, null);

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(field), draft());

        assertThat(result.getBlockedFields()).hasSize(1);
        // 未配置 blockedReason/blockedHint 时使用默认值
        assertThat(result.getBlockedFields().get(0).getReason()).isEqualTo("other");
        assertThat(result.getBlockedFields().get(0).getUserHint()).isEqualTo("模板标注需人工处理");
        assertThat(result.getUnmatchedFields()).isEmpty();
        assertThat(result.getMatchedAssignments()).isEmpty();
    }

    @Test
    void shouldUseMappingBlockedReasonAndHintForSkipField() {
        SiteTemplate tpl = template(List.of(
                TemplateFieldMapping.builder()
                        .anchor(FieldAnchor.builder().labelTextEquals("工号").build())
                        .skip(true)
                        .blockedReason("readonly")
                        .blockedHint("该字段为只读，请人工确认")
                        .build()
        ));
        PluginPageFormFieldDto field = field("#emp_no", "工号", null, null, null);

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(field), draft());

        assertThat(result.getBlockedFields()).hasSize(1);
        assertThat(result.getBlockedFields().get(0).getReason()).isEqualTo("readonly");
        assertThat(result.getBlockedFields().get(0).getUserHint()).isEqualTo("该字段为只读，请人工确认");
    }

    @Test
    void shouldOutputBlockedReasonAndHintFromRealBeisenTemplate() throws Exception {
        SiteTemplate beisen;
        try (var in = getClass().getResourceAsStream("/site-templates/beisen.json")) {
            beisen = new ObjectMapper().readValue(in, SiteTemplate.class);
        }
        PluginPageFormFieldDto field = field("#resume_upload", "附件", null, null, null);

        SiteTemplateMatchResult result = matcher.apply(beisen, List.of(field), draft());

        assertThat(result.getBlockedFields()).hasSize(1);
        // beisen 附件行 blockedReason=attachment，Matcher 透传不被下游改写
        assertThat(result.getBlockedFields().get(0).getReason()).isEqualTo("attachment");
        assertThat(result.getBlockedFields().get(0).getUserHint()).isEqualTo("附件需人工上传");
    }

    @Test
    void shouldDowngradeCompoundFieldToUnmatched() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("手机号").build(),
                        "basic.phone", null, false)
        ));
        PluginPageFormFieldDto field = field("#phone", "手机号", null, null, null);
        field.setCompoundSegment("local_number");

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(field), draft());

        assertThat(result.getMatchedAssignments()).isEmpty();
        assertThat(result.getUnmatchedFields()).contains(field);
    }

    @Test
    void shouldDowngradeTextareaToUnmatched() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("自我介绍").build(),
                        "profile.selfIntro", null, false)
        ));
        PluginPageFormFieldDto field = field("#intro", "自我介绍", null, null, null);
        field.setTagName("textarea");

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(field), draft());

        assertThat(result.getMatchedAssignments()).isEmpty();
        assertThat(result.getUnmatchedFields()).contains(field);
    }

    @Test
    void shouldNotOverrideFieldWithCurrentValue() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("姓名").build(),
                        "basic.fullName", null, false)
        ));
        PluginPageFormFieldDto field = field("#name", "姓名", null, null, null);
        field.setCurrentValue("已填的姓名");

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(field), draft());

        assertThat(result.getMatchedAssignments()).isEmpty();
        assertThat(result.getUnmatchedFields()).contains(field);
    }

    @Test
    void shouldLeaveFieldWithoutMappingUnmatched() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("姓名").build(),
                        "basic.fullName", null, false)
        ));
        PluginPageFormFieldDto nameField = field("#name", "姓名", null, null, null);
        PluginPageFormFieldDto unknownField = field("#x", "未知字段", null, null, null);

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(nameField, unknownField), draft());

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getUnmatchedFields()).contains(unknownField);
    }

    @Test
    void shouldHandleMultipleFieldsInOrder() {
        SiteTemplate tpl = template(List.of(
                mapping(FieldAnchor.builder().labelTextEquals("姓名").build(),
                        "basic.fullName", null, false),
                mapping(FieldAnchor.builder().labelTextEquals("手机号").build(),
                        "basic.phone", null, false)
        ));
        PluginPageFormFieldDto name = field("#name", "姓名", null, null, null);
        PluginPageFormFieldDto phone = field("#phone", "手机号", null, null, null);

        SiteTemplateMatchResult result = matcher.apply(tpl, List.of(name, phone), draft());

        assertThat(result.getMatchedAssignments()).hasSize(2);
        assertThat(result.getMatchedAssignments().get(0).getOrder()).isEqualTo(1);
        assertThat(result.getMatchedAssignments().get(1).getOrder()).isEqualTo(2);
    }

    // ======================== 测试夹具 ========================

    private PluginPageFormFieldDto field(String selector, String label, String name,
                                         String id, List<String> options) {
        PluginPageFormFieldDto f = new PluginPageFormFieldDto();
        f.setSelector(selector);
        f.setTagName("input");
        f.setInputType("text");
        f.setLabelText(label);
        f.setName(name);
        f.setId(id);
        if (options != null) {
            f.setOptionLabelsSample(options);
        }
        return f;
    }

    private SiteTemplate template(List<TemplateFieldMapping> mappings) {
        return SiteTemplate.builder()
                .siteId("test_site")
                .displayName("测试模板")
                .fieldMappings(mappings)
                .build();
    }

    private TemplateFieldMapping mapping(FieldAnchor anchor, String resumePath,
                                         String enumCategory, boolean skip) {
        TemplateFieldMapping.TemplateFieldMappingBuilder b = TemplateFieldMapping.builder()
                .anchor(anchor)
                .skip(skip);
        if (resumePath != null) b.resumePath(resumePath);
        if (enumCategory != null) b.enumCategory(enumCategory);
        return b.build();
    }

    private ResumeParseDraftDto draft() {
        ResumeParseDraftDto d = new ResumeParseDraftDto();
        d.setFullName("张三");
        d.setPhone("13800138000");
        d.setEmail("zhangsan@example.com");
        d.setGender(1);
        ResumeEducationDraftLine edu = new ResumeEducationDraftLine();
        edu.setSchoolName("清华大学");
        edu.setMajor("计算机科学");
        edu.setDegree("本科");
        d.setEducations(List.of(edu));
        return d;
    }
}
