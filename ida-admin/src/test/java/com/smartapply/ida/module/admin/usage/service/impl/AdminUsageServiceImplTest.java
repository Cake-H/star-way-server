package com.smartapply.ida.module.admin.usage.service.impl;

import com.smartapply.ida.module.admin.usage.mapper.UsageStatisticsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用量看板服务单测：时间区间缺省/交换/钳制、limit 上限。
 */
@ExtendWith(MockitoExtension.class)
class AdminUsageServiceImplTest {

    @Mock
    private UsageStatisticsMapper usageStatisticsMapper;
    @InjectMocks
    private AdminUsageServiceImpl service;

    @Test
    void dailyStats_nullRange_shouldDefaultToRecent30Days() {
        when(usageStatisticsMapper.selectDailyStats(any(), any())).thenReturn(List.of());

        service.dailyStats(null, null);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(usageStatisticsMapper).selectDailyStats(fromCaptor.capture(), toCaptor.capture());

        // 缺省区间应为约 30 天（29 天前 00:00 → 明天 00:00）
        long days = java.time.Duration.between(fromCaptor.getValue(), toCaptor.getValue()).toDays();
        assertThat(days).isEqualTo(30);
    }

    @Test
    void dailyStats_reversedRange_shouldSwap() {
        when(usageStatisticsMapper.selectDailyStats(any(), any())).thenReturn(List.of());
        LocalDate later = LocalDate.of(2026, 7, 20);
        LocalDate earlier = LocalDate.of(2026, 7, 1);

        service.dailyStats(later, earlier);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(usageStatisticsMapper).selectDailyStats(fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue().toLocalDate()).isEqualTo(earlier);
        assertThat(toCaptor.getValue().toLocalDate()).isEqualTo(later.plusDays(1));
    }

    @Test
    void dailyStats_excessiveRange_shouldClampTo366Days() {
        when(usageStatisticsMapper.selectDailyStats(any(), any())).thenReturn(List.of());
        LocalDate to = LocalDate.of(2026, 7, 20);

        service.dailyStats(LocalDate.of(2020, 1, 1), to);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(usageStatisticsMapper).selectDailyStats(fromCaptor.capture(), any());
        assertThat(fromCaptor.getValue().toLocalDate()).isEqualTo(to.minusDays(365));
    }

    @Test
    void topUsers_limit_shouldClampTo100() {
        when(usageStatisticsMapper.selectTopUsers(any(), any(), anyInt())).thenReturn(List.of());

        service.topUsers(null, null, 99999);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(usageStatisticsMapper).selectTopUsers(any(), any(), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(100);
    }

    @Test
    void topUsers_zeroLimit_shouldClampTo1() {
        when(usageStatisticsMapper.selectTopUsers(any(), any(), anyInt())).thenReturn(List.of());

        service.topUsers(null, null, 0);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(usageStatisticsMapper).selectTopUsers(any(), any(), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(1);
    }
}
