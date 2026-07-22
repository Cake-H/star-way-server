package com.smartapply.ida.common.domain.applysite.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * C 端「网申网站列表」站点视图（仅上架站点；不含 remark/status 等内部字段）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppApplySiteVO {

    /** 站点主键 */
    private Long id;

    /** 站点名称 */
    private String name;

    /** 站点 Logo URL */
    private String logoUrl;

    /** 网申入口 URL */
    private String url;

    /** 分类：互联网/国企/银行/外企/快消/综合招聘/实习/ATS系统 等 */
    private String category;

    /** 是否支持插件一键填表：1=支持（前端可引导安装插件） 0=不支持 */
    private Integer supportAutofill;
}
