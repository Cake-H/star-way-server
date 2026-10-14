package com.smartapply.ida.common.domain.resume.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResumeParseTaskStatusVO {
    private String taskId;
    private long createdAt;
    private long updatedAt;

    private String status; // RUNNING / SUCCESS /FAILED
    private ResumeParseResultVO result;
    private String errorMessage;
}
