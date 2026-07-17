package com.smartapply.ida.common.domain.mockInterview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewGenerateResultVO {

    @Builder.Default
    private List<MockInterviewQuestionDraftVO> questions = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockInterviewQuestionDraftVO {
        private String question;
        private String referenceAnswer;
        private String answerEvaluation;
        private String otherNotes;
    }
}
