package com.smartapply.ida.common.domain.billing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 额度流水明细。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuotaTransactionVO {

    /** 流水 ID */
    private Long id;

    /** 类型：daily_grant/consume/recharge/redeem/admin_adjust */
    private String type;

    /** 变动点数：增加为正，消耗为负 */
    private Integer points;

    /** 变动后余额 */
    private Integer balanceAfter;

    /** 备注 */
    private String remark;

    /** 发生时间 */
    private LocalDateTime createdAt;
}
