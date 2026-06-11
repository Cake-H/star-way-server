package com.smartapply.ida.module.plugin.resume.autofill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * 自动填表结果缓存：同一用户 + 同一简历版本 + 同一页面结构 → 命中缓存直接返回，跳过确定性匹配和 LLM。
 * <p>
 * 缓存键包含 resumeUpdatedAt（epoch ms），简历编辑后 key 自动变化无需手动失效。
 * Redis 不可用时静默降级——不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginAutofillResultCacheService {

    private static final String KEY_PREFIX = "ida:plugin:autofill:result:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询缓存的填表结果。
     *
     * @return 缓存命中返回结果 VO，未命中或异常返回 null
     */
    public PluginAutofillPlanResultVO get(Long userId, Long resumeId, long resumeUpdatedAtMs,
                                          String pageUrl, List<PluginPageFormFieldDto> fields) {
        try {
            String key = buildKey(userId, resumeId, resumeUpdatedAtMs, pageUrl, fields);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            log.info("自动填表结果缓存命中: userId={}, resumeId={}", userId, resumeId);
            return objectMapper.readValue(json, PluginAutofillPlanResultVO.class);
        } catch (Exception e) {
            log.warn("读取自动填表结果缓存失败，降级走正常流程: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入填表结果缓存。写入失败仅告警，不影响主流程。
     */
    public void put(Long userId, Long resumeId, long resumeUpdatedAtMs,
                    String pageUrl, List<PluginPageFormFieldDto> fields,
                    PluginAutofillPlanResultVO result) {
        try {
            String key = buildKey(userId, resumeId, resumeUpdatedAtMs, pageUrl, fields);
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(key, json, TTL);
            log.debug("自动填表结果已缓存: key={}", key);
        } catch (Exception e) {
            log.warn("写入自动填表结果缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 缓存键 = 前缀 + userId + resumeId + updatedAtMs + pageFingerprint(SHA-256)。
     * pageFingerprint 基于 pageUrl 和各字段的结构属性计算，排除 currentValue 等易变属性。
     */
    private String buildKey(Long userId, Long resumeId, long resumeUpdatedAtMs,
                            String pageUrl, List<PluginPageFormFieldDto> fields) {
        String fingerprint = computePageFingerprint(pageUrl, fields);
        return KEY_PREFIX + userId + ":" + resumeId + ":" + resumeUpdatedAtMs + ":" + fingerprint;
    }

    /**
     * 页面指纹：对 pageUrl + 各字段的 selector/clientFieldKey/labelText/interactionMode 做 SHA-256。
     * 不含 currentValue、optionLabelsSample 等运行时可变属性，保证同一表单结构命中同一缓存。
     */
    private String computePageFingerprint(String pageUrl, List<PluginPageFormFieldDto> fields) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(safeBytes(pageUrl));
            for (PluginPageFormFieldDto f : fields) {
                md.update(safeBytes(f.getSelector()));
                md.update(safeBytes(f.getClientFieldKey()));
                md.update(safeBytes(f.getLabelText()));
                md.update(safeBytes(f.getInteractionMode()));
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 必定存在，不会走到这里
            throw new RuntimeException(e);
        }
    }

    private static byte[] safeBytes(String s) {
        return (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
    }
}
