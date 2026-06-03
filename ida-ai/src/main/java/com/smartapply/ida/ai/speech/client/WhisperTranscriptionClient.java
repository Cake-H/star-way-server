package com.smartapply.ida.ai.speech.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartapply.ida.ai.speech.config.SpeechToTextProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * OpenAI 兼容 {@code POST /v1/audio/transcriptions}，将录音转为纯文本（<b>非</b>大模型 Chat）。
 * <p>
 * 与 ida-app 中面试总结服务配合：先听写得到 transcript，再交给 Chat 做结构化。
 */
@RequiredArgsConstructor
public class WhisperTranscriptionClient {

    private final RestClient whisperRestClient;
    private final SpeechToTextProperties properties;

    /**
     * @return 听写正文；空串表示模型未识别出内容
     */
    public String transcribe(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("音频文件为空");
        }
        String apiKey = properties.resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置听写 API Key，请设置环境变量 " + properties.getEnvApiKey());
        }

        String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "audio.webm";
        byte[] bytes = file.getBytes();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : "application/octet-stream"));
        builder.part("model", properties.getModel());
        builder.part("language", "zh");

        String base = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();

        JsonNode root = whisperRestClient.post()
                .uri(base + "/audio/transcriptions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(JsonNode.class);

        if (root == null) {
            return "";
        }
        return root.path("text").asText("").trim();
    }
}
