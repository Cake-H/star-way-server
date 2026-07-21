package com.smartapply.ida.module.app.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.billing.RedeemCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 兑换码表 {@code ida_redeem_code} 访问；通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供。
 */
@Mapper
public interface RedeemCodeMapper extends BaseMapper<RedeemCode> {
}
