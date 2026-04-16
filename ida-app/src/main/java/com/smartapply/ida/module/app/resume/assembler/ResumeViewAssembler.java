package com.smartapply.ida.module.app.resume.assembler;

import com.smartapply.ida.common.domain.resume.*;
import com.smartapply.ida.common.domain.resume.dto.parse.*;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 将数据库中的简历主表 + 子表实体装配为与「上传解析」一致的 {@link ResumeParseDraftDto}，
 * 供详情接口只读展示，并与保存/修改接口共用前端模型。
 * <p>
 * 设计为无状态静态工具类，避免与事务/Mapper 耦合，便于单测。
 */
public final class ResumeViewAssembler {

    private ResumeViewAssembler() {
    }

    /**
     * 汇总主表 + 各子表行为完整草稿对象；子表按库中 {@code sort_order} 已排序传入。
     */
    public static ResumeParseDraftDto toDraft(
            Resume r,
            List<ResumeEducation> educations,
            List<ResumeWork> works,
            List<ResumeProject> projects,
            List<ResumeCampus> campus,
            List<ResumeSkill> skills,
            List<ResumeLanguage> languages,
            List<ResumeCertificate> certificates,
            List<ResumeAward> awards,
            List<ResumePublication> publications) {
        ResumeParseDraftDto d = new ResumeParseDraftDto();
        copyMain(r, d);
        d.setEducations(mapList(educations, ResumeViewAssembler::toEducationLine));
        d.setWorks(mapList(works, ResumeViewAssembler::toWorkLine));
        d.setProjects(mapList(projects, ResumeViewAssembler::toProjectLine));
        d.setCampusExperiences(mapList(campus, ResumeViewAssembler::toCampusLine));
        d.setSkills(mapList(skills, ResumeViewAssembler::toSkillLine));
        d.setLanguages(mapList(languages, ResumeViewAssembler::toLanguageLine));
        d.setCertificates(mapList(certificates, ResumeViewAssembler::toCertificateLine));
        d.setAwards(mapList(awards, ResumeViewAssembler::toAwardLine));
        d.setPublications(mapList(publications, ResumeViewAssembler::toPublicationLine));
        return d;
    }

    private static <S, T> List<T> mapList(List<S> src, java.util.function.Function<S, T> fn) {
        if (src == null || src.isEmpty()) {
            return new ArrayList<>();
        }
        List<T> out = new ArrayList<>(src.size());
        for (S s : src) {
            out.add(fn.apply(s));
        }
        return out;
    }

    /**
     * 主表字段：草稿 DTO 与实体同名部分用拷贝；出生日期在库中为 {@link Date}，草稿为 {@code yyyy-MM-dd} 字符串。
     */
    private static void copyMain(Resume r, ResumeParseDraftDto d) {
        BeanUtils.copyProperties(r, d, "birthDate");
        d.setBirthDate(formatBirthDate(r.getBirthDate()));
    }

    private static String formatBirthDate(Date birthDate) {
        if (birthDate == null) {
            return null;
        }
        return birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private static String fmtLocalDate(LocalDate ld) {
        return ld == null ? null : ld.toString();
    }

    private static ResumeEducationDraftLine toEducationLine(ResumeEducation e) {
        ResumeEducationDraftLine x = new ResumeEducationDraftLine();
        BeanUtils.copyProperties(e, x, "enrollDate", "graduateDate");
        x.setEnrollDate(fmtLocalDate(e.getEnrollDate()));
        x.setGraduateDate(fmtLocalDate(e.getGraduateDate()));
        return x;
    }

    private static ResumeWorkDraftLine toWorkLine(ResumeWork e) {
        ResumeWorkDraftLine x = new ResumeWorkDraftLine();
        BeanUtils.copyProperties(e, x, "startDate", "endDate");
        x.setStartDate(fmtLocalDate(e.getStartDate()));
        x.setEndDate(fmtLocalDate(e.getEndDate()));
        return x;
    }

    private static ResumeProjectDraftLine toProjectLine(ResumeProject e) {
        ResumeProjectDraftLine x = new ResumeProjectDraftLine();
        BeanUtils.copyProperties(e, x, "startDate", "endDate");
        x.setStartDate(fmtLocalDate(e.getStartDate()));
        x.setEndDate(fmtLocalDate(e.getEndDate()));
        return x;
    }

    private static ResumeCampusDraftLine toCampusLine(ResumeCampus e) {
        ResumeCampusDraftLine x = new ResumeCampusDraftLine();
        BeanUtils.copyProperties(e, x, "startDate", "endDate");
        x.setStartDate(fmtLocalDate(e.getStartDate()));
        x.setEndDate(fmtLocalDate(e.getEndDate()));
        return x;
    }

    private static ResumeSkillDraftLine toSkillLine(ResumeSkill e) {
        ResumeSkillDraftLine x = new ResumeSkillDraftLine();
        BeanUtils.copyProperties(e, x);
        return x;
    }

    private static ResumeLanguageDraftLine toLanguageLine(ResumeLanguage e) {
        ResumeLanguageDraftLine x = new ResumeLanguageDraftLine();
        BeanUtils.copyProperties(e, x);
        return x;
    }

    private static ResumeCertificateDraftLine toCertificateLine(ResumeCertificate e) {
        ResumeCertificateDraftLine x = new ResumeCertificateDraftLine();
        BeanUtils.copyProperties(e, x, "obtainDate");
        x.setObtainDate(fmtLocalDate(e.getObtainDate()));
        return x;
    }

    private static ResumeAwardDraftLine toAwardLine(ResumeAward e) {
        ResumeAwardDraftLine x = new ResumeAwardDraftLine();
        BeanUtils.copyProperties(e, x, "awardDate");
        x.setAwardDate(fmtLocalDate(e.getAwardDate()));
        return x;
    }

    private static ResumePublicationDraftLine toPublicationLine(ResumePublication e) {
        ResumePublicationDraftLine x = new ResumePublicationDraftLine();
        BeanUtils.copyProperties(e, x, "publishDate");
        x.setPublishDate(fmtLocalDate(e.getPublishDate()));
        return x;
    }
}
