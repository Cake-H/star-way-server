package com.smartapply.ida.common.domain.plugin.autofill;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 复合控件的一段子赋值：例如手机号「区号 + 本地号」、证件「类型 + 号码」。
 * <p>
 * {@code targetRole} 建议取值（小写英文，扩展端与主 {@link PluginFieldAssignmentDto#selector} 组合理解）：
 * {@code dial_code}、{@code local_number}、{@code id_type}、{@code id_number}、{@code part}（泛化多段时）。
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
}
