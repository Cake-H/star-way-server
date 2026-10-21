package com.smartapply.ida.common.domain.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code ida_app_feedback.attachments} JSON 数组元素：记录 OSS 对象键与文件元信息，
 * 对象键不直接暴露给前端，读取时由应用层换成预签名 URL（见 {@code vo.AppFeedbackAttachmentVO}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppFeedbackAttachment {
    /** OSS 对象键（如 app/feedback/{userId}/{uuid}.png） */
    private String objectKey;
    private String fileName;
    private String mimeType;
    private Long size;
    /** IMAGE / VIDEO，见 {@code AppFeedbackMediaType} */
    private String mediaType;
}
