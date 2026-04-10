package com.smartapply.ida.ai.volcengine.autoconfigure;

import com.smartapply.ida.ai.volcengine.client.ArkChatCompletionClient;
import com.smartapply.ida.ai.volcengine.config.VolcengineArkProperties;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 方舟大模型专用 {@link RestClient}：读超时单独拉长；与业务侧通用 {@code restClient} Bean 分离。
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(VolcengineArkProperties.class)
public class VolcengineArkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "arkRestClient")
    public RestClient arkRestClient(
            VolcengineArkProperties volcengineArkProperties,
            @Value("${http.pool.max-total:20}") int maxTotal,
            @Value("${http.pool.connect-timeout:5000}") int connectTimeoutMs) {
        int readMs = volcengineArkProperties.getResumeParse().getReadTimeoutMs();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(Math.max(2, maxTotal / 4));

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readMs))
                .build();

        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readMs);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ArkChatCompletionClient arkChatCompletionClient(
            @Qualifier("arkRestClient") RestClient arkRestClient,
            VolcengineArkProperties volcengineArkProperties) {
        return new ArkChatCompletionClient(arkRestClient, volcengineArkProperties);
    }
}
