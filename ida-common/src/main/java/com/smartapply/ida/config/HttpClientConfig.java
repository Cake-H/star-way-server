package com.smartapply.ida.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 对外 HTTP 客户端（调用大模型、短信等第三方 API）。
 * <p>
 * 思路与 SmartAdmin 模板中 {@code RestClientConfig} 一致：连接池 + 超时，避免默认实现无界阻塞。
 */
@Configuration
public class HttpClientConfig {

    /** 连接池最大连接数 */
    @Value("${http.pool.max-total:20}")
    private int maxTotal;

    /** TCP 建连超时（毫秒） */
    @Value("${http.pool.connect-timeout:5000}")
    private int connectTimeoutMs;

    /** 读响应超时（毫秒），含大模型等慢接口 */
    @Value("${http.pool.read-timeout:30000}")
    private int readTimeoutMs;

    @Bean
    public RestClient restClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(Math.max(2, maxTotal / 4));

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();

        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
