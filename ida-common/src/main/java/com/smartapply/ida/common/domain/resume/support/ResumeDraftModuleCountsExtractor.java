package com.smartapply.ida.common.domain.resume.support;

import com.smartapply.ida.common.domain.plugin.autofill.PluginResumeModuleCountsVO;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;

import java.util.List;

/**
 * 从简历草稿 DTO 统计各子表 List 长度，供插件填表「展开块数量」与提示词 PART_C 共用同一套数字来源。
 * <p>
 * 纯函数、无 IO，可在任意层调用；null 安全。
 */
public final class ResumeDraftModuleCountsExtractor {

    private ResumeDraftModuleCountsExtractor() {
    }

    /**
     * @param draft 可为 null（返回全 0）
     */
    public static PluginResumeModuleCountsVO fromDraft(ResumeParseDraftDto draft) {
        if (draft == null) {
            return PluginResumeModuleCountsVO.builder().build();
        }
        return PluginResumeModuleCountsVO.builder()
                .educationCount(size(draft.getEducations()))
                .workCount(size(draft.getWorks()))
                .projectCount(size(draft.getProjects()))
                .campusExperienceCount(size(draft.getCampusExperiences()))
                .skillCount(size(draft.getSkills()))
                .languageCount(size(draft.getLanguages()))
                .certificateCount(size(draft.getCertificates()))
                .awardCount(size(draft.getAwards()))
                .publicationCount(size(draft.getPublications()))
                .build();
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
