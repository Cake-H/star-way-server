package com.smartapply.ida.module.admin.plugin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAlias;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasCandidateVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasUpsertRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedback;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackStatVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.plugin.mapper.PluginAutofillAliasMapper;
import com.smartapply.ida.module.app.plugin.mapper.PluginAutofillFeedbackMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理后台插件一键填表服务单测：别名 CRUD（默认 approved、重复拦截、审核白名单）与候选挖掘映射。
 */
@ExtendWith(MockitoExtension.class)
class AdminPluginAutofillServiceImplTest {

    @Mock
    private PluginAutofillFeedbackMapper feedbackMapper;
    @Mock
    private PluginAutofillAliasMapper aliasMapper;
    @InjectMocks
    private AdminPluginAutofillServiceImpl service;

    private PluginAutofillAliasUpsertRequest upsert(String label, String path) {
        PluginAutofillAliasUpsertRequest req = new PluginAutofillAliasUpsertRequest();
        req.setAliasLabel(label);
        req.setFieldPath(path);
        return req;
    }

    @Test
    void createAlias_shouldDefaultToApprovedAndManual() {
        when(aliasMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0L);

        service.createAlias(upsert("期望年薪", "job.salaryExpectation"));

        ArgumentCaptor<PluginAutofillAlias> captor = ArgumentCaptor.forClass(PluginAutofillAlias.class);
        verify(aliasMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PluginAutofillAlias.STATUS_APPROVED);
        assertThat(captor.getValue().getSource()).isEqualTo(PluginAutofillAlias.SOURCE_MANUAL);
        assertThat(captor.getValue().getAliasLabel()).isEqualTo("期望年薪");
        assertThat(captor.getValue().getFieldPath()).isEqualTo("job.salaryExpectation");
    }

    @Test
    void createAlias_duplicate_shouldThrow() {
        when(aliasMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.createAlias(upsert("期望年薪", "job.salaryExpectation")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("别名已存在");
    }

    @Test
    void createAlias_concurrentDuplicateKey_shouldThrowBusinessException() {
        // selectCount 预检与 insert 之间存在 TOCTOU 窗口：并发/重复点击撞 uk_alias_label 唯一索引
        when(aliasMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0L);
        when(aliasMapper.insert(any(PluginAutofillAlias.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_alias_label'"));

        assertThatThrownBy(() -> service.createAlias(upsert("期望年薪", "job.salaryExpectation")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("别名已存在: 期望年薪");
    }

    @Test
    void createAlias_shouldTrimLabelAndPath() {
        when(aliasMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0L);

        service.createAlias(upsert("  期望年薪  ", "  job.salaryExpectation  "));

        ArgumentCaptor<PluginAutofillAlias> captor = ArgumentCaptor.forClass(PluginAutofillAlias.class);
        verify(aliasMapper).insert(captor.capture());
        assertThat(captor.getValue().getAliasLabel()).isEqualTo("期望年薪");
        assertThat(captor.getValue().getFieldPath()).isEqualTo("job.salaryExpectation");
    }

    @Test
    void stats_daysAboveMax_shouldClampTo90() {
        when(feedbackMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0L);

        PluginAutofillFeedbackStatVO vo = service.stats(100000, null);

        assertThat(vo.getDays()).isEqualTo(90);
        // 查询条件的 created_at 下限应按钳制后的 90 天计算
        ArgumentCaptor<QueryWrapper<PluginAutofillFeedback>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(feedbackMapper, atLeastOnce()).selectCount(captor.capture());
        QueryWrapper<PluginAutofillFeedback> w = captor.getValue();
        // MP 延迟物化绑定参数：先生成 SQL 片段，paramNameValuePairs 才有值
        w.getCustomSqlSegment();
        LocalDateTime since = w.getParamNameValuePairs().values().stream()
                .filter(LocalDateTime.class::isInstance)
                .map(LocalDateTime.class::cast)
                .findFirst().orElseThrow();
        assertThat(since).isBetween(
                LocalDateTime.now().minusDays(91), LocalDateTime.now().minusDays(89));
    }

    @Test
    void createAlias_invalidStatus_shouldThrow() {
        PluginAutofillAliasUpsertRequest req = upsert("期望年薪", "job.salaryExpectation");
        req.setStatus("enabled");

        assertThatThrownBy(() -> service.createAlias(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pending/approved/rejected");
    }

    @Test
    void reviewAlias_shouldUpdateStatus() {
        PluginAutofillAlias existing = new PluginAutofillAlias();
        existing.setId(1L);
        existing.setStatus(PluginAutofillAlias.STATUS_PENDING);
        when(aliasMapper.selectById(1L)).thenReturn(existing);

        service.reviewAlias(1L, PluginAutofillAlias.STATUS_APPROVED);

        ArgumentCaptor<PluginAutofillAlias> captor = ArgumentCaptor.forClass(PluginAutofillAlias.class);
        verify(aliasMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PluginAutofillAlias.STATUS_APPROVED);
    }

    @Test
    void reviewAlias_invalidStatusOrNotFound_shouldThrow() {
        assertThatThrownBy(() -> service.reviewAlias(1L, "pending"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("approved/rejected");

        when(aliasMapper.selectById(2L)).thenReturn(null);
        assertThatThrownBy(() -> service.reviewAlias(2L, PluginAutofillAlias.STATUS_APPROVED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("别名不存在");
    }

    @Test
    void aliasCandidates_shouldMapAggregationRows() {
        Map<String, Object> row = new HashMap<>();
        row.put("k", "basic.email");
        row.put("cnt", 12L);
        row.put("sample_host", "beisen.com");
        when(feedbackMapper.selectMaps(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(row));

        List<PluginAutofillAliasCandidateVO> out = service.aliasCandidates(7, 10);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getKey()).isEqualTo("basic.email");
        assertThat(out.get(0).getCount()).isEqualTo(12L);
        assertThat(out.get(0).getSampleSiteHost()).isEqualTo("beisen.com");
    }

    @Test
    void siteCandidates_shouldSortByFillRateAsc() {
        Map<String, Object> good = new HashMap<>();
        good.put("k", "good.com");
        good.put("total", 10L);
        good.put("filled", 9L);
        Map<String, Object> bad = new HashMap<>();
        bad.put("k", "bad.com");
        bad.put("total", 10L);
        bad.put("filled", 2L);
        when(feedbackMapper.selectMaps(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(good, bad));

        var out = service.siteCandidates(30, 10);

        // 命中率升序：最差的在前
        assertThat(out).extracting("key").containsExactly("bad.com", "good.com");
        assertThat(out.get(0).getFillRate()).isEqualTo(0.2d);
    }
}
