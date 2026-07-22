package com.smartapply.ida.common.domain.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台批量生成兑换码请求。
 */
@Data
public class GenerateRedeemCodeRequest {

    /** 生成数量（1-1000） */
    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量至少为 1")
    @Max(value = 1000, message = "单次生成不能超过 1000 张")
    private Integer count;

    /** 每张兑换码点数 */
    @NotNull(message = "兑换点数不能为空")
    @Min(value = 1, message = "兑换点数必须大于 0")
    @Max(value = 100000, message = "单张兑换点数不能超过 100000")
    private Integer points;

    /** 过期时间，null 表示永不过期 */
    private LocalDateTime expireTime;

    /** 生成备注（运营批次、用途等） */
    @Size(max = 200, message = "备注不能超过 200 字")
    private String remark;
}
