package com.smartapply.ida.common.domain.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台用户详情（含额度与近 30 天 AI 用量汇总；手机号已脱敏）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserDetailVO {

    /** 用户主键 */
    private Long id;

    /** 手机号（脱敏） */
    private String phone;

    /** 1=正常 0=禁用 */
    private Integer status;

    /** AI 额度余额（点，可为负=欠费） */
    private Integer balance;

    /** 累计发放额度（点） */
    private Integer totalQuota;

    /** 会员等级 */
    private String memberLevel;

    /** 最近一次每日免费额度发放日期 */
    private LocalDate lastDailyGrantDate;

    /** 简历数量 */
    private Long resumeCount;

    /** 近 30 天 AI 调用次数 */
    private Long recent30dCallCount;

    /** 近 30 天 AI 消耗 token 数 */
    private Long recent30dTotalTokens;

    /** 近 30 天 AI 消耗额度点数 */
    private Long recent30dCostPoints;

    /** 注册时间 */
    private LocalDateTime createdAt;
}
