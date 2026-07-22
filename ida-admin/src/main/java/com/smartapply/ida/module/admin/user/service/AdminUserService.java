package com.smartapply.ida.module.admin.user.service;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.admin.vo.AdminUserDetailVO;
import com.smartapply.ida.common.domain.admin.vo.AdminUserListItemVO;
import com.smartapply.ida.common.domain.admin.vo.QuotaAdjustResultVO;

/**
 * 管理后台用户管理服务：用户列表/详情、手动调整额度。
 */
public interface AdminUserService {

    /**
     * 用户分页列表。
     *
     * @param page    页码（从 1 开始）
     * @param size    每页大小（上限 100）
     * @param keyword 关键字（手机号模糊 / 用户 ID 精确），可空
     * @param status  状态筛选（1=正常 0=禁用），可空
     */
    PageVO<AdminUserListItemVO> pageUsers(long page, long size, String keyword, Integer status);

    /** 用户详情：基本信息 + 额度 + 简历数 + 近 30 天用量汇总 */
    AdminUserDetailVO getUserDetail(Long userId);

    /**
     * 手动调整用户额度（写 {@code admin_adjust} 额度流水，记录操作管理员）。
     *
     * @param adminId 操作管理员主键（审计用）
     * @param userId  目标用户
     * @param points  调整点数（正=充值，负=扣减）
     * @param remark  调整原因
     */
    QuotaAdjustResultVO adjustQuota(Long adminId, Long userId, Integer points, String remark);
}
