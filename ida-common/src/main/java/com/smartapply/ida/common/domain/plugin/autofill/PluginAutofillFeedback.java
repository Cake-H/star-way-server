package com.smartapply.ida.common.domain.plugin.autofill;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 插件一键填表逐字段反馈明细，对应表 {@code ida_plugin_autofill_feedback}。
 * <p>
 * 由 {@code PluginAutofillFeedbackConsumer} 定时从 Redis 队列消费入库，支撑字段级命中率统计。
 * 诊断数据，不做逻辑删除，由清理任务按 {@code created_at} 轮转。
 */
@Data
@TableName("ida_plugin_autofill_feedback")
public class PluginAutofillFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long resumeId;

    /** 异步任务 id，同步路径留空 */
    private String taskId;

    private String pageUrl;

    /** 从 pageUrl 解析的 host，便于按站点聚合 */
    private String siteHost;

    /** 命中的站点模板 id，前端从 plan 结果透传；未命中留空 */
    private String siteId;

    private String selector;
    private String clientFieldKey;

    /** filled / skipped / error */
    private String status;

    /** option_not_found/readonly/date_nav_failed/selector_miss/other */
    private String failReason;

    /** 前端实际写入值，入库前已脱敏掩码+截断 */
    private String actualValue;

    private LocalDateTime createdAt;
}
