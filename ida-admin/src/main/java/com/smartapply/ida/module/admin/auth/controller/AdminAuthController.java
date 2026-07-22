package com.smartapply.ida.module.admin.auth.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.admin.dto.AdminLoginRequest;
import com.smartapply.ida.common.domain.admin.vo.AdminLoginResultVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.NoNeedLogin;
import com.smartapply.ida.module.admin.auth.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台认证 HTTP 层：仅做参数校验与委托，业务规则在 {@link AdminAuthService}。
 * <p>
 * 路径前缀：{@link ApiRouteConstants#API_V1_ADMIN}{@code /auth}。
 */
@Tag(name = "管理后台-认证")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_ADMIN + "/auth")
@Slf4j
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @NoNeedLogin
    @Operation(summary = "管理员账号密码登录")
    @PostMapping("/login")
    public ResponseDTO<AdminLoginResultVO> login(@Valid @RequestBody AdminLoginRequest body) {
        log.info("收到请求 /auth/login ：管理员登录，username={}", body.getUsername());
        return ResponseDTO.ok(adminAuthService.login(body.getUsername(), body.getPassword()));
    }

    @Operation(summary = "退出登录（作废会话 Token）")
    @PostMapping("/logout")
    public ResponseDTO<String> logout(HttpServletRequest request) {
        log.info("收到请求 /auth/logout ：管理员退出登录");
        adminAuthService.logout(request);
        return ResponseDTO.ok();
    }
}
