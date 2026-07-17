package com.smartapply.ida.ai.plugin.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormFieldDto;
import com.smartapply.ida.common.domain.plugin.autofill.PluginPageFormSnapshotDto;
import com.smartapply.ida.common.domain.plugin.autofill.site.FieldAnchor;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteFingerprint;
import com.smartapply.ida.common.domain.plugin.autofill.site.SiteTemplate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 站点模板注册表：启动时从 classpath {@code site-templates/*.json} 加载全部模板，
 * 运行时按 {@link #match(PluginPageFormSnapshotDto)} 选出命中的模板。
 * <p>
 * 加载失败仅告警并跳过该文件，不阻断启动；无任何模板时 {@link #match} 恒返回 empty。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteTemplateRegistry {

    private static final String TEMPLATE_LOCATION = "classpath:site-templates/*.json";

    private final ObjectMapper objectMapper;

    /** 已加载的模板，按 priority 升序、再按 siteId 字典序，保证多模板命中时选取稳定 */
    private volatile List<SiteTemplate> templates = List.of();

    @PostConstruct
    public void load() {
        List<SiteTemplate> loaded = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(TEMPLATE_LOCATION);
            for (Resource res : resources) {
                String name = res.getFilename();
                if (name == null || name.startsWith("_")) {
                    // 跳过 _template.json / _schema.json 等骨架/Schema 文件
                    continue;
                }
                try {
                    SiteTemplate t = objectMapper.readValue(res.getInputStream(), SiteTemplate.class);
                    if (t != null && StringUtils.hasText(t.getSiteId())) {
                        loaded.add(t);
                        log.info("[站点模板] 已加载 {} (siteId={}, 字段映射数={})",
                                name, t.getSiteId(),
                                t.getFieldMappings() == null ? 0 : t.getFieldMappings().size());
                    }
                } catch (Exception e) {
                    log.warn("[站点模板] 加载 {} 失败，跳过: {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[站点模板] 扫描 {} 失败，模板层将不生效: {}", TEMPLATE_LOCATION, e.getMessage());
        }
        loaded.sort(Comparator
                .comparingInt((SiteTemplate t) -> t.getPriority() == 0 ? 100 : t.getPriority())
                .thenComparing(t -> t.getSiteId() == null ? "" : t.getSiteId()));
        this.templates = List.copyOf(loaded);
        log.info("[站点模板] 加载完成，共 {} 个可用模板", this.templates.size());
    }

    /**
     * 按指纹规则匹配当前页快照，返回优先级最高的命中模板。
     *
     * @return 命中模板；未命中或无模板返回 empty
     */
    public Optional<SiteTemplate> match(PluginPageFormSnapshotDto page) {
        if (templates.isEmpty() || page == null) {
            return Optional.empty();
        }
        for (SiteTemplate t : templates) {
            if (t.getFingerprints() == null || t.getFingerprints().isEmpty()) {
                continue;
            }
            for (SiteFingerprint fp : t.getFingerprints()) {
                if (matchesFingerprint(fp, page)) {
                    log.info("[站点模板] 命中 siteId={} ({})", t.getSiteId(), t.getDisplayName());
                    return Optional.of(t);
                }
            }
        }
        return Optional.empty();
    }

    /** 当前已加载模板数量，供测试与诊断使用 */
    public int size() {
        return templates.size();
    }

    private boolean matchesFingerprint(SiteFingerprint fp, PluginPageFormSnapshotDto page) {
        if (fp == null) {
            return false;
        }
        String url = page.getPageUrl() == null ? "" : page.getPageUrl();
        String host = PageUrlUtils.extractHost(url);
        String path = extractPath(url);

        // urlHostSuffix 非空时必须命中
        if (StringUtils.hasText(fp.getUrlHostSuffix())) {
            if (!matchesHostSuffix(host, fp.getUrlHostSuffix())) {
                return false;
            }
        }
        // urlPathRegex 非空时必须命中
        if (StringUtils.hasText(fp.getUrlPathRegex())) {
            try {
                if (!Pattern.compile(fp.getUrlPathRegex(), Pattern.CASE_INSENSITIVE).matcher(path).find()) {
                    return false;
                }
            } catch (Exception e) {
                // 非法正则视为不命中，避免拖垮主流程
                return false;
            }
        }
        // fieldAnchorHint 非空时需满足最少命中字段数
        FieldAnchor hint = fp.getFieldAnchorHint();
        if (hint != null && hasAnyAnchorCondition(hint)) {
            int hits = 0;
            for (PluginPageFormFieldDto f : page.getFields()) {
                if (anchorMatches(f, hint)) {
                    hits++;
                }
            }
            if (hits < Math.max(1, fp.getMinFieldAnchorHits())) {
                return false;
            }
        }
        // 至少要有一项指纹信号（否则空规则不应命中）
        return StringUtils.hasText(fp.getUrlHostSuffix())
                || StringUtils.hasText(fp.getUrlPathRegex())
                || (hint != null && hasAnyAnchorCondition(hint));
    }

    private boolean matchesHostSuffix(String host, String suffixExpr) {
        if (host.isEmpty()) {
            return false;
        }
        String[] suffixes = suffixExpr.split(";");
        for (String s : suffixes) {
            String su = s.trim().toLowerCase();
            if (su.isEmpty()) continue;
            if (host.equals(su) || host.endsWith("." + su) || host.endsWith(su)) {
                return true;
            }
        }
        return false;
    }

    private String extractPath(String url) {
        String u = url.trim();
        int scheme = u.indexOf("://");
        int start = scheme >= 0 ? scheme + 3 : 0;
        int slash = u.indexOf('/', start);
        return slash > 0 ? u.substring(slash) : "/";
    }

    /**
     * 锚点匹配：{@link FieldAnchor} 内非空条件为「或」关系——满足任一即命中。
     * 与 {@code SiteTemplateMatcher} 内的锚点判定共用同一语义。
     */
    static boolean anchorMatches(PluginPageFormFieldDto f, FieldAnchor anchor) {
        if (anchor == null || f == null) {
            return false;
        }
        if (StringUtils.hasText(anchor.getClientFieldKeyEquals())
                && anchor.getClientFieldKeyEquals().equals(f.getClientFieldKey())) {
            return true;
        }
        if (StringUtils.hasText(anchor.getDataFormFieldIdEquals())
                && anchor.getDataFormFieldIdEquals().equals(f.getDataFormFieldId())) {
            return true;
        }
        if (StringUtils.hasText(anchor.getDataFormFieldIdPrefix())
                && f.getDataFormFieldId() != null
                && f.getDataFormFieldId().startsWith(anchor.getDataFormFieldIdPrefix())) {
            return true;
        }
        if (StringUtils.hasText(anchor.getI18nLabelEquals())
                && anchor.getI18nLabelEquals().equals(f.getI18nLabel())) {
            return true;
        }
        if (StringUtils.hasText(anchor.getLabelTextEquals())
                && anchor.getLabelTextEquals().equals(normalizeLabel(f.getLabelText()))) {
            return true;
        }
        if (StringUtils.hasText(anchor.getSelectorContains())
                && f.getSelector() != null
                && f.getSelector().contains(anchor.getSelectorContains())) {
            return true;
        }
        return false;
    }

    static boolean hasAnyAnchorCondition(FieldAnchor anchor) {
        return anchor != null && (
                StringUtils.hasText(anchor.getClientFieldKeyEquals())
                        || StringUtils.hasText(anchor.getDataFormFieldIdEquals())
                        || StringUtils.hasText(anchor.getDataFormFieldIdPrefix())
                        || StringUtils.hasText(anchor.getI18nLabelEquals())
                        || StringUtils.hasText(anchor.getLabelTextEquals())
                        || StringUtils.hasText(anchor.getSelectorContains()));
    }

    static String normalizeLabel(String label) {
        if (label == null) return "";
        return label.trim()
                .replaceAll("[：:*＊]$", "")
                .replaceAll("\\(必填\\)$", "")
                .replaceAll("（必填）$", "")
                .replaceAll("^请输入", "")
                .replaceAll("^请选择", "")
                .replaceAll("^请填写", "")
                .trim();
    }
}
