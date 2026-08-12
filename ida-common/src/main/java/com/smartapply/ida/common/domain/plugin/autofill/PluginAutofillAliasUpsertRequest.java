package com.smartapply.ida.common.domain.plugin.autofill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台新增动态别名请求。status 缺省为 approved（登记即生效），也可显式指定 pending。
 */
@Data
public class PluginAutofillAliasUpsertRequest {

    @NotBlank(message = "aliasLabel 不能为空")
    @Size(max = 128, message = "aliasLabel 最长 128 字符")
    private String aliasLabel;

    @NotBlank(message = "fieldPath 不能为空")
    @Size(max = 128, message = "fieldPath 最长 128 字符")
    private String fieldPath;

    /** feedback / manual，缺省 manual */
    private String source;

    /** pending / approved / rejected，缺省 approved */
    private String status;

    @Size(max = 255, message = "remark 最长 255 字符")
    private String remark;
}
