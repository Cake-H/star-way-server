package com.smartapply.ida.module.plugin.resume.autofill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 自动填表反馈服务：将前端上报的逐字段填写结果异步写入 Redis List，由
 * {@link PluginAutofillFeedbackConsumer} 定时消费入库做统计分析。
 * <p>
 * 入口做值域白名单校验：非法 status 的字段整行跳过，非法 failReason 归一为 "other"，
 * 防止异常前端/旧版本扩展污染统计口径。
 * <p>
 * 写入失败仅 log.warn，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginAutofillFeedbackService {

    private static final String FEEDBACK_QUEUE_KEY = "ida:plugin:autofill:feedback:queue";

    /** status 白名单 */
    private static final Set<String> ALLOWED_STATUS = Set.of("filled", "skipped", "error");

    /** failReason 白名单，非法值归一为 "other" */
    private static final Set<String> ALLOWED_FAIL_REASON =
            Set.of("option_not_found", "readonly", "date_nav_failed", "selector_miss", "other");

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
            record.setFields(sanitizeFields(request.getFields()));

            String json = objectMapper.writeValueAsString(record);
            stringRedisTemplate.opsForList().rightPush(FEEDBACK_QUEUE_KEY, json);
            log.info("自动填表反馈已入队: userId={}, resumeId={}, siteId={}, fieldCount={}",
                    userId, request.getResumeId(), request.getSiteId(),
                    record.getFields().size());
        } catch (Exception e) {
            log.warn("自动填表反馈写入 Redis 失败: {}", e.getMessage());
        }
    }

    /** 值域白名单校验：非法 status 整行跳过，非法 failReason 归一为 "other" */
    private List<PluginAutofillFeedbackRequest.FieldFeedbackItem> sanitizeFields(
            List<PluginAutofillFeedbackRequest.FieldFeedbackItem> fields) {
        List<PluginAutofillFeedbackRequest.FieldFeedbackItem> out = new ArrayList<>();
        if (fields == null) {
            return out;
        }
        for (PluginAutofillFeedbackRequest.FieldFeedbackItem item : fields) {
            if (item == null) {
                continue;
            }
            String status = item.getStatus() == null ? "" : item.getStatus().trim();
            if (!ALLOWED_STATUS.contains(status)) {
                log.debug("反馈字段 status 非法，整行跳过: status={}, selector={}", item.getStatus(), item.getSelector());
                continue;
            }
            item.setStatus(status);
            String reason = item.getFailReason() == null ? "" : item.getFailReason().trim();
            if (!reason.isEmpty() && !ALLOWED_FAIL_REASON.contains(reason)) {
                log.debug("反馈字段 failReason 非法，归一为 other: failReason={}, selector={}",
                        item.getFailReason(), item.getSelector());
                reason = "other";
            }
            item.setFailReason(reason);
            out.add(item);
        }
        return out;
    }
}
