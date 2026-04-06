package com.smartapply.ida.config.props.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 短信验证码：长度、有效期、发送频控、Redis 键前缀。
 */
@Data
@ConfigurationProperties(prefix = "ida.sms.code")
public class SmsVerificationProperties {

    private int length = 6;

    /** 验证码有效时间（秒） */
    private int ttlSeconds = 300;

    /** 同一手机号发送间隔（秒） */
    private int resendIntervalSeconds = 60;

    private String redisCodeKeyPrefix = "ida:sms:code:";

    private String redisRateKeyPrefix = "ida:sms:rate:";
}
