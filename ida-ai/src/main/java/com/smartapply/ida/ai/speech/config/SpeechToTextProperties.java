package com.smartapply.ida.ai.speech.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 兼容 Whisper 听写配置（{@code POST /v1/audio/transcriptions}）。
 */
@Data
@ConfigurationProperties(prefix = "ida.speech-to-text")
public class SpeechToTextProperties {

    private boolean enabled = true;

    private String envApiKey = "OPENAI_API_KEY";

    private String baseUrl = "https://api.openai.com/v1";

    private String model = "whisper-1";

    private int readTimeoutMs = 120000;

    /**
     * 浏览器 {@code rawTranscript} 去空白后低于该长度时，才尝试对上传的 {@code file} 做 Whisper 听写。
     */
    private int minTranscriptChars = 10;

    public String resolveApiKey() {
        String name = envApiKey == null || envApiKey.isBlank() ? "OPENAI_API_KEY" : envApiKey.trim();
        String v = System.getenv(name);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        v = System.getProperty(name);
        return v != null && !v.isBlank() ? v.trim() : null;
    }
}
