package com.smartapply.ida.common.domain.plugin.autofill.site;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 站点指纹规则：判定当前页快照是否属于某站点模板。
 * <p>
 * 各字段为「与」关系——非空字段需全部满足才视为本规则命中；规则之间在 {@link SiteTemplate#getFingerprints()} 列表内为「或」关系。
 * 至少要有一项非空，否则规则被忽略。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteFingerprint {

    /**
     * pageUrl 的 host 后缀匹配（忽略大小写），如 "beisen.com" 匹配 {@code https://campus.beisen.com/...}。
     * 多个后缀用分号分隔，命中其一即可。
     */
    private String urlHostSuffix;

    /**
     * pageUrl 全路径正则（忽略大小写），用于更精细的路径区分，例如匹配 {@code /campus/apply/} 子串的正则。
     * 与 urlHostSuffix 为「与」关系（两者都非空时都要满足）。
     */
    private String urlPathRegex;

    /**
     * 字段锚点特征：当快照中至少 {@code minFieldAnchorHits} 个字段满足给定锚点特征时命中。
     * 用于 URL 不唯一时二次确认站点。锚点字段复用 {@link FieldAnchor} 语义。
     */
    private FieldAnchor fieldAnchorHint;

    /** 满足 fieldAnchorHint 的最少字段数，默认 1 */
    @Builder.Default
    private int minFieldAnchorHits = 1;
}
