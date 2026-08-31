package com.smartapply.ida.ai.plugin.deterministic;

import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeEducationDraftLine;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeProjectDraftLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 确定性字段匹配器单测：静态 contains 命中（0.85/deterministic）与
 * 动态别名命中（0.80/dynamic_alias）的区分。
 */
class DeterministicFieldMatcherTest {

    private PluginPageFormFieldDto textField(String selector, String labelText) {
        PluginPageFormFieldDto f = new PluginPageFormFieldDto();
        f.setSelector(selector);
        f.setTagName("input");
        f.setInputType("text");
        f.setLabelText(labelText);
        return f;
    }

    @Test
    void dynamicAliasHit_shouldUseLowerConfidenceAndDynamicMatchType() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        when(aliasService.resolveExact(anyString())).thenReturn(null);
        // normalizeLabel 末尾统一小写，动态别名入参为小写形式
        when(aliasService.resolveContains("e-mail账号")).thenReturn("basic.email");
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        draft.setEmail("zhangsan@example.com");

        // 「E-mail账号」静态精确/静态 contains 均不命中，落到动态 contains
        DeterministicMatchResult result = matcher.matchAll(
                List.of(textField("#email", "E-mail账号")), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        PluginFieldAssignmentDto a = result.getMatchedAssignments().get(0);
        assertThat(a.getValue()).isEqualTo("zhangsan@example.com");
        assertThat(a.getConfidence()).isEqualTo(0.80);
        assertThat(a.getMatchType()).isEqualTo("dynamic_alias");
    }

    @Test
    void staticContainsHit_shouldKeepDeterministicConfidence() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        draft.setEmail("zhangsan@example.com");

        // 「您的邮箱」静态精确不命中，静态 contains 命中「邮箱」→ 不查动态别名
        DeterministicMatchResult result = matcher.matchAll(
                List.of(textField("#email", "您的邮箱")), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        PluginFieldAssignmentDto a = result.getMatchedAssignments().get(0);
        assertThat(a.getValue()).isEqualTo("zhangsan@example.com");
        assertThat(a.getConfidence()).isEqualTo(0.85);
        assertThat(a.getMatchType()).isEqualTo("deterministic");
    }

    @Test
    void noHit_shouldGoToLlm() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        when(aliasService.resolveExact(anyString())).thenReturn(null);
        when(aliasService.resolveContains(anyString())).thenReturn(null);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        DeterministicMatchResult result = matcher.matchAll(
                List.of(textField("#captcha", "图形验证码")), draft);

