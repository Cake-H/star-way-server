package com.smartapply.ida.module.admin.usage.mapper;

import com.smartapply.ida.common.domain.billing.vo.UsageBizStatVO;
import com.smartapply.ida.common.domain.billing.vo.UsageDailyStatVO;
import com.smartapply.ida.common.domain.billing.vo.UsageUserRankVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用量看板聚合查询（事实表 {@code ida_ai_usage_record}）。
 * <p>
 * 聚合 SQL 直接注解在 Mapper 上，全部走索引 {@code (user_id, created_at)} / {@code (biz_type, created_at)}；
 * 时间区间由 Service 保证不为空（默认近 30 天），避免全表扫描。
 */
@Mapper
public interface UsageStatisticsMapper {

    /** 按天聚合：趋势图数据 */
    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day,
                   COUNT(*) AS callCount,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM(cost_points), 0) AS costPoints
            FROM ida_ai_usage_record
            WHERE created_at >= #{from} AND created_at < #{to}
            GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')
            ORDER BY day
            """)
    List<UsageDailyStatVO> selectDailyStats(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 按业务类型聚合：占比图数据 */
    @Select("""
            SELECT biz_type AS bizType,
                   COUNT(*) AS callCount,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM(cost_points), 0) AS costPoints
            FROM ida_ai_usage_record
            WHERE created_at >= #{from} AND created_at < #{to}
            GROUP BY biz_type
            ORDER BY costPoints DESC
            """)
    List<UsageBizStatVO> selectBizStats(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 消耗 Top 用户榜 */
    @Select("""
            SELECT user_id AS userId,
                   COUNT(*) AS callCount,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM(cost_points), 0) AS costPoints
            FROM ida_ai_usage_record
            WHERE created_at >= #{from} AND created_at < #{to}
            GROUP BY user_id
            ORDER BY costPoints DESC
            LIMIT #{limit}
            """)
    List<UsageUserRankVO> selectTopUsers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                         @Param("limit") int limit);

    /** 单用户指定时间起的用量汇总（用户详情页近 30 天数据） */
    @Select("""
            SELECT #{userId} AS userId,
                   COUNT(*) AS callCount,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM(cost_points), 0) AS costPoints
            FROM ida_ai_usage_record
            WHERE user_id = #{userId} AND created_at >= #{from}
            """)
    UsageUserRankVO selectUserSummary(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}
