package com.smartapply.ida.module.app.applysite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.applysite.ApplySite;
import org.apache.ibatis.annotations.Mapper;

/**
 * C 端网申站点表 {@code ida_apply_site} 访问（只读用途）。
 * <p>
 * <b>命名说明：</b>管理后台模块已有 {@code ApplySiteMapper}（module.admin.applysite.mapper），
 * admin-server 会同时加载 ida-app 与 ida-admin，同名 Mapper 会产生 bean 冲突，故加 {@code App} 前缀。
 */
@Mapper
public interface AppApplySiteMapper extends BaseMapper<ApplySite> {
}
