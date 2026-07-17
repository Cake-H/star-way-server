package com.smartapply.ida.module.plugin.resume.autofill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.plugin.site.PageUrlUtils;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedback;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackRequest;
import com.smartapply.ida.module.app.plugin.mapper.PluginAutofillFeedbackMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 自动填表反馈消费器：定时从 Redis List {@code ida:plugin:autofill:feedback:queue} 批量取出反馈，
 * 脱敏后逐字段入库，并按天轮转清理 90 天前的明细。
 * <p>
 * <b>容错：</b>单条 JSON 解析失败跳过并告警；DB 入库失败丢弃该条对应批次并告警（诊断数据非关键路径，
 * 避免毒丸消息堵塞队列）。消费器异常不影响下次调度。
 * <p>
 * <b>多实例：</b>多副本部署会重复消费，反馈重复写入可接受（统计靠聚合查询去重，不靠行唯一）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginAutofillFeedbackConsumer {

    private static final String QUEUE_KEY = "ida:plugin:autofill:feedback:queue";

    /** 单次消费最多取多少条记录（每条记录含多个字段），平衡延迟与 DB 写入量 */
    private static final int BATCH_SIZE = 200;

    /** 明细保留天数 */
    private static final int RETAIN_DAYS = 90;

    /** actualValue 入库前截断长度 */
    private static final int ACTUAL_VALUE_MAX_LEN = 64;

    /** 手机号：11 位数字（前后不能紧跟数字，避免误匹配身份证号子串）；身份证号：17 位数字+1 位数字/X */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PluginAutofillFeedbackMapper feedbackMapper;

    /**
     * 定时消费反馈队列。fixedDelay=任务结束后等 N ms 再下次，避免空转压 Redis。
     */
    @Scheduled(fixedDelayString = "${ida.plugin.autofill.feedback.consume-interval-ms:30000}")
    public void consume() {
        List<PluginAutofillFeedback> rows = new ArrayList<>();
        int recordsPopped = 0;
        try {
            for (int i = 0; i < BATCH_SIZE; i++) {
                String json = stringRedisTemplate.opsForList().leftPop(QUEUE_KEY);
                if (json == null) {
                    break; // 队列空
                }
                recordsPopped++;
                expandToRows(json, rows);
            }
        } catch (Exception e) {
            log.warn("反馈队列消费取数异常（已取 {} 条记录）: {}", recordsPopped, e.getMessage(), e);
        }

        if (rows.isEmpty()) {
            return;
        }

        int inserted = 0;
        int failed = 0;
        for (PluginAutofillFeedback row : rows) {
            try {
                feedbackMapper.insert(row);
                inserted++;
            } catch (Exception e) {
                failed++;
                // 单行失败不阻塞其余行；毒丸字段记录后跳过
                log.debug("反馈单行入库失败，跳过: {}", e.getMessage());
            }
        }
        log.info("反馈入库完成: 记录 {} 条 → 明细行 {} (成功 {} 失败 {})", recordsPopped, rows.size(), inserted, failed);
    }

    /**
     * 每天清理 90 天前的反馈明细，避免表无限膨胀。cron 非整点，错峰。
     */
    @Scheduled(cron = "${ida.plugin.autofill.feedback.purge-cron:17 3 * * *}")
    public void purgeOld() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(RETAIN_DAYS);
            int deleted = feedbackMapper.delete(
                    Wrappers.<PluginAutofillFeedback>lambdaQuery()
                            .lt(PluginAutofillFeedback::getCreatedAt, threshold));
            if (deleted > 0) {
                log.info("反馈明细清理: 删除 {} 条 {} 天前数据", deleted, RETAIN_DAYS);
            }
        } catch (Exception e) {
            log.warn("反馈明细清理异常: {}", e.getMessage(), e);
        }
    }

    /** 将一条队列记录 JSON 展开为多行明细（每个字段一行） */
    private void expandToRows(String json, List<PluginAutofillFeedback> out) {
        PluginAutofillFeedbackRecord record;
        try {
            record = objectMapper.readValue(json, PluginAutofillFeedbackRecord.class);
        } catch (Exception e) {
            log.warn("反馈记录解析失败，跳过: {}", e.getMessage());
            return;
        }
        if (record == null || record.getUserId() == null || record.getResumeId() == null) {
            log.warn("反馈记录缺少 userId/resumeId，跳过");
            return;
        }
        String siteHost = PageUrlUtils.extractHost(record.getPageUrl());
        String siteId = sanitize(record.getSiteId());
        String taskId = sanitize(record.getTaskId());
        String pageUrl = truncate(sanitize(record.getPageUrl()), 512);

        List<PluginAutofillFeedbackRequest.FieldFeedbackItem> fields = record.getFields();
        if (fields == null || fields.isEmpty()) {
            return;
        }
        for (PluginAutofillFeedbackRequest.FieldFeedbackItem f : fields) {
            if (f == null || !StringUtils.hasText(f.getStatus())) {
                continue;
            }
            PluginAutofillFeedback row = new PluginAutofillFeedback();
            row.setUserId(record.getUserId());
            row.setResumeId(record.getResumeId());
            row.setTaskId(taskId);
            row.setPageUrl(pageUrl);
            row.setSiteHost(siteHost);
            row.setSiteId(siteId);
            row.setSelector(truncate(sanitize(f.getSelector()), 512));
            row.setClientFieldKey(truncate(sanitize(f.getClientFieldKey()), 128));
            row.setStatus(truncate(f.getStatus().trim(), 16));
            row.setFailReason(truncate(sanitize(f.getFailReason()), 32));
            row.setActualValue(maskAndTruncate(f.getActualValue()));
            out.add(row);
        }
    }

    /** actualValue 脱敏：先掩码身份证号（更长，优先匹配），再掩码手机号，最后截断到 64 字符 */
    static String maskAndTruncate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String s = raw.trim();
        s = ID_CARD_PATTERN.matcher(s).replaceAll(m -> maskMiddle(m.group(), 3, 4));
        s = PHONE_PATTERN.matcher(s).replaceAll(m -> maskMiddle(m.group(), 3, 4));
        return truncate(s, ACTUAL_VALUE_MAX_LEN);
    }

    private static String maskMiddle(String s, int prefix, int suffix) {
        if (s.length() <= prefix + suffix) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.substring(0, prefix));
        for (int i = 0; i < s.length() - prefix - suffix; i++) {
            sb.append('*');
        }
        sb.append(s.substring(s.length() - suffix));
        return sb.toString();
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
