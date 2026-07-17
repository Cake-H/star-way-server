package com.smartapply.ida.common.domain.plugin.autofill.site;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 站点模板：针对特定 ATS / 网申系统（北森、大汉、牛客等）预先标注的「字段锚点 → 简历字段路径」金标映射。
 * <p>
 * 命中本模板的字段可在确定性匹配与 LLM 之前直接赋值（毫秒级、~100% 准确），是覆盖率从 80% 提升到 95%+ 的关键。
 * 模板定义以 JSON 形式存放于 {@code resources/site-templates/*.json}，由 {@code SiteTemplateRegistry} 启动时加载，
 * 运营/数据团队可不改代码新增模板。
 * <p>
 * 字段路径格式复用 {@code ResumeFieldAccessor}：{@code basic.fullName}、{@code education.0.schoolName} 等。
 * 下拉枚举匹配复用 {@code OptionMatcher} + {@code ChineseFieldAliasTable}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteTemplate {

    /** 模板唯一标识，如 "beisen_campus"；用于日志、反馈记录与缓存诊断 */
    private String siteId;

    /** 展示名，如 "北森校招"，仅用于日志与后台诊断 */
    private String displayName;

    /**
     * 多模板同时命中指纹时的排序权重，数字越小优先级越高；默认 100。
     * 同优先级按注册顺序。
     */
    @Builder.Default
    private int priority = 100;

    /**
     * 指纹规则列表（OR 关系）：满足任一即视为命中本模板。
     * 主信号为 URL host/路径模式；辅信号为字段锚点特征。
     */
    private List<SiteFingerprint> fingerprints;

    /**
     * 字段金标映射列表：页面字段（按锚点匹配）→ 简历字段路径。
     * 模板匹配阶段逐条扫描，命中的字段直接生成 assignment。
     */
    private List<TemplateFieldMapping> fieldMappings;
}
