package com.smartapply.ida.ai.speech.autoconfigure;

import com.smartapply.ida.ai.speech.client.AliyunNlsRealtimeTranscriptionClient;
import com.smartapply.ida.ai.speech.client.SpeechTranscriptionClient;
import com.smartapply.ida.ai.speech.config.SpeechToTextProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.web.multipart.MultipartFile;

/**
 * 阿里云智能语音交互 — 实时语音识别听写。
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.alibaba.nls.client.protocol.NlsClient")
@ConditionalOnExpression("${ida.speech-to-text.enabled:true} && '${ida.speech-to-text.provider:aliyun}' == 'aliyun'")
public class AliyunSpeechAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpeechTranscriptionClient.class)
    @ConditionalOnClass(MultipartFile.class)
    public SpeechTranscriptionClient aliyunSpeechTranscriptionClient(SpeechToTextProperties properties) {
        return new AliyunNlsRealtimeTranscriptionClient(properties);
    }
}
