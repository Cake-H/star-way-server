package com.smartapply.ida.common.domain.feedback.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 管理后台用户反馈出参（列表与详情共用）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFeedbackVO {
    private Long id;
    private Long userId;
    private String source;
    private String contactEmail;
    private String content;
    /** 附件（objectKey 已换成预签名 URL） */
    private List<AppFeedbackAttachmentVO> attachments;
    private String status;
    private String statusLabel;
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
