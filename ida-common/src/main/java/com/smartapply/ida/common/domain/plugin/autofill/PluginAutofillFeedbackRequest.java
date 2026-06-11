package com.smartapply.ida.common.domain.plugin.autofill;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 浏览器扩展填写完成后的反馈请求：逐字段上报实际填写状态和失败原因。
 * <p>
 * 反馈数据异步写入 Redis 队列，后续可用于：
 * <ul>
 *   <li>统计字段匹配率（option_not_found 频率 → 补充 {@link com.smartapply.ida.ai.plugin.deterministic.ChineseFieldAliasTable}）</li>
 *   <li>发现 selector 定位失败的平台，推动前端改进采集策略</li>
 * </ul>
 */
@Data
public class PluginAutofillFeedbackRequest {

    /** 关联的异步任务 ID（可选，同步调用方式无 taskId） */
    private String taskId;

    @NotNull(message = "resumeId 不能为空")
    private Long resumeId;

    /** 填写发生的页面 URL */
    private String pageUrl;

    /** 各字段的实际填写状态 */
    @Valid
    private List<FieldFeedbackItem> fields;

    @Data
    public static class FieldFeedbackItem {
        private String selector;
        private String clientFieldKey;
        /** filled / skipped / error */
        private String status;
        /** option_not_found / readonly / date_nav_failed / selector_miss / other */
        private String failReason;
        /** 前端实际写入的值（可能与 assignment.value 不同） */
        private String actualValue;
    }
}
