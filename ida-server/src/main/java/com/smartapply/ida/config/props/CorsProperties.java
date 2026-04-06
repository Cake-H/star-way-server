package com.smartapply.ida.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置：浏览器插件、本地桌面客户端需调用 REST，此处集中配置允许的来源模式。
 * <p>
 * 生产环境请将 {@code ida.cors.allowed-origin-patterns} 改为具体域名或扩展 ID，避免长期使用 {@code *}。
 */
@Data
@ConfigurationProperties(prefix = "ida.cors")
public class CorsProperties {

    /**
     * 等价于 Spring {@code allowedOriginPatterns}，例如 https://*.example.com、chrome-extension://xxxx
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

    /**
     * 浏览器可读响应头（CORS expose）；默认空。若前端 JS 需读自定义响应头，在此列出名称。
     */
    private List<String> exposedHeaders = new ArrayList<>();
}
