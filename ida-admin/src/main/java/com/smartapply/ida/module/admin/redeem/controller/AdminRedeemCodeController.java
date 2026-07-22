package com.smartapply.ida.module.admin.redeem.controller;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.admin.dto.GenerateRedeemCodeRequest;
import com.smartapply.ida.common.domain.admin.vo.RedeemCodeListItemVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.admin.redeem.service.AdminRedeemCodeService;
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

import java.util.List;

/**
 * 管理后台 — 兑换码管理 HTTP 入口（前缀 {@code /api/v1/admin/redeem-codes}）。
 */
@Tag(name = "管理后台-兑换码管理")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_ADMIN + "/redeem-codes")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminRedeemCodeController {

    private final AdminRedeemCodeService adminRedeemCodeService;

    /**
     * 批量生成兑换码：明文码仅此一次完整返回，前端应提供导出/复制。
     */
    @Operation(summary = "批量生成兑换码")
    @PostMapping("/generate")
    public ResponseDTO<List<RedeemCodeListItemVO>> generateCodes(
            @RequestUserId(required = true) Long adminId,
            @Valid @RequestBody GenerateRedeemCodeRequest body) {
        log.info("收到后台生成兑换码申请 adminId={} count={} points={}", adminId, body.getCount(), body.getPoints());
        return ResponseDTO.ok(adminRedeemCodeService.generateCodes(
                adminId, body.getCount(), body.getPoints(), body.getExpireTime(), body.getRemark()));
    }

    @Operation(summary = "兑换码分页列表")
    @GetMapping
    public ResponseDTO<PageVO<RedeemCodeListItemVO>> pageCodes(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size,
            @RequestParam(value = "status", required = false) String status) {
        log.debug("收到后台兑换码列表申请 page={} size={} status={}", page, size, status);
        return ResponseDTO.ok(adminRedeemCodeService.pageCodes(page, size, status));
    }

    @Operation(summary = "禁用兑换码（仅未使用状态可禁用）")
    @PostMapping("/{codeId}/disable")
    public ResponseDTO<String> disableCode(
            @RequestUserId(required = true) Long adminId,
            @PathVariable("codeId") Long codeId) {
        log.info("收到后台禁用兑换码申请 adminId={} codeId={}", adminId, codeId);
        adminRedeemCodeService.disableCode(adminId, codeId);
        return ResponseDTO.ok();
    }
}
