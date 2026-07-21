package com.smartapply.ida.module.app.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartapply.ida.ai.usage.AiUsageContext;
import com.smartapply.ida.common.code.UserErrorCode;
import com.smartapply.ida.common.domain.billing.AiUsageRecord;
import com.smartapply.ida.common.domain.billing.QuotaTransaction;
import com.smartapply.ida.common.domain.billing.vo.QuotaBalanceVO;
import com.smartapply.ida.common.domain.billing.vo.QuotaTransactionVO;
import com.smartapply.ida.common.domain.user.UserEntity;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.props.billing.BillingProperties;
import com.smartapply.ida.module.app.billing.mapper.AiUsageRecordMapper;
import com.smartapply.ida.module.app.billing.mapper.QuotaTransactionMapper;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.module.app.billing.support.BillingCostCalculator;
import com.smartapply.ida.module.app.user.mapper.UserMapper;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * AI 额度计费服务实现（M1.1 核心）：额度守卫 + 用量计量 + 每日免费额度。
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
 * <b>欠费制：</b>结算允许余额扣为负数（欠款），后续每日免费额度/兑换码/充值优先抵扣，
 * 避免「剩少量额度时发起大额调用、超额部分被清零兜底」的薅羊毛漏洞；
 * 极端并发下可能短暂超扣，测试期可接受，M4 压测阶段再视情况引入预扣/乐观锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final BillingProperties properties;
    private final UserMapper userMapper;
    private final AiUsageRecordMapper usageRecordMapper;
    private final QuotaTransactionMapper quotaTransactionMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public <T> T execute(Long userId, String bizType, Long bizId, Supplier<T> aiCall) {
        if (!properties.isEnabled()) {
            return aiCall.get();
        }

        ensureDailyGrant(userId);
        requireSufficientBalance(userId);

        AiUsageContext.begin(userId, bizType, bizId);
        try {
            T result = aiCall.get();
            settle(userId, bizType, bizId, AiUsageContext.current());
            return result;
        } finally {
            AiUsageContext.clear();
        }
    }

    @Override
    public void ensureDailyGrant(Long userId) {
        int daily = properties.getDailyFreePoints();
        if (daily <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        try {
            Boolean granted = transactionTemplate.execute(status -> {
                LambdaUpdateWrapper<UserEntity> uw = new LambdaUpdateWrapper<>();
                uw.eq(UserEntity::getId, userId)
                        .and(w -> w.isNull(UserEntity::getLastDailyGrantDate)
                                .or().lt(UserEntity::getLastDailyGrantDate, today))
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

    @Override
    public void requireSufficientBalance(Long userId) {
        Integer balance = selectBalance(userId);
        if (balance == null || balance < properties.getMinPointsPerCall()) {
            throw new BusinessException(UserErrorCode.QUOTA_INSUFFICIENT);
        }
    }

    @Override
    public Integer selectBalance(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user != null ? user.getBalance() : null;
    }

    @Override
    public QuotaBalanceVO getBalanceVO(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return QuotaBalanceVO.builder()
                .balance(user.getBalance())
                .totalQuota(user.getTotalQuota())
                .memberLevel(user.getMemberLevel())
                .lastDailyGrantDate(user.getLastDailyGrantDate())
                .build();
    }

    @Override
    public List<QuotaTransactionVO> listTransactions(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<QuotaTransaction> rows = quotaTransactionMapper.selectList(
                new LambdaQueryWrapper<QuotaTransaction>()
                        .eq(QuotaTransaction::getUserId, userId)
                        .orderByDesc(QuotaTransaction::getCreatedAt)
                        .last("LIMIT " + safeLimit));
        return rows.stream()
                .map(tx -> QuotaTransactionVO.builder()
                        .id(tx.getId())
                        .type(tx.getType())
                        .points(tx.getPoints())
                        .balanceAfter(tx.getBalanceAfter())
                        .remark(tx.getRemark())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
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

                // 欠费制：允许扣成负数，欠款由后续每日免费额度/兑换码/充值优先抵扣，
                // 防止「剩 1 点时发起大额调用、超额部分被清零兜底」的薅羊毛漏洞
                LambdaUpdateWrapper<UserEntity> uw = new LambdaUpdateWrapper<>();
                uw.eq(UserEntity::getId, userId).setSql("balance = balance - " + cost);
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
}
