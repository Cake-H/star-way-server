package com.smartapply.ida.common.domain.mockInterview.dto;

import lombok.Data;

@Data
public class MockInterviewUpdateProgressDTO {
    private Long currentQuestionId;
    private Boolean answerRevealed;
}
