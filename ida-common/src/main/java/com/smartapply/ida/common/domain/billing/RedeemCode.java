package com.smartapply.ida.common.domain.billing;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对应库表 {@code ida_redeem_code}：测试期过渡用 AI 额度兑换码。
 * <p>
 * 由运营后台生成，用户在客户端输入后兑换额度；
 * 正式支付通道（M4）上线后，兑换码可作为营销/补偿手段保留。
 */
@SuppressWarnings("serial")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_redeem_code")
public class RedeemCode {

    /** 可用 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 已使用 */
    public static final String STATUS_USED = "USED";
    /** 已过期（查询时动态判断，也可由后台任务标记） */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 已禁用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 兑换码记录 ID */
    private Long id;

    /** 兑换码，全局唯一 */
    private String code;

    /** 可兑换额度点数 */
    private Integer points;

    /** 状态，见 STATUS_* 常量 */
    private String status;

    /** 使用用户 ID */
    private Long usedBy;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 过期时间，NULL 表示永不过期 */
    private LocalDateTime expireTime;

    /** 生成备注 */
    private String remark;

    /** 生成人/系统标识 */
    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
