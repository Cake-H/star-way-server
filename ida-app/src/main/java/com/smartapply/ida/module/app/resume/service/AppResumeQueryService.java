package com.smartapply.ida.module.app.resume.service;

import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillResumeContextVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeDetailVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeListItemVO;

import java.util.List;

/**
 * 简历读模型：列表与详情（不含写操作）。
 * <p>
 * 与 {@link AppResumeDraftService} 分离的目的：
 * <ul>
 *   <li>读路径不持有「保存草稿」的事务与副作用，职责清晰；</li>
 *   <li>以后加缓存、只读副本、审计时只动本接口即可。</li>
 * </ul>
 * 实现类：{@link com.smartapply.ida.module.app.resume.service.impl.AppResumeQueryServiceImpl}。
 */
public interface AppResumeQueryService {

    /**
     * 当前用户下、未软删的简历列表。
     * <p>
     * 排序：{@code updated_at} 降序（最近改动的排在前面）。
     *
     * @param userId 当前登录用户 id
     */
    List<ResumeListItemVO> listMyResumes(long userId);

    /**
     * 单份简历详情，供「只读详情页」使用。
     * <p>
     * 会校验：简历存在、未删除、且 {@code user_id} 与当前用户一致，否则抛业务异常。
     *
     * @param userId   当前登录用户 id
     * @param resumeId 简历主键
     * @return 含主表元数据 + 完整 {@code draft}（与解析/保存 JSON 结构一致）
     */
    ResumeDetailVO getResumeDetail(long userId, long resumeId);

    /**
     * 插件「一键填表」前置数据：返回当前简历 draft 中各可重复模块的条目数（与云端一致），
     * 供扩展在采 DOM 快照前自动点击「+添加」展开对应行数，避免仅靠浏览器缓存猜测。
     * <p>
     * 权限与 {@link #getResumeDetail(long, long)} 相同：简历须存在、未删且归属 userId。
     *
     * @param userId   当前用户
     * @param resumeId 简历主键
     */
    PluginAutofillResumeContextVO getAutofillResumeContext(long userId, long resumeId);
}
