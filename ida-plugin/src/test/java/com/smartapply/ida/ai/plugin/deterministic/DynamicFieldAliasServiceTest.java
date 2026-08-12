package com.smartapply.ida.ai.plugin.deterministic;

import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAlias;
import com.smartapply.ida.module.app.plugin.mapper.PluginAutofillAliasMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 动态字段别名服务单测：精确/最长包含匹配、并列歧义（同路径不算歧义，不同路径→null）、
 * 短 key（<2）不参与、非法 fieldPath 加载时丢弃、刷新异常静默降级。
 */
class DynamicFieldAliasServiceTest {

    private PluginAutofillAlias row(String label, String path) {
        PluginAutofillAlias a = new PluginAutofillAlias();
        a.setAliasLabel(label);
        a.setFieldPath(path);
        a.setStatus(PluginAutofillAlias.STATUS_APPROVED);
        return a;
    }

    private DynamicFieldAliasService serviceWith(List<PluginAutofillAlias> rows) {
        PluginAutofillAliasMapper mapper = mock(PluginAutofillAliasMapper.class);
        when(mapper.selectList(any())).thenReturn(rows);
        DynamicFieldAliasService service = new DynamicFieldAliasService(mapper);
        service.refresh();
        return service;
    }

    @Test
    void resolveExact_shouldHitApprovedAlias() {
        DynamicFieldAliasService service = serviceWith(List.of(
                row("期望年薪", "job.salaryExpectation")));
        assertThat(service.resolveExact("期望年薪")).isEqualTo("job.salaryExpectation");
        assertThat(service.resolveExact("期望月薪")).isNull();
    }

    @Test
    void resolveContains_shouldPickLongestContainedKey() {
        DynamicFieldAliasService service = serviceWith(List.of(
                row("年薪", "job.salaryExpectation"),
                row("期望年薪", "job.salaryExpectation")));
        // 两个 key 都被包含，取更长的「期望年薪」
        assertThat(service.resolveContains("您的期望年薪")).isEqualTo("job.salaryExpectation");
    }

    @Test
    void resolveContains_tie_shouldReturnNull() {
        DynamicFieldAliasService service = serviceWith(List.of(
                row("年薪", "job.salaryExpectation"),
                row("月薪", "job.workYears")));
        // 「年薪」「月薪」同为长度 2 的并列最长且映射到不同路径 → 歧义返回 null
        assertThat(service.resolveContains("年薪月薪")).isNull();
    }

    @Test
    void resolveContains_tieSamePath_shouldReturnPath() {
        DynamicFieldAliasService service = serviceWith(List.of(
                row("年薪", "job.salaryExpectation"),
                row("月薪", "job.salaryExpectation")));
        // 并列最长但映射到同一路径 → 不算歧义，直接返回该路径
        assertThat(service.resolveContains("年薪月薪")).isEqualTo("job.salaryExpectation");
    }

    @Test
    void resolveContains_shortKey_shouldNotParticipate() {
        DynamicFieldAliasService service = serviceWith(List.of(
                row("Q", "basic.qq")));
        // 长度 < 2 的 key 不参与 contains 匹配
        assertThat(service.resolveContains("Q")).isNull();
        // 但精确匹配不受长度限制
        assertThat(service.resolveExact("Q")).isEqualTo("basic.qq");
    }

    @Test
    void refresh_shouldDropUnknownFieldPath() {
        DynamicFieldAliasService service = serviceWith(List.of(
                row("坏路径", "not.registered.path")));
        assertThat(service.resolveExact("坏路径")).isNull();
    }

    @Test
    void refreshException_shouldDegradeSilently() {
        PluginAutofillAliasMapper mapper = mock(PluginAutofillAliasMapper.class);
        when(mapper.selectList(any())).thenThrow(new RuntimeException("db down"));
        DynamicFieldAliasService service = new DynamicFieldAliasService(mapper);
        service.refresh(); // 不抛异常
        assertThat(service.resolveExact("期望年薪")).isNull();
    }
}
