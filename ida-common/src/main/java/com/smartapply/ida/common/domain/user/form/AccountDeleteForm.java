package com.smartapply.ida.common.domain.user.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号注销确认表单：必须输入固定确认文案，防止误触。
 */
@Data
@Schema(description = "账号注销确认")
public class AccountDeleteForm {

    /** 确认文案，必须等于「注销」 */
    @NotBlank(message = "请输入确认文案")
    @Schema(description = "确认文案，必须输入「注销」", example = "注销")
    private String confirmText;
}
