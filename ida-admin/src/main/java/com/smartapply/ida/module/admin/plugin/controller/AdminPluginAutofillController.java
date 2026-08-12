package com.smartapply.ida.module.admin.plugin.controller;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAlias;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasCandidateVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasStatusRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasUpsertRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackStatVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.module.admin.plugin.service.AdminPluginAutofillService;
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

import java.util.List;

/**
 * 管理后台 — 插件一键填表反馈全局统计与动态别名审核（前缀 {@code /api/v1/admin/plugin}）。
 * <p>
 * 反馈统计为全局聚合（不带 userId 过滤），用于驱动站点模板扩充与动态别名登记；
 * 别名审核结果由 ida-plugin 的 {@code DynamicFieldAliasService} 定时加载生效。
 */
@Tag(name = "管理后台-插件一键填表")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_ADMIN + "/plugin")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminPluginAutofillController {

    private final AdminPluginAutofillService adminPluginAutofillService;

    @Operation(summary = "反馈全局聚合统计")
    @GetMapping("/autofill-feedback/stats")
    public ResponseDTO<PluginAutofillFeedbackStatVO> stats(
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "siteHost", required = false) String siteHost) {
        log.debug("收到后台反馈全局统计申请 days={} siteHost={}", days, siteHost);
        return ResponseDTO.ok(adminPluginAutofillService.stats(days, siteHost));
    }

    @Operation(summary = "别名候选挖掘（option_not_found/selector_miss 高频字段）")
    @GetMapping("/autofill-feedback/alias-candidates")
    public ResponseDTO<List<PluginAutofillAliasCandidateVO>> aliasCandidates(
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "limit", required = false) Integer limit) {
        log.debug("收到后台别名候选挖掘申请 days={} limit={}", days, limit);
        return ResponseDTO.ok(adminPluginAutofillService.aliasCandidates(days, limit));
    }

    @Operation(summary = "站点候选挖掘（命中率最差站点 Top N）")
    @GetMapping("/autofill-feedback/site-candidates")
    public ResponseDTO<List<PluginAutofillFeedbackStatVO.FieldCount>> siteCandidates(
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "limit", required = false) Integer limit) {
        log.debug("收到后台站点候选挖掘申请 days={} limit={}", days, limit);
        return ResponseDTO.ok(adminPluginAutofillService.siteCandidates(days, limit));
    }

    @Operation(summary = "动态别名分页列表")
    @GetMapping("/autofill-alias")
    public ResponseDTO<PageVO<PluginAutofillAlias>> pageAliases(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        log.debug("收到后台动态别名列表申请 status={} page={} size={}", status, page, size);
        return ResponseDTO.ok(adminPluginAutofillService.pageAliases(status, page, size));
    }

    @Operation(summary = "新增动态别名（默认 approved 即生效）")
    @PostMapping("/autofill-alias")
    public ResponseDTO<Long> createAlias(@Valid @RequestBody PluginAutofillAliasUpsertRequest body) {
        log.info("收到后台新增动态别名申请 label={}", body.getAliasLabel());
        return ResponseDTO.ok(adminPluginAutofillService.createAlias(body));
    }

    @Operation(summary = "动态别名审核（approved/rejected）")
    @PutMapping("/autofill-alias/{id}/status")
    public ResponseDTO<String> reviewAlias(
            @PathVariable("id") Long id,
            @Valid @RequestBody PluginAutofillAliasStatusRequest body) {
        log.info("收到后台动态别名审核申请 id={} status={}", id, body.getStatus());
        adminPluginAutofillService.reviewAlias(id, body.getStatus());
        return ResponseDTO.ok();
    }
}
