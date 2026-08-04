package com.smartapply.ida.module.app.user.service;

/**
 * 用户账号服务。
 */
public interface UserService {

    /**
     * 账号注销（合规要求）：匿名化手机号 + 逻辑删除用户 + 清理关联数据。
     * <p>
     * 处理内容：
     * <ol>
     *   <li>手机号改写为不可逆占位（释放唯一索引）、清空尾号盲索引、状态置禁用；</li>
     *   <li>{@code ida_user} 逻辑删除（deleted_at）；</li>
     *   <li>简历 9 张子表物理删除、主表逻辑删除；</li>
     *   <li>面试复盘、模拟面试场次逻辑删除；</li>
     *   <li>撤销当前登录 token。</li>
     * </ol>
     * OSS 简历原件的 objectKey 未持久化（仅解析响应透传），无法按用户枚举，
     * 依赖 Bucket 生命周期策略兜底清理。
     *
     * @param userId      当前登录用户
     * @param confirmText 确认文案，必须为「注销」
     * @param token       当前 Bearer token（用于撤销，可为空）
     */
    void deleteAccount(long userId, String confirmText, String token);
}
