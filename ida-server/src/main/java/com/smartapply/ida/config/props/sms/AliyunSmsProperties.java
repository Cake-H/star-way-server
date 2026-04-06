package com.smartapply.ida.config.props.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云号码认证 — 短信认证（OpenAPI {@code SendSmsVerifyCode}），非旧版短信服务 {@code dysmsapi SendSms}。
 * <p>
 * 签名、模板须与号码认证控制台「赠送」资源一致；AK/SK 用环境变量注入，勿提交仓库。
 */
@Data
@ConfigurationProperties(prefix = "ida.aliyun.sms")
public class AliyunSmsProperties {

    /** false 时不调阿里云，走 {@link com.smartapply.ida.integration.sms.LoggingSmsTemplateSender} 仅打日志（本地开发） */
    private boolean enabled = false;

    private String accessKeyId = "";

    private String accessKeySecret = "";

    /** 赠送签名名称（号码认证控制台配置） */
    private String signName = "";

    /** 赠送模板 CODE（与模板变量、{@code templateParam} JSON 一致） */
    private String templateCode = "";

    /** 可选；不填则走控制台默认方案 */
    private String schemeName = "";

    /** 号码认证 API 域名 */
    private String endpoint = "dypnsapi.aliyuncs.com";
}
