package com.smartapply.ida.common.domain.plugin.autofill;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 扩展侧<strong>不应</strong>自动填写或模型明确跳过的控件：只读、验证码、附件、富文本策略限制等。
 * <p>
 * {@code reason} 建议使用稳定枚举串（便于前端 i18n）：{@code readonly}、{@code captcha}、{@code attachment}、
 * {@code no_mapping}、{@code ambiguous}、{@code compound_unresolved}、{@code policy}、{@code other}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginBlockedFieldDto {

    private String selector;

    /** 机器可读原因码，见类注释 */
    private String reason;

    /** 给用户看的极短中文，可展示在红框旁或 Tooltip */
    private String userHint;

    /** 请求里带来的锚点，响应中原样带回（若模型未填则由服务端合并） */
    private String dataFormFieldId;
    private String i18nLabel;
    private String clientFieldKey;
}
