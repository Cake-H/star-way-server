package com.smartapply.ida.module.app.jobApplication.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationCreateRequest;
import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationQueryRequest;
import com.smartapply.ida.common.domain.jobApplication.dto.JobApplicationUpdateRequest;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationIdVO;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationListItemVO;
import com.smartapply.ida.common.domain.jobApplication.vo.JobApplicationProgressOptionVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.app.jobApplication.service.AppJobApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 正式客户端 — 投递记录 HTTP 入口（前缀 {@code /api/v1/app/job-application}）。
 * <p>
 * <b>与面试复盘模块一致：</b>鉴权走 {@link RequestUserId} + Bearer；响应统一 {@link ResponseDTO}；
 * 单行单岗位，列表无分页，筛选条件可选且可组合。
 */
@Tag(name = "正式客户端-投递记录")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/job-application")
@Slf4j
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AppJobApplicationController {

    private final AppJobApplicationService appJobApplicationService;

    @Operation(summary = "投递记录列表（公司/岗位/日期/进度组合筛选）")
    @GetMapping
    public ResponseDTO<List<JobApplicationListItemVO>> listApplications(
            @RequestUserId(required = true) Long userId,
            JobApplicationQueryRequest query) {
        log.info("listApplications userId={} query={}", userId, query);
        return ResponseDTO.ok(appJobApplicationService.listApplications(userId, query));
    }

    @Operation(summary = "投递进度下拉选项")
    @GetMapping("/progress-options")
    public ResponseDTO<List<JobApplicationProgressOptionVO>> listProgressOptions(
            @RequestUserId(required = true) Long userId) {
        log.info("listProgressOptions userId={}", userId);
        return ResponseDTO.ok(appJobApplicationService.listProgressOptions());
    }

    @Operation(summary = "新建投递记录")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<JobApplicationIdVO> createApplication(
            @RequestUserId(required = true) Long userId,
            @Valid @RequestBody JobApplicationCreateRequest body) {
        log.info("createApplication userId={} body={}", userId, body);
        return ResponseDTO.ok(appJobApplicationService.createApplication(userId, body));
    }

    @Operation(summary = "更新投递记录")
    @PutMapping(value = "/{applicationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<String> updateApplication(
            @RequestUserId(required = true) Long userId,
            @PathVariable("applicationId") Long applicationId,
            @RequestBody JobApplicationUpdateRequest body) {
        log.info("updateApplication userId={} applicationId={} body={}", userId, applicationId, body);
        appJobApplicationService.updateApplication(userId, applicationId, body);
        return ResponseDTO.ok();
    }

    @Operation(summary = "删除投递记录（软删）")
    @DeleteMapping("/{applicationId}")
    public ResponseDTO<String> deleteApplication(
            @RequestUserId(required = true) Long userId,
            @PathVariable("applicationId") Long applicationId) {
        log.info("deleteApplication userId={} applicationId={}", userId, applicationId);
        appJobApplicationService.deleteApplication(userId, applicationId);
        return ResponseDTO.ok();
    }
}
