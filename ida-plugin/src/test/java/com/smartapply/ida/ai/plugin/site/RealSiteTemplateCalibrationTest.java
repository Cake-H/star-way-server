package com.smartapply.ida.ai.plugin.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginFieldAssignmentDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormSnapshotDto;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplate;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplateMatchResult;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeCertificateDraftLine;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeProjectDraftLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实站点模板校准测试：字节/美团模板按真实快照的关键锚点命中与消歧。
 * 模板文件：resources/site-templates/bytedance_campus.json、meituan_campus.json。
 */
class RealSiteTemplateCalibrationTest {

    private SiteTemplateRegistry registry;
    private final SiteTemplateMatcher matcher = new SiteTemplateMatcher();

    @BeforeEach
    void setUp() {
        registry = new SiteTemplateRegistry(new ObjectMapper());
        registry.load();
    }

    @Test
    void shouldMatchBytedanceAndMeituanHosts() {
        assertThat(registry.match(pageOf("https://jobs.bytedance.com/campus/apply/123"))).isPresent();
        assertThat(registry.match(pageOf("https://zhaopin.meituan.com/web/campus/apply"))).isPresent();
        assertThat(registry.match(pageOf("https://www.baidu.com/apply"))).isEmpty();
    }

    @Test
    void bytedanceTemplate_shouldDisambiguateNameAnchorByI18nLabel() {
        // 字节页面「姓名」与「项目名称」的 dataFormFieldId 都是 name，必须按 i18nLabel 各归各位
        PluginPageFormFieldDto nameRow = field("input[data-form-field-id=\"name\"]-a", "name", "姓名");
        PluginPageFormFieldDto projectRow = field("input[data-form-field-id=\"name\"]-b", "name", "项目名称");

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        draft.setFullName("黄晋豪");
        ResumeProjectDraftLine p0 = new ResumeProjectDraftLine();
        p0.setProjectName("智能求职助手");
        draft.setProjects(List.of(p0));

        Optional<SiteTemplate> tpl = registry.match(pageOf("https://jobs.bytedance.com/campus/apply/1"));
        assertThat(tpl).isPresent();
        SiteTemplateMatchResult result = matcher.apply(tpl.get(), List.of(nameRow, projectRow), draft);

        assertThat(result.getMatchedAssignments()).hasSize(2);
        PluginFieldAssignmentDto nameA = result.getMatchedAssignments().stream()
                .filter(a -> a.getSelector().endsWith("-a")).findFirst().orElseThrow();
        PluginFieldAssignmentDto projA = result.getMatchedAssignments().stream()
                .filter(a -> a.getSelector().endsWith("-b")).findFirst().orElseThrow();
        assertThat(nameA.getValue()).isEqualTo("黄晋豪");
        assertThat(projA.getValue()).isEqualTo("智能求职助手");
        assertThat(nameA.getMatchType()).isEqualTo("template");
    }

    @Test
    void bytedanceTemplate_skipRowsShouldProduceNoMappingBlocked() {
        PluginPageFormFieldDto labRow = field("#lab", "7480588161707165970", "实验室");
        Optional<SiteTemplate> tpl = registry.match(pageOf("https://jobs.bytedance.com/campus/apply/1"));
        SiteTemplateMatchResult result = matcher.apply(tpl.orElseThrow(), List.of(labRow), new ResumeParseDraftDto());

        assertThat(result.getMatchedAssignments()).isEmpty();
        assertThat(result.getBlockedFields()).hasSize(1);
        assertThat(result.getBlockedFields().get(0).getReason()).isEqualTo("no_mapping");
        assertThat(result.getBlockedFields().get(0).getUserHint()).contains("实验室");
    }

