package com.smartapply.ida.common.domain.plugin.autofill;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 复合控件的一段子赋值：例如手机号「区号 + 本地号」、证件「类型 + 号码」。
 * <p>
 * {@code targetRole} 建议取值（小写英文，扩展端与主 {@link PluginFieldAssignmentDto#selector} 组合理解）：
 * {@code dial_code}、{@code local_number}、{@code id_type}、{@code id_number}、{@code date_start}、{@code date_end}、{@code part}（泛化多段时）。
 * 应与快照 {@link PluginPageFormFieldDto#getCompoundSegment()} 对齐，便于执行期把值写到正确子节点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginSubAssignmentDto {

    /** 子控件 CSS 选择器，须来自本次请求快照 fields[].selector */
    private String selector;

    private String value;

    /**
     * 在复合字段中的语义角色，供前端选子节点写入，减少误填到同一灰框。
     */
    private String targetRole;

    /** 子控件的下拉候选项样本，与主 assignment 的 optionLabelsSample 同理 */
    private List<String> optionLabelsSample;
}
