package com.smartapply.ida.module.app.resume.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.common.domain.resume.*;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillResumeContextVO;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.domain.resume.support.ResumeDraftModuleCountsExtractor;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeListItemVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.executor.ResumeQueryExecutorConfig;
import com.smartapply.ida.module.app.resume.assembler.ResumeViewAssembler;
import com.smartapply.ida.module.app.resume.mapper.*;
import com.smartapply.ida.module.app.resume.service.AppResumeDraftService;
import com.smartapply.ida.module.app.resume.service.AppResumeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 简历「读模型」实现：只查库、装配 VO，不做任何 INSERT/UPDATE/DELETE。
 * <p>
 * <b>为何单独成类：</b>与 {@link AppResumeDraftService} 的写路径分离，
 * 以后若对列表/详情做缓存、字段裁剪或审计日志，不会与保存草稿的事务搅在一起。
 */
@Service
@RequiredArgsConstructor
public class AppResumeQueryServiceImpl implements AppResumeQueryService {

    private final ResumeMapper resumeMapper;
    private final ResumeEducationMapper resumeEducationMapper;
    private final ResumeWorkMapper resumeWorkMapper;
    private final ResumeProjectMapper resumeProjectMapper;
    private final ResumeCampusMapper resumeCampusMapper;
    private final ResumeSkillMapper resumeSkillMapper;
    private final ResumeLanguageMapper resumeLanguageMapper;
    private final ResumeCertificateMapper resumeCertificateMapper;
    private final ResumeAwardMapper resumeAwardMapper;
    private final ResumePublicationMapper resumePublicationMapper;

    @Qualifier(ResumeQueryExecutorConfig.BEAN_NAME)
    private final ThreadPoolTaskExecutor resumeQueryExecutor;

    /**
     * 单份简历在 9 张子表中的全部行，按 {@code sort_order} 升序已排好序，
     * 仅用于本类内部一次性交给 {@link ResumeViewAssembler#toDraft}。
     */
    private record ResumeChildSnapshot(
            List<ResumeEducation> educations,
            List<ResumeWork> works,
            List<ResumeProject> projects,
            List<ResumeCampus> campus,
            List<ResumeSkill> skills,
            List<ResumeLanguage> languages,
            List<ResumeCertificate> certificates,
            List<ResumeAward> awards,
            List<ResumePublication> publications) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * 查询条件：当前用户、未软删；排序：{@code updated_at} 从新到旧，便于列表页「最近编辑」在上。
     */
    @Override
    public List<ResumeListItemVO> listMyResumes(long userId) {
        List<Resume> rows = resumeMapper.selectList(Wrappers.<Resume>lambdaQuery()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUpdatedAt));

        return rows.stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

