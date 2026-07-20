package com.smartapply.ida.module.app.mockInterview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.mockInterview.MockInterviewGenerateAiService;
import com.smartapply.ida.common.domain.mockInterview.MockInterviewQuestion;
import com.smartapply.ida.common.domain.mockInterview.MockInterviewSession;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.common.domain.mockInterview.dto.MockInterviewCreateDTO;
import com.smartapply.ida.common.domain.mockInterview.dto.MockInterviewUpdateProgressDTO;
import com.smartapply.ida.common.domain.mockInterview.vo.*;
import com.smartapply.ida.common.domain.resume.Resume;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.module.app.mockInterview.mapper.MockInterviewQuestionMapper;
import com.smartapply.ida.module.app.mockInterview.mapper.MockInterviewSessionMapper;
import com.smartapply.ida.module.app.mockInterview.service.MockInterviewService;
import com.smartapply.ida.module.app.resume.mapper.ResumeMapper;
import com.smartapply.ida.module.app.resume.service.AppResumeQueryService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class MockInterviewServiceImpl implements MockInterviewService {

    private final MockInterviewSessionMapper mockInterviewSessionMapper;
    private final MockInterviewQuestionMapper mockInterviewQuestionMapper;
    private final ResumeMapper resumeMapper;
    private final AppResumeQueryService appResumeQueryService;
    private final MockInterviewGenerateAiService mockInterviewGenerateAiService;
    private final ObjectMapper objectMapper;

    @Override
    public List<MockInterviewListItemVO> getMockInterviewList(long userId) {
        List<MockInterviewSession> mockInterviewSessions = mockInterviewSessionMapper.selectList(new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getUserId, userId)
                .orderByDesc(MockInterviewSession::getCreatedAt));

        return mockInterviewSessions.stream().map(
                session -> MockInterviewListItemVO.builder()
                        .mockInterviewId(session.getId())
                        .resumeId(session.getResumeId())
                        .title(session.getTitle())
                        .questionCount(session.getQuestionCount())
                        .status(session.getStatus())
                        .updatedAt(session.getUpdatedAt())
                        .build()
        ).toList();
    }

    @Override
    public MockInterviewDetailVO getMockInterviewDetail(Long userId, Long sessionId) {
        MockInterviewSession session = mockInterviewSessionMapper.selectOne(new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getId, sessionId)
                .eq(MockInterviewSession::getUserId, userId));
        if (session == null) {
            throw new BusinessException("模拟面试不存在或无权访问");
        }

        MockInterviewDetailVO mockInterviewDetailVO = new MockInterviewDetailVO();
        mockInterviewDetailVO.setMockInterviewId(session.getId());
        mockInterviewDetailVO.setResumeId(session.getResumeId());
        mockInterviewDetailVO.setTitle(session.getTitle());
        mockInterviewDetailVO.setQuestionCount(session.getQuestionCount());
        mockInterviewDetailVO.setStatus(session.getStatus());
        mockInterviewDetailVO.setUpdatedAt(LocalDateTime.now());

        MockInterviewProgressVO mockInterviewProgressVO = new MockInterviewProgressVO();
        mockInterviewProgressVO.setCurrentQuestionId(session.getCurrentQuestionId());
        mockInterviewProgressVO.setAnswerRevealed(session.getAnswerRevealed());

        mockInterviewDetailVO.setMockInterviewProgress(mockInterviewProgressVO);

        List<MockInterviewQuestion> questions = mockInterviewQuestionMapper.selectList(new LambdaQueryWrapper<MockInterviewQuestion>()
                .eq(MockInterviewQuestion::getSessionId, sessionId)
                .orderByAsc(MockInterviewQuestion::getSortOrder));

        List<MockInterviewQuestionVO> questionVOS = new ArrayList<>();
        questions.forEach(qs -> {
            MockInterviewQuestionVO questionVO = new MockInterviewQuestionVO();
            BeanUtils.copyProperties(qs, questionVO);
            questionVOS.add(questionVO);
        });

        mockInterviewDetailVO.setMockInterviewQuestions(questionVOS);

        return mockInterviewDetailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMockInterview(Long userId, MockInterviewCreateDTO dto) {
        if (dto.getResumeId() == null) {
            throw new BusinessException("resumeId不能为空");
        }
        // 校验resumeId是否是当前用户的
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, dto.getResumeId())
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException("简历不存在或无权访问");
        }

        // 拉取完整简历
        ResumeDetailVO resumeDetail = appResumeQueryService.getResumeDetail(userId, dto.getResumeId());

        // 把简历草稿序列化为文本上下文
        String resumeContext = serializeResumeDraft(resumeDetail);

        // 调用LLM生成题目（AI Service 层已内置额度守卫 + 用量计量）
        MockInterviewGenerateResultVO generated = mockInterviewGenerateAiService.generateQuestions(userId, resumeContext);
        List<MockInterviewGenerateResultVO.MockInterviewQuestionDraftVO> drafts =
                generated.getQuestions() == null ? new ArrayList<>() : generated.getQuestions();

        // 模拟面试场次入库
        LocalDateTime now = LocalDateTime.now();
        MockInterviewSession session = new MockInterviewSession();
        session.setUserId(userId);
        session.setResumeId(dto.getResumeId());
        session.setTitle(resolveTitle(dto, resumeDetail));
        session.setStatus("GENERATED");
        session.setQuestionCount(drafts.size());
        session.setCurrentQuestionId(null);
        session.setAnswerRevealed(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        int inserted = mockInterviewSessionMapper.insert(session);
        if (inserted <= 0) {
            throw new BusinessException("创建模拟面试失败");
        }

        // 题目入库
        for (int i = 0; i < drafts.size(); i++) {
            MockInterviewGenerateResultVO.MockInterviewQuestionDraftVO d = drafts.get(i);
            MockInterviewQuestion question = new MockInterviewQuestion();
            question.setSessionId(session.getId());
            question.setQuestion(d.getQuestion());
            question.setReferenceAnswer(d.getReferenceAnswer());
            question.setAnswerEvaluation(d.getAnswerEvaluation());
            question.setOtherNotes(d.getOtherNotes());
            question.setSortOrder(i + 1);
            question.setCreatedAt(now);
            question.setUpdatedAt(now);
            int qInserted = mockInterviewQuestionMapper.insert(question);
            if (qInserted <= 0) {
                throw new BusinessException("模拟面试问题入库失败");
            }
        }
        return session.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMockInterview(Long userId, Long sessionId) {
        MockInterviewSession session = mockInterviewSessionMapper.selectOne(
                new LambdaQueryWrapper<MockInterviewSession>()
                        .eq(MockInterviewSession::getId, sessionId)
                        .eq(MockInterviewSession::getUserId, userId));
        if (session == null) {
            throw new BusinessException("模拟面试不存在或无权访问");
        }

        int deleted = mockInterviewSessionMapper.delete(new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getId, sessionId)
                .eq(MockInterviewSession::getUserId, userId));
        if (deleted <= 0) {
            throw new BusinessException("删除模拟面试失败");
        }
        // 级联软删题目（本场面试可能没有题目，不强制要求删除行数 > 0）
        mockInterviewQuestionMapper.delete(
                new LambdaQueryWrapper<MockInterviewQuestion>()
                        .eq(MockInterviewQuestion::getSessionId, sessionId));
    }

    @Override
    public void updateMockInterviewStatus(Long userId, Long sessionId, String status) {
        MockInterviewSession session = mockInterviewSessionMapper.selectOne(new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getId, sessionId)
                .eq(MockInterviewSession::getUserId, userId));
        if (session == null) {
            throw new BusinessException("模拟面试不存在或无权访问");
        }

        if (!status.equals("GENERATED") && !status.equals("PRACTICING") && !status.equals("COMPLETED")) {
            throw new BusinessException("无效的模拟面试状态");
        }

        session.setStatus(status);
        session.setUpdatedAt(LocalDateTime.now());

        int updated = mockInterviewSessionMapper.updateById(session);

        if (updated <= 0) {
            throw new BusinessException("更新模拟面试状态失败");
        }
    }

    @Override
    public void saveMockInterviewProgress(Long userId, Long sessionId, MockInterviewUpdateProgressDTO progressDto) {
        MockInterviewSession session = mockInterviewSessionMapper.selectOne(new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getId, sessionId)
                .eq(MockInterviewSession::getUserId, userId));
        if (session == null) {
            throw new BusinessException("模拟面试不存在或无权访问");
        }

        MockInterviewQuestion question = mockInterviewQuestionMapper.selectOne(
                new LambdaQueryWrapper<MockInterviewQuestion>()
                        .eq(MockInterviewQuestion::getId, progressDto.getCurrentQuestionId())
                        .eq(MockInterviewQuestion::getSessionId, sessionId));
        if (question == null) {
            throw new BusinessException("当前题目不存在或不属于本场模拟面试");
        }

        session.setCurrentQuestionId(progressDto.getCurrentQuestionId());
        session.setAnswerRevealed(progressDto.getAnswerRevealed() != null && progressDto.getAnswerRevealed() ? 1 : 0);
        session.setUpdatedAt(LocalDateTime.now());

        int updated = mockInterviewSessionMapper.updateById(session);
        if (updated <= 0) {
            throw new BusinessException("保存模拟面试进度失败");
        }
    }

    /**
     * 把简历详情中的结构化草稿序列化为 JSON，作为 LLM 的输入上下文。
     * 序列化失败时降级为空对象，避免整单失败。
     */
    private String serializeResumeDraft(ResumeDetailVO detail) {
        try {
            ResumeParseDraftDto draft = detail.getDraft();
            if (draft == null) {
                log.warn("简历草稿为空");
                return "{}";
            }
            return objectMapper.writeValueAsString(draft);
        } catch (Exception e) {
            log.warn("简历草稿序列化失败，将使用空上下文", e);
            return "{}";
        }
    }

    private String resolveTitle(MockInterviewCreateDTO dto, ResumeDetailVO detailVO) {
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            return dto.getTitle();
        }
        ResumeParseDraftDto draft = detailVO.getDraft();

        if (draft != null && draft.getExpectedPositions() != null && !draft.getExpectedPositions().isBlank()) {
            return draft.getExpectedPositions().trim() + " · 模拟面试";
        }

        if (draft != null && draft.getFullName() != null && !draft.getFullName().isBlank()) {
            return draft.getFullName().trim() + " · 模拟面试";
        }
        return "模拟面试";
    }

}
