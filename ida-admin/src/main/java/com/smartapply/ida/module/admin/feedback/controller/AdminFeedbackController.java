package com.smartapply.ida.module.admin.feedback.controller;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.feedback.dto.AppFeedbackStatusUpdateRequest;
import com.smartapply.ida.common.domain.feedback.vo.AdminFeedbackVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.admin.feedback.service.AdminFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 — 用户反馈 HTTP 入口（前缀 {@code /api/v1/admin/feedback}）。
 * <p>
 * {@code @RequestUserId} 注入的是当前<b>管理员</b>主键（由后台鉴权过滤器写入统一身份属性）。
 */
@Tag(name = "管理后台-用户反馈")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_ADMIN + "/feedback")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    /**
     * 反馈分页列表（按提交时间倒序）：status 精确筛选，keyword 模糊匹配反馈内容/联系邮箱。
     */
    @Operation(summary = "反馈分页列表")
    @GetMapping
    public ResponseDTO<PageVO<AdminFeedbackVO>> pageFeedbacks(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        log.debug("收到后台反馈列表申请 page={} size={} status={} keyword={}", page, size, status, keyword);
        return ResponseDTO.ok(adminFeedbackService.pageFeedbacks(page, size, status, keyword));
    }

    /**
     * 反馈详情：附件 objectKey 已换成预签名 URL。
     */
    @Operation(summary = "反馈详情")
    @GetMapping("/{feedbackId}")
    public ResponseDTO<AdminFeedbackVO> getFeedbackDetail(@PathVariable("feedbackId") Long feedbackId) {
        log.debug("收到后台反馈详情申请 feedbackId={}", feedbackId);
        return ResponseDTO.ok(adminFeedbackService.getFeedbackDetail(feedbackId));
    }

    /**
     * 更新反馈处理状态（PENDING/PROCESSING/RESOLVED），可附处理备注。
     */
    @Operation(summary = "更新反馈处理状态")
    @PutMapping("/{feedbackId}/status")
    public ResponseDTO<String> updateFeedbackStatus(
            @RequestUserId(required = true) Long adminId,
            @PathVariable("feedbackId") Long feedbackId,
            @Valid @RequestBody AppFeedbackStatusUpdateRequest body) {
        log.info("收到后台更新反馈状态申请 adminId={} feedbackId={} status={}", adminId, feedbackId, body.getStatus());
        adminFeedbackService.updateFeedbackStatus(feedbackId, body);
        return ResponseDTO.ok();
    }
}
