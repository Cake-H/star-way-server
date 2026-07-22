package com.smartapply.ida.common.domain.billing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用量看板：用户消耗聚合行（Top 榜 / 单用户汇总复用）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UsageUserRankVO {

    /** 用户主键（单用户汇总时同为用户主键） */
    private Long userId;

    /** AI 调用次数 */
    private Long callCount;

    /** 消耗 token 总数 */
    private Long totalTokens;

    /** 消耗额度点数 */
    private Long costPoints;
}
