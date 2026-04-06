package com.smartapply.ida.common.domain.auth.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * POST {@code /api/v1/plugin/auth/sms/send-code} 请求体：仅含待验证手机号。
 * <p>
 * 校验规则与 {@link SmsLoginForm #phone} 一致，避免发送与登录阶段号码格式不一致。
 */
@Data
public class SmsSendCodeForm {

    /** 中国大陆 11 位手机号，不含区号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入合法的中国大陆手机号")
    private String phone;
}
