package com.smartapply.ida.config.props.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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

    /** 模拟登录开关：true 时白名单手机号用固定验证码登录，不发真实短信、不受频控；仅限测试环境，生产必须为 false */
    private boolean mockCodeEnabled = false;

    /** 模拟登录白名单手机号（需满足手机号格式校验） */
    private List<String> mockPhones = new ArrayList<>();

    /** 白名单手机号的固定验证码 */
    private String mockCode = "246810";
}
