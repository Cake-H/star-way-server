package com.smartapply.ida.module.admin.applysite.controller;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.applysite.dto.ApplySiteUpsertRequest;
import com.smartapply.ida.common.domain.applysite.vo.ApplySiteVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.module.admin.applysite.service.AdminApplySiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 — 网申站点管理 HTTP 入口（前缀 {@code /api/v1/admin/apply-sites}）。
 * <p>
 * 站点目录由后台维护，C 端「网申网站列表」功能读取同一张表的上架数据。
 */
@Tag(name = "管理后台-网申站点管理")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_ADMIN + "/apply-sites")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminApplySiteController {

    private final AdminApplySiteService adminApplySiteService;

    @Operation(summary = "站点分页列表")
    @GetMapping
    public ResponseDTO<PageVO<ApplySiteVO>> pageSites(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) Integer status) {
        log.debug("收到后台站点列表申请 page={} size={} category={} status={}", page, size, category, status);
        return ResponseDTO.ok(adminApplySiteService.pageSites(page, size, category, status));
    }

    @Operation(summary = "新建站点")
    @PostMapping
    public ResponseDTO<Long> createSite(@Valid @RequestBody ApplySiteUpsertRequest body) {
        log.info("收到后台新建站点申请 name={}", body.getName());
        return ResponseDTO.ok(adminApplySiteService.createSite(body));
    }

    @Operation(summary = "更新站点（不变更上下架状态）")
    @PutMapping("/{siteId}")
    public ResponseDTO<String> updateSite(
            @PathVariable("siteId") Long siteId,
            @Valid @RequestBody ApplySiteUpsertRequest body) {
        log.info("收到后台更新站点申请 id={} name={}", siteId, body.getName());
        adminApplySiteService.updateSite(siteId, body);
        return ResponseDTO.ok();
    }

    @Operation(summary = "站点上下架切换")
    @PostMapping("/{siteId}/toggle")
    public ResponseDTO<String> toggleSite(@PathVariable("siteId") Long siteId) {
        log.info("收到后台站点上下架申请 id={}", siteId);
        adminApplySiteService.toggleSite(siteId);
        return ResponseDTO.ok();
    }
}
