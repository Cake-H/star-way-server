package com.smartapply.ida.integration.sms;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.smartapply.ida.config.props.sms.AliyunSmsProperties;
import darabonba.core.client.ClientOverrideConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.StringUtils;

/**
 * 阿里云号码认证 — 短信认证 {@link com.aliyun.sdk.service.dypnsapi20170525.AsyncClient#sendSmsVerifyCode}。
 * <p>
 * 与旧版「短信服务 dysmsapi SendSms」不同：须使用号码认证控制台
 * <a href="https://dypns.console.aliyun.com/smsCertParamsConfig/sign">赠送签名</a> 与
 * <a href="https://dypns.console.aliyun.com/smsCertParamsConfig/template">赠送模板</a>。
 * <p>
 * 成功条件：响应 body 中 {@code Code == OK} 且 {@code Success == true}。
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunSmsTemplateSender implements SmsTemplateSender, DisposableBean {

    private final AsyncClient client;
    private final AliyunSmsProperties properties;

    @Override
    public void sendVerificationCode(String phone, String templateParamJson) throws Exception {
        SendSmsVerifyCodeRequest.Builder b = SendSmsVerifyCodeRequest.builder()
                .phoneNumber(phone)
                .signName(properties.getSignName())
                .templateCode(properties.getTemplateCode())
                .templateParam(templateParamJson)
                .countryCode("86");
        if (StringUtils.hasText(properties.getSchemeName())) {
            b.schemeName(properties.getSchemeName());
        }
        SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(b.build()).get();
        if (response == null || response.getBody() == null) {
            throw new IllegalStateException("阿里云短信认证返回为空");
        }
        SendSmsVerifyCodeResponseBody body = response.getBody();
        String code = body.getCode();
        if (!"OK".equalsIgnoreCase(code)
                || (body.getSuccess() != null && !body.getSuccess())) {
            log.warn("阿里云短信认证发送失败 code={} message={} success={}", code, body.getMessage(), body.getSuccess());
            throw new IllegalStateException("短信发送失败: " + body.getMessage());
        }
    }

    @Override
    public void destroy() {
        try {
            client.close();
        } catch (Exception e) {
            log.debug("关闭 Dypns AsyncClient 时忽略异常", e);
        }
    }

    /**
     * 使用 AK/SK 构造 {@link AsyncClient}；endpoint 默认 {@code dypnsapi.aliyuncs.com}。
     */
    public static AsyncClient createClient(AliyunSmsProperties p) {
        StaticCredentialProvider provider = StaticCredentialProvider.create(Credential.builder()
                .accessKeyId(p.getAccessKeyId())
                .accessKeySecret(p.getAccessKeySecret())
                .build());
        return AsyncClient.builder()
                .credentialsProvider(provider)
                .overrideConfiguration(ClientOverrideConfiguration.create()
                        .setEndpointOverride(p.getEndpoint()))
                .build();
    }
}
