package com.smartapply.ida.module.app.user.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.user.form.AccountDeleteForm;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.app.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 正式客户端 — 用户账号。
 */
@Tag(name = "正式客户端-用户")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/user")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class UserController {

    private final UserService userService;

    /**
     * 账号注销（合规）：匿名化手机号 + 逻辑删除账号及简历/面试等关联数据，操作不可恢复。
     */
    @Operation(summary = "账号注销（需确认文案「注销」，不可恢复）")
    @DeleteMapping("/account")
    public ResponseDTO<Void> deleteAccount(
            @RequestUserId(required = true) Long userId,
            @Valid @RequestBody AccountDeleteForm form,
            HttpServletRequest request) {
        log.info("收到账号注销申请 userId={}", userId);
        userService.deleteAccount(userId, form.getConfirmText(), extractBearerToken(request));
        return ResponseDTO.ok(null);
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String raw = request.getHeader("Authorization");
        if (!StringUtils.hasText(raw) || !raw.startsWith("Bearer ")) {
            return null;
        }
        return raw.substring("Bearer ".length()).trim();
    }
}
