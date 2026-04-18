package com.smartapply.ida.common.domain.plugin.autofill;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前浏览器页的一次「表单上下文」快照，由扩展 content script 采集后随填表计划请求一并提交。
 */
@Data
public class PluginPageFormSnapshotDto {

    /** 当前页 URL，便于模型理解站点语境（可为空） */
    private String pageUrl;

    /** document.title */
    private String pageTitle;

    /**
     * 候选控件列表；可为空数组——此时服务端直接判定为不可填并返回提示，不调用大模型。
     */
    @NotNull(message = "fields 不能为 null")
    @Valid
    private List<PluginPageFormFieldDto> fields = new ArrayList<>();
}
