package com.smartapply.ida.common.domain.plugin.autofill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 浏览器扩展从当前页采集的单个可交互控件元信息，供服务端 AI 与简历字段对齐。
 * <p>
 * {@code selector} 须为扩展侧可稳定用 {@code document.querySelector} 定位的 CSS 选择器（建议带唯一 id 或路径），
 * 否则返回的填值计划无法在 content script 中落地。
 */
@Data
public class PluginPageFormFieldDto {

    @NotBlank(message = "selector 不能为空")
    private String selector;

    /** 标签名小写：input、textarea、select 等 */
    private String tagName;

    /** input 的 type：text、email、tel、number、date、radio、checkbox、hidden 等 */
    private String inputType;

    private String name;
    private String id;

    /** 扩展侧尽量解析的关联文案：{@code label} 元素、{@code aria-label}、邻近文本等 */
    private String labelText;

    private String placeholder;

    /** 采集时控件已有内容（可为空） */
    private String currentValue;

    /** 扩展侧是否认为在视口内且可交互（可选，供模型参考） */
    private Boolean visible;

    // —— 客户端锚点（请求带入，响应 assignments/blockedFields 中应原样带回，避免仅依赖 selector 被模型改写）—— //

    /** 网申系统内部字段 id，如美团等站的 data-form-field-id */
    private String dataFormFieldId;

    /** 国际化/展示用标签键或文案，便于多站点对齐 */
    private String i18nLabel;

    /** 扩展侧自定义稳定键，如 resume.phone */
    private String clientFieldKey;

    // —— 以下三项为可选增强：用于复合控件拆解、交互类识别与下拉候选（提升填表准确率）—— //

    /**
     * 本条 DOM 在「同一行复合 UI」中的语义分段，英文小写，需与模型输出的 {@code subAssignments[].targetRole} 对齐。
     * <p>
     * 典型：手机号左侧区号下拉 {@code dial_code}、右侧号码 {@code local_number}；证件左侧类型 {@code id_type}、右侧号码 {@code id_number}；
     * 起止时间左/右框 {@code date_start} / {@code date_end}。扩展采集时若能判断应填写，可显著减少「把号码写进下拉框」的误填。
     */
    private String compoundSegment;

    /**
     * 扩展侧推断的交互形态（自由字符串，建议统一枚举便于排查）：{@code plain_text}、{@code textarea}、{@code single_select}、
     * {@code multi_select}、{@code combobox_typeahead}（需先输入再点选）、{@code composite_left_select}、{@code composite_right_text} 等。
     * 模型应据此区分：下拉只输出与选项文案一致的值，文本框才输出长内容。
     */
    private String interactionMode;

    /**
     * 下拉/联想已展开时可见的前若干项文案（不必全量），供模型做选项匹配；未采集则留空，模型不得捏造站点选项。
     */
    @Size(max = 40, message = "optionLabelsSample 条数过多")
    private List<String> optionLabelsSample;
}
