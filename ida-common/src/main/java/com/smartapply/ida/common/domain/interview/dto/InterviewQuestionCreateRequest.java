package com.smartapply.ida.common.domain.interview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** {@code POST .../questions}：{@code sortOrder} 未传时服务端追加到末尾。 */
@Data
public class InterviewQuestionCreateRequest {
    @NotBlank(message = "面试问题不能为空")
    private String question;
    private String myAnswer;
    private String answerEvaluation;
    private String referenceAnswer;
    private String otherNotes;
    private Integer sortOrder;
}
