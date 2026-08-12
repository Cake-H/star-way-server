package com.smartapply.ida.module.admin.plugin.service;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAlias;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasCandidateVO;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillAliasUpsertRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillFeedbackStatVO;

import java.util.List;

/**
 * 管理后台 — 插件一键填表反馈全局统计与动态别名审核。
 * <p>
 * 与 C 端 {@code PluginAutofillFeedbackStatsService}（用户级）的区别：不做 userId 过滤，
 * 面向运营视角的全局聚合，驱动站点模板扩充与动态别名登记。
 */
public interface AdminPluginAutofillService {

    /**
     * 全局反馈聚合统计（所有用户），结构复用 {@link PluginAutofillFeedbackStatVO}。
     *
     * @param days     统计近 N 天，默认 30
     * @param siteHost 可选，按站点过滤
     */
    PluginAutofillFeedbackStatVO stats(Integer days, String siteHost);

    /**
     * 别名候选挖掘：近 N 天 failReason 为 option_not_found / selector_miss 的
     * clientFieldKey 按失败次数降序 Top N，附样例站点 host。
     */
    List<PluginAutofillAliasCandidateVO> aliasCandidates(Integer days, Integer limit);

    /**
     * 站点候选挖掘：按 siteHost 聚合 total/filled/fillRate，命中率升序（最差在前）Top N。
     */
    List<PluginAutofillFeedbackStatVO.FieldCount> siteCandidates(Integer days, Integer limit);

    /** 动态别名分页列表（可按 status 过滤） */
    PageVO<PluginAutofillAlias> pageAliases(String status, long page, long size);

    /** 新增动态别名，status 缺省 approved（登记即生效） */
    Long createAlias(PluginAutofillAliasUpsertRequest request);

    /** 审核动态别名：status 仅允许 approved / rejected */
    void reviewAlias(Long id, String status);
}
