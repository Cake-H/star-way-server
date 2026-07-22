package com.smartapply.ida.module.admin.applysite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.applysite.ApplySite;
import com.smartapply.ida.common.domain.applysite.dto.ApplySiteUpsertRequest;
import com.smartapply.ida.common.domain.applysite.vo.ApplySiteVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.admin.applysite.mapper.ApplySiteMapper;
import com.smartapply.ida.module.admin.applysite.service.AdminApplySiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理后台网申站点管理服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminApplySiteServiceImpl implements AdminApplySiteService {

    private final ApplySiteMapper applySiteMapper;

    @Override
    public PageVO<ApplySiteVO> pageSites(long page, long size, String category, Integer status) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<ApplySite> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            qw.eq(ApplySite::getCategory, category.trim());
        }
        if (status != null) {
            qw.eq(ApplySite::getStatus, status);
        }
        qw.orderByAsc(ApplySite::getSortOrder).orderByDesc(ApplySite::getUpdatedAt);

        Page<ApplySite> result = applySiteMapper.selectPage(new Page<>(safePage, safeSize), qw);
        return PageVO.of(
                result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public Long createSite(ApplySiteUpsertRequest request) {
        ApplySite site = new ApplySite();
        BeanUtils.copyProperties(request, site);
        site.setStatus(ApplySite.STATUS_ON);
        applySiteMapper.insert(site);
        log.info("[后台] 新建网申站点 name={} id={}", site.getName(), site.getId());
        return site.getId();
    }

    @Override
    public void updateSite(Long siteId, ApplySiteUpsertRequest request) {
        requireSite(siteId);
        ApplySite site = new ApplySite();
        BeanUtils.copyProperties(request, site);
        site.setId(siteId);
        // 状态字段不随编辑接口变更，上下架走 toggleSite，避免误操作覆盖
        site.setStatus(null);
        applySiteMapper.updateById(site);
        log.info("[后台] 更新网申站点 id={} name={}", siteId, site.getName());
    }

    @Override
    public void toggleSite(Long siteId) {
        ApplySite site = requireSite(siteId);
        int next = site.getStatus() != null && site.getStatus() == ApplySite.STATUS_ON
                ? ApplySite.STATUS_OFF : ApplySite.STATUS_ON;
        ApplySite update = new ApplySite();
        update.setId(siteId);
        update.setStatus(next);
        applySiteMapper.updateById(update);
        log.info("[后台] 网申站点上下架切换 id={} status={} -> {}", siteId, site.getStatus(), next);
    }

    /** 站点不存在时统一抛业务异常 */
    private ApplySite requireSite(Long siteId) {
        ApplySite site = applySiteMapper.selectById(siteId);
        if (site == null) {
            throw new BusinessException("站点不存在");
        }
        return site;
    }

    private ApplySiteVO toVO(ApplySite site) {
        return ApplySiteVO.builder()
                .id(site.getId())
                .name(site.getName())
                .logoUrl(site.getLogoUrl())
                .url(site.getUrl())
                .category(site.getCategory())
                .supportAutofill(site.getSupportAutofill())
                .sortOrder(site.getSortOrder())
                .status(site.getStatus())
                .remark(site.getRemark())
                .updatedAt(site.getUpdatedAt())
                .build();
    }
}
