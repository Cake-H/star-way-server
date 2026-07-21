package com.smartapply.ida.module.app.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartapply.ida.common.code.UserErrorCode;
import com.smartapply.ida.common.domain.billing.QuotaTransaction;
import com.smartapply.ida.common.domain.billing.RedeemCode;
import com.smartapply.ida.common.domain.billing.vo.RedeemResultVO;
import com.smartapply.ida.common.domain.user.UserEntity;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.billing.mapper.QuotaTransactionMapper;
import com.smartapply.ida.module.app.billing.mapper.RedeemCodeMapper;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.module.app.billing.service.RedeemCodeService;
import com.smartapply.ida.module.app.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 兑换码充值服务实现：测试期无需支付通道即可给种子用户发放额度。
 * <p>
 * 兑换流程在同一事务内完成：
 * <ol>
 *   <li>按 code 查询并校验状态/过期时间</li>
 *   <li>条件 UPDATE 将状态置为 USED（原子防并发重复使用）</li>
 *   <li>给用户加额度并记录额度流水</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedeemCodeServiceImpl implements RedeemCodeService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final RedeemCodeMapper redeemCodeMapper;
    private final UserMapper userMapper;
    private final QuotaTransactionMapper quotaTransactionMapper;
    private final BillingService billingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RedeemResultVO redeem(Long userId, String code) {
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(UserErrorCode.REDEEM_CODE_INVALID);
        }

        // 1. 查询兑换码并做前置校验
        RedeemCode redeemCode = redeemCodeMapper.selectOne(
                new LambdaQueryWrapper<RedeemCode>().eq(RedeemCode::getCode, trimmed));
        if (redeemCode == null) {
            throw new BusinessException(UserErrorCode.REDEEM_CODE_INVALID, "兑换码不存在");
        }
        if (!RedeemCode.STATUS_ACTIVE.equals(redeemCode.getStatus())) {
            throw new BusinessException(UserErrorCode.REDEEM_CODE_INVALID, "兑换码已使用或已禁用");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (redeemCode.getExpireTime() != null && redeemCode.getExpireTime().isBefore(now)) {
            throw new BusinessException(UserErrorCode.REDEEM_CODE_INVALID, "兑换码已过期");
        }

        // 2. 原子置为已使用，防止并发重复兑换
        LambdaUpdateWrapper<RedeemCode> uw = new LambdaUpdateWrapper<>();
        uw.eq(RedeemCode::getId, redeemCode.getId())
                .eq(RedeemCode::getStatus, RedeemCode.STATUS_ACTIVE)
                .set(RedeemCode::getStatus, RedeemCode.STATUS_USED)
                .set(RedeemCode::getUsedBy, userId)
                .set(RedeemCode::getUsedAt, now);
        int affected = redeemCodeMapper.update(null, uw);
        if (affected == 0) {
            // 并发下已被他人兑换
            throw new BusinessException(UserErrorCode.REDEEM_CODE_INVALID, "兑换码已被使用");
        }

        // 3. 给用户加额度并记录流水
        int points = redeemCode.getPoints() != null ? redeemCode.getPoints() : 0;
        LambdaUpdateWrapper<UserEntity> userUw = new LambdaUpdateWrapper<>();
        userUw.eq(UserEntity::getId, userId)
                .setSql("balance = balance + " + points + ", total_quota = total_quota + " + points);
        userMapper.update(null, userUw);

        Integer balanceAfter = billingService.selectBalance(userId);
        quotaTransactionMapper.insert(QuotaTransaction.builder()
                .userId(userId)
                .type(QuotaTransaction.TYPE_REDEEM)
                .points(points)
                .balanceAfter(balanceAfter != null ? balanceAfter : 0)
                .remark("兑换码充值：" + trimmed)
                .build());

        log.info("[兑换码] 兑换成功 userId={} code={} points={} balanceAfter={}", userId, trimmed, points, balanceAfter);
        return RedeemResultVO.builder()
                .code(trimmed)
                .points(points)
                .balanceAfter(balanceAfter)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RedeemCode> generateCodes(int count, int points, LocalDateTime expireTime, String remark, String createdBy) {
        if (count <= 0 || count > 1000) {
            throw new BusinessException("生成数量必须在 1-1000 之间");
        }
        if (points <= 0) {
            throw new BusinessException("兑换点数必须大于 0");
        }

        List<RedeemCode> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RedeemCode rc = RedeemCode.builder()
                    .code(generateUniqueCode())
                    .points(points)
                    .status(RedeemCode.STATUS_ACTIVE)
                    .expireTime(expireTime)
                    .remark(remark)
                    .createdBy(createdBy)
                    .build();
            redeemCodeMapper.insert(rc);
            codes.add(rc);
        }
        log.info("[兑换码] 批量生成 count={} points={} remark={} createdBy={}", count, points, remark, createdBy);
        return codes;
    }

    /** 生成 24 位去横线 UUID 作为兑换码 */
    private String generateUniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
