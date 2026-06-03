package com.smartapply.ida.ai.speech.autoconfigure;

import com.smartapply.ida.ai.speech.client.WhisperTranscriptionClient;
import com.smartapply.ida.ai.speech.config.SpeechToTextProperties;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Whisper 听写专用 {@link RestClient}（读超时见 {@link SpeechToTextProperties#getReadTimeoutMs()}），与方舟 Chat 客户端分离。
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "ida.speech-to-text", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpeechToTextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "whisperRestClient")
    public RestClient whisperRestClient(
            SpeechToTextProperties properties,
            @Value("${http.pool.max-total:20}") int maxTotal,
            @Value("${http.pool.connect-timeout:5000}") int connectTimeoutMs) {
        int readMs = properties.getReadTimeoutMs();
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
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MultipartFile.class)
    public WhisperTranscriptionClient whisperTranscriptionClient(
            @Qualifier("whisperRestClient") RestClient whisperRestClient,
            SpeechToTextProperties properties) {
        return new WhisperTranscriptionClient(whisperRestClient, properties);
    }
}
