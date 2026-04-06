package com.smartapply.ida.common.domain.auth.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * POST {@code /api/v1/plugin/auth/sms/login} 请求体：手机号 + 短信验证码。
 */
@Data
public class SmsLoginForm {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入合法的中国大陆手机号")
    private String phone;

    /** 与 Redis 中存储的验证码比对，长度按运营商/模板实际可调 */
    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 8, message = "验证码长度不合法")
    private String code;
}