    @Test
    void meituanTemplate_shouldMatchByLabelTextAndMapCertificate() {
        PluginPageFormFieldDto certRow = new PluginPageFormFieldDto();
        certRow.setSelector("#cert");
        certRow.setTagName("input");
        certRow.setLabelText("证书名称");

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        ResumeCertificateDraftLine c0 = new ResumeCertificateDraftLine();
        c0.setCertName("英语四级证书");
        draft.setCertificates(List.of(c0));

        Optional<SiteTemplate> tpl = registry.match(pageOf("https://zhaopin.meituan.com/web/campus/apply"));
        assertThat(tpl).isPresent();
        SiteTemplateMatchResult result = matcher.apply(tpl.get(), List.of(certRow), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("英语四级证书");
    }

    @Test
    void meituanTemplate_twoProjectBlocks_shouldFillSequentialEntries() {
        // 展开两个项目块：两个「项目名称」字段（锚点相同）→ 第 N 个命中取简历第 N 条
        PluginPageFormFieldDto nameRow0 = new PluginPageFormFieldDto();
        nameRow0.setSelector("#proj-name-0");
        nameRow0.setTagName("input");
        nameRow0.setLabelText("项目名称");
        PluginPageFormFieldDto nameRow1 = new PluginPageFormFieldDto();
        nameRow1.setSelector("#proj-name-1");
        nameRow1.setTagName("input");
        nameRow1.setLabelText("项目名称");

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        ResumeProjectDraftLine p0 = new ResumeProjectDraftLine();
        p0.setProjectName("Maverick 智投");
        ResumeProjectDraftLine p1 = new ResumeProjectDraftLine();
        p1.setProjectName("电商小程序");
        draft.setProjects(List.of(p0, p1));

        Optional<SiteTemplate> tpl = registry.match(pageOf("https://zhaopin.meituan.com/web/campus/apply"));
        SiteTemplateMatchResult result = matcher.apply(tpl.orElseThrow(),
                List.of(nameRow0, nameRow1), draft);

        assertThat(result.getMatchedAssignments()).hasSize(2);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("Maverick 智投");
        assertThat(result.getMatchedAssignments().get(1).getValue()).isEqualTo("电商小程序");
    }

    @Test
    void meituanTemplate_blockBeyondResumeEntries_shouldDeferToUnmatched() {
        // 简历只有 1 条项目，页面有 2 个块 → 第二块降级 unmatched（交下层），而不是错填第一条
        PluginPageFormFieldDto nameRow0 = new PluginPageFormFieldDto();
        nameRow0.setSelector("#proj-name-0");
        nameRow0.setTagName("input");
        nameRow0.setLabelText("项目名称");
        PluginPageFormFieldDto nameRow1 = new PluginPageFormFieldDto();
        nameRow1.setSelector("#proj-name-1");
        nameRow1.setTagName("input");
        nameRow1.setLabelText("项目名称");

        ResumeParseDraftDto draft = new ResumeParseDraftDto();
        ResumeProjectDraftLine p0 = new ResumeProjectDraftLine();
        p0.setProjectName("Maverick 智投");
        draft.setProjects(List.of(p0));

        Optional<SiteTemplate> tpl = registry.match(pageOf("https://zhaopin.meituan.com/web/campus/apply"));
        SiteTemplateMatchResult result = matcher.apply(tpl.orElseThrow(),
                List.of(nameRow0, nameRow1), draft);

        assertThat(result.getMatchedAssignments()).hasSize(1);
        assertThat(result.getMatchedAssignments().get(0).getValue()).isEqualTo("Maverick 智投");
        assertThat(result.getUnmatchedFields()).hasSize(1);
        assertThat(result.getUnmatchedFields().get(0).getSelector()).isEqualTo("#proj-name-1");
    }

    private PluginPageFormFieldDto field(String selector, String dataFormFieldId, String i18nLabel) {
        PluginPageFormFieldDto f = new PluginPageFormFieldDto();
        f.setSelector(selector);
        f.setTagName("input");
        f.setLabelText(i18nLabel);
        f.setDataFormFieldId(dataFormFieldId);
        f.setI18nLabel(i18nLabel);
        return f;
    }

    private PluginPageFormSnapshotDto pageOf(String url) {
        PluginPageFormSnapshotDto p = new PluginPageFormSnapshotDto();
        p.setPageUrl(url);
        p.setPageTitle("test");
        p.setFields(List.of());
        return p;
    }
}
