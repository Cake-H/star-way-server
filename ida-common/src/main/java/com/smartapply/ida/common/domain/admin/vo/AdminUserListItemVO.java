package com.smartapply.ida.common.domain.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理后台用户列表项（手机号已脱敏）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserListItemVO {

    /** 用户主键 */
    private Long id;

    /** 手机号（脱敏：前 3 后 4，中间打码） */
    private String phone;

    /** 1=正常 0=禁用 */
    private Integer status;

    /** AI 额度余额（点，可为负=欠费） */
    private Integer balance;

    /** 会员等级 */
    private String memberLevel;

    /** 注册时间 */
    private LocalDateTime createdAt;
}
