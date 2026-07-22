package com.smartapply.ida.module.admin.auth.service;

import com.smartapply.ida.common.domain.admin.vo.AdminLoginResultVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理员认证服务：账号密码登录、退出登录。
 */
public interface AdminAuthService {

    /**
     * 账号密码登录，成功签发会话 Token。
     *
     * @param username 用户名
     * @param password 明文密码（仅用于本次 BCrypt 比对）
     * @return Token 与管理员信息
     */
    AdminLoginResultVO login(String username, String password);

    /**
     * 退出登录：作废请求头中携带的会话 Token。
     */
    void logout(HttpServletRequest request);
}
