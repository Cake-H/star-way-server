package com.smartapply.ida.common.domain.plugin.autofill.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交异步填表任务后的立即响应：只含任务 id 与轮询说明，不包含模型结果。
 * <p>
 * 扩展端应保存 {@link #taskId}，然后定时请求
 * {@code GET /api/v1/plugin/resume/autofill-plan/tasks/{taskId}} 直至 {@link PluginAutofillTaskStatusVO#getStatus()}
 * 为终态（成功或失败）。
 * <p>
 * 与「SSE 流式」无关：本方案为轮询拉取快照；架构说明见 {@code docs/plugin-autofill-async-architecture.md}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginAutofillTaskSubmitResultVO {

    /** 全局唯一任务标识（UUID），轮询路径占位符 */
    private String taskId;

    /**
     * 相对路径提示，便于扩展拼接 baseUrl；实际路由以服务端 Controller 为准。
     * 例：{@code autofill-plan/tasks/{taskId}} 中的 taskId 需替换为本字段。
     */
    private String pollPathTemplate;
}
