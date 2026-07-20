package com.smartapply.ida.module.app.billing.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.smartapply.ida.ai.usage.AiUsageContext;
import com.smartapply.ida.common.code.UserErrorCode;
import com.smartapply.ida.common.domain.billing.AiUsageRecord;
import com.smartapply.ida.common.domain.billing.QuotaTransaction;
import com.smartapply.ida.common.domain.user.UserEntity;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.props.billing.BillingProperties;
import com.smartapply.ida.module.app.billing.mapper.AiUsageRecordMapper;
import com.smartapply.ida.module.app.billing.mapper.QuotaTransactionMapper;
import com.smartapply.ida.module.app.billing.support.BillingCostCalculator;
import com.smartapply.ida.module.app.user.mapper.UserMapper;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * AI 额度计费服务（M1.1 核心）：额度守卫 + 用量计量 + 每日免费额度。
 * <p>
 * <b>接入方式：</b>AI Service 层（持有 LangChain4j Assistant）用 {@link #execute(Long, String, Long, Supplier)}
 * 包裹实际 LLM 调用；业务 Service 只传 {@code userId}，不感知计费：
 * <ol>
 *   <li>懒发放当日免费额度（条件更新保证并发下不重复发放，无需定时任务）</li>
 *   <li>余额不足 → 抛 {@link UserErrorCode#QUOTA_INSUFFICIENT}，LLM 调用不会发生</li>
 *   <li>调用期间通过 {@link AiUsageContext} 捕获真实 token 用量</li>
 *   <li>调用成功后按用量结算扣点，落用量记录 + 额度流水</li>
 * </ol>
 * <b>事务边界：</b>LLM 调用本身不持有数据库事务；发放与结算分别用 {@link TransactionTemplate} 短事务。
 * <b>并发说明：</b>扣减用 {@code GREATEST(balance - ?, 0)} 兜底不为负；极端并发下可能轻微超扣，
 * 测试期可接受，M4 压测阶段再视情况引入预扣/乐观锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final BillingProperties properties;
    private final UserMapper userMapper;
    private final AiUsageRecordMapper usageRecordMapper;
    private final QuotaTransactionMapper quotaTransactionMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 在额度守卫与用量计量下执行一次 LLM 调用。
     *
     * @param userId  当前登录用户
     * @param bizType 业务类型，见 {@link com.smartapply.ida.ai.usage.AiUsageBizType}
     * @param bizId   关联业务 ID（可空，如场次/简历 ID）
     * @param aiCall  实际调用 Assistant 的动作；抛出的异常原样向上传播（失败不扣点）
     * @return aiCall 的返回值
     */
    public <T> T execute(Long userId, String bizType, Long bizId, Supplier<T> aiCall) {
        if (!properties.isEnabled()) {
            return aiCall.get();
        }

        // 懒发放每日免费额度
        ensureDailyGrant(userId);
        // 余额校验，若不足则抛异常，前端据此引导充值
        requireSufficientBalance(userId);

        // 开始计量一次上下文
        AiUsageContext.begin(userId, bizType, bizId);
        try {
            T result = aiCall.get();
            // 结算：按捕获到的 token 用量换算扣点，落用量记录 + 扣余额 + 记流水
            settle(userId, bizType, bizId, AiUsageContext.current());
            return result;
        } finally {
            // 必须清除上下文，防止线程复用导致串号
            AiUsageContext.clear();
        }
    }

    /**
     * 懒发放每日免费额度：{@code last_daily_grant_date < 今天} 才更新，
     * 条件 UPDATE 的原子性保证并发请求不会重复发放。
     */
    public void ensureDailyGrant(Long userId) {
        int daily = properties.getDailyFreePoints();
        if (daily <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        try {
            Boolean granted = transactionTemplate.execute(status -> {
                UpdateWrapper<UserEntity> uw = new UpdateWrapper<>();
                uw.eq("id", userId)
                        .and(w -> w.isNull("last_daily_grant_date").or().lt("last_daily_grant_date", today))
                        .setSql("balance = balance + " + daily
                                + ", total_quota = total_quota + " + daily
                                + ", last_daily_grant_date = '" + today + "'");
                int affected = userMapper.update(null, uw);
                if (affected == 0) {
                    return false;
                }
                Integer balanceAfter = selectBalance(userId);
                quotaTransactionMapper.insert(QuotaTransaction.builder()
                        .userId(userId)
                        .type(QuotaTransaction.TYPE_DAILY_GRANT)
                        .points(daily)
                        .balanceAfter(balanceAfter != null ? balanceAfter : 0)
                        .remark("每日免费额度")
                        .build());
                return true;
            });
            if (Boolean.TRUE.equals(granted)) {
                log.info("[计费] 已发放每日免费额度 userId={} points={}", userId, daily);
            }
        } catch (Exception e) {
            // 发放失败不阻断业务（老库未执行 v5 脚本等场景），余额校验会兜底拦截
            log.warn("[计费] 每日免费额度发放失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 余额校验：不足则抛 {@link UserErrorCode#QUOTA_INSUFFICIENT}，前端据此引导充值 */
    public void requireSufficientBalance(Long userId) {
        Integer balance = selectBalance(userId);
        if (balance == null || balance < properties.getMinPointsPerCall()) {
            throw new BusinessException(UserErrorCode.QUOTA_INSUFFICIENT);
        }
    }

    /** 结算：按捕获到的 token 用量换算扣点，落用量记录 + 扣余额 + 记流水（短事务） */
    private void settle(Long userId, String bizType, Long bizId, AiUsageContext ctx) {
        TokenUsage usage = ctx != null ? ctx.getCapturedUsage() : null;
        String model = ctx != null ? ctx.getCapturedModel() : null;
        int cost = BillingCostCalculator.pointsFor(usage, properties.getTokensPerPoint(), properties.getMinPointsPerCall());
        int totalTokens = BillingCostCalculator.totalTokens(usage);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                usageRecordMapper.insert(AiUsageRecord.builder()
                        .userId(userId)
                        .bizType(bizType)
                        .bizId(bizId)
                        .model(model)
                        .promptTokens(usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0)
                        .completionTokens(usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0)
                        .totalTokens(totalTokens)
                        .costPoints(cost)
                        .build());

                UpdateWrapper<UserEntity> uw = new UpdateWrapper<>();
                uw.eq("id", userId).setSql("balance = GREATEST(balance - " + cost + ", 0)");
                userMapper.update(null, uw);

                Integer balanceAfter = selectBalance(userId);
                quotaTransactionMapper.insert(QuotaTransaction.builder()
                        .userId(userId)
                        .type(QuotaTransaction.TYPE_CONSUME)
                        .points(-cost)
                        .balanceAfter(balanceAfter != null ? balanceAfter : 0)
                        .remark("AI 调用消耗：" + bizType)
                        .build());
            });
            log.info("[计费] 结算完成 userId={} bizType={} tokens={} cost={}", userId, bizType, totalTokens, cost);
        } catch (Exception e) {
            // 结算失败不影响已完成的业务结果，但必须告警级日志，便于事后对账
            log.error("[计费] 结算失败（用户已消费但未扣点）userId={} bizType={} tokens={} cost={}",
                    userId, bizType, totalTokens, cost, e);
        }
    }

    /** 查询当前余额；用户不存在或字段为 null 时返回 null */
    public Integer selectBalance(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user != null ? user.getBalance() : null;
    }
}
