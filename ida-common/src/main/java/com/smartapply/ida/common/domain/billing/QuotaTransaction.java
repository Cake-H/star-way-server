package com.smartapply.ida.common.domain.billing;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对应库表 {@code ida_quota_transaction}：额度余额变动的可审计流水。
 * <p>
 * {@link #points} 增加为正、消耗为负；{@link #balanceAfter} 为变动后余额快照，便于对账。
 */
@SuppressWarnings("serial")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_quota_transaction")
public class QuotaTransaction {

    /** 每日免费额度发放 */
    public static final String TYPE_DAILY_GRANT = "daily_grant";
    /** AI 调用消耗 */
    public static final String TYPE_CONSUME = "consume";
    /** 充值（M4 支付接入后使用） */
    public static final String TYPE_RECHARGE = "recharge";
    /** 兑换码充值（M1.2 使用） */
    public static final String TYPE_REDEEM = "redeem";
    /** 管理后台手动调整（M1.3 使用） */
    public static final String TYPE_ADMIN_ADJUST = "admin_adjust";

    /** 流水 ID */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 类型，见 TYPE_* 常量 */
    private String type;

    /** 变动点数：增加为正，消耗为负 */
    private Integer points;

    /** 变动后余额 */
    private Integer balanceAfter;

    /** 备注（消耗的业务类型、兑换码、操作人等） */
    private String remark;

    private LocalDateTime createdAt;
}
