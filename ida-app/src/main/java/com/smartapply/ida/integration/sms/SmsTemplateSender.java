package com.smartapply.ida.integration.sms;

/**
 * 短信发送网关接口：业务层只依赖本接口，便于切换阿里云、腾讯云或本地 Mock。
 */
public interface SmsTemplateSender {

    /**
     * 发送验证码类短信。
     *
     * @param phoneE164OrDomestic 阿里云国内号可直接传 11 位手机号
     * @param templateParamJson   与控制台模板变量一致的 JSON；号码认证赠送模板通常为 {@code {"code":"...","min":"5"}}（分钟）
     */
    void sendVerificationCode(String phoneE164OrDomestic, String templateParamJson) throws Exception;
}
