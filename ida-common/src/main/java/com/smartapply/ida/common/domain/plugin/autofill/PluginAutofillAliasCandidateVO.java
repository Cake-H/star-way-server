package com.smartapply.ida.common.domain.plugin.autofill;

import lombok.Data;

/**
 * 别名候选挖掘结果：近 N 天 failReason 为 option_not_found / selector_miss 的
 * clientFieldKey 聚合 Top N，供 admin 审核后登记为动态别名。
 */
@Data
public class PluginAutofillAliasCandidateVO {

    /** 聚合键：clientFieldKey */
    private String key;

    /** 失败次数 */
    private long count;

    /** 出现该失败的一个样例站点 host */
    private String sampleSiteHost;
}
