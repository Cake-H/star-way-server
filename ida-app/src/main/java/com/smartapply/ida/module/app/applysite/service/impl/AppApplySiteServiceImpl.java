package com.smartapply.ida.module.app.applysite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartapply.ida.common.domain.applysite.ApplySite;
import com.smartapply.ida.common.domain.applysite.vo.AppApplySiteVO;
import com.smartapply.ida.module.app.applysite.mapper.AppApplySiteMapper;
import com.smartapply.ida.module.app.applysite.service.AppApplySiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * C 端网申站点目录服务实现：只查询上架（{@code status = 1}）站点，按排序值升序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppApplySiteServiceImpl implements AppApplySiteService {

    private final AppApplySiteMapper appApplySiteMapper;

    @Override
    public List<String> listCategories() {
        List<ApplySite> sites = appApplySiteMapper.selectList(
                new LambdaQueryWrapper<ApplySite>()
                        .select(ApplySite::getCategory)
                        .eq(ApplySite::getStatus, ApplySite.STATUS_ON)
                        .orderByAsc(ApplySite::getSortOrder));
        // 按 sortOrder 首次出现的顺序去重，保证 Tab 顺序与站点排序一致
        return sites.stream()
                .map(ApplySite::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @Override
    public List<AppApplySiteVO> listSites(String category) {
        LambdaQueryWrapper<ApplySite> qw = new LambdaQueryWrapper<>();
        qw.eq(ApplySite::getStatus, ApplySite.STATUS_ON);
        if (StringUtils.hasText(category)) {
            qw.eq(ApplySite::getCategory, category.trim());
        }
        qw.orderByAsc(ApplySite::getSortOrder).orderByAsc(ApplySite::getId);

        return appApplySiteMapper.selectList(qw).stream()
                .map(this::toVO)
                .toList();
    }

    private AppApplySiteVO toVO(ApplySite site) {
        return AppApplySiteVO.builder()
                .id(site.getId())
                .name(site.getName())
                .logoUrl(site.getLogoUrl())
                .url(site.getUrl())
                .category(site.getCategory())
                .supportAutofill(site.getSupportAutofill())
                .build();
    }
}
