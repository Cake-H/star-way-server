package com.smartapply.ida.module.plugin.auth.controller;

import com.smartapply.ida.common.domain.ResponseDTO;
import com.smartapply.ida.common.domain.auth.form.SmsLoginForm;
import com.smartapply.ida.common.domain.auth.form.SmsSendCodeForm;
import com.smartapply.ida.common.domain.auth.vo.SmsLoginResultVO;
import com.smartapply.ida.constant.ApiRouteConstants;
import com.smartapply.ida.http.annotation.NoNeedLogin;
import com.smartapply.ida.module.plugin.auth.service.UserAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 正式客户端短信认证 HTTP 层：仅做参数校验与委托，业务规则在 {@link UserAuthService}。
 * <p>
 * 路径前缀：{@link ApiRouteConstants#API_V1_APP}{@code /auth/sms}，与插件端路由隔离。
 */
@Tag(name = "插件端-认证")
@RestController
@RequestMapping(ApiRouteConstants.API_V1_PLUGIN + "/auth/sms")
@Slf4j
@Validated
@RequiredArgsConstructor
public class PluginAuthSmsController {

    private final UserAuthService userAuthService;

    @NoNeedLogin
    @Operation(summary = "发送登录短信验证码（JSON 或 text/plain 的 JSON 体）")
    @PostMapping(value = "/send-code", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseDTO<String> sendCode(@Valid @RequestBody SmsSendCodeForm form) {
        log.info("收到请求 /send-code ：发送登录短信验证码，phone={}", form.getPhone());
        userAuthService.sendLoginCode(form.getPhone());
        return ResponseDTO.ok();
    }

//    @NoNeedLogin
//    @Operation(summary = "发送登录短信验证码（form-data，字段 phone）")
//    @PostMapping(value = "/send-code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseDTO<String> sendCodeForm(
//            @RequestParam("phone")
//            @NotBlank(message = "手机号不能为空")
//            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入合法的中国大陆手机号")
//            String phone) {
//        log.info("收到请求 /send-code(form-data) ：发送登录短信验证码，phone={}", phone);
//        userAuthService.sendLoginCode(phone);
//        return ResponseDTO.ok();
//    }

    @NoNeedLogin
    @Operation(summary = "短信验证码登录（未注册自动注册）")
    @PostMapping("/login")
    public ResponseDTO<SmsLoginResultVO> login(@Valid @RequestBody SmsLoginForm form) {
        log.info("收到请求 /login ：短信验证码登录，phone={}", form.getPhone());
        // 事务与验码在 Service 内；此处仅包装为 ResponseDTO
        return ResponseDTO.ok(userAuthService.loginBySms(form.getPhone(), form.getCode()));
    }

    @Operation(summary = "退出登录（作废会话 Token）")
    @PostMapping("/logout")
    public ResponseDTO<String> logout(HttpServletRequest request) {
        log.info("收到请求 /logout ：退出登录");
        userAuthService.logout(request);
        return ResponseDTO.ok();
    }
}
