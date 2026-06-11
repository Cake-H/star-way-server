package com.smartapply.ida.module.plugin.resume.autofill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * 自动填表反馈服务：将前端上报的逐字段填写结果异步写入 Redis List。
 * <p>
 * 写入失败仅 log.warn，不影响主流程。
 * 后续可添加定时任务从 Redis List 消费入库做统计分析（option_not_found 频率、selector_miss 频率等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginAutofillFeedbackService {

    private static final String FEEDBACK_QUEUE_KEY = "ida:plugin:autofill:feedback:queue";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 异步写入反馈数据到 Redis List。
     * 写入失败仅告警，不向调用方抛异常。
     */
    @Async
    public void submitFeedback(long userId, PluginAutofillFeedbackRequest request) {
        try {
            Map<String, Object> record = Map.of(
                    "userId", userId,
                    "resumeId", request.getResumeId(),
                    "taskId", request.getTaskId() != null ? request.getTaskId() : "",
                    "pageUrl", request.getPageUrl() != null ? request.getPageUrl() : "",
                    "fields", request.getFields() != null ? request.getFields() : java.util.List.of(),
                    "timestamp", Instant.now().toString()
            );
            String json = objectMapper.writeValueAsString(record);
            stringRedisTemplate.opsForList().rightPush(FEEDBACK_QUEUE_KEY, json);
            log.info("自动填表反馈已写入 Redis: userId={}, resumeId={}, fieldCount={}",
                    userId, request.getResumeId(),
                    request.getFields() != null ? request.getFields().size() : 0);
        } catch (Exception e) {
            log.warn("自动填表反馈写入 Redis 失败: {}", e.getMessage());
        }
    }
}