        assertThat(result.getMatchedAssignments()).isEmpty();
        assertThat(result.getUnmatchedFields()).hasSize(1);
    }

    @Test
    void clientFieldKeyConflictWithLabel_shouldDeferToLlm() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        draft.setFullName("黄晋豪");

        // 前端把「项目名称」行（dataFormFieldId=name）错映射成 basic.fullName：
        // key 有值但标签语义冲突 → 不信任高置信直填，交 LLM
        PluginPageFormFieldDto f = textField("input[data-form-field-id=\"name\"]", "项目名称");
        f.setClientFieldKey("basic.fullName");
        DeterministicMatchResult result = matcher.matchAll(List.of(f), draft);

        assertThat(result.getMatchedAssignments()).isEmpty();
        assertThat(result.getUnmatchedFields()).hasSize(1);
    }

    @Test
    void clientFieldKeyConsistentWithLabel_shouldMatch() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        draft.setFullName("黄晋豪");

        // clientFieldKey 与标签语义一致（姓名 → basic.fullName）→ 正常 0.98 直填
        PluginPageFormFieldDto f = textField("input[data-form-field-i18n-name=\"姓名\"]", "姓名");
        f.setClientFieldKey("basic.fullName");
        DeterministicMatchResult result = matcher.matchAll(List.of(f), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("黄晋豪");
        assertThat(result.getMatchedAssignments().get(0).getConfidence()).isEqualTo(0.98);
    }

    @Test
    void genderComboboxWithoutOptions_shouldFillDisplayTextNotRawCode() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        draft.setGender(1);

        // 选择类控件无选项样本：枚举原始值 "1" 必须转显示文案 "男"，备选放整组别名
        PluginPageFormFieldDto f = textField("#gender", "性别");
        f.setClientFieldKey("basic.gender");
        f.setInteractionMode("combobox_typeahead");
        DeterministicMatchResult result = matcher.matchAll(List.of(f), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        PluginFieldAssignmentDto a = result.getMatchedAssignments().get(0);
        assertThat(a.getValue()).isEqualTo("男");
        assertThat(a.getFallbackValues()).contains("男性");
    }

    @Test
    void enumComboboxWithoutOptions_readableValueKeptAsPrimary() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        draft.setGender(2);

        // 标签路径命中（策略 2）同样转换："2" → "女"
        PluginPageFormFieldDto f = textField("#gender", "性别");
        f.setInteractionMode("single_select");
        DeterministicMatchResult result = matcher.matchAll(List.of(f), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("女");
    }

    @Test
    void duplicateCollectionLabels_shouldMapToSequentialResumeEntries() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        ResumeProjectDraftLine p0 = new ResumeProjectDraftLine();
        p0.setProjectName("项目A");
        ResumeProjectDraftLine p1 = new ResumeProjectDraftLine();
        p1.setProjectName("项目B");
        draft.setProjects(List.of(p0, p1));

        // 页面两个项目块，两个「项目名称」字段 → 第一个取第 0 条，第二个取第 1 条
        DeterministicMatchResult result = matcher.matchAll(
                List.of(textField("#p0name", "项目名称"), textField("#p1name", "项目名称")), draft);

        assertThat(result.getMatchedAssignments()).hasSize(2);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("项目A");
        assertThat(result.getMatchedAssignments().get(1).getValue()).isEqualTo("项目B");
    }

    @Test
    void secondBlockBeyondResumeEntries_shouldDeferToLlm() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        ResumeProjectDraftLine p0 = new ResumeProjectDraftLine();
        p0.setProjectName("项目A");
        draft.setProjects(List.of(p0));

        // 简历只有 1 条项目，页面有 2 个块 → 第 2 个块无对应条目，交 LLM 而不是错填项目A
        DeterministicMatchResult result = matcher.matchAll(
                List.of(textField("#p0name", "项目名称"), textField("#p1name", "项目名称")), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("项目A");
        assertThat(result.getUnmatchedFields()).hasSize(1);
        assertThat(result.getUnmatchedFields().get(0).getSelector()).isEqualTo("#p1name");
    }

    @Test
    void openTypeaheadSchoolWithPartialOptionSample_shouldKeepResumeRawValue() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        ResumeEducationDraftLine edu = new ResumeEducationDraftLine();
        edu.setSchoolName("江苏科技大学");
        draft.setEducations(List.of(edu));

        // 线上事故场景：学校 combobox 的 optionLabelsSample 只有未展开时采到的残缺项「大学」，
        // 不得 contains 改写为「大学」，必须直填简历原值，由前端键入后在完整选项中匹配
        PluginPageFormFieldDto f = textField("#school", null);
        f.setClientFieldKey("education.0.schoolName");
        f.setInteractionMode("combobox_typeahead");
        f.setOptionLabelsSample(List.of("大学", "其他"));
        DeterministicMatchResult result = matcher.matchAll(List.of(f), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("江苏科技大学");
    }

    @Test
    void educationTypeLabel_shouldMapToIsFullTimeNotDegree() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        ResumeEducationDraftLine edu = new ResumeEducationDraftLine();
        edu.setDegree("本科");
        edu.setIsFullTime(1);
        draft.setEducations(List.of(edu));

        // 「学历类型」值域是 统招全日制/自考 等，不是「学历(degree)」的 本科/硕士：
        // 精确别名命中 education.0.isFullTime，经 fullTime 枚举别名组映射到选项「统招全日制」
        PluginPageFormFieldDto f = textField("#edu-type", "学历类型");
        f.setInteractionMode("single_select");
        f.setOptionLabelsSample(List.of("海外及港澳台", "统招全日制", "统招非全日制", "自考", "其他"));
        DeterministicMatchResult result = matcher.matchAll(List.of(f), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        PluginFieldAssignmentDto a = result.getMatchedAssignments().get(0);
        assertThat(a.getValue()).isEqualTo("统招全日制");
    }

    @Test
    void educationTypeWithoutOptions_shouldUseFullTimeDisplayText() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        ResumeEducationDraftLine edu = new ResumeEducationDraftLine();
        edu.setIsFullTime(1);
        draft.setEducations(List.of(edu));

        // 选择类控件无选项样本：isFullTime 原始值 "1" 转 fullTime 枚举组首选展示文案，不得借 degree 值域填「本科」
        PluginPageFormFieldDto f = textField("#edu-type", "学历类型");
        f.setInteractionMode("combobox_typeahead");
        DeterministicMatchResult result = matcher.matchAll(List.of(f), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        PluginFieldAssignmentDto a = result.getMatchedAssignments().get(0);
        assertThat(a.getValue()).isEqualTo("统招全日制");
        assertThat(a.getFallbackValues()).contains("全日制");
    }

    @Test
    void currentCityLabel_shouldMapToCurrentCityNotCity() {
        DynamicFieldAliasService aliasService = mock(DynamicFieldAliasService.class);
        DeterministicFieldMatcher matcher = new DeterministicFieldMatcher(aliasService);

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        draft.setCity("广州");        // location.city 是户籍/籍贯语义
        draft.setCurrentCity("深圳"); // location.currentCity 才是现居

        // 「当前居住城市」精确别名命中 location.currentCity，不再被 contains 的「城市」误命中户籍城市
        DeterministicMatchResult result = matcher.matchAll(
                List.of(textField("#cur-city", "当前居住城市")), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("深圳");
    }
}
