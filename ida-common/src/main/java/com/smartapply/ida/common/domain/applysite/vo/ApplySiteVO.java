package com.smartapply.ida.common.domain.applysite.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 网申站点视图对象（管理后台与 C 端列表共用结构；C 端仅返回上架站点）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplySiteVO {

    /** 站点主键 */
    private Long id;

    /** 站点名称 */
    private String name;

    /** 站点 Logo URL */
    private String logoUrl;

    /** 网申入口 URL */
    private String url;

    /** 分类 */
    private String category;

    /** 是否支持插件一键填表：1=支持 0=不支持 */
    private Integer supportAutofill;

    /** 排序值 */
    private Integer sortOrder;

    /** 1=上架 0=下架 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
