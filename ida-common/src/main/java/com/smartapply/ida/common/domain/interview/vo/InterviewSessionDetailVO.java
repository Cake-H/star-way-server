package com.smartapply.ida.common.domain.interview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 场次详情：元信息 + 按 {@code sortOrder} 排序的问题列表。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionDetailVO {
    private Long sessionId;
    private String title;
    private String companyName;
    private String positionName;
    private String interviewRound;
    private String interviewAt;
    private Integer questionCount;
    private LocalDateTime updatedAt;
    @Builder.Default
    private List<InterviewQuestionVO> questions = new ArrayList<>();
}
