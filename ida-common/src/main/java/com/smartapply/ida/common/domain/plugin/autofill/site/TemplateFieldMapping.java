package com.smartapply.ida.common.domain.plugin.autofill.site;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 模板内单条字段金标映射：页面字段（按 {@link FieldAnchor} 定位）→ 简历字段路径。
 * <p>
 * 命中后由 {@code SiteTemplateMatcher} 生成 assignment；下拉字段经 {@code OptionMatcher} 选项匹配，
 * 复合字段/长文本不在模板层处理（流转到确定性/LLM 层）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateFieldMapping {

    /** 锚点条件：满足任一非空条件即命中本映射行 */
    private FieldAnchor anchor;

    /**
     * 简历字段路径，复用 {@code ResumeFieldAccessor} 格式：
     * {@code basic.fullName}、{@code education.0.schoolName}、{@code work.1.jobTitle} 等。
     */
    private String resumePath;

    /**
     * 下拉枚举类别（可选）：{@code gender}、{@code degree}、{@code politicalStatus}、{@code hukouType}，
     * 交给 {@code OptionMatcher.matchOption} 做选项同义词匹配。非下拉字段留空。
     */
    private String enumCategory;

    /**
     * 复合字段角色（可选）：{@code dial_code}、{@code local_number}、{@code id_type}、{@code id_number}、
     * {@code date_start}、{@code date_end}、{@code part}。与提示词 {@code subAssignments[].targetRole} 对齐。
     * <p>
     * 注意：模板层不直接产出 subAssignments（复合行需多锚点协同），仅当本字段是复合行的某一格、
     * 且值为单一标量时才填；复合行的整体处理仍交 LLM。本轮默认不启用，留作扩展。
     */
    private String targetRole;

    /**
     * 若为 true，模板已知该字段无法自动填（附件、验证码、只读等），直接进 blockedFields，
     * 不尝试取简历值。{@code resumePath} 此时可为空。
     */
    @Builder.Default
    private boolean skip = false;

    /**
     * skip=true 时的 blocked 原因码（可选），值域以
     * {@code com.smartapply.ida.common.domain.plugin.autofill.BlockedFieldReasons} 为准：
     * {@code readonly}/{@code captcha}/{@code attachment}/{@code no_mapping}/{@code ambiguous}/
     * {@code compound_unresolved}/{@code policy}/{@code other}。
     * 为空（JSON 中不写）时默认 {@code other}。
     */
    private String blockedReason;

    /**
     * skip=true 时给用户看的提示文案（可选，如 "附件需人工上传"）；
     * 为空（JSON 中不写）时由匹配器使用默认文案。
     */
    private String blockedHint;
}
