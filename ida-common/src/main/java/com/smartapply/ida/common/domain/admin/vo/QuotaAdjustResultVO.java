package com.smartapply.ida.common.domain.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员调整额度结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuotaAdjustResultVO {

    /** 被调整用户主键 */
    private Long userId;

    /** 本次调整点数（正=充值，负=扣减） */
    private Integer points;

    /** 调整后余额 */
    private Integer balanceAfter;
}
