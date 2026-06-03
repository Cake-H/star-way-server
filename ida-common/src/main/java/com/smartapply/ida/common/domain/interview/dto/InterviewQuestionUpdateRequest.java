package com.smartapply.ida.common.domain.interview.dto;

import lombok.Data;

@Data
public class InterviewQuestionUpdateRequest {
    private String question;
    private String myAnswer;
    private String answerEvaluation;
    private String referenceAnswer;
    private String otherNotes;
    private Integer sortOrder;
}
