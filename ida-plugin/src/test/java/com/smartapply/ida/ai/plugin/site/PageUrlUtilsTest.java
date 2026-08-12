package com.smartapply.ida.ai.plugin.site;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PageUrlUtils#extractPath(String)} 单测：去 query/fragment、小写、无 path 归一为 "/"。
 */
class PageUrlUtilsTest {

    @Test
    void shouldStripQueryAndFragment() {
        assertThat(PageUrlUtils.extractPath("https://jobs.example.com/Apply/Form?jobId=123#section"))
                .isEqualTo("/apply/form");
    }

    @Test
    void shouldReturnSlashWhenNoPath() {
        assertThat(PageUrlUtils.extractPath("https://jobs.example.com")).isEqualTo("/");
        assertThat(PageUrlUtils.extractPath("https://jobs.example.com?x=1")).isEqualTo("/");
        assertThat(PageUrlUtils.extractPath("https://jobs.example.com#top")).isEqualTo("/");
    }

    @Test
    void shouldReturnSlashForBlankOrNoSlashUrl() {
        assertThat(PageUrlUtils.extractPath(null)).isEqualTo("/");
        assertThat(PageUrlUtils.extractPath("")).isEqualTo("/");
        assertThat(PageUrlUtils.extractPath("   ")).isEqualTo("/");
        assertThat(PageUrlUtils.extractPath("jobs.example.com")).isEqualTo("/");
    }

    @Test
    void shouldHandleUrlWithoutScheme() {
        assertThat(PageUrlUtils.extractPath("jobs.example.com/Apply?jobId=1")).isEqualTo("/apply");
    }

    @Test
    void shouldKeepTrailingSlashPath() {
        assertThat(PageUrlUtils.extractPath("https://jobs.example.com/job/")).isEqualTo("/job/");
        assertThat(PageUrlUtils.extractPath("https://jobs.example.com/")).isEqualTo("/");
    }

    @Test
    void shouldNotTreatSlashInsideQueryAsPath() {
        // query 内的斜杠不能被误认为 path 起点
        assertThat(PageUrlUtils.extractPath("https://host?next=/job/1")).isEqualTo("/");
        assertThat(PageUrlUtils.extractPath("https://host/apply?next=/job/1")).isEqualTo("/apply");
        assertThat(PageUrlUtils.extractPath("https://host#frag/x")).isEqualTo("/");
    }

    @Test
    void shouldExtractHostAsBefore() {
        assertThat(PageUrlUtils.extractHost("https://user@Jobs.Example.com:8443/apply?x=1"))
                .isEqualTo("jobs.example.com");
    }
}
