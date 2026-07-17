package com.smartapply.ida.common.domain.mockInterview.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MockInterviewListItemVO {
    private Long mockInterviewId;
    //关联简历 ID
    private Long resumeId;
    //标题，如「Java 后端开发 · 模拟面试」
    private String title;
    //题目数量
    private Integer questionCount;
    //状态：GENERATED/PRACTICING/COMPLETED
    private String status;

    private LocalDateTime updatedAt;
}