    /**
     * 主表一行 → 列表项 VO（不含大字段 JSON，减轻列表接口体积）。
     */
    private ResumeListItemVO toListItem(Resume r) {
        return ResumeListItemVO.builder()
                .resumeId(r.getId())
                .title(r.getTitle())
                .isDefault(r.getIsDefault())
                .fullName(r.getFullName())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 步骤：① 主表存在且未删、且归属当前用户；② 拉齐 9 张子表；③ 装配为与解析接口一致的 {@link ResumeParseDraftDto}。
     */
    @Override
    public ResumeDetailVO getResumeDetail(long userId, long resumeId) {
        Resume r = resumeMapper.selectById(resumeId);
        assertResumeReadableByUser(r, userId);

        long rid = r.getId();
        ResumeChildSnapshot children = loadChildSnapshot(rid);

        ResumeParseDraftDto draft = ResumeViewAssembler.toDraft(
                r,
                children.educations(),
                children.works(),
                children.projects(),
                children.campus(),
                children.skills(),
                children.languages(),
                children.certificates(),
                children.awards(),
                children.publications());

        return ResumeDetailVO.builder()
                .resumeId(r.getId())
                .title(r.getTitle())
                .isDefault(r.getIsDefault())
                .schemaVersion(r.getSchemaVersion())
                .updatedAt(r.getUpdatedAt())
                .draft(draft)
                .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现：复用 {@link #getResumeDetail(long, long)} 的鉴权与 draft 装配，再用
     * {@link ResumeDraftModuleCountsExtractor#fromDraft(ResumeParseDraftDto)} 数各 List 长度，
     * 不额外查库。
     */
    @Override
    public PluginAutofillResumeContextVO getAutofillResumeContext(long userId, long resumeId) {
        // 与详情接口共用读路径：抛「无权查看」等业务异常的行为一致
        ResumeDetailVO detail = getResumeDetail(userId, resumeId);
        // 表draft 在 getResumeDetail 内已保证非 null 子表列表（可能为空列）
        return PluginAutofillResumeContextVO.builder()
                .resumeId(resumeId)
                .schemaVersion(detail.getSchemaVersion())
                .updatedAt(detail.getUpdatedAt())
                .moduleCounts(ResumeDraftModuleCountsExtractor.fromDraft(detail.getDraft()))
                .build();
    }

    /**
     * 校验简历可被当前用户读取：存在、未 soft-delete、归属一致。
     *
     * @param r      主表查询结果，可为 null
     * @param userId 当前登录用户
     * @throws BusinessException 不可读时抛出（文案与写侧区分，使用「无权查看」）
     */
    private void assertResumeReadableByUser(Resume r, long userId) {
        if (r == null) {
            throw new BusinessException("简历不存在或已删除");
        }
        if (!r.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该简历");
        }
    }

    /**
     * 一次性加载 9 张子表；每张表单独一个查询方法，避免泛型魔法，方便新人按表名跳转。
     */
    private ResumeChildSnapshot loadChildSnapshot(long resumeId) {
        CompletableFuture<List<ResumeEducation>> f1 = CompletableFuture.supplyAsync(() -> listEducations(resumeId), resumeQueryExecutor);
        CompletableFuture<List<ResumeWork>> f2 = CompletableFuture.supplyAsync(() -> listWorks(resumeId), resumeQueryExecutor);
        CompletableFuture<List<ResumeProject>> f3 = CompletableFuture.supplyAsync(() -> listProjects(resumeId), resumeQueryExecutor);
        CompletableFuture<List<ResumeCampus>> f4 = CompletableFuture.supplyAsync(() -> listCampus(resumeId), resumeQueryExecutor);
        CompletableFuture<List<ResumeSkill>> f5 = CompletableFuture.supplyAsync(() -> listSkills(resumeId), resumeQueryExecutor);
        CompletableFuture<List<ResumeLanguage>> f6 = CompletableFuture.supplyAsync(() -> listLanguages(resumeId), resumeQueryExecutor);
        CompletableFuture<List<ResumeCertificate>> f7 = CompletableFuture.supplyAsync(() -> listCertificates(resumeId), resumeQueryExecutor);
        CompletableFuture<List<ResumeAward>> f8 = CompletableFuture.supplyAsync(() -> listAwards(resumeId), resumeQueryExecutor);
        CompletableFuture<List<ResumePublication>> f9 = CompletableFuture.supplyAsync(() -> listPublications(resumeId), resumeQueryExecutor);
        return new ResumeChildSnapshot(f1.join(), f2.join(), f3.join(), f4.join(), f5.join(), f6.join(), f7.join(), f8.join(), f9.join());
    }

    /** 教育经历：按 {@code sort_order} 升序，与保存草稿时插入顺序一致。 */
    private List<ResumeEducation> listEducations(long resumeId) {
        return resumeEducationMapper.selectList(Wrappers.<ResumeEducation>lambdaQuery()
                .eq(ResumeEducation::getResumeId, resumeId)
                .orderByAsc(ResumeEducation::getSortOrder));
    }

    /** 工作经历 */
    private List<ResumeWork> listWorks(long resumeId) {
        return resumeWorkMapper.selectList(Wrappers.<ResumeWork>lambdaQuery()
                .eq(ResumeWork::getResumeId, resumeId)
                .orderByAsc(ResumeWork::getSortOrder));
    }

    /** 项目经历 */
    private List<ResumeProject> listProjects(long resumeId) {
        return resumeProjectMapper.selectList(Wrappers.<ResumeProject>lambdaQuery()
                .eq(ResumeProject::getResumeId, resumeId)
                .orderByAsc(ResumeProject::getSortOrder));
    }

    /** 校园经历 */
    private List<ResumeCampus> listCampus(long resumeId) {
        return resumeCampusMapper.selectList(Wrappers.<ResumeCampus>lambdaQuery()
                .eq(ResumeCampus::getResumeId, resumeId)
                .orderByAsc(ResumeCampus::getSortOrder));
    }

    /** 技能 */
    private List<ResumeSkill> listSkills(long resumeId) {
        return resumeSkillMapper.selectList(Wrappers.<ResumeSkill>lambdaQuery()
                .eq(ResumeSkill::getResumeId, resumeId)
                .orderByAsc(ResumeSkill::getSortOrder));
    }

    /** 语言能力 */
    private List<ResumeLanguage> listLanguages(long resumeId) {
        return resumeLanguageMapper.selectList(Wrappers.<ResumeLanguage>lambdaQuery()
                .eq(ResumeLanguage::getResumeId, resumeId)
                .orderByAsc(ResumeLanguage::getSortOrder));
    }

    /** 证书 */
    private List<ResumeCertificate> listCertificates(long resumeId) {
        return resumeCertificateMapper.selectList(Wrappers.<ResumeCertificate>lambdaQuery()
                .eq(ResumeCertificate::getResumeId, resumeId)
                .orderByAsc(ResumeCertificate::getSortOrder));
    }

    /** 获奖 */
    private List<ResumeAward> listAwards(long resumeId) {
        return resumeAwardMapper.selectList(Wrappers.<ResumeAward>lambdaQuery()
                .eq(ResumeAward::getResumeId, resumeId)
                .orderByAsc(ResumeAward::getSortOrder));
    }

    /** 著作/论文 */
    private List<ResumePublication> listPublications(long resumeId) {
        return resumePublicationMapper.selectList(Wrappers.<ResumePublication>lambdaQuery()
                .eq(ResumePublication::getResumeId, resumeId)
                .orderByAsc(ResumePublication::getSortOrder));
    }
}
