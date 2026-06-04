package com.smartapply.ida.ai.speech.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 听写总配置：通过 {@link #provider} 在阿里云实时识别与 OpenAI Whisper 之间切换。
 */
@Data
@ConfigurationProperties(prefix = "ida.speech-to-text")
public class SpeechToTextProperties {

    private boolean enabled = true;

    /** {@code aliyun}：智能语音交互实时识别；{@code whisper}：OpenAI 兼容 {@code /audio/transcriptions} */
    private String provider = "aliyun";

    /**
     * 浏览器 {@code rawTranscript} 去空白后低于该长度时，才对上传的 {@code file} 做服务端听写。
     */
    private int minTranscriptChars = 10;

    private Whisper whisper = new Whisper();

    private Aliyun aliyun = new Aliyun();

    @Data
    public static class Whisper {

        private String envApiKey = "OPENAI_API_KEY";

        private String baseUrl = "https://api.openai.com/v1";

        private String model = "whisper-1";

        private int readTimeoutMs = 120000;

        public String resolveApiKey() {
            return resolveEnvOrProperty(envApiKey, "OPENAI_API_KEY");
        }
    }

    @Data
    public static class Aliyun {

        /** 智能语音交互控制台项目 AppKey */
        private String appKey = "";

        /** 与 {@code ida.aliyun.sms/oss} 一致，由 yml {@code ${ALIBABA_ACCESS_KEY_ID:}} 注入 */
        private String accessKeyId = "";

        private String accessKeySecret = "";

        /** 如 {@code cn-shanghai}，用于拼接网关与获取 Token */
        private String regionId = "cn-shanghai";

        /** 留空则使用 {@code wss://nls-gateway-{regionId}.aliyuncs.com/ws/v1} */
        private String gatewayUrl = "";

        private int sampleRate = 16000;

        /**
         * 上传音频在实时识别中的格式：{@code pcm}、{@code wav}、{@code mp3}、{@code opus}。
         * 留空则按 Content-Type 推断。
         */
        private String defaultFormat = "";

        private int readTimeoutMs = 120000;

        /** 分片发送字节数（实时识别建议 3200 左右） */
        private int sendChunkBytes = 3200;

        public String resolveGatewayUrl() {
            if (gatewayUrl != null && !gatewayUrl.isBlank()) {
                return gatewayUrl.trim();
            }
            String region = regionId == null || regionId.isBlank() ? "cn-shanghai" : regionId.trim();
            return "wss://nls-gateway-" + region + ".aliyuncs.com/ws/v1";
        }
    }

    private static String resolveEnvOrProperty(String envName, String defaultName) {
        String name = envName == null || envName.isBlank() ? defaultName : envName.trim();
        String v = System.getenv(name);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        v = System.getProperty(name);
        return v != null && !v.isBlank() ? v.trim() : null;
    }
}
