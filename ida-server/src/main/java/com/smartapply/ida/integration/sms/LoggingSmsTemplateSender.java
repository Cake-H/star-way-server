package com.smartapply.ida.integration.sms;

import lombok.extern.slf4j.Slf4j;

/**
 * 开发/联调用实现：不调用外网，仅 WARN 打印目标手机号与模板 JSON（含明文验证码，切勿在生产开启）。
 */
@Slf4j
public class LoggingSmsTemplateSender implements SmsTemplateSender {

    @Override
    public void sendVerificationCode(String phone, String templateParamJson) {
        // WARN 级别便于在默认日志级别下看到验证码；生产切勿将 enabled=false 误部署到线上
        log.warn("[DEV] 模拟发送短信 phone={} param={}", phone, templateParamJson);
    }
}
