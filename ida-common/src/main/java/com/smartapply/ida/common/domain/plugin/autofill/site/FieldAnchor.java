package com.smartapply.ida.common.domain.plugin.autofill.site;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 字段锚点条件：用于在页面快照中定位「同一语义」的控件。
 * <p>
 * 各字段为「或」关系——快照字段满足任一非空条件即视为命中本锚点。
 * 用于站点模板的 {@link TemplateFieldMapping#getAnchor()} 与指纹的 {@link SiteFingerprint#getFieldAnchorHint()}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldAnchor {

    /** 精确匹配 {@code PluginPageFormFieldDto.clientFieldKey}（区分大小写） */
    private String clientFieldKeyEquals;

    /** 精确匹配 {@code PluginPageFormFieldDto.dataFormFieldId}（区分大小写） */
    private String dataFormFieldIdEquals;

    /** 前缀匹配 {@code PluginPageFormFieldDto.dataFormFieldId}（区分大小写），用于 ATS 字段 id 带动态后缀的场景 */
    private String dataFormFieldIdPrefix;

    /** 精确匹配 {@code PluginPageFormFieldDto.i18nLabel}（区分大小写） */
    private String i18nLabelEquals;

    /** 标签文本精确匹配（经标签归一化后比较，去首尾空格与冒号等） */
    private String labelTextEquals;

    /** {@code PluginPageFormFieldDto.selector} 包含给定子串（区分大小写） */
    private String selectorContains;
}
