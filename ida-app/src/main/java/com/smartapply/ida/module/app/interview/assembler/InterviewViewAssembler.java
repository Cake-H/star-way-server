package com.smartapply.ida.module.app.interview.assembler;

import com.smartapply.ida.common.domain.interview.InterviewQuestion;
import com.smartapply.ida.common.domain.interview.InterviewSession;
import com.smartapply.ida.common.domain.interview.vo.InterviewQuestionVO;
import com.smartapply.ida.common.domain.interview.vo.InterviewSessionDetailVO;
import com.smartapply.ida.common.domain.interview.vo.InterviewSessionListItemVO;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 面试复盘 Entity → 对外 VO：库表 {@code id} 映射为 {@code sessionId}/{@code questionId}，日期格式化为字符串。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InterviewViewAssembler {

    public static InterviewSessionListItemVO toListItem(InterviewSession s) {
        return InterviewSessionListItemVO.builder()
                .sessionId(s.getId())
                .title(s.getTitle())
                .companyName(s.getCompanyName())
                .positionName(s.getPositionName())
                .interviewRound(s.getInterviewRound())
                .interviewAt(fmtDate(s.getInterviewAt()))
                .questionCount(s.getQuestionCount() != null ? s.getQuestionCount() : 0)
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    public static InterviewSessionDetailVO toDetail(InterviewSession s, List<InterviewQuestion> questions) {
        return InterviewSessionDetailVO.builder()
                .sessionId(s.getId())
                .title(s.getTitle())
                .companyName(s.getCompanyName())
                .positionName(s.getPositionName())
                .interviewRound(s.getInterviewRound())
                .interviewAt(fmtDate(s.getInterviewAt()))
                .questionCount(s.getQuestionCount() != null ? s.getQuestionCount() : 0)
                .updatedAt(s.getUpdatedAt())
                .questions(questions.stream().map(InterviewViewAssembler::toQuestionVo).toList())
                .build();
    }

    public static InterviewQuestionVO toQuestionVo(InterviewQuestion q) {
        return InterviewQuestionVO.builder()
                .questionId(q.getId())
                .sessionId(q.getSessionId())
                .sortOrder(q.getSortOrder())
                .question(q.getQuestion())
                .myAnswer(q.getMyAnswer())
                .answerEvaluation(q.getAnswerEvaluation())
                .referenceAnswer(q.getReferenceAnswer())
                .otherNotes(q.getOtherNotes())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }

    /** 请求体中的 {@code interviewAt}（YYYY-MM-DD）→ {@link LocalDate}，空串返回 null。 */
    public static LocalDate parseInterviewAt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return LocalDate.parse(s.trim());
    }

    private static String fmtDate(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
