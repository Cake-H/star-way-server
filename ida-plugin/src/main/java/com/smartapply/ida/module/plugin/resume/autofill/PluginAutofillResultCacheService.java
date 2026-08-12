package com.smartapply.ida.module.plugin.resume.autofill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.plugin.site.PageUrlUtils;
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
 * <p>
 * 已知限制：selector 含动态 token（如 antd {@code :r1:}、css-in-js 的 hash class）的站点，
 * 每次加载页面指纹都会变化，缓存命中率低。
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
     * 页面指纹：对「host + path + 各字段结构属性」做 SHA-256，取前 16 个 hex 字符。
     * <p>
     * URL 部分用 {@link PageUrlUtils#extractHost(String)} + {@link PageUrlUtils#extractPath(String)}，
     * 丢弃 query/fragment——同站不同职位链接（{@code ?jobId=xxx}）命中同一缓存；
     * 残留限制：若站点把职位 id 放在 path（如 {@code /job/123}），仍会产生不同指纹。
     * <p>
     * 字段部分含 selector/clientFieldKey/labelText/interactionMode/placeholder/optionLabelsSample（逐项），
     * 选项或提示文案变化会使指纹变化、避免命中旧答案；
     * 不含 currentValue 等运行时用户输入，但 <b>对字段 DOM 顺序敏感</b>——扩展采集顺序变化会击穿缓存。
     * <p>
     * 包私有 static 便于单测直接调用。
     */
    static String computePageFingerprint(String pageUrl, List<PluginPageFormFieldDto> fields) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            updateSegment(md, PageUrlUtils.extractHost(pageUrl));
            updateSegment(md, PageUrlUtils.extractPath(pageUrl));
            for (PluginPageFormFieldDto f : fields) {
                updateSegment(md, f.getSelector());
                updateSegment(md, f.getClientFieldKey());
                updateSegment(md, f.getLabelText());
                updateSegment(md, f.getInteractionMode());
                updateSegment(md, f.getPlaceholder());
                if (f.getOptionLabelsSample() != null) {
                    for (String option : f.getOptionLabelsSample()) {
                        updateSegment(md, option);
                    }
                }
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 必定存在，不会走到这里
            throw new RuntimeException(e);
        }
    }

    /**
     * 按「4 字节长度前缀 + 内容」写入一段，消除相邻段拼接歧义
     * （如 ["ab","c"] 与 ["a","bc"] 裸拼接后字节流相同，会错误命中同一缓存键）。
     */
    private static void updateSegment(MessageDigest md, String s) {
        byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        int len = bytes.length;
        md.update((byte) (len >>> 24));
        md.update((byte) (len >>> 16));
        md.update((byte) (len >>> 8));
        md.update((byte) len);
        md.update(bytes);
    }
}
