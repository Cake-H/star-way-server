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
     * 同 {@link #execute(Long, String, Long, Supplier)}，但仅当结果满足 {@code settleWhen} 时才结算扣点。
     * <p>
     * 用于「调用成功返回但结果不可用」的场景（如模型输出无法解析返回 null）：
     * 功能未成功服务用户，不计费、不记用量。
     *
     * @param settleWhen 结果是否可结算的判定；为 null 视同「始终结算」
     */
    <T> T execute(Long userId, String bizType, Long bizId, Supplier<T> aiCall,
                  java.util.function.Predicate<T> settleWhen);

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
