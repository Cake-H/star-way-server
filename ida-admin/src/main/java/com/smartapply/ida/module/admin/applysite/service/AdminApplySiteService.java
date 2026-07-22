package com.smartapply.ida.module.admin.applysite.service;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.applysite.dto.ApplySiteUpsertRequest;
import com.smartapply.ida.common.domain.applysite.vo.ApplySiteVO;

/**
 * 管理后台网申站点管理服务：站点目录的分页、新建、更新、上下架。
 */
public interface AdminApplySiteService {

    /** 站点分页列表（可按分类/状态筛选） */
    PageVO<ApplySiteVO> pageSites(long page, long size, String category, Integer status);

    /** 新建站点，返回站点 ID */
    Long createSite(ApplySiteUpsertRequest request);

    /** 更新站点（全量字段） */
    void updateSite(Long siteId, ApplySiteUpsertRequest request);

    /** 上下架切换（上架 ↔ 下架） */
    void toggleSite(Long siteId);
}
