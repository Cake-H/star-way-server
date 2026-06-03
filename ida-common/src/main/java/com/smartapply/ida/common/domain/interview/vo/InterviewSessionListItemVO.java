package com.smartapply.ida.common.domain.interview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 复盘列表项（不含问题明细）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionListItemVO {
    private Long sessionId;
    private String title;
    private String companyName;
    private String positionName;
    private String interviewRound;
    private String interviewAt;
    private Integer questionCount;
    private LocalDateTime updatedAt;
}
