package com.smartapply.ida.module.admin.redeem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.admin.vo.RedeemCodeListItemVO;
import com.smartapply.ida.common.domain.billing.RedeemCode;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.admin.redeem.service.AdminRedeemCodeService;
import com.smartapply.ida.module.app.billing.mapper.RedeemCodeMapper;
import com.smartapply.ida.module.app.billing.service.RedeemCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台兑换码管理服务实现：生成委托 C 端 {@link RedeemCodeService} 复用同一套逻辑，
 * 列表/禁用直接操作 {@code ida_redeem_code}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRedeemCodeServiceImpl implements AdminRedeemCodeService {

    private final RedeemCodeMapper redeemCodeMapper;
    private final RedeemCodeService redeemCodeService;

    @Override
    public List<RedeemCodeListItemVO> generateCodes(Long adminId, int count, int points, LocalDateTime expireTime, String remark) {
        List<RedeemCode> codes = redeemCodeService.generateCodes(
                count, points, expireTime, remark, "admin:" + adminId);
        log.info("[后台] 批量生成兑换码 adminId={} count={} points={}", adminId, count, points);
        // 实体不直接出参，统一映射为 VO（与列表接口返回结构一致）
        return codes.stream().map(this::toListItemVO).toList();
    }

    @Override
    public PageVO<RedeemCodeListItemVO> pageCodes(long page, long size, String status) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<RedeemCode> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(RedeemCode::getStatus, status.trim());
        }
        qw.orderByDesc(RedeemCode::getCreatedAt);

        Page<RedeemCode> result = redeemCodeMapper.selectPage(new Page<>(safePage, safeSize), qw);
        return PageVO.of(
                result.getRecords().stream().map(this::toListItemVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void disableCode(Long adminId, Long codeId) {
        // 条件更新：只有 ACTIVE 状态可被禁用，防止并发/重复操作
        LambdaUpdateWrapper<RedeemCode> uw = new LambdaUpdateWrapper<>();
        uw.eq(RedeemCode::getId, codeId)
                .eq(RedeemCode::getStatus, RedeemCode.STATUS_ACTIVE)
                .set(RedeemCode::getStatus, RedeemCode.STATUS_DISABLED);
        int affected = redeemCodeMapper.update(null, uw);
        if (affected == 0) {
            throw new BusinessException("兑换码不存在或已被使用/禁用");
        }
        log.info("[后台] 禁用兑换码 adminId={} codeId={}", adminId, codeId);
    }

    private RedeemCodeListItemVO toListItemVO(RedeemCode rc) {
        return RedeemCodeListItemVO.builder()
                .id(rc.getId())
                .code(rc.getCode())
                .points(rc.getPoints())
                .status(rc.getStatus())
                .usedBy(rc.getUsedBy())
                .usedAt(rc.getUsedAt())
                .expireTime(rc.getExpireTime())
                .remark(rc.getRemark())
                .createdAt(rc.getCreatedAt())
                .build();
    }
}
