package com.smartapply.ida.module.app.interview.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartapply.ida.ai.interview.InterviewSummarizeAiService;
import com.smartapply.ida.ai.speech.client.SpeechTranscriptionClient;
import com.smartapply.ida.ai.speech.config.SpeechToTextProperties;
import com.smartapply.ida.common.domain.interview.InterviewQuestion;
import com.smartapply.ida.common.domain.interview.InterviewSession;
import com.smartapply.ida.common.domain.interview.dto.*;
import com.smartapply.ida.common.domain.interview.vo.*;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.interview.assembler.InterviewViewAssembler;
import com.smartapply.ida.module.app.interview.mapper.InterviewQuestionMapper;
import com.smartapply.ida.module.app.interview.mapper.InterviewSessionMapper;
import com.smartapply.ida.module.app.interview.service.AppInterviewService;
import com.smartapply.ida.module.app.interview.support.InterviewSessionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 面试复盘落库与 AI 总结编排。
 * <p>
 * <b>数据策略：</b>场次/问题均通过 {@link com.baomidou.mybatisplus.annotation.TableLogic} 软删；列表展示 {@code question_count} 冗余字段，在增删问题时重算。
 * <b>语音链路：</b>听写（阿里云实时识别 / Whisper / 前端 {@code rawTranscript}）→ {@link InterviewSummarizeAiService} 结构化；
 * 不在本类内自动更新场次元信息，由前端核对后 {@code PUT /sessions/{id}}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppInterviewServiceImpl implements AppInterviewService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewSessionGuard sessionGuard;
    private final InterviewSummarizeAiService interviewSummarizeAiService;
    private final ObjectProvider<SpeechTranscriptionClient> speechTranscriptionClientProvider;
    private final SpeechToTextProperties speechToTextProperties;

    @Override
    public List<InterviewSessionListItemVO> listSessions(long userId) {
        List<InterviewSession> rows = interviewSessionMapper.selectList(Wrappers.<InterviewSession>lambdaQuery()
                .eq(InterviewSession::getUserId, userId)
                .orderByDesc(InterviewSession::getCreatedAt));
        return rows.stream().map(InterviewViewAssembler::toListItem).toList();
    }

    @Override
    public InterviewSessionDetailVO getSessionDetail(long userId, long sessionId) {
        InterviewSession s = sessionGuard.requireOwned(userId, sessionId);
        List<InterviewQuestion> questions = interviewQuestionMapper.selectList(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .orderByAsc(InterviewQuestion::getSortOrder)
                .orderByDesc(InterviewQuestion::getCreatedAt));
        return InterviewViewAssembler.toDetail(s, questions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionIdVO createSession(long userId, InterviewSessionCreateRequest req) {
        LocalDateTime now = LocalDateTime.now();
        InterviewSession s = new InterviewSession();
        s.setUserId(userId);
        s.setTitle(req.getTitle().trim());
        s.setCompanyName(trimNullable(req.getCompanyName()));
        s.setPositionName(trimNullable(req.getPositionName()));
        s.setInterviewRound(trimNullable(req.getInterviewRound()));
        s.setInterviewAt(parseDate(req.getInterviewAt()));
        s.setQuestionCount(0);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        interviewSessionMapper.insert(s);
        if (s.getId() == null) {
            throw new BusinessException("创建面试复盘失败");
        }
        return InterviewSessionIdVO.builder().sessionId(s.getId()).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSession(long userId, long sessionId, InterviewSessionUpdateRequest req) {
        InterviewSession s = sessionGuard.requireOwned(userId, sessionId);
        if (req.getTitle() != null && StringUtils.hasText(req.getTitle())) {
            s.setTitle(req.getTitle().trim());
        }
        if (req.getCompanyName() != null) {
            s.setCompanyName(trimNullable(req.getCompanyName()));
        }
        if (req.getPositionName() != null) {
            s.setPositionName(trimNullable(req.getPositionName()));
        }
        if (req.getInterviewRound() != null) {
            s.setInterviewRound(trimNullable(req.getInterviewRound()));
        }
        if (req.getInterviewAt() != null) {
            s.setInterviewAt(parseDate(req.getInterviewAt()));
        }
        s.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(s);
    }

    /** 软删场次并级联软删其下所有问题（物理行保留）。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(long userId, long sessionId) {
        sessionGuard.requireOwned(userId, sessionId);

        interviewSessionMapper.deleteById(sessionId);
        interviewQuestionMapper.delete(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getSessionId, sessionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterviewQuestionIdVO createQuestion(long userId, long sessionId, InterviewQuestionCreateRequest req) {
        sessionGuard.requireOwned(userId, sessionId);
        LocalDateTime now = LocalDateTime.now();
        int sort = req.getSortOrder() != null ? req.getSortOrder() : nextSortOrder(sessionId);

        InterviewQuestion q = new InterviewQuestion();
        q.setSessionId(sessionId);
        q.setSortOrder(sort);
        q.setQuestion(req.getQuestion().trim());
        q.setMyAnswer(trimNullable(req.getMyAnswer()));
        q.setAnswerEvaluation(trimNullable(req.getAnswerEvaluation()));
        q.setReferenceAnswer(trimNullable(req.getReferenceAnswer()));
        q.setOtherNotes(trimNullable(req.getOtherNotes()));
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        interviewQuestionMapper.insert(q);
        refreshQuestionCount(sessionId);
        touchSession(sessionId);
        return InterviewQuestionIdVO.builder().questionId(q.getId()).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestion(long userId, long sessionId, long questionId, InterviewQuestionUpdateRequest req) {
        sessionGuard.requireOwned(userId, sessionId);
        InterviewQuestion q = requireQuestion(sessionId, questionId);
        if (req.getQuestion() != null && StringUtils.hasText(req.getQuestion())) {
            q.setQuestion(req.getQuestion().trim());
        }
        if (req.getMyAnswer() != null) {
            q.setMyAnswer(trimNullable(req.getMyAnswer()));
        }
        if (req.getAnswerEvaluation() != null) {
            q.setAnswerEvaluation(trimNullable(req.getAnswerEvaluation()));
        }
        if (req.getReferenceAnswer() != null) {
            q.setReferenceAnswer(trimNullable(req.getReferenceAnswer()));
        }
        if (req.getOtherNotes() != null) {
            q.setOtherNotes(trimNullable(req.getOtherNotes()));
        }
        if (req.getSortOrder() != null) {
            q.setSortOrder(req.getSortOrder());
        }
        q.setUpdatedAt(LocalDateTime.now());
        interviewQuestionMapper.updateById(q);
        touchSession(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(long userId, long sessionId, long questionId) {
        sessionGuard.requireOwned(userId, sessionId);
        requireQuestion(sessionId, questionId);

        interviewQuestionMapper.deleteById(questionId);
        refreshQuestionCount(sessionId);
        touchSession(sessionId);
    }

    @Override
    public InterviewVoiceSummarizeResultVO voiceSummarize(long userId, long sessionId, MultipartFile file, String rawTranscript) {
        InterviewSession session = sessionGuard.requireOwned(userId, sessionId);
        String transcript = resolveTranscript(file, rawTranscript);
        log.info("面试语音转写全文 sessionId={} len={} text={}", sessionId, transcript.length(), transcript);
        InterviewVoiceSummarizeResultVO result = interviewSummarizeAiService.summarize(session, transcript);
        log.info("面试语音总结完成 sessionId={} questionCount={}", sessionId,
                result.getQuestions() != null ? result.getQuestions().size() : 0);
        return result;
    }

    /**
     * 得到供大模型使用的纯文本口述稿：优先浏览器转写，不足阈值再服务端听写（阿里云 / Whisper）。
     */
    private String resolveTranscript(MultipartFile file, String rawTranscript) {
        String trimmed = rawTranscript != null ? rawTranscript.trim() : "";
        if (trimmed.length() >= speechToTextProperties.getMinTranscriptChars()) {
            return trimmed;
        }
        if (file != null && !file.isEmpty()) {
            SpeechTranscriptionClient client = speechTranscriptionClientProvider.getIfAvailable();
            if (client == null) {
                throw new BusinessException("听写服务未启用，请改用文字输入或配置 ida.speech-to-text");
            }
            try {
                String text = client.transcribe(file);
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            } catch (IOException e) {
                log.warn("音频转写 IO 失败", e);
                throw new BusinessException("无法识别语音，请重录或改用文字");
            } catch (Exception e) {
                log.warn("音频转写失败", e);
                throw new BusinessException("无法识别语音，请重录或改用文字");
            }
        }
        if (StringUtils.hasText(trimmed)) {
            return trimmed;
        }
        throw new BusinessException("无法识别语音，请重录或改用文字");
    }

    private InterviewQuestion requireQuestion(long sessionId, long questionId) {
        InterviewQuestion q = interviewQuestionMapper.selectOne(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getId, questionId)
                .eq(InterviewQuestion::getSessionId, sessionId));
        if (q == null) {
            throw new BusinessException("问题不存在或已删除");
        }
        return q;
    }

    private int nextSortOrder(long sessionId) {
        InterviewQuestion last = interviewQuestionMapper.selectOne(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .orderByDesc(InterviewQuestion::getSortOrder)
                .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 1 : last.getSortOrder() + 1;
    }

    private void touchSession(long sessionId) {
        interviewSessionMapper.update(null, Wrappers.<InterviewSession>lambdaUpdate()
                .set(InterviewSession::getUpdatedAt, LocalDateTime.now())
                .eq(InterviewSession::getId, sessionId));
    }

    /** 按未删除问题行数回写 {@code ida_interview_session.question_count}。 */
    private void refreshQuestionCount(long sessionId) {
        Long count = interviewQuestionMapper.selectCount(Wrappers.<InterviewQuestion>lambdaQuery()
                .eq(InterviewQuestion::getSessionId, sessionId));
        interviewSessionMapper.update(null, Wrappers.<InterviewSession>lambdaUpdate()
                .set(InterviewSession::getQuestionCount, count != null ? count.intValue() : 0)
                .eq(InterviewSession::getId, sessionId));
    }

    private static String trimNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return InterviewViewAssembler.parseInterviewAt(s.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("面试日期格式应为 YYYY-MM-DD");
        }
    }
}
