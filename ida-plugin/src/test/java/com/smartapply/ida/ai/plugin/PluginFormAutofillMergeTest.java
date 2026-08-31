package com.smartapply.ida.ai.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.langchain4j.assistant.PluginAutofillAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.PluginAutofillPromptProvider;
import com.smartapply.ida.ai.plugin.deterministic.DeterministicFieldMatcher;
import com.smartapply.ida.ai.plugin.deterministic.DeterministicMatchResult;
import com.smartapply.ida.ai.plugin.site.SiteTemplateMatcher;
import com.smartapply.ida.ai.plugin.site.SiteTemplateRegistry;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginBlockedFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormSnapshotDto;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplateMatchResult;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.module.app.resume.service.AppResumeQueryService;
import com.smartapply.ida.module.plugin.resume.autofill.PluginAutofillResultCacheService;
import com.smartapply.ida.module.plugin.resume.autofill.ResumePreloadCacheService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 三层结果合并单测：assignment 与 blockedFields 的 selector 互斥。
 * 模型偶发把同一控件同时写进 assignments 与 blockedFields（如区号下拉既给值又报
 * compound_unresolved），合并结果中以 assignment 为准、剔除对应 blocked 项。
 */
class PluginFormAutofillMergeTest {

    private PluginFormAutofillAiService newService() {
        return new PluginFormAutofillAiService(
                mock(PluginAutofillAssistant.class),
                mock(PluginAutofillPromptProvider.class),
                new ObjectMapper(),
                mock(AppResumeQueryService.class),
                mock(DeterministicFieldMatcher.class),
                mock(ResumeDraftPromptPruner.class),
                mock(SiteTemplateRegistry.class),
                mock(SiteTemplateMatcher.class),
                mock(PluginAutofillResultCacheService.class),
                mock(ResumePreloadCacheService.class),
                mock(BillingService.class));
    }

    @Test
    void selectorInBothAssignmentAndBlocked_shouldKeepAssignmentOnly() {
        PluginAutofillPlanResultVO llm = PluginAutofillPlanResultVO.builder()
                .fillable(true)
                .assignments(List.of(PluginFieldAssignmentDto.builder()
                        .selector("#mobile-dial").value("+86").build()))
                .blockedFields(List.of(
                        PluginBlockedFieldDto.builder()
                                .selector("#mobile-dial").reason("compound_unresolved").build(),
                        PluginBlockedFieldDto.builder()
                                .selector("#captcha").reason("captcha").build()))
                .build();

        PluginAutofillPlanResultVO merged = newService().mergeResults(
                SiteTemplateMatchResult.empty(List.of()),
                DeterministicMatchResult.builder().build(),
                llm);

        assertThat(merged.getAssignments()).hasSize(1);
        // #mobile-dial 已有 assignment → 其 blocked 被剔除；#captcha 无 assignment → 保留
        assertThat(merged.getBlockedFields()).hasSize(1);
        assertThat(merged.getBlockedFields().get(0).getSelector()).isEqualTo("#captcha");
    }

    @Test
    void selectorOnlyInBlocked_shouldRemainBlocked() {
        PluginAutofillPlanResultVO llm = PluginAutofillPlanResultVO.builder()
                .fillable(false)
                .assignments(List.of())
                .blockedFields(List.of(PluginBlockedFieldDto.builder()
                        .selector("#upload").reason("attachment").build()))
                .build();

        PluginAutofillPlanResultVO merged = newService().mergeResults(
                SiteTemplateMatchResult.empty(List.of()),
                DeterministicMatchResult.builder().build(),
                llm);

        assertThat(merged.getAssignments()).isEmpty();
        assertThat(merged.getBlockedFields()).hasSize(1);
    }

    @Test
    void compoundFieldWithPlainValue_shouldBeWrappedAsSubAssignment() {
        // 快照：起止时间的开始日期控件（复合段 date_start）
        PluginPageFormFieldDto dateField = new PluginPageFormFieldDto();
        dateField.setSelector("#date-start");
        dateField.setTagName("input");
        dateField.setCompoundSegment("date_start");
        dateField.setInteractionMode("combobox_typeahead");

        // 模型违反规则 17，把日期写在了主 value 而未用 subAssignments
        PluginAutofillPlanResultVO llm = PluginAutofillPlanResultVO.builder()
                .fillable(true)
                .assignments(List.of(PluginFieldAssignmentDto.builder()
                        .selector("#date-start").value("2023-09-10").build()))
                .blockedFields(List.of())
                .build();

        PluginAutofillPlanResultVO sanitized = newService().sanitizePlan(llm, List.of(dateField));

        assertThat(sanitized.getAssignments()).hasSize(1);
        PluginFieldAssignmentDto a = sanitized.getAssignments().get(0);
        // 主 value 清空，转为带 targetRole 的子赋值，前端据此走日期控件交互
        assertThat(a.getValue()).isNull();
        assertThat(a.getSubAssignments()).hasSize(1);
        assertThat(a.getSubAssignments().get(0).getTargetRole()).isEqualTo("date_start");
        assertThat(a.getSubAssignments().get(0).getValue()).isEqualTo("2023-09-10");
    }

