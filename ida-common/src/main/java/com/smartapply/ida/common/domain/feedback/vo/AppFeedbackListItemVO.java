package com.smartapply.ida.common.domain.feedback.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** APP 端「我的反馈」列表项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppFeedbackListItemVO {
    private Long feedbackId;
    private String content;
    /** 附件（objectKey 已换成预签名 URL） */
    private List<AppFeedbackAttachmentVO> attachments;
    private String status;
    private String statusLabel;
    private String adminNote;
    private LocalDateTime createdAt;
}
