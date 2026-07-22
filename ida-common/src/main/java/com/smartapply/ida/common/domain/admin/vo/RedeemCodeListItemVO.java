package com.smartapply.ida.common.domain.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理后台兑换码列表项。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RedeemCodeListItemVO {

    /** 兑换码记录 ID */
    private Long id;

    /** 兑换码 */
    private String code;

    /** 可兑换点数 */
    private Integer points;

    /** 状态：ACTIVE/USED/EXPIRED/DISABLED */
    private String status;

    /** 使用用户 ID */
    private Long usedBy;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 生成备注 */
    private String remark;

    /** 生成时间 */
    private LocalDateTime createdAt;
}
