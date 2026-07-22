package com.smartapply.ida.module.admin.usage.service;

import com.smartapply.ida.common.domain.billing.vo.UsageBizStatVO;
import com.smartapply.ida.common.domain.billing.vo.UsageDailyStatVO;
import com.smartapply.ida.common.domain.billing.vo.UsageUserRankVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 用量看板服务：AI 用量的按天/按业务/按用户聚合查询。
 */
public interface AdminUsageService {

    /**
     * 按天聚合（趋势图）。
     *
     * @param from 起始日期（含），null 取 30 天前
     * @param to   结束日期（含），null 取今天
     */
    List<UsageDailyStatVO> dailyStats(LocalDate from, LocalDate to);

    /** 按业务类型聚合（占比图），时间语义同 {@link #dailyStats} */
    List<UsageBizStatVO> bizStats(LocalDate from, LocalDate to);

    /** 消耗 Top 用户榜，limit 上限 100 */
    List<UsageUserRankVO> topUsers(LocalDate from, LocalDate to, int limit);
}
