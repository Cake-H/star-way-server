package com.smartapply.ida.common.domain.plugin.autofill;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 插件一键填表动态字段别名，对应表 {@code ida_plugin_autofill_alias}。
 * <p>
 * 由 admin 根据反馈挖掘（alias-candidates）审核登记；{@code status=approved} 的行由
 * {@code DynamicFieldAliasService}（ida-plugin）定时全量加载，参与确定性标签匹配，
 * 优先级低于 {@code ChineseFieldAliasTable} 静态表。
 */
@Data
@TableName("ida_plugin_autofill_alias")
public class PluginAutofillAlias {

    /** 审核状态：待审核 */
    public static final String STATUS_PENDING = "pending";
    /** 审核状态：已生效 */
    public static final String STATUS_APPROVED = "approved";
    /** 审核状态：已拒绝 */
    public static final String STATUS_REJECTED = "rejected";

    /** 来源：反馈挖掘 */
    public static final String SOURCE_FEEDBACK = "feedback";
    /** 来源：人工登记 */
    public static final String SOURCE_MANUAL = "manual";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归一化后的表单标签，匹配时与前端 labelText 归一化结果比对 */
    private String aliasLabel;

    /** 简历字段路径，格式同 ResumeFieldAccessor（如 basic.email） */
    private String fieldPath;

    /** feedback / manual */
    private String source;

    /** pending / approved / rejected；仅 approved 参与匹配 */
    private String status;

    private String remark;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
