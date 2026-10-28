package com.smartapply.ida.module.app.interview.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.interview.dto.*;
import com.smartapply.ida.common.domain.interview.vo.*;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.app.interview.service.AppInterviewService;
import com.smartapply.ida.module.app.interview.service.AppInterviewSummarizeTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 正式客户端 — 面试复盘 HTTP 入口（前缀 {@code /api/v1/app/interview}）。
 * <p>
 * <b>与简历模块一致：</b>鉴权走 {@link RequestUserId} + Bearer；响应统一 {@link ResponseDTO}。
 * <p>
 * <b>语音总结：</b>{@code voice-summarize} 仅返回结构化草稿，不入库；用户确认后调 {@code POST .../questions} 保存。
 * 支持 multipart（录音 + 浏览器转写）与 JSON（纯文本口述）两种 Content-Type。
 */
@Tag(name = "正式客户端-面试复盘")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/interview")
@Slf4j
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AppInterviewController {

    private final AppInterviewService appInterviewService;
    private final AppInterviewSummarizeTaskService appInterviewSummarizeTaskService;

    @Operation(summary = "复盘场次列表")
    @GetMapping("/sessions")
    public ResponseDTO<List<InterviewSessionListItemVO>> listSessions(@RequestUserId(required = true) Long userId) {
        return ResponseDTO.ok(appInterviewService.listSessions(userId));
    }

    @Operation(summary = "场次详情（含问题列表）")
    @GetMapping("/sessions/{sessionId}")
    public ResponseDTO<InterviewSessionDetailVO> getSessionDetail(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId) {
        return ResponseDTO.ok(appInterviewService.getSessionDetail(userId, sessionId));
    }

    @Operation(summary = "新建复盘场次")
    @PostMapping(value = "/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<InterviewSessionIdVO> createSession(
            @RequestUserId(required = true) Long userId,
            @Valid @RequestBody InterviewSessionCreateRequest body) {
        return ResponseDTO.ok(appInterviewService.createSession(userId, body));
    }

    @Operation(summary = "更新场次元信息")
    @PutMapping(value = "/sessions/{sessionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<String> updateSession(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @RequestBody InterviewSessionUpdateRequest body) {
        appInterviewService.updateSession(userId, sessionId, body);
        return ResponseDTO.ok("ok");
    }

    @Operation(summary = "删除复盘场次（级联软删问题）")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseDTO<String> deleteSession(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId) {
        appInterviewService.deleteSession(userId, sessionId);
        return ResponseDTO.ok("ok");
    }

    @Operation(summary = "手动新增问题")
    @PostMapping(value = "/sessions/{sessionId}/questions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<InterviewQuestionIdVO> createQuestion(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody InterviewQuestionCreateRequest body) {
        return ResponseDTO.ok(appInterviewService.createQuestion(userId, sessionId, body));
    }

    @Operation(summary = "更新问题")
    @PutMapping(value = "/sessions/{sessionId}/questions/{questionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<String> updateQuestion(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @PathVariable("questionId") Long questionId,
            @RequestBody InterviewQuestionUpdateRequest body) {
        appInterviewService.updateQuestion(userId, sessionId, questionId, body);
        return ResponseDTO.ok("ok");
    }

    @Operation(summary = "删除问题")
    @DeleteMapping("/sessions/{sessionId}/questions/{questionId}")
    public ResponseDTO<String> deleteQuestion(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @PathVariable("questionId") Long questionId) {
        appInterviewService.deleteQuestion(userId, sessionId, questionId);
        return ResponseDTO.ok("ok");
    }

    @Operation(summary = "语音/文本 AI 总结（multipart：file + rawTranscript）")
    @PostMapping(value = "/sessions/{sessionId}/voice-summarize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<InterviewVoiceSummarizeResultVO> voiceSummarizeMultipart(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "rawTranscript", required = false) String rawTranscript) {
        log.info("面试语音总结 multipart userId={} sessionId={} hasFile={}", userId, sessionId, file != null && !file.isEmpty());
        return ResponseDTO.ok(appInterviewService.voiceSummarize(userId, sessionId, file, rawTranscript));
    }

    @Operation(summary = "语音/文本 AI 总结（JSON：rawTranscript）")
    @PostMapping(value = "/sessions/{sessionId}/voice-summarize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<InterviewVoiceSummarizeResultVO> voiceSummarizeJson(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @RequestBody(required = false) InterviewVoiceSummarizeTextRequest body) {
        String raw = body != null ? body.getRawTranscript() : null;
        log.info("面试语音总结 json userId={} sessionId={} transcriptLen={}", userId, sessionId, raw != null ? raw.length() : 0);
        return ResponseDTO.ok(appInterviewService.voiceSummarize(userId, sessionId, null, raw));
    }

    @Operation(summary = "语音/文本 AI 总结（异步任务，multipart：file + rawTranscript，返回 taskId 轮询）")
    @PostMapping(value = "/sessions/{sessionId}/voice-summarize/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDTO<String> voiceSummarizeTaskMultipart(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "rawTranscript", required = false) String rawTranscript) throws IOException {
        log.info("面试语音总结异步任务 multipart userId={} sessionId={} hasFile={}", userId, sessionId, file != null && !file.isEmpty());
        return ResponseDTO.ok(appInterviewSummarizeTaskService.submit(userId, sessionId, file, rawTranscript));
    }

    @Operation(summary = "语音/文本 AI 总结（异步任务，JSON：rawTranscript，返回 taskId 轮询）")
    @PostMapping(value = "/sessions/{sessionId}/voice-summarize/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<String> voiceSummarizeTaskJson(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @RequestBody(required = false) InterviewVoiceSummarizeTextRequest body) throws IOException {
        String raw = body != null ? body.getRawTranscript() : null;
        log.info("面试语音总结异步任务 json userId={} sessionId={} transcriptLen={}", userId, sessionId, raw != null ? raw.length() : 0);
        return ResponseDTO.ok(appInterviewSummarizeTaskService.submit(userId, sessionId, null, raw));
    }

    @Operation(summary = "轮询语音总结任务状态")
    @GetMapping("/sessions/{sessionId}/voice-summarize/tasks/{taskId}")
    public ResponseDTO<InterviewVoiceSummarizeTaskStatusVO> queryVoiceSummarizeTask(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @PathVariable("taskId") String taskId) {
        return ResponseDTO.ok(appInterviewSummarizeTaskService.query(userId, sessionId, taskId));
    }
}
