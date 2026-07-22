package com.smartapply.ida.module.app.applysite.service;

import com.smartapply.ida.common.domain.applysite.vo.AppApplySiteVO;

import java.util.List;

/**
 * C 端网申站点目录服务（只读）：为用户提供可快捷跳转的网申入口列表。
 */
public interface AppApplySiteService {

    /** 上架站点的去重分类列表（用于前端分类 Tab/筛选器） */
    List<String> listCategories();

    /**
     * 上架站点列表，按 sortOrder 升序。
     *
     * @param category 分类筛选，空/blank 返回全部分类
     */
    List<AppApplySiteVO> listSites(String category);
}
