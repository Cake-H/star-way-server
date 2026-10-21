package com.smartapply.ida.common.domain.feedback.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 反馈附件的对外展示结构：objectKey 已由应用层换成预签名 URL。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppFeedbackAttachmentVO {
    /** 预签名下载 URL（OSS 不可用时为空串） */
    private String url;
    private String fileName;
    private String mimeType;
    private Long size;
    /** IMAGE / VIDEO，见 {@code AppFeedbackMediaType} */
    private String mediaType;
}
