package com.smartapply.ida.security.mask;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具（隐私安全基线 1.4）。
 *
 * <p>提供两类能力：
 * <ul>
 *   <li>{@link #mask(String)} / {@link #restore(String, MaskSession)}：占位符映射脱敏，
 *       用于 LLM 链路——发送 prompt 前把手机号/身份证号/邮箱替换为 {@code [PHONE_1]} 等占位符，
 *       映射表仅存请求内存中，LLM 返回后再还原，保证第三方模型永远看不到明文 PII 且业务功能无损。</li>
 *   <li>{@link #maskMiddle(String)}：不可逆中间打码（前 3 后 4 保留），用于日志、反馈明细等
 *       只需要展示/留存脱敏值的场景。</li>
 * </ul>
 */
public final class SensitiveDataMasker {

    /** 身份证号（18 位，优先于手机号匹配，避免被手机号正则吃掉子串） */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    /** 手机号（11 位数字） */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");
    /** 邮箱 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private SensitiveDataMasker() {
    }

    /**
     * 把文本中的敏感信息替换为占位符，返回脱敏文本与映射表。
     * 同一次调用内相同原值复用同一占位符（如两处 13800138000 都是 [PHONE_1]）。
     * 入参为空时返回空会话（maskedText 原样、映射为空）。
     */
    public static MaskSession mask(String text) {
        if (text == null || text.isEmpty()) {
            return new MaskSession(text, Collections.emptyMap());
        }
        Map<String, String> placeholderToOriginal = new LinkedHashMap<>();
        Map<String, String> originalToPlaceholder = new LinkedHashMap<>();
        String masked = text;
        masked = replaceWithPlaceholder(masked, ID_CARD_PATTERN, "IDCARD", placeholderToOriginal, originalToPlaceholder);
        masked = replaceWithPlaceholder(masked, PHONE_PATTERN, "PHONE", placeholderToOriginal, originalToPlaceholder);
        masked = replaceWithPlaceholder(masked, EMAIL_PATTERN, "EMAIL", placeholderToOriginal, originalToPlaceholder);
        return new MaskSession(masked, placeholderToOriginal);
    }

    /** 把 LLM 返回文本中的占位符还原为原值；session 为空或无映射时原样返回。 */
    public static String restore(String text, MaskSession session) {
        if (text == null || text.isEmpty() || session == null || session.mapping().isEmpty()) {
            return text;
        }
        String restored = text;
        for (Map.Entry<String, String> e : session.mapping().entrySet()) {
            restored = restored.replace(e.getKey(), e.getValue());
        }
        return restored;
    }

    /** 不可逆中间打码：前 3 后 4 保留，中间补 *（如 13800138000 → 138****8000）。 */
    public static String maskMiddle(String raw) {
        return maskMiddle(raw, 3, 4);
    }

    /**
     * 不可逆正则脱敏：把文本中的身份证（优先）/手机号/邮箱替换为中间打码形式。
     * 用于日志输出、反馈明细落库等不需要还原的场景；入参空返回空串。
     */
    public static String maskSensitive(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw;
        s = ID_CARD_PATTERN.matcher(s).replaceAll(m -> Matcher.quoteReplacement(maskMiddle(m.group())));
        s = PHONE_PATTERN.matcher(s).replaceAll(m -> Matcher.quoteReplacement(maskMiddle(m.group())));
        s = EMAIL_PATTERN.matcher(s).replaceAll(m -> Matcher.quoteReplacement(maskEmail(m.group())));
        return s;
    }

    /** 邮箱打码：本地段保留首字符，其余打 *（如 zhang.san@x.com → z***@x.com） */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** 不可逆中间打码，可指定前后保留位数；长度不足时原样返回。 */
    public static String maskMiddle(String s, int prefix, int suffix) {
        if (s == null || s.length() <= prefix + suffix) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.substring(0, prefix));
        for (int i = 0; i < s.length() - prefix - suffix; i++) {
            sb.append('*');
        }
        sb.append(s.substring(s.length() - suffix));
        return sb.toString();
    }

    /**
     * 将 text 中所有匹配 pattern 的内容替换为编号占位符（如 [PHONE_1]），
     * 并在两个映射表中记录"原文 ↔ 占位符"的对应关系，供后续脱敏还原。
     *
     * @param text                  待处理的原始文本
     * @param pattern               敏感内容的匹配正则（如手机号、邮箱正则）
     * @param typePrefix            占位符类型前缀，如 "PHONE"、"EMAIL"
     * @param placeholderToOriginal 占位符 → 原文（用于把 AI 返回结果中的占位符还原回真实值）
     * @param originalToPlaceholder 原文 → 占位符（用于去重：同一原文复用同一占位符）
     * @return 替换后的文本（敏感内容已变为占位符）
     */
    private static String replaceWithPlaceholder(String text, Pattern pattern, String typePrefix,
                                                 Map<String, String> placeholderToOriginal,
                                                 Map<String, String> originalToPlaceholder) {
        // 用正则对整段文本创建匹配器
        Matcher matcher = pattern.matcher(text);
        // 拼装替换后的结果
        StringBuilder sb = new StringBuilder();
        // 逐个扫描所有匹配项
        while (matcher.find()) {
            // 本次匹配到的原始敏感内容，如 "13812345678"
            String original = matcher.group();
            // 先查重：这段原文之前是否已经分配过占位符（保证同一原文在全文/全任务中占位符一致）
            String placeholder = originalToPlaceholder.get(original);
            if (placeholder == null) {
                // 首次出现：按类型前缀统计已有数量，序号 +1 生成新占位符
                // 例如已有 [PHONE_1]、[PHONE_2]，则新的是 [PHONE_3]
                int seq = countByPrefix(placeholderToOriginal, typePrefix) + 1;
                placeholder = "[" + typePrefix + "_" + seq + "]";
                // 写入双向映射：原文 → 占位符（去重用）、占位符 → 原文（还原用）
                originalToPlaceholder.put(original, placeholder);
                placeholderToOriginal.put(placeholder, original);
            }
            // 把"上一个匹配结束位置到当前匹配开始位置"之间的原文追加到 sb，并用占位符替换当前匹配内容。
            // quoteReplacement 是必须的：占位符中的 [ ] 虽安全，但若 replacement 含 $ 或 \
            // 会被 appendReplacement 当作分组引用/转义符解析，quoteReplacement 将其按字面量处理
            matcher.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
        }
        // 追加最后一个匹配之后剩余的原文（没有匹配时则追加整段文本）
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static int countByPrefix(Map<String, String> placeholderToOriginal, String typePrefix) {
        int count = 0;
        for (String key : placeholderToOriginal.keySet()) {
            if (key.startsWith("[" + typePrefix + "_")) {
                count++;
            }
        }
        return count;
    }

    /** 一次脱敏会话的结果：脱敏后文本 + 占位符→原值映射（仅存内存，用完即弃）。 */
    public static final class MaskSession {
        private final String maskedText;
        private final Map<String, String> placeholderToOriginal;

        private MaskSession(String maskedText, Map<String, String> placeholderToOriginal) {
            this.maskedText = maskedText;
            this.placeholderToOriginal = Collections.unmodifiableMap(placeholderToOriginal);
        }

        /** 脱敏后的文本，可安全发往第三方 LLM */
        public String maskedText() {
            return maskedText;
        }

        /** 占位符 → 原值 映射 */
        public Map<String, String> mapping() {
            return placeholderToOriginal;
        }
    }
}
