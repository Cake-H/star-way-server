package com.smartapply.ida.common.domain.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员登录结果。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminLoginResultVO {

    /** 会话 Token，后续请求以 {@code Authorization: Bearer <token>} 携带 */
    private String token;

    /** 管理员显示名称 */
    private String name;

    /** 角色 */
    private String role;
}
