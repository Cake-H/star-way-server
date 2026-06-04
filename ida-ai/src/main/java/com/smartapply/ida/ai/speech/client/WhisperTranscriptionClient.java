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
 */
@RequiredArgsConstructor
public class WhisperTranscriptionClient implements SpeechTranscriptionClient {

    private final RestClient whisperRestClient;
    private final SpeechToTextProperties properties;

    @Override
    public String transcribe(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("音频文件为空");
        }
        SpeechToTextProperties.Whisper whisper = properties.getWhisper();
        String apiKey = whisper.resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "未配置 Whisper API Key，请设置环境变量 " + whisper.getEnvApiKey());
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
        builder.part("model", whisper.getModel());
        builder.part("language", "zh");

        String base = whisper.getBaseUrl().endsWith("/")
                ? whisper.getBaseUrl().substring(0, whisper.getBaseUrl().length() - 1)
                : whisper.getBaseUrl();

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
