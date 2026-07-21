package com.smartapply.ida.module.app.billing.service;

import com.smartapply.ida.common.domain.billing.vo.QuotaBalanceVO;
import com.smartapply.ida.common.domain.billing.vo.QuotaTransactionVO;

import java.util.List;
import java.util.function.Supplier;

/**
 * AI 额度计费服务（M1.1 核心）：额度守卫 + 用量计量 + 每日免费额度。
 */
public interface BillingService {

    /**
     * 在额度守卫与用量计量下执行一次 LLM 调用。
     *
     * @param userId  当前登录用户
     * @param bizType 业务类型，见 {@link com.smartapply.ida.ai.usage.AiUsageBizType}
     * @param bizId   关联业务 ID（可空，如场次/简历 ID）
     * @param aiCall  实际调用 Assistant 的动作；抛出的异常原样向上传播（失败不扣点）
     * @return aiCall 的返回值
     */
    <T> T execute(Long userId, String bizType, Long bizId, Supplier<T> aiCall);

    /**
     * 懒发放每日免费额度。
     */
    void ensureDailyGrant(Long userId);

    /**
     * 余额校验：不足则抛 {@code UserErrorCode.QUOTA_INSUFFICIENT}。
     */
    void requireSufficientBalance(Long userId);

    /**
     * 查询当前余额；用户不存在或字段为 null 时返回 null。
     */
    Integer selectBalance(Long userId);

    /**
     * 查询用户额度总览。
     */
    QuotaBalanceVO getBalanceVO(Long userId);

    /**
     * 查询用户额度流水（按时间倒序）。
     *
     * @param userId 用户 ID
     * @param limit  最大返回条数
     */
    List<QuotaTransactionVO> listTransactions(Long userId, int limit);
}
