package com.smartapply.ida.module.admin.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.admin.vo.AdminUserDetailVO;
import com.smartapply.ida.common.domain.admin.vo.AdminUserListItemVO;
import com.smartapply.ida.common.domain.admin.vo.QuotaAdjustResultVO;
import com.smartapply.ida.common.domain.billing.QuotaTransaction;
import com.smartapply.ida.common.domain.billing.vo.UsageUserRankVO;
import com.smartapply.ida.common.domain.resume.Resume;
import com.smartapply.ida.common.domain.user.UserEntity;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.admin.usage.mapper.UsageStatisticsMapper;
import com.smartapply.ida.module.admin.user.service.AdminUserService;
import com.smartapply.ida.module.app.billing.mapper.QuotaTransactionMapper;
import com.smartapply.ida.module.app.resume.mapper.ResumeMapper;
import com.smartapply.ida.module.app.user.mapper.UserMapper;
import com.smartapply.ida.security.crypto.phone.PhoneCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 管理后台用户管理服务实现。
 * <p>
 * <b>隐私说明：</b>列表与详情中的手机号一律脱敏返回（前 3 后 4），
 * 后台界面不展示完整手机号；phone 列为确定性 AES 密文，此处先解密再打码。
 * 关键字搜索支持：完整手机号（密文等值）/ 后 4 位（盲索引 phone_tail 模糊）/ 用户 ID。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 用户详情中「近 30 天用量」的统计窗口 */
    private static final int RECENT_USAGE_DAYS = 30;

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final QuotaTransactionMapper quotaTransactionMapper;
    private final UsageStatisticsMapper usageStatisticsMapper;
    private final PhoneCrypto phoneCrypto;

    @Override
    public PageVO<AdminUserListItemVO> pageUsers(long page, long size, String keyword, Integer status) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<UserEntity> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            Long userId = parseLongOrNull(kw);
            if (kw.matches("\\d+")) {
                // phone 列为确定性 AES 密文，无法 LIKE。盲索引方案：
                // 11 位 → 密文等值（全号精确）；任意数字 → 尾号盲索引模糊 + 用户 ID 精确
                qw.and(w -> {
                    w.like(UserEntity::getPhoneTail, kw).or().eq(UserEntity::getId, userId);
                    if (kw.length() == 11) {
                        w.or().eq(UserEntity::getPhone, phoneCrypto.encrypt(kw));
                    }
                });
            } else {
                // 非数字关键字不可能命中手机号/尾号，仅按用户 ID 精确匹配
                qw.eq(UserEntity::getId, userId);
            }
        }
        if (status != null) {
            qw.eq(UserEntity::getStatus, status);
        }
        qw.orderByDesc(UserEntity::getCreatedAt);

        Page<UserEntity> result = userMapper.selectPage(new Page<>(safePage, safeSize), qw);
        return PageVO.of(
                result.getRecords().stream().map(this::toListItemVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AdminUserDetailVO getUserDetail(Long userId) {
        UserEntity user = requireUser(userId);

        Long resumeCount = resumeMapper.selectCount(
                new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));

        UsageUserRankVO usage = usageStatisticsMapper.selectUserSummary(
                userId, LocalDateTime.now(ZONE).minusDays(RECENT_USAGE_DAYS));

        return AdminUserDetailVO.builder()
                .id(user.getId())
                .phone(maskPhone(user.getPhone()))
                .status(user.getStatus())
                .balance(user.getBalance())
                .totalQuota(user.getTotalQuota())
                .memberLevel(user.getMemberLevel())
                .lastDailyGrantDate(user.getLastDailyGrantDate())
                .resumeCount(resumeCount)
                .recent30dCallCount(usage != null ? usage.getCallCount() : 0L)
                .recent30dTotalTokens(usage != null ? usage.getTotalTokens() : 0L)
                .recent30dCostPoints(usage != null ? usage.getCostPoints() : 0L)
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotaAdjustResultVO adjustQuota(Long adminId, Long userId, Integer points, String remark) {
        if (points == null || points == 0) {
            throw new BusinessException("调整点数不能为 0");
        }
        requireUser(userId);

        // 余额增减走 setSql 原子表达式；累计发放额度只在正向调整时累加（负向扣减不算"发放"）
        LambdaUpdateWrapper<UserEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserEntity::getId, userId)
                .setSql(points > 0
                        ? "balance = balance + " + points + ", total_quota = total_quota + " + points
                        : "balance = balance + " + points);
        userMapper.update(null, uw);

        Integer balanceAfter = userMapper.selectById(userId).getBalance();
        quotaTransactionMapper.insert(QuotaTransaction.builder()
                .userId(userId)
                .type(QuotaTransaction.TYPE_ADMIN_ADJUST)
                .points(points)
                .balanceAfter(balanceAfter != null ? balanceAfter : 0)
                .remark("管理员调整：" + remark + "（操作管理员ID：" + adminId + "）")
                .build());

        log.info("[后台] 手动调整额度 adminId={} userId={} points={} balanceAfter={}",
                adminId, userId, points, balanceAfter);
        return QuotaAdjustResultVO.builder()
                .userId(userId)
                .points(points)
                .balanceAfter(balanceAfter)
                .build();
    }

    /** 用户不存在时统一抛业务异常 */
    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private AdminUserListItemVO toListItemVO(UserEntity user) {
        return AdminUserListItemVO.builder()
                .id(user.getId())
                .phone(maskPhone(user.getPhone()))
                .status(user.getStatus())
                .balance(user.getBalance())
                .memberLevel(user.getMemberLevel())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /** 手机号脱敏：密文先解密（解密失败按明文兼容），再保留前 3 后 4；异常长度/空值统一返回 **** */
    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "****";
        }
        String plain = phone;
        try {
            plain = phoneCrypto.decrypt(phone);
        } catch (Exception e) {
            // 存量明文/未开启加密：按原文继续脱敏
            log.debug("手机号解密失败按原文脱敏: {}", e.getMessage());
        }
        if (plain == null || plain.length() < 7) {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }

    /** 关键字可解析为数字时返回 Long（用于按用户 ID 精确匹配），否则返回 null 不参与条件 */
    private Long parseLongOrNull(String kw) {
        try {
            return Long.parseLong(kw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
