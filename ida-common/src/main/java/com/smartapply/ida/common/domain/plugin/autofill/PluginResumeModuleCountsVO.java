package com.smartapply.ida.common.domain.plugin.autofill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 云简历（结构化 draft）中「可多条」模块的条目数量，供扩展在采快照前自动点击「+添加」展开对应行数。
 * <p>
 * 数值与 {@link com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto} 各 List 的 size 一致；
 * 单条模块（如基本信息）不在此统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginResumeModuleCountsVO {

    /** 教育经历条数，对应 draft.educations */
    @Builder.Default
    private int educationCount = 0;

    /** 工作经历条数，对应 draft.works */
    @Builder.Default
    private int workCount = 0;

    /** 项目经历条数，对应 draft.projects */
    @Builder.Default
    private int projectCount = 0;

    /** 校园经历/实践条数，对应 draft.campusExperiences */
    @Builder.Default
    private int campusExperienceCount = 0;

    /** 技能条数，对应 draft.skills */
    @Builder.Default
    private int skillCount = 0;

    /** 语言条数，对应 draft.languages */
    @Builder.Default
    private int languageCount = 0;

    /** 证书条数，对应 draft.certificates */
    @Builder.Default
    private int certificateCount = 0;

    /** 获奖条数，对应 draft.awards */
    @Builder.Default
    private int awardCount = 0;

    /** 论文/著作条数，对应 draft.publications */
    @Builder.Default
    private int publicationCount = 0;
}
