package com.smartapply.ida.module.admin.usage.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.billing.vo.UsageBizStatVO;
import com.smartapply.ida.common.domain.billing.vo.UsageDailyStatVO;
import com.smartapply.ida.common.domain.billing.vo.UsageUserRankVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.module.admin.usage.service.AdminUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理后台 — 用量看板 HTTP 入口（前缀 {@code /api/v1/admin/usage}）。
 * <p>
 * from/to 均为可选（yyyy-MM-dd），缺省统计近 30 天；跨度由 Service 钳制，防止超大区间拖垮聚合。
 */
@Tag(name = "管理后台-用量看板")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_ADMIN + "/usage")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminUsageController {

    private final AdminUsageService adminUsageService;

    @Operation(summary = "按天聚合（调用次数/token/扣点趋势）")
    @GetMapping("/daily")
    public ResponseDTO<List<UsageDailyStatVO>> dailyStats(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("收到用量按天聚合申请 from={} to={}", from, to);
        return ResponseDTO.ok(adminUsageService.dailyStats(from, to));
    }

    @Operation(summary = "按业务类型聚合（简历解析/一键填表/面试总结/模拟面试占比）")
    @GetMapping("/by-biz")
    public ResponseDTO<List<UsageBizStatVO>> bizStats(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("收到用量按业务聚合申请 from={} to={}", from, to);
        return ResponseDTO.ok(adminUsageService.bizStats(from, to));
    }

    @Operation(summary = "消耗 Top 用户榜")
    @GetMapping("/top-users")
    public ResponseDTO<List<UsageUserRankVO>> topUsers(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.debug("收到消耗 Top 用户申请 from={} to={} limit={}", from, to, limit);
        return ResponseDTO.ok(adminUsageService.topUsers(from, to, limit));
    }
}
