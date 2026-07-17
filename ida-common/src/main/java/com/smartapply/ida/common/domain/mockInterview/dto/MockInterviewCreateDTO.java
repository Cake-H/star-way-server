package com.smartapply.ida.common.domain.mockInterview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MockInterviewCreateDTO {
    @NotNull(message = "简历 ID 不能为空")
    private Long resumeId;
    private String title;
}
