package com.smartapply.ida.module.admin.usage.service.impl;

import com.smartapply.ida.common.domain.billing.vo.UsageBizStatVO;
import com.smartapply.ida.common.domain.billing.vo.UsageDailyStatVO;
import com.smartapply.ida.common.domain.billing.vo.UsageUserRankVO;
import com.smartapply.ida.module.admin.usage.mapper.UsageStatisticsMapper;
import com.smartapply.ida.module.admin.usage.service.AdminUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 用量看板服务实现：统一处理时间区间缺省值（近 30 天）与上限，避免全表扫描。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUsageServiceImpl implements AdminUsageService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 时间区间最大跨度（天），防止误传超大区间拖垮聚合查询 */
    private static final int MAX_RANGE_DAYS = 366;

    private final UsageStatisticsMapper usageStatisticsMapper;

    @Override
    public List<UsageDailyStatVO> dailyStats(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        return usageStatisticsMapper.selectDailyStats(
                range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay());
    }

    @Override
    public List<UsageBizStatVO> bizStats(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        return usageStatisticsMapper.selectBizStats(
                range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay());
    }

    @Override
    public List<UsageUserRankVO> topUsers(LocalDate from, LocalDate to, int limit) {
        LocalDate[] range = normalizeRange(from, to);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return usageStatisticsMapper.selectTopUsers(
                range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay(), safeLimit);
    }

    /**
     * 规范化时间区间：缺省近 30 天；from 晚于 to 时交换；跨度钳制到 {@link #MAX_RANGE_DAYS}。
     */
    private LocalDate[] normalizeRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZONE);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        if (start.isBefore(end.minusDays(MAX_RANGE_DAYS - 1L))) {
            start = end.minusDays(MAX_RANGE_DAYS - 1L);
        }
        return new LocalDate[]{start, end};
    }
}
