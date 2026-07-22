package com.smartapply.ida.config.props.admin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理后台管理员会话与登录安全配置（{@code ida.admin.auth}）。
 * <p>
 * 与 C 端 {@code ida.auth.session} 完全隔离：Redis key 前缀不同，互不能冒用 Token。
 */
@Data
@ConfigurationProperties(prefix = "ida.admin.auth")
public class AdminAuthProperties {

    /** 管理员会话 Token 有效期（秒），默认 12 小时（短于 C 端，降低泄露风险） */
    private int tokenTtlSeconds = 12 * 3600;

    /** 管理员 Token 在 Redis 中的 key 前缀（与 C 端 ida:auth:token: 隔离） */
    private String redisTokenKeyPrefix = "ida:admin:token:";

    /** 登录连续失败次数上限，超过后锁定账号 */
    private int loginMaxFailCount = 5;

    /** 触发锁定后的锁定时长（秒） */
    private int loginLockSeconds = 600;
}
