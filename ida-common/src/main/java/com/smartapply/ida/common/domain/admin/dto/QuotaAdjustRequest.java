package com.smartapply.ida.common.domain.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员手动调整用户额度请求。
 */
@Data
public class QuotaAdjustRequest {

    /** 调整点数：正数=充值，负数=扣减；不允许为 0 */
    @NotNull(message = "调整点数不能为空")
    @Min(value = -100000, message = "单次调整不能低于 -100000 点")
    @Max(value = 100000, message = "单次调整不能超过 100000 点")
    private Integer points;

    /** 调整原因（必填，写入额度流水便于审计） */
    @NotBlank(message = "调整原因不能为空")
    @Size(max = 200, message = "调整原因不能超过 200 字")
    private String remark;
}
