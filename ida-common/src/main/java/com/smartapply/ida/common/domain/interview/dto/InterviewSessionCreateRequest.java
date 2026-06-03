package com.smartapply.ida.common.domain.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** {@code POST /interview/sessions} 请求体。 */
@Data
public class InterviewSessionCreateRequest {
    @NotBlank(message = "复盘标题不能为空")
    @Size(max = 80, message = "复盘标题不能超过 80 字")
    private String title;
    private String companyName;
    private String positionName;
    private String interviewRound;
    /** YYYY-MM-DD */
    private String interviewAt;
}
