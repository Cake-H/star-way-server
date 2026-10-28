package com.smartapply.ida.common.domain.interview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InterviewVoiceSummarizeTaskStatusVO {
    private String taskId;
    private long createdAt;
    private long updatedAt;

    private String status; // RUNNING / SUCCESS /FAILED
    private InterviewVoiceSummarizeResultVO result;
    private String errorMessage;
}
