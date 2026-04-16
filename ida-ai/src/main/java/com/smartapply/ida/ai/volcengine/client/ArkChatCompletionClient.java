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
     * 走 {@link VolcengineArkProperties#getResumeParse()} 的思考/推理参数（简历解析等），不下发 {@code max_tokens}。
     *
     * @param apiKey 已解析的非空 Bearer Token（不含 {@code Bearer } 前缀）
     */
    public JsonNode chatCompletions(String apiKey, String model, double temperature,
                                    List<Map<String, Object>> messages) {
        VolcengineArkProperties.ResumeParse rp = properties.getResumeParse();
        return chatCompletions(apiKey, model, temperature, messages,
                rp.getThinkingType(), rp.getReasoningEffort(), null, false);
    }

    /**
     * 显式指定 {@code thinking.type} 与 {@code reasoning_effort}；{@code maxTokens} 为正时写入请求体 {@code max_tokens}；
     * {@code responseFormatJsonObject} 为 true 时写入 {@code response_format}（需模型/接入点支持）。
     */
    public JsonNode chatCompletions(String apiKey, String model, double temperature,
                                    List<Map<String, Object>> messages,
                                    String thinkingType,
                                    String reasoningEffort,
                                    Integer maxTokens,
                                    boolean responseFormatJsonObject) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", messages);

        boolean hasThinking = StringUtils.hasText(thinkingType);
        if (hasThinking) {
            Map<String, Object> thinking = new HashMap<>();
            thinking.put("type", thinkingType.trim());
            body.put("thinking", thinking);
        }
        // InvalidParameter: reasoning_effort 与 thinking.type=disabled 不能组合
        if (hasThinking && !isThinkingDisabled(thinkingType) && StringUtils.hasText(reasoningEffort)) {
            body.put("reasoning_effort", reasoningEffort.trim());
        }
        if (maxTokens != null && maxTokens > 0) {
            body.put("max_tokens", maxTokens);
        }
        if (responseFormatJsonObject) {
            body.put("response_format", Map.of("type", "json_object"));
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
