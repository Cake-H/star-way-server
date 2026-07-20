package com.smartapply.ida.module.app.billing.support;

import dev.langchain4j.model.output.TokenUsage;

/**
 * 额度成本换算（纯函数，便于单测）。
 */
public final class BillingCostCalculator {

    private BillingCostCalculator() {
    }

    /**
     * 按 token 用量换算扣点：输入+输出合并，按 {@code tokensPerPoint} 向上取整，不低于 {@code minPoints}。
     *
     * @param usage          模型返回的 token 用量，null 视为 0（模型未上报时按最低扣点兜底）
     * @param tokensPerPoint 多少 token 折算 1 点，必须 &gt; 0
     * @param minPoints      每次调用最低扣点
     */
    public static int pointsFor(TokenUsage usage, int tokensPerPoint, int minPoints) {
        int total = totalTokens(usage);
        int points = total <= 0 ? 0 : (int) Math.ceil((double) total / tokensPerPoint);
        return Math.max(points, minPoints);
    }

    /** 总 token 数：优先取模型上报的 totalTokenCount，缺省用输入+输出相加；全缺为 0 */
    public static int totalTokens(TokenUsage usage) {
        if (usage == null) {
            return 0;
        }
        if (usage.totalTokenCount() != null) {
            return usage.totalTokenCount();
        }
        int in = usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
        int out = usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
        return in + out;
    }
}
