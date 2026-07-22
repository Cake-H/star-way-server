package com.smartapply.ida.common.domain.applysite.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

/**
 * 网申站点新建/更新请求。
 */
@Data
public class ApplySiteUpsertRequest {

    /** 站点名称 */
    @NotBlank(message = "站点名称不能为空")
    @Size(max = 128, message = "站点名称不能超过 128 字")
    private String name;

    /** 网申入口 URL（必须是合法 http/https 地址） */
    @NotBlank(message = "站点 URL 不能为空")
    @URL(protocol = "https", message = "站点 URL 必须是合法的 https 地址", regexp = "^https?://.*")
    @Size(max = 512, message = "站点 URL 过长")
    private String url;

    /** 站点 Logo URL，可空 */
    @Size(max = 512, message = "Logo URL 过长")
    private String logoUrl;

    /** 分类：互联网/国企/银行/外企/快消/综合招聘/实习/ATS系统 等 */
    @NotBlank(message = "分类不能为空")
    @Size(max = 32, message = "分类不能超过 32 字")
    private String category;

    /** 是否支持插件一键填表：1=支持 0=不支持 */
    @NotNull(message = "是否支持一键填表不能为空")
    @Min(value = 0, message = "supportAutofill 只能为 0 或 1")
    @Max(value = 1, message = "supportAutofill 只能为 0 或 1")
    private Integer supportAutofill;

    /** 排序值，越小越靠前 */
    @NotNull(message = "排序值不能为空")
    private Integer sortOrder;

    /** 备注（内部说明），可空 */
    @Size(max = 255, message = "备注不能超过 255 字")
    private String remark;
}
