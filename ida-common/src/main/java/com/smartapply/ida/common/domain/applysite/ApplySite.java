package com.smartapply.ida.common.domain.applysite;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对应库表 {@code ida_apply_site}：网申站点目录。
 * <p>
 * 由管理后台维护；C 端「网申网站列表」按 {@code status = 1} 读取上架站点。
 */
@SuppressWarnings("serial")
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_apply_site")
public class ApplySite {

    /** 状态：上架 */
    public static final int STATUS_ON = 1;
    /** 状态：下架 */
    public static final int STATUS_OFF = 0;

    /** 站点主键 */
    private Long id;

    /** 站点名称，如「腾讯校招」 */
    private String name;

    /** 站点 Logo URL */
    private String logoUrl;

    /** 网申入口 URL */
    private String url;

    /** 分类：互联网/国企/银行/外企/快消/综合招聘/实习/ATS系统 等 */
    private String category;

    /** 是否支持插件一键填表：1=支持 0=不支持 */
    private Integer supportAutofill;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 1=上架 0=下架 */
    private Integer status;

    /** 备注（内部说明） */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
