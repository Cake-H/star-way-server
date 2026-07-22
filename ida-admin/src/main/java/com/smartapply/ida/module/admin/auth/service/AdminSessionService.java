package com.smartapply.ida.module.admin.auth.service;

import com.smartapply.ida.config.props.admin.AdminAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 管理员无状态会话：结构对齐 C 端 {@code LoginSessionService}，
 * 但使用独立的 Redis key 前缀（{@code ida:admin:token:}），与 C 端 Token 互不通兑。
 */
@Service("adminSessionService")
@RequiredArgsConstructor
public class AdminSessionService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AdminAuthProperties adminAuthProperties;

    /**
     * 生成 UUID（无横线）作为 Token 写入 Redis，过期时间见 {@link AdminAuthProperties#getTokenTtlSeconds()}。
     */
    public String issueToken(long adminId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = adminAuthProperties.getRedisTokenKeyPrefix() + token;
        stringRedisTemplate.opsForValue()
                .set(key, String.valueOf(adminId), Duration.ofSeconds(adminAuthProperties.getTokenTtlSeconds()));
        return token;
    }

    /** 供鉴权过滤器使用：根据 Token 解析管理员主键；非法或过期返回 empty。 */
    public Optional<Long> findAdminIdByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String v = stringRedisTemplate.opsForValue().get(adminAuthProperties.getRedisTokenKeyPrefix() + token.trim());
        if (v == null || v.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 退出登录或安全处置时删除 Redis 键，使 Token 立即失效 */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        stringRedisTemplate.delete(adminAuthProperties.getRedisTokenKeyPrefix() + token.trim());
    }
}
