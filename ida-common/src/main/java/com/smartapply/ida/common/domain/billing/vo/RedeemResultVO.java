package com.smartapply.ida.common.domain.billing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 兑换成功结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RedeemResultVO {

    /** 已使用的兑换码 */
    private String code;

    /** 本次到账点数 */
    private Integer points;

    /** 兑换后余额 */
    private Integer balanceAfter;
}
