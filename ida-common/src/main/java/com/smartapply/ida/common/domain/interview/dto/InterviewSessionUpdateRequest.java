package com.smartapply.ida.common.domain.interview.dto;

import lombok.Data;

/** {@code PUT /interview/sessions/{sessionId}}：仅更新传入的非 null 字段。 */
@Data
public class InterviewSessionUpdateRequest {
    private String title;
    private String companyName;
    private String positionName;
    private String interviewRound;
    private String interviewAt;
}
