package com.smartapply.ida.ai.plugin.deterministic;

import java.util.List;

/**
 * 下拉选项模糊匹配器：将简历值映射到平台下拉选项的实际文案。
 * <p>
 * 匹配优先级：
 * <ol>
 *   <li>归一化后精确匹配（去首尾空格、统一小写、全角→半角）</li>
 *   <li>枚举别名匹配（通过 {@link ChineseFieldAliasTable} 的同义词组）——比包含关系更可靠，提前到第二层</li>
 *   <li>包含关系打分竞争：所有双向 contains 候选，分数 = 较短串长度 / 较长串长度，
 *       取最高分；并列最高视为歧义返回 null，降级交 LLM（不再盲目取第一个）</li>
 * </ol>
 * 三层均未命中则返回 null，该字段交由 LLM 处理。
 * <p>
 * <b>开放联想框护栏：</b>{@code combobox_typeahead} 且非枚举类别的字段（学校/公司/专业等开放值域），
 * 快照里的 optionLabelsSample 只是未键入时采到的部分候选，不是封闭值域，
 * 应通过 {@link #matchOption(String, List, String, boolean)} 传 {@code allowContains=false}
 * 禁用第三层包含打分——否则「江苏科技大学」会被样本里的残缺项「大学」contains 命中并改写值。
 */
public final class OptionMatcher {

    private OptionMatcher() {}

    /**
     * 判断字段是否为「开放联想框」：键入搜索后从联想结果中选择，值域开放（学校/公司/专业等）。
     * 枚举类别（性别/学历等有固定选项名录）的 combobox 不算开放，仍允许 contains 打分。
     */
    public static boolean isOpenTypeahead(String interactionMode, String enumCategory) {
        return enumCategory == null && interactionMode != null
                && "combobox_typeahead".equalsIgnoreCase(interactionMode.trim());
    }

    /**
     * 在下拉选项中查找与简历值最匹配的一项（封闭下拉语义，含 contains 打分层）。
     *
     * @param resumeValue    简历中的原始值（如 "本科"、"1"）
     * @param optionLabels   平台下拉选项文案列表
     * @param enumCategory   枚举类别（如 "gender"、"degree"），可为 null 则跳过枚举别名匹配
     * @return 匹配到的原始选项文案（保持原有大小写和格式），未匹配或并列歧义返回 null
     */
    public static String matchOption(String resumeValue, List<String> optionLabels, String enumCategory) {
        return matchOption(resumeValue, optionLabels, enumCategory, true);
    }

    /**
     * 在下拉选项中查找与简历值最匹配的一项。
     *
     * @param allowContains false 时禁用第三层包含打分（开放联想框场景），只做精确与枚举别名匹配
     */
    public static String matchOption(String resumeValue, List<String> optionLabels, String enumCategory,
                                     boolean allowContains) {
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

        // 第二层：枚举别名匹配（同义词组，比包含关系更可靠）
        if (enumCategory != null) {
            String aliasMatch = ChineseFieldAliasTable.matchByEnumAlias(enumCategory, resumeValue, optionLabels);
            if (aliasMatch != null) {
                return aliasMatch;
            }
        }

        if (!allowContains) {
            // 开放联想框：样本不是封闭值域，低分包含匹配会把长值改写成残缺短串，禁用
            return null;
        }

        // 第三层：包含关系打分竞争（较短串/较长串，取最高分；并列最高 → 歧义返回 null）
        String best = null;
        double bestScore = 0d;
        boolean tie = false;
        for (String opt : optionLabels) {
            String normalizedOpt = normalize(opt);
            if (normalizedOpt.isEmpty()) {
                continue; // 空选项 contains 恒真，必须排除
            }
            if (!normalizedOpt.contains(normalizedResume) && !normalizedResume.contains(normalizedOpt)) {
                continue;
            }
            double score = (double) Math.min(normalizedOpt.length(), normalizedResume.length())
                    / Math.max(normalizedOpt.length(), normalizedResume.length());
            if (score > bestScore) {
                best = opt;
                bestScore = score;
                tie = false;
            } else if (score == bestScore && !normalizedOpt.equals(normalize(best))) {
                tie = true;
            }
        }
        return tie ? null : best;
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
