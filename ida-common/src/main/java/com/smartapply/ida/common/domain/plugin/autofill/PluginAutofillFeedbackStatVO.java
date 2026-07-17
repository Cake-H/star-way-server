package com.smartapply.ida.common.domain.plugin.autofill;

import lombok.Data;

import java.util.List;

/**
 * 一键填表反馈统计结果（用户级，按当前 userId 聚合）。
 * <p>
 * 用于回答「哪类字段、哪个站点、哪种失败原因最常卡住」，驱动模板校准与同义词扩充。
 */
@Data
public class PluginAutofillFeedbackStatVO {

    /** 统计时间范围（天） */
    private Integer days;

    private long totalFields;
    private long filledCount;
    private long skippedCount;
    private long errorCount;

    /** 命中率 = filledCount / totalFields，totalFields=0 时为 0 */
    private double fillRate;

    /** 按失败原因聚合，count 降序 */
    private List<ReasonCount> byFailReason;

    /** 按 clientFieldKey 聚合，命中率升序（最该优化的字段在前），Top 20 */
    private List<FieldCount> byClientFieldKey;

    /** 按 siteHost 聚合，命中率升序，Top 20 */
    private List<FieldCount> bySiteHost;

    @Data
    public static class ReasonCount {
        private String reason;
        private long count;
    }

    @Data
    public static class FieldCount {
        private String key;
        private long total;
        private long filled;
        private double fillRate;
    }
}
