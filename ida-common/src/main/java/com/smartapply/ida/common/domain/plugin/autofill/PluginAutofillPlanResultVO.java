package com.smartapply.ida.common.domain.plugin.autofill;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 填表计划结果：扩展端根据 {@link #fillable} 决定是否执行 {@link #assignments}；
 * 不可填时展示 {@link #hint}；{@link #blockedFields} 用于只读/验证码/附件等「需人工」红框提示；
 * 每条 {@link PluginFieldAssignmentDto} 可带置信度、匹配类型、子赋值与锚点回显，便于多站点 DOM 与逐格动画。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginAutofillPlanResultVO {

    /** 当前页是否适合自动填简历类网申表 */
    @JsonAlias({"Fillable"})
    private boolean fillable;

    /**
     * 命中的站点模板 id（未命中模板为 null）。前端在反馈接口中原样透传，用于统计模板命中率。
     *
     * @see com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackRequest#getSiteId()
     */
    private String siteId;

    /** 不可填或需用户注意时的说明（中文） */
    private String hint;

    /** 可填时待写入的控件列表（selector 必须来自请求快照） */
    @Builder.Default
    private List<PluginFieldAssignmentDto> assignments = new ArrayList<>();

    /**
     * 明确不可自动填或需人工处理的控件（只读、验证码、附件、无法映射等），前端可直接红框 +「需人工」类提示，
     * 避免用户以为扩展故障。
     */
    @Builder.Default
    private List<PluginBlockedFieldDto> blockedFields = new ArrayList<>();
}
