package com.smartapply.ida.module.plugin.resume.autofill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.constant.PluginCacheConstants;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 简历预加载缓存：在用户获取自动填表上下文（GET /autofill-context）时，
 * 将已查出的简历详情预热到 Redis，后续 planAutofill 直接从 Redis 读取，跳过 DB 查询。
 * <p>
 * TTL = 10 分钟，覆盖用户从打开页面到触发填表的典型时间窗口。
 * 简历写路径（ida-app 保存/更新/删除、账号注销）会经 {@link PluginCacheConstants}
 * 按同一键直接删 Redis 主动失效（本类不提供失效方法），窗口期内的修改不会让 planAutofill 读到旧 draft。
 * Redis 不可用时静默降级——planAutofill 回退查 DB。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumePreloadCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 预热简历详情到 Redis。写入失败仅告警，不影响主流程。
     */
    public void preload(Long userId, Long resumeId, ResumeDetailVO detail) {
        try {
            String key = buildKey(userId, resumeId);
            String json = objectMapper.writeValueAsString(detail);
            stringRedisTemplate.opsForValue().set(key, json, TTL);
            log.debug("简历预加载缓存已写入: userId={}, resumeId={}", userId, resumeId);
        } catch (Exception e) {
            log.warn("简历预加载缓存写入失败: {}", e.getMessage());
        }
    }

    /**
     * 从 Redis 读取预加载的简历详情。
     *
     * @return 缓存命中返回 ResumeDetailVO，未命中或异常返回 null
     */
    public ResumeDetailVO get(Long userId, Long resumeId) {
        try {
            String key = buildKey(userId, resumeId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            log.debug("简历预加载缓存命中: userId={}, resumeId={}", userId, resumeId);
            return objectMapper.readValue(json, ResumeDetailVO.class);
        } catch (Exception e) {
            log.warn("简历预加载缓存读取失败，降级查 DB: {}", e.getMessage());
            return null;
        }
    }

    private String buildKey(Long userId, Long resumeId) {
        return PluginCacheConstants.resumePreloadKey(userId, resumeId);
    }
}
