package com.smartapply.ida.ai.interview;

import com.smartapply.ida.ai.langchain4j.assistant.InterviewSummarizeAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.InterviewSummarizePromptProvider;
import com.smartapply.ida.common.domain.interview.InterviewSession;
import com.smartapply.ida.common.domain.interview.vo.InterviewQuestionDraftVO;
import com.smartapply.ida.common.domain.interview.vo.InterviewSessionMetaDraftVO;
import com.smartapply.ida.common.domain.interview.vo.InterviewVoiceSummarizeResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 面试复盘总结服务（基于 LangChain4j）。
 * <p>
 * 调用 AI Service 将口述纯文本结构化为 {@link InterviewVoiceSummarizeResultVO}。
 * <b>注意：</b>本类只处理文字，不处理音频；听写由 {@link com.smartapply.ida.ai.speech.client.WhisperTranscriptionClient} 完成。
 * <p>
 * 改造要点：移除手写 JSON 解析、Markdown 清洗、异常定位等样板代码，
 * 由 LangChain4j AI Service 直接返回结构化对象。业务逻辑完全保留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSummarizeAiService {

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    /** 单字段写入库前的保护上限，避免异常长文本 */
    private static final int MAX_FIELD_CHARS = 8000;

    private final InterviewSummarizeAssistant assistant;
    private final InterviewSummarizePromptProvider promptProvider;

    /**
     * @param session    当前复盘场次（用于 User 消息中的已保存元信息对照）
     * @param transcript 已听写/转写完成的口述全文
     */
    public InterviewVoiceSummarizeResultVO summarize(InterviewSession session, String transcript) {
        if (!StringUtils.hasText(transcript)) {
            throw new BusinessException("无法识别语音，请重录或改用文字");
        }

        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String userMessage = promptProvider.buildUserMessage(
                today,
                session.getCompanyName(),
                session.getPositionName(),
                session.getInterviewRound(),
                session.getInterviewAt() != null ? session.getInterviewAt().toString() : "",
                transcript.trim());

        log.info("面试总结开始 sessionId={}", session.getId());

        // 调用 LangChain4j AI Service，直接返回强类型对象
        InterviewVoiceSummarizeResultVO result;
        try {
            result = assistant.summarize(InterviewSummarizePromptProvider.SYSTEM, userMessage);
        } catch (Exception e) {
            log.error("面试总结调用大模型失败 sessionId={}", session.getId(), e);
            throw new BusinessException("调用大模型失败：" + e.getMessage());
        }

        if (result == null) {
            throw new BusinessException("模型未返回有效结果");
        }

        log.info("面试总结完成 sessionId={} questionCount={}", session.getId(),
                result.getQuestions() != null ? result.getQuestions().size() : 0);

        // 后处理：校验日期、补全 suggestedTitle、过滤空问题等（保留原有逻辑）
        return sanitizeResult(result);
    }

    /** 校验日期格式、补全 suggestedTitle、过滤空 question 行。 */
    private InterviewVoiceSummarizeResultVO sanitizeResult(InterviewVoiceSummarizeResultVO result) {
        InterviewSessionMetaDraftVO meta = parseMeta(result.getExtractedSession());
        List<InterviewQuestionDraftVO> questions = parseQuestions(result);

        String hint = result.getHint();
        if (!StringUtils.hasText(hint) && questions.isEmpty()) {
            hint = "未能从口述中识别出面试问答，请补充更多细节后重试";
        }

        InterviewVoiceSummarizeResultVO out = InterviewVoiceSummarizeResultVO.builder()
                .hint(StringUtils.hasText(hint) ? hint.trim() : null)
                .extractedSession(meta)
                .questions(questions)
                .build();
        syncLegacyTopLevelFields(out);
        return out;
    }

    private InterviewSessionMetaDraftVO parseMeta(InterviewSessionMetaDraftVO meta) {
        if (meta == null) {
            meta = emptyMeta();
        }
        meta.setCompanyName(trimOrEmpty(meta.getCompanyName()));
        meta.setPositionName(trimOrEmpty(meta.getPositionName()));
        meta.setInterviewRound(trimOrEmpty(meta.getInterviewRound()));
        String at = trimOrEmpty(meta.getInterviewAt());
        if (StringUtils.hasText(at) && !DATE_PATTERN.matcher(at).matches()) {
            at = "";
        }
        meta.setInterviewAt(at);
        if (!StringUtils.hasText(meta.getSuggestedTitle())) {
            meta.setSuggestedTitle(buildSuggestedTitle(meta));
        } else {
            meta.setSuggestedTitle(meta.getSuggestedTitle().trim());
        }
        return meta;
    }

    private List<InterviewQuestionDraftVO> parseQuestions(InterviewVoiceSummarizeResultVO result) {
        List<InterviewQuestionDraftVO> questions = result.getQuestions();
        if (questions == null || questions.isEmpty()) {
            // 兼容单题格式
            if (StringUtils.hasText(result.getQuestion())) {
                InterviewQuestionDraftVO single = new InterviewQuestionDraftVO();
                single.setQuestion(result.getQuestion());
                single.setMyAnswer(result.getMyAnswer());
                single.setAnswerEvaluation(result.getAnswerEvaluation());
                single.setReferenceAnswer(result.getReferenceAnswer());
                single.setOtherNotes(result.getOtherNotes());
                return List.of(sanitizeDraft(single));
            }
            return List.of();
        }
        List<InterviewQuestionDraftVO> list = new ArrayList<>();
        for (InterviewQuestionDraftVO d : questions) {
            if (d != null && StringUtils.hasText(d.getQuestion())) {
                list.add(sanitizeDraft(d));
            }
        }
        return list;
    }

    private InterviewQuestionDraftVO sanitizeDraft(InterviewQuestionDraftVO d) {
        d.setQuestion(truncate(d.getQuestion()));
        d.setMyAnswer(truncate(d.getMyAnswer()));
        d.setAnswerEvaluation(truncate(d.getAnswerEvaluation()));
        d.setReferenceAnswer(truncate(d.getReferenceAnswer()));
        d.setOtherNotes(truncate(d.getOtherNotes()));
        return d;
    }

    /** 将 {@code questions[0]} 同步到顶层五字段，兼容只读单题的前端。 */
    private static void syncLegacyTopLevelFields(InterviewVoiceSummarizeResultVO out) {
        if (out.getQuestions() == null || out.getQuestions().isEmpty()) {
            return;
        }
        InterviewQuestionDraftVO first = out.getQuestions().get(0);
        out.setQuestion(first.getQuestion());
        out.setMyAnswer(first.getMyAnswer());
        out.setAnswerEvaluation(first.getAnswerEvaluation());
        out.setReferenceAnswer(first.getReferenceAnswer());
        out.setOtherNotes(first.getOtherNotes());
    }

    private static InterviewSessionMetaDraftVO emptyMeta() {
        return InterviewSessionMetaDraftVO.builder()
                .companyName("")
                .positionName("")
                .interviewRound("")
                .interviewAt("")
                .suggestedTitle("")
                .build();
    }

    private static String buildSuggestedTitle(InterviewSessionMetaDraftVO meta) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(meta.getCompanyName())) {
            parts.add(meta.getCompanyName().trim());
        }
        if (StringUtils.hasText(meta.getPositionName())) {
            parts.add(meta.getPositionName().trim());
        }
        if (StringUtils.hasText(meta.getInterviewRound())) {
            parts.add(meta.getInterviewRound().trim());
        }
        return String.join(" · ", parts);
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= MAX_FIELD_CHARS ? t : t.substring(0, MAX_FIELD_CHARS);
    }
}
