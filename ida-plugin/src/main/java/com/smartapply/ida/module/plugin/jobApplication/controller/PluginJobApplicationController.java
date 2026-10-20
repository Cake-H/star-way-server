package com.smartapply.ida.module.plugin.jobApplication.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.plugin.jobApplication.dto.PluginJobApplicationParseRequest;
import com.smartapply.ida.common.domain.plugin.jobApplication.vo.PluginJobApplicationParseResultVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.RequestUserId;
import com.smartapply.ida.module.plugin.jobApplication.service.PluginJobApplicationParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;

@Tag(name = "插件端-投递记录")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_PLUGIN + "/job-application")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PluginJobApplicationController {

    private final PluginJobApplicationParseService parseService;

    @Operation(summary = "识别当前页面投递信息并入库")
    @PostMapping(value = "/parse-and-save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseDTO<PluginJobApplicationParseResultVO> parseAndSave(
            @RequestUserId(required = true) Long userId,
            @Valid @RequestBody PluginJobApplicationParseRequest body) {
        log.info("/parse-and-save userId={} pageUrl={} titleLen={} textLen={}", userId, body.getPageUrl(),
                body.getPageTitle() == null ? 0 : body.getPageTitle().length(),
                body.getTextContent() == null ? 0 : body.getTextContent().length());
        return ResponseDTO.ok(parseService.parseAndSave(userId, body));
    }

}
