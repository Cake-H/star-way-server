package com.smartapply.ida.module.app.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.common.domain.interview.InterviewSession;
import com.smartapply.ida.common.domain.mockInterview.MockInterviewSession;
import com.smartapply.ida.common.domain.resume.Resume;
import com.smartapply.ida.common.domain.resume.ResumeAward;
import com.smartapply.ida.common.domain.resume.ResumeCampus;
import com.smartapply.ida.common.domain.resume.ResumeCertificate;
import com.smartapply.ida.common.domain.resume.ResumeEducation;
import com.smartapply.ida.common.domain.resume.ResumeLanguage;
import com.smartapply.ida.common.domain.resume.ResumeProject;
import com.smartapply.ida.common.domain.resume.ResumePublication;
import com.smartapply.ida.common.domain.resume.ResumeSkill;
import com.smartapply.ida.common.domain.resume.ResumeWork;
import com.smartapply.ida.common.domain.user.UserEntity;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.auth.service.LoginSessionService;
import com.smartapply.ida.module.app.interview.mapper.InterviewSessionMapper;
import com.smartapply.ida.module.app.mockInterview.mapper.MockInterviewSessionMapper;
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
import com.smartapply.ida.module.app.user.mapper.UserMapper;
import com.smartapply.ida.module.app.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 账号注销实现（隐私安全基线 1.4 / 合规要求）。
 * <p>
 * 采用「匿名化 + 逻辑删除」而非物理删除用户行：简历等表外键为 {@code ON DELETE RESTRICT}，
 * 且合规口径为"注销后删除或匿名化个人信息"。手机号被不可逆改写后，原号码可重新注册。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 注销确认文案 */
    private static final String CONFIRM_TEXT = "注销";

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeEducationMapper resumeEducationMapper;
    private final ResumeWorkMapper resumeWorkMapper;
    private final ResumeProjectMapper resumeProjectMapper;
    private final ResumeCampusMapper resumeCampusMapper;
    private final ResumeSkillMapper resumeSkillMapper;
    private final ResumeCertificateMapper resumeCertificateMapper;
    private final ResumeAwardMapper resumeAwardMapper;
    private final ResumeLanguageMapper resumeLanguageMapper;
    private final ResumePublicationMapper resumePublicationMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final MockInterviewSessionMapper mockInterviewSessionMapper;
    private final LoginSessionService loginSessionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(long userId, String confirmText, String token) {
        if (!CONFIRM_TEXT.equals(confirmText == null ? null : confirmText.trim())) {
            throw new BusinessException("确认文案不正确，请输入「" + CONFIRM_TEXT + "」");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在或已注销");
        }

        // 1. 匿名化：手机号不可逆改写（释放 uk_ida_user_phone，原号码可重新注册）、清盲索引、禁用
        user.setPhone("del_" + userId + "_" + ThreadLocalRandom.current().nextInt(100000, 999999));
        user.setPhoneTail(null);
        user.setStatus(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 2. 逻辑删除用户（写 deleted_at）
        userMapper.deleteById(userId);

        // 3. 简历：子表物理删除（无软删字段），主表逻辑删除
        List<Long> resumeIds = resumeMapper.selectList(
                        Wrappers.<Resume>lambdaQuery().eq(Resume::getUserId, userId))
                .stream().map(Resume::getId).toList();
        if (!resumeIds.isEmpty()) {
            resumeEducationMapper.delete(Wrappers.<ResumeEducation>lambdaQuery().in(ResumeEducation::getResumeId, resumeIds));
            resumeWorkMapper.delete(Wrappers.<ResumeWork>lambdaQuery().in(ResumeWork::getResumeId, resumeIds));
            resumeProjectMapper.delete(Wrappers.<ResumeProject>lambdaQuery().in(ResumeProject::getResumeId, resumeIds));
            resumeCampusMapper.delete(Wrappers.<ResumeCampus>lambdaQuery().in(ResumeCampus::getResumeId, resumeIds));
            resumeSkillMapper.delete(Wrappers.<ResumeSkill>lambdaQuery().in(ResumeSkill::getResumeId, resumeIds));
            resumeCertificateMapper.delete(Wrappers.<ResumeCertificate>lambdaQuery().in(ResumeCertificate::getResumeId, resumeIds));
            resumeAwardMapper.delete(Wrappers.<ResumeAward>lambdaQuery().in(ResumeAward::getResumeId, resumeIds));
            resumeLanguageMapper.delete(Wrappers.<ResumeLanguage>lambdaQuery().in(ResumeLanguage::getResumeId, resumeIds));
            resumePublicationMapper.delete(Wrappers.<ResumePublication>lambdaQuery().in(ResumePublication::getResumeId, resumeIds));
            resumeMapper.delete(Wrappers.<Resume>lambdaQuery().eq(Resume::getUserId, userId));
        }

        // 4. 面试复盘、模拟面试：逻辑删除
        interviewSessionMapper.delete(Wrappers.<InterviewSession>lambdaQuery().eq(InterviewSession::getUserId, userId));
        mockInterviewSessionMapper.delete(Wrappers.<MockInterviewSession>lambdaQuery().eq(MockInterviewSession::getUserId, userId));

        // 5. 撤销当前 token（插件端 token 相互独立，随 TTL 自然过期）
        if (StringUtils.hasText(token)) {
            loginSessionService.revokeToken(token.trim());
        }

        log.info("账号注销完成 userId={}，已匿名化并清理关联数据（简历数={}）", userId, resumeIds.size());
    }
}
