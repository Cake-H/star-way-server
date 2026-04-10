package com.smartapply.ida.ai.volcengine.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartapply.ida.ai.volcengine.config.VolcengineArkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用火山方舟 OpenAPI 兼容的 {@code /chat/completions}，返回原始 JSON 响应体。
 */
@RequiredArgsConstructor
public class ArkChatCompletionClient {

    private final RestClient arkRestClient;
    private final VolcengineArkProperties properties;

    /**
     * @param apiKey 已解析的非空 Bearer Token（不含 {@code Bearer } 前缀）
     */
    public JsonNode chatCompletions(String apiKey, String model, double temperature,
                                    List<Map<String, Object>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", messages);

        VolcengineArkProperties.ResumeParse rp = properties.getResumeParse();
        boolean hasThinking = StringUtils.hasText(rp.getThinkingType());
        if (hasThinking) {
            Map<String, Object> thinking = new HashMap<>();
            thinking.put("type", rp.getThinkingType().trim());
            body.put("thinking", thinking);
        }
        // InvalidParameter: reasoning_effort 与 thinking.type=disabled 不能组合
        if (hasThinking && !isThinkingDisabled(rp.getThinkingType()) && StringUtils.hasText(rp.getReasoningEffort())) {
            body.put("reasoning_effort", rp.getReasoningEffort().trim());
        }

        return arkRestClient.post()
                .uri(properties.getBaseUrl() + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private static boolean isThinkingDisabled(String thinkingType) {
        return thinkingType != null && "disabled".equalsIgnoreCase(thinkingType.trim());
    }
}
