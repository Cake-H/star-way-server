package com.smartapply.ida.common.domain.billing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 用户额度总览。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuotaBalanceVO {

    /** 当前可用额度点数 */
    private Integer balance;

    /** 累计发放额度点数 */
    private Integer totalQuota;

    /** 会员等级 */
    private String memberLevel;

    /** 最近一次每日免费额度发放日期 */
    private LocalDate lastDailyGrantDate;
}
