package com.smartapply.ida.common.domain.plugin.autofill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 插件「一键填表」前置上下文：在采页面 DOM 快照之前，由服务端根据当前简历 draft 给出各模块条目数，
 * 避免扩展仅靠本地缓存猜测「应添加几段工作经历/项目」导致与云端不一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginAutofillResumeContextVO {

    /** 与请求路径中的 resumeId 一致 */
    private Long resumeId;

    /** 与主表一致，便于扩展做缓存失效判断 */
    private Integer schemaVersion;

    /** 简历最近更新时间，可与本地缓存比对 */
    private LocalDateTime updatedAt;

    /** 各可重复模块在云端的真实条数 */
    private PluginResumeModuleCountsVO moduleCounts;

    /**
     * 各日期字段的精度提示。key 为 clientFieldKey 模式，value 为 "date" 或 "year_month"。
     * 前端据此决定日期选择器操作方式（完整日期 vs 仅年月），无需猜测。
     * 例：{"education.*.enrollDate": "year_month", "basic.birthDate": "date"}
     */
    private Map<String, String> dateFieldPrecision;
}
