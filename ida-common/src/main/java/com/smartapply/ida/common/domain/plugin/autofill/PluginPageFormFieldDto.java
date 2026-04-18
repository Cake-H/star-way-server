package com.smartapply.ida.common.domain.plugin.autofill;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
}
