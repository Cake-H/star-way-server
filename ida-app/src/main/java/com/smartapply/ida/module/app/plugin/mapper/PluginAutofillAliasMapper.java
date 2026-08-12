package com.smartapply.ida.module.app.plugin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAlias;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表 {@code ida_plugin_autofill_alias}：动态字段别名。
 * <p>
 * 查询/CRUD 均用 {@code BaseMapper} + {@code QueryWrapper} 完成，无需 XML。
 * ida-plugin（{@code DynamicFieldAliasService}）与 ida-admin（别名审核管理）共用本 Mapper。
 */
@Mapper
public interface PluginAutofillAliasMapper extends BaseMapper<PluginAutofillAlias> {
}
