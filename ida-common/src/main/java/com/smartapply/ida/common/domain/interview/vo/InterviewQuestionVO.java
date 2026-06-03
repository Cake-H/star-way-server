package com.smartapply.ida.common.domain.interview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestionVO {
    private Long questionId;
    private Long sessionId;
    private Integer sortOrder;
    private String question;
    private String myAnswer;
    private String answerEvaluation;
    private String referenceAnswer;
    private String otherNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
