package com.smartapply.ida.common.domain.mockInterview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class MockInterviewQuestionVO {
    private Long id;
    private Long sessionId;
    private Integer sortOrder;
    private String question;
    private String answerEvaluation;
    private String referenceAnswer;
    private String otherNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
