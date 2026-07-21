package com.smartapply.ida.module.app.billing.service;

import com.smartapply.ida.common.domain.billing.RedeemCode;
import com.smartapply.ida.common.domain.billing.vo.RedeemResultVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兑换码充值服务（M1.2）：测试期无需支付通道即可给种子用户发放额度。
 */
public interface RedeemCodeService {

    /**
     * 用户兑换额度。
     *
     * @param userId 当前登录用户
     * @param code   兑换码
     * @return 兑换结果（到账点数 + 兑换后余额）
     */
    RedeemResultVO redeem(Long userId, String code);

    /**
     * 批量生成兑换码（预留 M1.3 管理后台调用；测试期也可直接执行 SQL 示例）。
     *
     * @param count      生成数量
     * @param points     每张点数
     * @param expireTime 过期时间，null 表示永不过期
     * @param remark     备注
     * @param createdBy  创建人标识
     * @return 生成成功的兑换码列表
     */
    List<RedeemCode> generateCodes(int count, int points, LocalDateTime expireTime, String remark, String createdBy);
}
