package com.smartapply.ida.config.props.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录会话 Token（存 Redis，客户端用 Bearer 传递）。
 */
@Data
@ConfigurationProperties(prefix = "ida.auth.session")
public class AuthSessionProperties {

    /** 访问令牌有效期（秒），默认 7 天 */
    private int tokenTtlSeconds = 7 * 24 * 3600;

    private String redisTokenKeyPrefix = "ida:auth:token:";

    /** Authorization: Bearer xxx 的前缀 */
    private String bearerPrefix = "Bearer ";
}
