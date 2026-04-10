package com.smartapply.ida.integration.sms;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 阿里云号码认证「短信验证码」接口 {@code TemplateParam} 的 JSON 拼装。
 * <p>
 * 赠送模板常见正文为「验证码 ${code}，${min} 分钟内有效」一类，因此除 {@code code} 外必须传 {@code min}（分钟，正整数），
 * 否则控制台会报「模版变量 min 内容非法」。{@code min} 应与 Redis 中验证码 TTL 一致（向上取整到分钟）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SmsTemplateParamJsonBuilder {

    /**
     * @param code      数字验证码
     * @param ttlSeconds 验证码有效秒数，与 {@link com.smartapply.ida.config.props.sms.SmsVerificationProperties#getTtlSeconds()} 一致
     */
    public static String buildCodeAndValidMinutesJson(String code, int ttlSeconds) {
        int minutes = Math.max(1, (ttlSeconds + 59) / 60);
        // min 与 code 均按字符串传入，与控制台示例 {"code":"...","min":"5"} 一致
        return String.format("{\"code\":\"%s\",\"min\":\"%s\"}",
                code == null ? "" : code,
                Integer.toString(minutes));
    }
}
