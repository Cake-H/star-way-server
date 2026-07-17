package com.smartapply.ida.common.domain.mockInterview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MockInterviewProgressVO {
    private Long currentQuestionId;
    private Integer answerRevealed;
}
