package com.smartapply.ida.module.app.interview.service;

import com.smartapply.ida.common.domain.interview.dto.*;
import com.smartapply.ida.common.domain.interview.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 面试复盘业务：场次与问题的 CRUD，以及语音/文本 AI 总结（总结结果不写库）。
 */
public interface AppInterviewService {

    List<InterviewSessionListItemVO> listSessions(long userId);

    InterviewSessionDetailVO getSessionDetail(long userId, long sessionId);

    InterviewSessionIdVO createSession(long userId, InterviewSessionCreateRequest req);

    void updateSession(long userId, long sessionId, InterviewSessionUpdateRequest req);

    void deleteSession(long userId, long sessionId);

    InterviewQuestionIdVO createQuestion(long userId, long sessionId, InterviewQuestionCreateRequest req);

    void updateQuestion(long userId, long sessionId, long questionId, InterviewQuestionUpdateRequest req);

    void deleteQuestion(long userId, long sessionId, long questionId);

    /**
     * 将口述转为多题结构化草稿（含从口述抽取的场次元信息）。
     * <p>
     * {@code rawTranscript} 足够长时优先使用（浏览器 Web Speech）；否则对 {@code file} 走 Whisper 听写。
     */
    InterviewVoiceSummarizeResultVO voiceSummarize(long userId, long sessionId, MultipartFile file, String rawTranscript);
}
