package com.smartapply.ida.common.domain.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户兑换额度请求。
 */
@Data
public class RedeemCodeRequest {

    /** 兑换码 */
    @NotBlank(message = "兑换码不能为空")
    @Size(min = 4, max = 64, message = "兑换码长度必须在 4-64 位之间")
    private String code;
}
