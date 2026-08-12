package com.smartapply.ida.common.domain.plugin.autofill;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理后台别名审核请求：仅允许 approved / rejected。
 */
@Data
public class PluginAutofillAliasStatusRequest {

    @NotBlank(message = "status 不能为空")
    private String status;
}
