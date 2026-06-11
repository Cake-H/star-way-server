package com.smartapply.ida.ai.plugin.deterministic;

import java.util.List;

/**
 * 下拉选项模糊匹配器：将简历值映射到平台下拉选项的实际文案。
 * <p>
 * 匹配优先级：
 * <ol>
 *   <li>归一化后精确匹配（去首尾空格、统一小写、全角→半角）</li>
 *   <li>包含关系（选项包含简历值，或简历值包含选项）</li>
 *   <li>枚举别名匹配（通过 {@link ChineseFieldAliasTable} 的同义词组）</li>
 * </ol>
 * 三层均未命中则返回 null，该字段交由 LLM 处理。
 */
public final class OptionMatcher {

    private OptionMatcher() {}

    /**
     * 在下拉选项中查找与简历值最匹配的一项。
     *
     * @param resumeValue    简历中的原始值（如 "本科"、"1"）
     * @param optionLabels   平台下拉选项文案列表
     * @param enumCategory   枚举类别（如 "gender"、"degree"），可为 null 则跳过枚举别名匹配
     * @return 匹配到的原始选项文案（保持原有大小写和格式），未匹配返回 null
     */
    public static String matchOption(String resumeValue, List<String> optionLabels, String enumCategory) {
        if (resumeValue == null || optionLabels == null || optionLabels.isEmpty()) {
            return null;
        }
        String normalizedResume = normalize(resumeValue);
        if (normalizedResume.isEmpty()) {
            return null;
        }

        // 第一层：归一化后精确匹配
        for (String opt : optionLabels) {
            if (normalize(opt).equals(normalizedResume)) {
                return opt;
            }
        }

        // 第二层：包含关系（双向）
        for (String opt : optionLabels) {
            String normalizedOpt = normalize(opt);
            if (normalizedOpt.contains(normalizedResume) || normalizedResume.contains(normalizedOpt)) {
                return opt;
            }
        }

        // 第三层：枚举别名匹配
        if (enumCategory != null) {
            String aliasMatch = ChineseFieldAliasTable.matchByEnumAlias(enumCategory, resumeValue, optionLabels);
            if (aliasMatch != null) {
                return aliasMatch;
            }
        }

        return null;
    }

    /**
     * 归一化字符串：去首尾空格 → 小写 → 全角英数转半角 → 去除常见干扰字符（括号内英文标注等暂不去除，保留完整语义）。
     */
    static String normalize(String s) {
        if (s == null) return "";
        // 全角英数→半角
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '！' && c <= '～') {
                sb.append((char) (c - 0xFEE0));
            } else if (c == '　') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim().toLowerCase();
    }
}