    @Test
    void llmValueNotInOptions_shouldMoveToBlocked() {
        // 快照：语言能力「精通程度」下拉，选项名录不含「精通」
        PluginPageFormFieldDto langField = new PluginPageFormFieldDto();
        langField.setSelector("#lang-level");
        langField.setTagName("input");
        langField.setInteractionMode("single_select");
        langField.setOptionLabelsSample(List.of("入门", "日常会话", "商务会话", "无障碍沟通", "母语"));

        // 模型输出了不在选项内的值「精通」→ 出口枚举校验拦截，转 blocked 而不是透传错填
        PluginAutofillPlanResultVO llm = PluginAutofillPlanResultVO.builder()
                .fillable(true)
                .assignments(List.of(PluginFieldAssignmentDto.builder()
                        .selector("#lang-level").value("精通").build()))
                .blockedFields(List.of())
                .build();

        PluginAutofillPlanResultVO sanitized = newService().sanitizePlan(llm, List.of(langField));

        assertThat(sanitized.getAssignments()).isEmpty();
        assertThat(sanitized.getBlockedFields()).hasSize(1);
        PluginBlockedFieldDto b = sanitized.getBlockedFields().get(0);
        assertThat(b.getSelector()).isEqualTo("#lang-level");
        assertThat(b.getReason()).isEqualTo("ambiguous");
    }

    @Test
    void llmValueMatchingOption_shouldAlignToOptionText() {
        PluginPageFormFieldDto degreeField = new PluginPageFormFieldDto();
        degreeField.setSelector("#degree");
        degreeField.setTagName("input");
        degreeField.setInteractionMode("single_select");
        degreeField.setClientFieldKey("education.0.degree");
        degreeField.setOptionLabelsSample(List.of("大学本科(Bachelor)", "硕士研究生(Master)"));

        // 值能映射到选项名录（枚举别名/contains）→ 保留并对齐为平台选项原文
        PluginAutofillPlanResultVO llm = PluginAutofillPlanResultVO.builder()
                .fillable(true)
                .assignments(List.of(PluginFieldAssignmentDto.builder()
                        .selector("#degree").value("本科").build()))
                .blockedFields(List.of())
                .build();

        PluginAutofillPlanResultVO sanitized = newService().sanitizePlan(llm, List.of(degreeField));

        assertThat(sanitized.getAssignments()).hasSize(1);
        assertThat(sanitized.getAssignments().get(0).getValue()).isEqualTo("大学本科(Bachelor)");
        assertThat(sanitized.getBlockedFields()).isEmpty();
    }

    @Test
    void openTypeaheadValueNotInSample_shouldPassThrough() {
        // 开放联想框（学校）：值不在部分选项样本内属正常，出口校验不得拦截
        PluginPageFormFieldDto schoolField = new PluginPageFormFieldDto();
        schoolField.setSelector("#school");
        schoolField.setTagName("input");
        schoolField.setInteractionMode("combobox_typeahead");
        schoolField.setOptionLabelsSample(List.of("大学", "其他"));

        PluginAutofillPlanResultVO llm = PluginAutofillPlanResultVO.builder()
                .fillable(true)
                .assignments(List.of(PluginFieldAssignmentDto.builder()
                        .selector("#school").value("江苏科技大学").build()))
                .blockedFields(List.of())
                .build();

        PluginAutofillPlanResultVO sanitized = newService().sanitizePlan(llm, List.of(schoolField));

        assertThat(sanitized.getAssignments()).hasSize(1);
        assertThat(sanitized.getAssignments().get(0).getValue()).isEqualTo("江苏科技大学");
        assertThat(sanitized.getBlockedFields()).isEmpty();
    }

