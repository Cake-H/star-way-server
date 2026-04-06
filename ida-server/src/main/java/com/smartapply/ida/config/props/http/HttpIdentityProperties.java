package com.smartapply.ida.config.props.http;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 从 HTTP 请求头识别调用方身份时的头名称（网关 / 插件 / 桌面端统一约定）。
 * <p>
 * 默认值写在类上；若需覆盖仅在 yml 写 {@code ida.http.identity.user-id-header: ...}。
 */
@Data
@ConfigurationProperties(prefix = "ida.http.identity")
public class HttpIdentityProperties {

    /**
     * 用户主键（数字），由可信上游（登录服务、网关）写入；本服务仅解析，不校验签名。
     */
    private String userIdHeader = "X-User-Id";

    /**
     * 可选：链路追踪或设备 id，仅占位，供后续过滤器使用。
     */
    private String requestIdHeader = "X-Request-Id";

    /**
     * 携带登录令牌：{@code Authorization: Bearer &lt;token&gt;}
     */
    private String authorizationHeader = "Authorization";
}
