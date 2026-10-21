package com.smartapply.ida.module.app.feedback.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.feedback.vo.AppFeedbackIdVO;
import com.smartapply.ida.common.domain.feedback.vo.AppFeedbackListItemVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.app.feedback.service.AppFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 正式客户端 — 用户反馈 HTTP 入口（前缀 {@code /api/v1/app/feedback}）。
 * <p>
 * <b>与投递记录模块一致：</b>鉴权走 {@link RequestUserId} + Bearer；响应统一 {@link ResponseDTO}；
 * 「我的反馈」列表无分页，按提交时间倒序。
 */
@Tag(name = "正式客户端-用户反馈")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/feedback")
@Slf4j
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AppFeedbackController {

    private final AppFeedbackService appFeedbackService;

    @Operation(summary = "提交用户反馈（multipart，可带图片/视频附件）")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<AppFeedbackIdVO> submitFeedback(
            @RequestUserId(required = true) Long userId,
            @RequestParam("content")
            @NotBlank(message = "反馈内容不能为空")
            @Size(max = 2000, message = "反馈内容不能超过 2000 字") String content,
            @RequestParam(value = "contactEmail", required = false)
            @Email(message = "联系邮箱格式不正确")
            @Size(max = 128, message = "联系邮箱不能超过 128 字") String contactEmail,
            @RequestParam(value = "attachments", required = false) MultipartFile[] attachments) throws Exception {
        log.info("submitFeedback userId={} contentLen={} attachmentCount={}",
                userId, content != null ? content.length() : 0, attachments != null ? attachments.length : 0);
        return ResponseDTO.ok(appFeedbackService.submitFeedback(userId, content, contactEmail, attachments));
    }

    @Operation(summary = "我的反馈列表（按提交时间倒序）")
    @GetMapping("/my")
    public ResponseDTO<List<AppFeedbackListItemVO>> listMyFeedbacks(
            @RequestUserId(required = true) Long userId) {
        log.info("listMyFeedbacks userId={}", userId);
        return ResponseDTO.ok(appFeedbackService.listMyFeedbacks(userId));
    }
}
