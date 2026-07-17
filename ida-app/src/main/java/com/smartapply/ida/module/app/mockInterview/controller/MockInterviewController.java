package com.smartapply.ida.module.app.mockInterview.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.mockInterview.dto.MockInterviewCreateDTO;
import com.smartapply.ida.common.domain.mockInterview.dto.MockInterviewUpdateProgressDTO;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewDetailVO;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewListItemVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.app.mockInterview.service.MockInterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "模拟面试")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_APP + "/mock-interview")
@Slf4j
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;

    @Operation(summary = "获取模拟面试列表")
    @GetMapping("")
    public ResponseDTO<List<MockInterviewListItemVO>> getMockInterviewList(@RequestUserId(required = true) Long userId) {
        log.info("收到请求 /mock-interview ：获取模拟面试列表");
        return ResponseDTO.ok(mockInterviewService.getMockInterviewList(userId));
    }

    @Operation(summary = "获取模拟面试详情")
    @GetMapping("/{sessionId}")
    public ResponseDTO<MockInterviewDetailVO> getMockInterviewDetail(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId) {
        log.info("收到请求 /mock-interview/{} ：获取模拟面试详情", sessionId);
        return ResponseDTO.ok(mockInterviewService.getMockInterviewDetail(userId, sessionId));
    }

    @Operation(summary = "创建模拟面试")
    @PostMapping("")
    public ResponseDTO<Long> createMockInterview(
            @RequestUserId(required = true) Long userId,
            @RequestBody MockInterviewCreateDTO dto) {
        log.info("收到请求 /mock-interview ：创建模拟面试");
        return ResponseDTO.ok(mockInterviewService.createMockInterview(userId, dto));
    }

    @Operation(summary = "删除模拟面试")
    @DeleteMapping("/{sessionId}")
    public ResponseDTO<Void> deleteMockInterview(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId) {
        log.info("收到请求 /mock-interview/{} ：删除模拟面试", sessionId);
        mockInterviewService.deleteMockInterview(userId, sessionId);
        return ResponseDTO.ok();
    }

    @Operation(summary = "更新模拟面试状态")
    @PutMapping("/{sessionId}/status")
    public ResponseDTO<Void> updateMockInterviewStatus(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @RequestParam("status") String status) {
        log.info("收到请求 /mock-interview/{}/status ：更新模拟面试状态为 {}", sessionId, status);
        mockInterviewService.updateMockInterviewStatus(userId, sessionId, status);
        return ResponseDTO.ok();
    }

    @Operation(summary = "保存练习进度")
    @PutMapping("/{sessionId}/progress")
    public ResponseDTO<Void> saveMockInterviewProgress(
            @RequestUserId(required = true) Long userId,
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody MockInterviewUpdateProgressDTO progressDto) {
        log.info("收到请求 /mock-interview/{}/progress ：保存练习进度为 {}", sessionId, progressDto);
        mockInterviewService.saveMockInterviewProgress(userId, sessionId, progressDto);
        return ResponseDTO.ok();
    }

}
