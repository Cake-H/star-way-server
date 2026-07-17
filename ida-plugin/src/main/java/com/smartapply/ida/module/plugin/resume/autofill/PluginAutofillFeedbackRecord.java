package com.smartapply.ida.module.plugin.resume.autofill;

import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackRequest;
import lombok.Data;

import java.util.List;

/**
 * 反馈队列载荷：{@link PluginAutofillFeedbackService#submitFeedback} 入队、
 * {@link PluginAutofillFeedbackConsumer} 出队消费的结构。
 * <p>
 * 比 {@link PluginAutofillFeedbackRequest} 多一个 {@code userId}（鉴权头解析得到，不在 request 上）。
 */
@Data
public class PluginAutofillFeedbackRecord {

    private Long userId;
    private Long resumeId;
    private String taskId;
    private String pageUrl;
    private String siteId;
    private List<PluginAutofillFeedbackRequest.FieldFeedbackItem> fields;
}
