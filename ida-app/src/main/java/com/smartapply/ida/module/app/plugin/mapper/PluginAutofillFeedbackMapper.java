package com.smartapply.ida.module.app.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表 {@code ida_plugin_autofill_feedback}：逐字段反馈明细。
 * <p>
 * 统计聚合查询在 service 层用 {@code QueryWrapper.selectMaps + groupBy} 完成，无需 XML。
 */
@Mapper
public interface PluginAutofillFeedbackMapper extends BaseMapper<PluginAutofillFeedback> {
}
