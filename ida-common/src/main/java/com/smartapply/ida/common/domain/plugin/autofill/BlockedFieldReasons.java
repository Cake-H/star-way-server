package com.smartapply.ida.common.domain.plugin.autofill;

import java.util.Set;

/**
 * 网申填表 blockedFields 的 reason 值域常量。
 * <p>
 * 唯一来源：LLM 提示词中约定的 reason 枚举、{@code PluginFormAutofillAiService} 的归一化白名单、
 * 站点模板 {@code TemplateFieldMapping.blockedReason} 的加载校验均以此为准。
 */
public final class BlockedFieldReasons {

    private BlockedFieldReasons() {}

    /** 只读控件 */
    public static final String READONLY = "readonly";
    /** 验证码 */
    public static final String CAPTCHA = "captcha";
    /** 附件上传 */
    public static final String ATTACHMENT = "attachment";
    /** 无法与简历字段建立映射 */
    public static final String NO_MAPPING = "no_mapping";
    /** 语义不明确 */
    public static final String AMBIGUOUS = "ambiguous";
    /** 复合字段未能拆分 */
    public static final String COMPOUND_UNRESOLVED = "compound_unresolved";
    /** 策略限制（如富文本编辑器不允许自动填） */
    public static final String POLICY = "policy";
    /** 其他/兜底 */
    public static final String OTHER = "other";

    /** 全部合法 reason 值（小写） */
    public static final Set<String> VALUES = Set.of(
            READONLY, CAPTCHA, ATTACHMENT, NO_MAPPING, AMBIGUOUS, COMPOUND_UNRESOLVED, POLICY, OTHER);

    /** 判断是否为合法 reason（大小写敏感，调用方负责归一化） */
    public static boolean isValid(String reason) {
        return reason != null && VALUES.contains(reason);
    }
}
