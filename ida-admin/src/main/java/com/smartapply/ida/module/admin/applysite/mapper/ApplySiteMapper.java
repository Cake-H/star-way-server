package com.smartapply.ida.module.admin.applysite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.applysite.ApplySite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网申站点表 {@code ida_apply_site} 访问；通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供。
 */
@Mapper
public interface ApplySiteMapper extends BaseMapper<ApplySite> {
}
