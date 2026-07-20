package com.smartapply.ida.config.props.billing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 额度计费配置（ida_v5_billing.sql 对应的应用层参数）。
 */
@Data
@ConfigurationProperties(prefix = "ida.billing")
public class BillingProperties {

    /** 总开关：false 时跳过额度校验与扣减（本地调试/灰度用），默认开启 */
    private boolean enabled = true;

    /** 多少 token 折算 1 点额度（向上取整，输入+输出合并计算） */
    private int tokensPerPoint = 1000;

    /** 每次成功调用的最低扣点（防止极短 prompt 白嫖） */
    private int minPointsPerCall = 1;

    /** 每日免费额度点数，懒发放（首次 AI 调用时触发） */
    private int dailyFreePoints = 10;
}
