package com.smartapply.ida.module.app.resume.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartapply.ida.common.domain.resume.*;
import com.smartapply.ida.common.domain.resume.dto.ResumeSaveDraftRequest;
import com.smartapply.ida.common.domain.resume.dto.ResumeUpdateRequest;
import com.smartapply.ida.common.domain.resume.dto.parse.*;
import com.smartapply.ida.common.domain.resume.vo.ResumeSaveDraftResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.resume.mapper.ResumeAwardMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeCampusMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeCertificateMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeEducationMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeLanguageMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeProjectMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumePublicationMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeSkillMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeWorkMapper;
import com.smartapply.ida.module.app.resume.service.AppResumeDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;

/**
 * 将前端提交的「简历草稿」持久化到 {@code ida_resume} 及 9 张子表。
 * <p>
 * <b>核心策略（与产品约定）：</b>
 * <ul>
 *   <li><b>整份覆盖</b>：更新时先按 {@code resume_id} 删除所有子表行，再按草稿列表重新插入，不做行级 diff。
 *       这样实现简单，且与「前端一次提交完整 JSON」的交互一致；若以后要增量更新，需要子表主键回传与更复杂合并逻辑。</li>
 *   <li><b>归属校验</b>：{@code resumeId} 更新路径下必须校验 {@code user_id}，防止水平越权。</li>
 *   <li><b>默认简历</b>：当请求 {@code isDefault=1} 时，将该用户其余未删除简历的 {@code is_default} 置 0，再置当前为 1，保证「至多一条默认」。</li>
 *   <li><b>字段映射</b>：主表/子表与 {@link ResumeParseDraftDto} 同名字段优先用 {@link BeanUtils #copyProperties}；
 *       草稿里日期多为 {@code yyyy-MM-dd} 字符串，库表多为 {@link LocalDate}/{@link Date}，须在拷贝时排除再手动解析。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AppResumeDraftServiceImpl implements AppResumeDraftService {

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
    private final ObjectMapper objectMapper;

    /**
     * 新建或更新一份简历草稿。
     * <p>
     * 事务边界：主表写入 + 子表删插 +（可选）默认标记批量更新在同一事务内，任一步失败则全部回滚。
     *
     * @param userId 当前登录用户，来自网关/鉴权
     * @param req    请求体：{@code resumeId} 为空表示新建；非空表示更新。{@code draft} 为必填。
     * @return 最终简历主键 id，供前端继续关联投递等场景
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeSaveDraftResultVO saveDraft(long userId, ResumeSaveDraftRequest req) {
        if (req == null || req.getDraft() == null) {
            throw new BusinessException("草稿内容不能为空");
        }
        ResumeParseDraftDto d = req.getDraft();
        LocalDateTime now = LocalDateTime.now();

        Long resumeId = req.getResumeId();
        if (resumeId != null) {
            // ——— 更新已有简历 ———
            Resume existing = resumeMapper.selectById(resumeId);
            if (existing == null || existing.getDeletedAt() != null) {
                throw new BusinessException("简历不存在或已删除");
            }
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException("无权操作该简历");
            }
            // 子表全量替换：先删后插，保证与前端提交的列表顺序、条数一致
            deleteChildren(resumeId);
            applyDraftToResume(existing, d, req, now, false);
            resumeMapper.updateById(existing);
            insertAllChildren(resumeId, d, now);
            applyDefaultFlag(userId, resumeId, req.getIsDefault());
            return ResumeSaveDraftResultVO.builder().resumeId(resumeId).build();
        }

        // ——— 新建简历 ———
        Resume created = new Resume();
        created.setUserId(userId);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        applyDraftToResume(created, d, req, now, true);
        resumeMapper.insert(created);
        Long newId = created.getId();
        if (newId == null) {
            throw new BusinessException("保存简历失败");
        }
        insertAllChildren(newId, d, now);
        applyDefaultFlag(userId, newId, req.getIsDefault());
        return ResumeSaveDraftResultVO.builder().resumeId(newId).build();
    }

    /**
     * 修改已有简历（对应 REST {@code PUT /resume/{resumeId}}）。
     * <p>
     * <b>为何不把逻辑写进 {@link #saveDraft}：</b>PUT 语义上主键在路径里，请求体用 {@link ResumeSaveDraftRequest}
     * 会重复携带 {@code resumeId}，易与 body 不一致；故单独 DTO {@link ResumeUpdateRequest}，在此组装成
     * {@link ResumeSaveDraftRequest} 再复用同一套落库。
     * <p>
     * <b>事务：</b>本方法带 {@link Transactional}；内部调用的 {@link #saveDraft} 经由同类 this 调用不会二次开事务，
     * 整段仍在当前事务中，失败一并回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeSaveDraftResultVO updateResume(long userId, long resumeId, ResumeUpdateRequest req) {
        if (req == null || req.getDraft() == null) {
            throw new BusinessException("草稿内容不能为空");
        }
        ResumeSaveDraftRequest merged = toSaveDraftRequest(resumeId, req);
        return saveDraft(userId, merged);
    }

    /**
     * 将「编辑页」的 PUT 请求体转为内部统一的保存请求：主键只来自路径参数，避免与 JSON 里可能误传的 id 冲突。
     */
    private static ResumeSaveDraftRequest toSaveDraftRequest(long resumeId, ResumeUpdateRequest req) {
        ResumeSaveDraftRequest out = new ResumeSaveDraftRequest();
        BeanUtils.copyProperties(req, out, "resumeId");
        out.setResumeId(resumeId);
        return out;
    }

    /**
     * 当用户将本简历设为「默认」时，取消同用户下其它简历的默认标记，再标记当前。
     * <p>
     * 若 {@code isDefault} 未传或非 1，则不修改库中默认关系（更新场景下保留原默认状态）。
     */
    private void applyDefaultFlag(long userId, long resumeId, Integer isDefault) {
        if (isDefault == null || isDefault != 1) {
            return;
        }
        resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .set(Resume::getIsDefault, 0)
                .eq(Resume::getUserId, userId)
                .ne(Resume::getId, resumeId)
                .isNull(Resume::getDeletedAt));

        resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .set(Resume::getIsDefault, 1)
                .eq(Resume::getId, resumeId));
    }

    /**
     * 按简历主键删除所有子表数据（物理删除）。须在主表更新前调用，且处于同一事务。
     */
    private void deleteChildren(long resumeId) {
        resumeEducationMapper.delete(Wrappers.<ResumeEducation>lambdaQuery().eq(ResumeEducation::getResumeId, resumeId));
        resumeWorkMapper.delete(Wrappers.<ResumeWork>lambdaQuery().eq(ResumeWork::getResumeId, resumeId));
        resumeProjectMapper.delete(Wrappers.<ResumeProject>lambdaQuery().eq(ResumeProject::getResumeId, resumeId));
        resumeCampusMapper.delete(Wrappers.<ResumeCampus>lambdaQuery().eq(ResumeCampus::getResumeId, resumeId));
        resumeSkillMapper.delete(Wrappers.<ResumeSkill>lambdaQuery().eq(ResumeSkill::getResumeId, resumeId));
        resumeLanguageMapper.delete(Wrappers.<ResumeLanguage>lambdaQuery().eq(ResumeLanguage::getResumeId, resumeId));
        resumeCertificateMapper.delete(Wrappers.<ResumeCertificate>lambdaQuery().eq(ResumeCertificate::getResumeId, resumeId));
        resumeAwardMapper.delete(Wrappers.<ResumeAward>lambdaQuery().eq(ResumeAward::getResumeId, resumeId));
        resumePublicationMapper.delete(Wrappers.<ResumePublication>lambdaQuery().eq(ResumePublication::getResumeId, resumeId));
    }

    /**
     * 把草稿 DTO 中的主表字段合并到实体 {@link Resume}。
     * <p>
     * 注意：{@link BeanUtils #copyProperties} 不会拷贝类型不兼容的属性（如草稿 {@code birthDate} 为 String，实体为 {@link Date}），
     * 故将 {@code birthDate} 放入 ignore 列表，再用 {@link #toUtilDate} 转换。
     * 标题优先取请求里的 {@code title}，其次草稿，再次由姓名推导默认文案。
     * <p>
     * 插入与更新的差异：插入时初始化 {@code rowVersion}、默认简历标记；更新时递增 {@code rowVersion}，且仅当请求显式带 {@code isDefault} 时改默认标记。
     */
    private void applyDraftToResume(Resume r, ResumeParseDraftDto d, ResumeSaveDraftRequest req, LocalDateTime now, boolean isInsert) {
        BeanUtils.copyProperties(d, r, "birthDate");
        r.setTitle(resolveResumeTitle(req, d));
        r.setBirthDate(toUtilDate(d.getBirthDate()));
        r.setSchemaVersion(2);
        r.setUpdatedAt(now);
        if (isInsert) {
            r.setIsDefault(req.getIsDefault() != null && req.getIsDefault() == 1 ? 1 : 0);
            r.setRowVersion(1);
            r.setDeletedAt(null);
        } else {
            r.setRowVersion(r.getRowVersion() == null ? 1 : r.getRowVersion() + 1);
            if (req.getIsDefault() != null) {
                r.setIsDefault(req.getIsDefault() == 1 ? 1 : 0);
            }
        }
        r.setExtraJson(mergeExtraJson(r.getExtraJson(), req));
    }

    /**
     * 简历标题解析顺序：请求参数 &gt; 草稿 title &gt;「姓名+的简历」&gt; 固定默认「我的简历」。
     */
    private static String resolveResumeTitle(ResumeSaveDraftRequest req, ResumeParseDraftDto d) {
        if (StringUtils.hasText(req.getTitle())) {
            return req.getTitle().trim();
        }
        if (StringUtils.hasText(d.getTitle())) {
            return d.getTitle().trim();
        }
        if (StringUtils.hasText(d.getFullName())) {
            return d.getFullName().trim() + "的简历";
        }
        return "我的简历";
    }

    /**
     * 将本次保存附带的「上传来源」信息合并进主表 {@code extra_json}（字段 {@code lastUpload}），不覆盖其它业务自定义键。
     * 若解析已有 JSON 失败则保持原字符串，避免整单保存失败。
     */
    private String mergeExtraJson(String existing, ResumeSaveDraftRequest req) {
        boolean hasMeta = StringUtils.hasText(req.getOriginalFilename())
                || StringUtils.hasText(req.getOssBucket())
                || StringUtils.hasText(req.getOssObjectKey());
        if (!hasMeta) {
            return existing;
        }
        try {
            ObjectNode root = (existing != null && !existing.isBlank())
                    ? (ObjectNode) objectMapper.readTree(existing)
                    : objectMapper.createObjectNode();
            ObjectNode up = root.putObject("lastUpload");
            if (StringUtils.hasText(req.getOriginalFilename())) {
                up.put("originalFilename", req.getOriginalFilename());
            }
            if (StringUtils.hasText(req.getOssBucket())) {
                up.put("ossBucket", req.getOssBucket());
            }
            if (StringUtils.hasText(req.getOssObjectKey())) {
                up.put("ossObjectKey", req.getOssObjectKey());
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return existing;
        }
    }

    /**
     * 依次写入 9 类子表；每类内部按列表下标作为 {@code sort_order}，与前端展示顺序一致。
     */
    private void insertAllChildren(long resumeId, ResumeParseDraftDto d, LocalDateTime now) {
        insertEducations(resumeId, d.getEducations(), now);
        insertWorks(resumeId, d.getWorks(), now);
        insertProjects(resumeId, d.getProjects(), now);
        insertCampus(resumeId, d.getCampusExperiences(), now);
        insertSkills(resumeId, d.getSkills(), now);
        insertLanguages(resumeId, d.getLanguages(), now);
        insertCertificates(resumeId, d.getCertificates(), now);
        insertAwards(resumeId, d.getAwards(), now);
        insertPublications(resumeId, d.getPublications(), now);
    }

    /**
     * 教育经历：库表要求 {@code school_name} 非空，故无校名行直接跳过；日期字段从字符串解析为 {@link LocalDate}。
     */
    private void insertEducations(long resumeId, List<ResumeEducationDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumeEducationDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getSchoolName())) {
                continue;
            }
            ResumeEducation e = new ResumeEducation();
            BeanUtils.copyProperties(x, e, "enrollDate", "graduateDate");
            e.setResumeId(resumeId);
            e.setSchoolName(x.getSchoolName().trim());
            e.setEnrollDate(parseLocalDate(x.getEnrollDate()));
            e.setGraduateDate(parseLocalDate(x.getGraduateDate()));
            e.setSortOrder(i);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            resumeEducationMapper.insert(e);
        }
    }

    /**
     * 工作/实习：公司名与职位在库表中均为必填语义，缺一即跳过该行。
     */
    private void insertWorks(long resumeId, List<ResumeWorkDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumeWorkDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getCompanyName()) || !StringUtils.hasText(x.getJobTitle())) {
                continue;
            }
            ResumeWork w = new ResumeWork();
            BeanUtils.copyProperties(x, w, "startDate", "endDate");
            w.setResumeId(resumeId);
            w.setCompanyName(x.getCompanyName().trim());
            w.setJobTitle(x.getJobTitle().trim());
            w.setStartDate(parseLocalDate(x.getStartDate()));
            w.setEndDate(parseLocalDate(x.getEndDate()));
            w.setSortOrder(i);
            w.setCreatedAt(now);
            w.setUpdatedAt(now);
            resumeWorkMapper.insert(w);
        }
    }

    /**
     * 项目经历：至少需项目名称；起止日期同样走字符串 → {@link LocalDate}。
     */
    private void insertProjects(long resumeId, List<ResumeProjectDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumeProjectDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getProjectName())) {
                continue;
            }
            ResumeProject p = new ResumeProject();
            BeanUtils.copyProperties(x, p, "startDate", "endDate");
            p.setResumeId(resumeId);
            p.setProjectName(x.getProjectName().trim());
            p.setStartDate(parseLocalDate(x.getStartDate()));
            p.setEndDate(parseLocalDate(x.getEndDate()));
            p.setSortOrder(i);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            resumeProjectMapper.insert(p);
        }
    }

    /**
     * 校园/实践：组织名称为必填语义。
     */
    private void insertCampus(long resumeId, List<ResumeCampusDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumeCampusDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getOrganizationName())) {
                continue;
            }
            ResumeCampus c = new ResumeCampus();
            BeanUtils.copyProperties(x, c, "startDate", "endDate");
            c.setResumeId(resumeId);
            c.setOrganizationName(x.getOrganizationName().trim());
            c.setStartDate(parseLocalDate(x.getStartDate()));
            c.setEndDate(parseLocalDate(x.getEndDate()));
            c.setSortOrder(i);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            resumeCampusMapper.insert(c);
        }
    }

    /**
     * 技能：技能名称为必填语义。
     */
    private void insertSkills(long resumeId, List<ResumeSkillDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumeSkillDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getSkillName())) {
                continue;
            }
            ResumeSkill s = new ResumeSkill();
            BeanUtils.copyProperties(x, s);
            s.setResumeId(resumeId);
            s.setSkillName(x.getSkillName().trim());
            s.setSortOrder(i);
            s.setCreatedAt(now);
            s.setUpdatedAt(now);
            resumeSkillMapper.insert(s);
        }
    }

    /**
     * 语言：语种名称为必填语义。
     */
    private void insertLanguages(long resumeId, List<ResumeLanguageDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumeLanguageDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getLanguageName())) {
                continue;
            }
            ResumeLanguage l = new ResumeLanguage();
            BeanUtils.copyProperties(x, l);
            l.setResumeId(resumeId);
            l.setLanguageName(x.getLanguageName().trim());
            l.setSortOrder(i);
            l.setCreatedAt(now);
            l.setUpdatedAt(now);
            resumeLanguageMapper.insert(l);
        }
    }

    /**
     * 证书：证书名称为必填语义。
     */
    private void insertCertificates(long resumeId, List<ResumeCertificateDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumeCertificateDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getCertName())) {
                continue;
            }
            ResumeCertificate c = new ResumeCertificate();
            BeanUtils.copyProperties(x, c, "obtainDate");
            c.setResumeId(resumeId);
            c.setCertName(x.getCertName().trim());
            c.setObtainDate(parseLocalDate(x.getObtainDate()));
            c.setSortOrder(i);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            resumeCertificateMapper.insert(c);
        }
    }

    /**
     * 获奖：奖项名称为必填语义。
     */
    private void insertAwards(long resumeId, List<ResumeAwardDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumeAwardDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getAwardName())) {
                continue;
            }
            ResumeAward a = new ResumeAward();
            BeanUtils.copyProperties(x, a, "awardDate");
            a.setResumeId(resumeId);
            a.setAwardName(x.getAwardName().trim());
            a.setAwardDate(parseLocalDate(x.getAwardDate()));
            a.setSortOrder(i);
            a.setCreatedAt(now);
            a.setUpdatedAt(now);
            resumeAwardMapper.insert(a);
        }
    }

    /**
     * 论文/专利等：标题必填；{@code pub_type} 空时写入 {@code other} 以满足库表非空约束。
     */
    private void insertPublications(long resumeId, List<ResumePublicationDraftLine> lines, LocalDateTime now) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            ResumePublicationDraftLine x = lines.get(i);
            if (x == null || !StringUtils.hasText(x.getTitle())) {
                continue;
            }
            ResumePublication p = new ResumePublication();
            BeanUtils.copyProperties(x, p, "publishDate", "pubType");
            p.setResumeId(resumeId);
            p.setPubType(StringUtils.hasText(x.getPubType()) ? x.getPubType().trim() : "other");
            p.setTitle(x.getTitle().trim());
            p.setPublishDate(parseLocalDate(x.getPublishDate()));
            p.setSortOrder(i);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            resumePublicationMapper.insert(p);
        }
    }

    /**
     * 草稿日期多为 ISO 日期字符串；解析失败返回 null，由数据库接受 NULL 的列自然落空。
     */
    private static LocalDate parseLocalDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 主表出生日期仍为历史 {@link Date} 类型，由 {@link LocalDate} 转系统默认时区当日零点。
     */
    private static Date toUtilDate(String s) {
        LocalDate ld = parseLocalDate(s);
        if (ld == null) {
            return null;
        }
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
