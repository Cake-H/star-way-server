package com.smartapply.ida.common.domain.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** {@code PUT /admin/feedback/{id}/status} 请求体。 */
@Data
public class AppFeedbackStatusUpdateRequest {
    /** 目标处理状态 code（见 {@code AppFeedbackStatus}） */
    @NotBlank(message = "处理状态不能为空")
    private String status;
    @Size(max = 512, message = "处理备注不能超过 512 字")
    private String adminNote;
}
