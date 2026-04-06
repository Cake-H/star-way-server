package com.smartapply.ida.config.sms;

import com.smartapply.ida.config.props.sms.AliyunSmsProperties;
import com.smartapply.ida.integration.sms.AliyunSmsTemplateSender;
import com.smartapply.ida.integration.sms.LoggingSmsTemplateSender;
import com.smartapply.ida.integration.sms.SmsTemplateSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 装配 {@link SmsTemplateSender} 单例。
 * <ul>
 *   <li>{@code ida.aliyun.sms.enabled=true}：校验 AK/SK/签名/模板均已配置，使用号码认证 {@code SendSmsVerifyCode}。</li>
 *   <li>{@code enabled=false}：使用 {@link LoggingSmsTemplateSender}，本地可看日志中的验证码。</li>
 * </ul>
 */
@Configuration
public class SmsIntegrationConfiguration {

    @Bean
    public SmsTemplateSender smsTemplateSender(AliyunSmsProperties aliyunSmsProperties) throws Exception {
        if (aliyunSmsProperties.isEnabled()) {
            // 启动期强校验：避免 enabled=true 但缺项导致运行时才在发送接口失败
            if (!StringUtils.hasText(aliyunSmsProperties.getAccessKeyId())
                    || !StringUtils.hasText(aliyunSmsProperties.getAccessKeySecret())
                    || !StringUtils.hasText(aliyunSmsProperties.getSignName())
                    || !StringUtils.hasText(aliyunSmsProperties.getTemplateCode())) {
                throw new IllegalStateException(
                        "ida.aliyun.sms.enabled=true 时必须配置 access-key-id、access-key-secret、sign-name、template-code");
            }
            return new AliyunSmsTemplateSender(
                    AliyunSmsTemplateSender.createClient(aliyunSmsProperties),
                    aliyunSmsProperties);
        }
        // 本地/测试：不落真实短信，日志打印模板参数即可联调 Redis 与登录流程
        return new LoggingSmsTemplateSender();
    }
}
