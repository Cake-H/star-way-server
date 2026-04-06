package com.smartapply.ida.module.plugin.auth.service;

import com.smartapply.ida.common.domain.auth.vo.SmsLoginResultVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 正式端短信登录领域入口：与 {@link SmsVerificationService}、{@link LoginSessionService} 协作。
 * <p>
 * 业务约定：手机号在库中经 {@link com.smartapply.ida.security.crypto.phone.PhoneCrypto} 加密后存储，查询条件与写入必须一致。
 */
public interface UserAuthService {

    /**
     * 发送登录验证码：含 Redis 频控与短信通道调用；业务异常以 {@link com.smartapply.ida.common.exception.BusinessException} 抛出。
     *
     * @param phone 已 trim 的国内手机号（Controller 层已通过 Bean 校验格式）
     */
    void sendLoginCode(String phone);

    /**
     * 校验验证码（成功后立即作废），按加密手机号查用户；不存在则插入新用户，再签发会话 Token。
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @return 令牌、用户 id、是否新用户、过期秒数
     */
    SmsLoginResultVO loginBySms(String phone, String code);

    void logout(HttpServletRequest request);
}
