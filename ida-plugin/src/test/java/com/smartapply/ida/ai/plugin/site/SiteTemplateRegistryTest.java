package com.smartapply.ida.ai.plugin.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormSnapshotDto;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 站点模板注册表单测：验证从 classpath 加载模板、按 URL 指纹匹配、未命中返回 empty。
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
    void shouldMatchYingjieshengHostSuffix() {
        PluginPageFormSnapshotDto page = pageOf("https://www.yingjiesheng.com/apply", List.of());
        Optional<SiteTemplate> hit = registry.match(page);
        assertThat(hit).isPresent();
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

    private PluginPageFormSnapshotDto pageOf(String url, List<PluginPageFormFieldDto> fields) {
        PluginPageFormSnapshotDto p = new PluginPageFormSnapshotDto();
        p.setPageUrl(url);
        p.setPageTitle("test");
        p.setFields(fields);
        return p;
    }
}
