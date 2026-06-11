package com.smartapply.ida.common.domain.plugin.autofill;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 对单个控件的填值指令：扩展端优先用锚点回写，再用 {@code selector}；可带子赋值描述复合字段。
 * <p>
 * {@code matchType} 建议：{@code exact}、{@code fuzzy}、{@code guess}；{@code confidence} 为 0~1。
 * {@code skipReason} 在仍给出填写建议但不确定时可选，与 {@link PluginBlockedFieldDto} 的 {@code reason} 语义不同（后者表示不自动填）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginFieldAssignmentDto {

    private String selector;
    private String value;

    /** 建议填写顺序，1 最先；便于前端做「逐格高亮」动画 */
    private Integer order;

    /** 0~1，越高表示模型对「控件↔简历字段」对齐越有把握 */
    private Double confidence;

    /** exact / fuzzy / guess */
    private String matchType;

    /**
     * 本行仍要写入但需向用户解释不确定性时可选，如 {@code ambiguous}；
     * 真正不可自动填的控件请用顶层 {@link PluginAutofillPlanResultVO#getBlockedFields()}。
     */
    private String skipReason;

    /** 给用户的极短说明，可与黄框/二次确认配合 */
    private String userHint;

    /** 请求快照带入，服务端合并回显 */
    private String dataFormFieldId;
    private String i18nLabel;
    private String clientFieldKey;

    /**
     * 备选值列表（按优先级排序），前端在首选 value 匹配不到下拉选项时依次尝试。
     * 后端更了解简历原始数据和选项列表上下文，提供备选值可提高命中率。
     * 例：学历字段 ["大学本科", "本科", "Bachelor"]
     */
    @Builder.Default
    private List<String> fallbackValues = new ArrayList<>();

    /** 区号+号码、证件类型+号码等多控件合一语义 */
    @Builder.Default
    private List<PluginSubAssignmentDto> subAssignments = new ArrayList<>();
}