    @Test
    void emptyValueWithSkipReason_shouldMoveToBlocked() {
        PluginPageFormFieldDto eduTypeField = new PluginPageFormFieldDto();
        eduTypeField.setSelector("#edu-type");
        eduTypeField.setTagName("input");
        eduTypeField.setInteractionMode("single_select");

        // 模型按规则输出空 value + skipReason=no_mapping：不得静默消失，应转 blocked 提示用户
        PluginAutofillPlanResultVO llm = PluginAutofillPlanResultVO.builder()
                .fillable(true)
                .assignments(List.of(PluginFieldAssignmentDto.builder()
                        .selector("#edu-type").value("").skipReason("no_mapping").build()))
                .blockedFields(List.of())
                .build();

        PluginAutofillPlanResultVO sanitized = newService().sanitizePlan(llm, List.of(eduTypeField));

        assertThat(sanitized.getAssignments()).isEmpty();
        assertThat(sanitized.getBlockedFields()).hasSize(1);
        PluginBlockedFieldDto b = sanitized.getBlockedFields().get(0);
        assertThat(b.getSelector()).isEqualTo("#edu-type");
        assertThat(b.getReason()).isEqualTo("no_mapping");
    }

    @Test
    void emptyValueWithoutSkipReason_shouldBeDroppedSilently() {
        PluginPageFormFieldDto field = new PluginPageFormFieldDto();
        field.setSelector("#x");
        field.setTagName("input");

        PluginAutofillPlanResultVO llm = PluginAutofillPlanResultVO.builder()
                .fillable(true)
                .assignments(List.of(PluginFieldAssignmentDto.builder()
                        .selector("#x").value("  ").build()))
                .blockedFields(List.of())
                .build();

        PluginAutofillPlanResultVO sanitized = newService().sanitizePlan(llm, List.of(field));

        // 无值且无 skipReason 的 assignment 维持原行为：剔除，不进 blocked
        assertThat(sanitized.getAssignments()).isEmpty();
        assertThat(sanitized.getBlockedFields()).isEmpty();
    }

    @Test
    void llmTimeout_shouldDegradeToDeterministicResultInsteadOfFailing() {
        PluginAutofillAssistant assistant = mock(PluginAutofillAssistant.class);
        BillingService billingService = mock(BillingService.class);
        AppResumeQueryService resumeQueryService = mock(AppResumeQueryService.class);
        SiteTemplateRegistry templateRegistry = mock(SiteTemplateRegistry.class);
        DeterministicFieldMatcher deterministicMatcher = mock(DeterministicFieldMatcher.class);
        ResumeDraftPromptPruner pruner = mock(ResumeDraftPromptPruner.class);

        PluginFormAutofillAiService svc = new PluginFormAutofillAiService(
                assistant,
                mock(PluginAutofillPromptProvider.class),
                new ObjectMapper(),
                resumeQueryService,
                deterministicMatcher,
                pruner,
                templateRegistry,
                mock(SiteTemplateMatcher.class),
                mock(PluginAutofillResultCacheService.class),
                mock(ResumePreloadCacheService.class),
                billingService);

        PluginPageFormFieldDto field = new PluginPageFormFieldDto();
        field.setSelector("#name");
        field.setTagName("input");
        PluginAutofillPlanRequest req = new PluginAutofillPlanRequest();
        req.setResumeId(6L);
        PluginPageFormSnapshotDto page = new PluginPageFormSnapshotDto();
        page.setPageUrl("https://example.com/apply");
        page.setFields(List.of(field));
        req.setPage(page);

        ResumeDetailVO detail = new ResumeDetailVO();
        detail.setDraft(new ResumeParseDraftDto());
        when(resumeQueryService.getResumeDetail(anyLong(), anyLong())).thenReturn(detail);
        when(templateRegistry.match(any())).thenReturn(java.util.Optional.empty());
        // 确定性层已匹配 1 个字段，剩余 1 个交 LLM
        when(deterministicMatcher.matchAll(any(), any())).thenReturn(DeterministicMatchResult.builder()
                .matchedAssignments(List.of(PluginFieldAssignmentDto.builder()
                        .selector("#name").value("黄晋豪").build()))
                .unmatchedFields(List.of(field))
                .build());
        when(pruner.prune(anyString(), any())).thenReturn("{}");
        // LLM 超时
        when(billingService.execute(anyLong(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("Read timed out"));

        PluginAutofillPlanResultVO result = svc.planAutofill(1L, req, PluginAutofillProgressSink.noop());

        // 不抛异常、不失败：降级为确定性结果，LLM 未结算
        assertThat(result.isFillable()).isTrue();
        assertThat(result.getAssignments()).hasSize(1);
        assertThat(result.getAssignments().get(0).getValue()).isEqualTo("黄晋豪");
        assertThat(result.getHint()).contains("AI");
    }
}
