package com.smartapply.ida.module.admin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.admin.AdminAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员账号表 {@code ida_admin_account} 访问；通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供。
 */
@Mapper
public interface AdminAccountMapper extends BaseMapper<AdminAccount> {
}
