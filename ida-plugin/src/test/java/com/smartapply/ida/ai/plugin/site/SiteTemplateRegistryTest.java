package com.smartapply.ida.ai.plugin.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormSnapshotDto;
import com.smartapply.ida.common.domain.plugin.autofill.site.FieldAnchor;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplate;
import com.smartapply.ida.common.domain.plugin.autofill.site.TemplateFieldMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 站点模板注册表单测：验证从 classpath 加载模板、按 URL 指纹匹配、未命中返回 empty，
 * 以及加载校验（非法 resumePath / enumCategory 的模板被拒绝）。
 * 依赖 {@code resources/site-templates/beisen.json} 示例模板。
 */
class SiteTemplateRegistryTest {

    private SiteTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SiteTemplateRegistry(new ObjectMapper());
        registry.load();
    }

    @Test
    void shouldLoadExampleTemplateFromClasspath() {
        assertThat(registry.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldMatchBeisenHostSuffix() {
        PluginPageFormSnapshotDto page = pageOf("https://campus.beisen.com/apply/123", List.of());
        Optional<SiteTemplate> hit = registry.match(page);
        assertThat(hit).isPresent();
        assertThat(hit.get().getSiteId()).isEqualTo("beisen_campus");
    }

    @Test
    void shouldNotMatchYingjieshengHostSuffix() {
        // yingjiesheng.com（应届生网）不是北森 ATS，已从指纹中移除，不应命中北森模板
        PluginPageFormSnapshotDto page = pageOf("https://www.yingjiesheng.com/apply", List.of());
        assertThat(registry.match(page)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownSite() {
        PluginPageFormSnapshotDto page = pageOf("https://random.example.com/form", List.of());
        assertThat(registry.match(page)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNullPage() {
        assertThat(registry.match(null)).isEmpty();
    }

    @Test
    void shouldSkipSkeletonFilesPrefixedWithUnderscore() {
        // _template.json 的 siteId 为占位串，不应被作为正式模板加载
        PluginPageFormSnapshotDto page = pageOf("https://example.com/apply", List.of());
        Optional<SiteTemplate> hit = registry.match(page);
        assertThat(hit).isEmpty();
    }

    @Test
    void shouldRejectTemplateWithInvalidResumePath() {
        SiteTemplate tpl = SiteTemplate.builder()
                .siteId("bad_path")
                .fieldMappings(List.of(
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("姓名").build())
                                .resumePath("basic.noSuchField")
                                .build()
                ))
                .build();

        assertThat(SiteTemplateRegistry.validateTemplate(tpl)).contains("resumePath");
    }

    @Test
    void shouldRejectTemplateWithInvalidEnumCategory() {
        SiteTemplate tpl = SiteTemplate.builder()
                .siteId("bad_enum")
                .fieldMappings(List.of(
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("学历").build())
                                .resumePath("education.0.degree")
                                .enumCategory("noSuchCategory")
                                .build()
                ))
                .build();

        assertThat(SiteTemplateRegistry.validateTemplate(tpl)).contains("enumCategory");
    }

    @Test
    void shouldAcceptValidTemplateIncludingIdDocTypeCategory() {
        SiteTemplate tpl = SiteTemplate.builder()
                .siteId("good")
                .fieldMappings(List.of(
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("姓名").build())
                                .resumePath("basic.fullName")
                                .build(),
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("证件类型").build())
                                .resumePath("basic.idDocType")
                                .enumCategory("idDocType")
                                .build(),
                        // skip 行不要求 resumePath
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("附件").build())
                                .skip(true)
                                .build()
                ))
                .build();

        assertThat(SiteTemplateRegistry.validateTemplate(tpl)).isNull();
    }

    @Test
    void shouldRejectNonSkipRowWithoutResumePath() {
        SiteTemplate tpl = SiteTemplate.builder()
                .siteId("missing_path")
                .fieldMappings(List.of(
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("姓名").build())
                                .build()
                ))
                .build();

        assertThat(SiteTemplateRegistry.validateTemplate(tpl)).contains("resumePath");
    }

    @Test
    void shouldRejectSkipRowWithInvalidBlockedReason() {
        SiteTemplate tpl = SiteTemplate.builder()
                .siteId("bad_reason")
                .fieldMappings(List.of(
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("附件").build())
                                .skip(true)
                                .blockedReason("option_not_found") // 是 failReason 枚举，不是 blockedReason 白名单
                                .build()
                ))
                .build();

        assertThat(SiteTemplateRegistry.validateTemplate(tpl)).contains("blockedReason");
    }

    @Test
    void shouldAcceptSkipRowWithValidBlockedReason() {
        SiteTemplate tpl = SiteTemplate.builder()
                .siteId("good_reason")
                .fieldMappings(List.of(
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("附件").build())
                                .skip(true)
                                .blockedReason("attachment")
                                .build(),
                        // blockedReason 为空也合法（默认 other）
                        TemplateFieldMapping.builder()
                                .anchor(FieldAnchor.builder().labelTextEquals("验证码").build())
                                .skip(true)
                                .build()
                ))
                .build();

        assertThat(SiteTemplateRegistry.validateTemplate(tpl)).isNull();
    }

    private PluginPageFormSnapshotDto pageOf(String url, List<PluginPageFormFieldDto> fields) {
        PluginPageFormSnapshotDto p = new PluginPageFormSnapshotDto();
        p.setPageUrl(url);
        p.setPageTitle("test");
        p.setFields(fields);
        return p;
    }
}
