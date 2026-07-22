package com.smartapply.ida.module.admin.user.controller;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.admin.dto.QuotaAdjustRequest;
import com.smartapply.ida.common.domain.admin.vo.AdminUserDetailVO;
import com.smartapply.ida.common.domain.admin.vo.AdminUserListItemVO;
import com.smartapply.ida.common.domain.admin.vo.QuotaAdjustResultVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.admin.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 — 用户管理 HTTP 入口（前缀 {@code /api/v1/admin/users}）。
 * <p>
 * {@code @RequestUserId} 注入的是当前<b>管理员</b>主键（由后台鉴权过滤器写入统一身份属性）。
 */
@Tag(name = "管理后台-用户管理")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_ADMIN + "/users")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 用户分页列表（手机号脱敏）。
     */
    @Operation(summary = "用户分页列表")
    @GetMapping
    public ResponseDTO<PageVO<AdminUserListItemVO>> pageUsers(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status) {
        log.debug("收到后台用户列表申请 page={} size={} keyword={} status={}", page, size, keyword, status);
        return ResponseDTO.ok(adminUserService.pageUsers(page, size, keyword, status));
    }

    /**
     * 用户详情：基本信息 + 额度 + 简历数 + 近 30 天用量汇总。
     */
    @Operation(summary = "用户详情")
    @GetMapping("/{userId}")
    public ResponseDTO<AdminUserDetailVO> getUserDetail(@PathVariable("userId") Long userId) {
        log.debug("收到后台用户详情申请 userId={}", userId);
        return ResponseDTO.ok(adminUserService.getUserDetail(userId));
    }

    /**
     * 手动调整用户额度（正数充值、负数扣减，必须填写调整原因）。
     */
    @Operation(summary = "手动调整用户额度")
    @PostMapping("/{userId}/quota-adjust")
    public ResponseDTO<QuotaAdjustResultVO> adjustQuota(
            @RequestUserId(required = true) Long adminId,
            @PathVariable("userId") Long userId,
            @Valid @RequestBody QuotaAdjustRequest body) {
        log.info("收到后台调整额度申请 adminId={} userId={} points={}", adminId, userId, body.getPoints());
        return ResponseDTO.ok(adminUserService.adjustQuota(adminId, userId, body.getPoints(), body.getRemark()));
    }
}
