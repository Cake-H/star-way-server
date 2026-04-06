package com.smartapply.ida.module.app.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.user.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 {@code ida_user} 访问；通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供，复杂查询可在此追加方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
