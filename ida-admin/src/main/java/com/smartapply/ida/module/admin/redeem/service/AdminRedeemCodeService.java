package com.smartapply.ida.module.admin.redeem.service;

import com.smartapply.ida.common.domain.PageVO;
import com.smartapply.ida.common.domain.admin.vo.RedeemCodeListItemVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台兑换码管理服务：批量生成、分页列表、禁用。
 */
public interface AdminRedeemCodeService {

    /**
     * 批量生成兑换码。
     *
     * @param adminId    操作管理员主键（记录生成人）
     * @param count      数量
     * @param points     每张点数
     * @param expireTime 过期时间，null 永不过期
     * @param remark     备注
     * @return 生成的兑换码列表（明文码仅此一次返回，请提示运营及时导出保存）
     */
    List<RedeemCodeListItemVO> generateCodes(Long adminId, int count, int points, LocalDateTime expireTime, String remark);

    /** 兑换码分页列表 */
    PageVO<RedeemCodeListItemVO> pageCodes(long page, long size, String status);

    /** 禁用一张未使用的兑换码（已使用/已禁用的不可重复操作） */
    void disableCode(Long adminId, Long codeId);
}
