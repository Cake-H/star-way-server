package com.smartapply.ida.module.plugin.resume.autofill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动填表反馈服务：将前端上报的逐字段填写结果异步写入 Redis List，由
 * {@link PluginAutofillFeedbackConsumer} 定时消费入库做统计分析。
 * <p>
 * 写入失败仅 log.warn，不影响主流程。
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
            PluginAutofillFeedbackRecord record = new PluginAutofillFeedbackRecord();
            record.setUserId(userId);
            record.setResumeId(request.getResumeId());
            record.setTaskId(request.getTaskId());
            record.setPageUrl(request.getPageUrl());
            record.setSiteId(request.getSiteId());
            record.setFields(request.getFields() != null ? request.getFields() : List.of());

            String json = objectMapper.writeValueAsString(record);
            stringRedisTemplate.opsForList().rightPush(FEEDBACK_QUEUE_KEY, json);
            log.info("自动填表反馈已入队: userId={}, resumeId={}, siteId={}, fieldCount={}",
                    userId, request.getResumeId(), request.getSiteId(),
                    record.getFields().size());
        } catch (Exception e) {
            log.warn("自动填表反馈写入 Redis 失败: {}", e.getMessage());
        }
    }
}
