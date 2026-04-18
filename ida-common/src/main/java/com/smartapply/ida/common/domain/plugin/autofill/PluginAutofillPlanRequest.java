package com.smartapply.ida.common.domain.plugin.autofill;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 插件「一键填表」：选定简历 + 当前页控件快照，由服务端生成填值计划（扩展再在页面执行）。
 */
@Data
public class PluginAutofillPlanRequest {

    @NotNull(message = "resumeId 不能为空")
    private Long resumeId;

    @NotNull(message = "page 快照不能为空")
    @Valid
    private PluginPageFormSnapshotDto page;
}
