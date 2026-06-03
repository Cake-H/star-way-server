package com.smartapply.ida.common.domain.interview.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 单题五字段草稿，与 {@link com.smartapply.ida.common.domain.interview.dto.InterviewQuestionCreateRequest} 字段一致。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterviewQuestionDraftVO {
    private String question;
    private String myAnswer;
    private String answerEvaluation;
    private String referenceAnswer;
    private String otherNotes;
}
