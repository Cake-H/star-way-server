package com.smartapply.ida.common.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员登录请求。
 */
@Data
public class AdminLoginRequest {

    /** 登录用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64 位")
    private String username;

    /** 登录密码（明文仅存在于本次请求，服务端比对 BCrypt 哈希后即丢弃） */
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过 128 位")
    private String password;
}
