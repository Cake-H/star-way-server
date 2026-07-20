package com.smartapply.ida.module.app.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.billing.AiUsageRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 用量记录表 {@code ida_ai_usage_record} 访问；通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供。
 */
@Mapper
public interface AiUsageRecordMapper extends BaseMapper<AiUsageRecord> {
}
