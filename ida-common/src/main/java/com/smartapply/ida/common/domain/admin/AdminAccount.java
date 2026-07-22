package com.smartapply.ida.common.domain.admin;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对应库表 {@code ida_admin_account}：管理后台管理员账号。
 * <p>
 * 与 C 端用户（{@code ida_user}）完全隔离：账号密码登录，密码仅存 BCrypt 哈希。
 */
@SuppressWarnings("serial")
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_admin_account")
public class AdminAccount {

    /** 角色：管理员（当前唯一角色，预留 RBAC 扩展） */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    /** 管理员主键 */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 密码 BCrypt 哈希（任何接口不得出参此字段） */
    private String passwordHash;

    /** 显示名称 */
    private String name;

    /** 角色，见 ROLE_* 常量 */
    private String role;

    /** 1=正常 0=禁用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
