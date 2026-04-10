package com.smartapply.ida.ai.resume;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.volcengine.client.ArkChatCompletionClient;
import com.smartapply.ida.ai.volcengine.config.VolcengineArkProperties;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 调用火山方舟 {@code /chat/completions}，将简历纯文本转为 {@link ResumeParseDraftDto}。
 * <p>
 * 通用 HTTP 与配置见 {@code ida-ai} 模块；本类仅负责简历领域提示词与 DTO 映射。
 */
@Slf4j
@Service
public class ResumeArkStructuringService {

    private final ArkChatCompletionClient arkChatCompletionClient;
    private final VolcengineArkProperties arkProperties;
    private final ObjectMapper objectMapper;

    public ResumeArkStructuringService(
            ArkChatCompletionClient arkChatCompletionClient,
            VolcengineArkProperties arkProperties,
            ObjectMapper objectMapper) {
        this.arkChatCompletionClient = arkChatCompletionClient;
        this.arkProperties = arkProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * @param plainText 已截断后的简历全文
     */
    public ResumeParseDraftDto structure(String plainText) throws Exception {
        String apiKey = arkProperties.resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("未配置火山方舟 API Key，请设置环境变量 " + arkProperties.getEnvApiKey());
        }

        String model = arkProperties.getResumeParse().getModel();
        double temperature = arkProperties.getResumeParse().getTemperature();

        String userBody = ResumeParsePrompts.USER_INSTRUCTION + plainText + ResumeParsePrompts.USER_SUFFIX;

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", ResumeParsePrompts.SYSTEM.trim()),
                Map.of("role", "user", "content", userBody)
        );

        JsonNode root = arkChatCompletionClient.chatCompletions(apiKey, model, temperature, messages);

        if (root == null) {
            throw new BusinessException("方舟接口返回空响应");
        }
        JsonNode err = root.get("error");
        if (err != null && !err.isNull()) {
            String msg = err.path("message").asText("方舟接口错误");
            log.warn("方舟 chat/completions 错误: {}", root);
            throw new BusinessException("简历智能解析失败：" + msg);
        }

        String content = root.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            log.warn("方舟返回无 choices[0].message.content: {}", root);
            throw new BusinessException("模型未返回可解析内容");
        }

        String json = stripMarkdownFence(content.trim());
        try {
            return objectMapper.readValue(json, ResumeParseDraftDto.class);
        } catch (JsonProcessingException e) {
            log.warn("模型输出非合法 JSON，前 200 字: {}", json.length() > 200 ? json.substring(0, 200) : json, e);
            throw new BusinessException("模型返回内容不是可解析的 JSON，请重试或简化简历格式");
        }
    }

    /** 部分模型仍习惯输出 ```json ... ```，此处去掉围栏 */
    private static String stripMarkdownFence(String raw) {
        String s = raw;
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int end = s.lastIndexOf("```");
            if (end > 0) {
                s = s.substring(0, end);
            }
        }
        return s.trim();
    }
}
