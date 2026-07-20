package com.smartapply.ida.module.app.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.billing.QuotaTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 额度流水表 {@code ida_quota_transaction} 访问；通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供。
 */
@Mapper
public interface QuotaTransactionMapper extends BaseMapper<QuotaTransaction> {
}
