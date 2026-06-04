package com.smartapply.ida.ai.speech.autoconfigure;

import com.smartapply.ida.ai.speech.client.SpeechTranscriptionClient;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * OpenAI Whisper 听写（{@code provider=whisper} 时生效）。
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnExpression("${ida.speech-to-text.enabled:true} && '${ida.speech-to-text.provider:aliyun}' == 'whisper'")
public class SpeechToTextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "whisperRestClient")
    public RestClient whisperRestClient(
            SpeechToTextProperties properties,
            @Value("${http.pool.max-total:20}") int maxTotal,
            @Value("${http.pool.connect-timeout:5000}") int connectTimeoutMs) {
        int readMs = properties.getWhisper().getReadTimeoutMs();
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
    @ConditionalOnMissingBean(SpeechTranscriptionClient.class)
    @ConditionalOnClass(MultipartFile.class)
    public SpeechTranscriptionClient whisperSpeechTranscriptionClient(
            @Qualifier("whisperRestClient") RestClient whisperRestClient,
            SpeechToTextProperties properties) {
        return new WhisperTranscriptionClient(whisperRestClient, properties);
    }
}
