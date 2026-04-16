package com.smartapply.ida.module.app.auth.service;

import com.smartapply.ida.config.props.auth.AuthSessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 无状态会话：服务端不存 Session，仅由随机 Token 与 Redis 中的 userId 映射维持登录态。
 * <p>
 * 客户端应在后续请求的 {@code Authorization} 头携带 {@code Bearer &lt;token&gt;}，
 * 由 {@link com.smartapply.ida.http.filter.BearerTokenAuthenticationFilter} 解析并写入请求属性。
 */
@Service("appLoginSessionService")
@RequiredArgsConstructor
public class LoginSessionService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthSessionProperties authSessionProperties;

    /**
     * 生成 UUID（无横线）作为 Token，写入 Redis，过期时间见 {@link AuthSessionProperties#getTokenTtlSeconds()}。
     */
    public String issueToken(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        // Redis：token -> userId 字符串；键前缀与业务其它 key 隔离，避免误删
        String key = authSessionProperties.getRedisTokenKeyPrefix() + token;
        stringRedisTemplate.opsForValue()
                .set(key, String.valueOf(userId), Duration.ofSeconds(authSessionProperties.getTokenTtlSeconds()));
        return token;
    }

    /**
     * 供鉴权过滤器使用：根据 Token 解析用户主键；非法或过期返回 empty。
     */
    public Optional<Long> findUserIdByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String v = stringRedisTemplate.opsForValue().get(authSessionProperties.getRedisTokenKeyPrefix() + token.trim());
        if (v == null || v.isBlank()) {
            return Optional.empty(); // 过期、从未签发或 key 被 revoke
        }
        try {
            return Optional.of(Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty(); // 数据被污染时当作无效 token，避免 NPE/500
        }
    }

    /** 退出登录或风控封禁时可删除 Redis 键，使 Token 立即失效 */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String key = authSessionProperties.getRedisTokenKeyPrefix() + token.trim();
        stringRedisTemplate.delete(key);
    }
}
