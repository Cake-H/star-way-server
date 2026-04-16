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
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
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
    public ResumeParseDraftDto structure(String plainText) {
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

        JsonNode root;
        try {
            root = arkChatCompletionClient.chatCompletions(apiKey, model, temperature, messages);
        } catch (ResourceAccessException e) {
            throw translateArkNetworkFailure(e);
        }

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

    /**
     * 将 Spring {@link org.springframework.web.client.RestClient} 的网络层异常转为业务提示，避免前端只看到冗长 {@link ResourceAccessException} 栈。
     * <p>
     * 常见根因：本机 DNS 无法解析 {@code ark.cn-beijing.volces.com}、无公网、公司防火墙拦截 HTTPS、需系统代理/VPN。
     */
    private BusinessException translateArkNetworkFailure(ResourceAccessException e) {
        String baseUrl = arkProperties.getBaseUrl();
        String host = extractHostForMessage(baseUrl);
        Throwable cause = e.getCause() != null ? e.getCause() : e;

        if (cause instanceof UnknownHostException) {
            log.warn("火山方舟域名无法解析 host={} baseUrl={}", host, baseUrl, e);
            return new BusinessException(
                    "无法访问火山方舟：域名解析失败（UnknownHost），系统无法找到主机「" + host
                            + "」。请在本机终端执行 nslookup 或 ping 该域名确认 DNS/网络；若在公司内网，尝试 VPN 或放行出站 HTTPS。"
                            + " 配置项 ida.volcengine-ark.base-url 当前为：" + baseUrl);
        }
        if (cause instanceof ConnectException) {
            log.warn("火山方舟 TCP 连接失败 host={} baseUrl={}", host, baseUrl, e);
            return new BusinessException(
                    "无法连接火山方舟：TCP 连接被拒绝或超时。请检查本机/防火墙是否允许访问「" + host
                            + "」的 HTTPS（443）。当前 base-url=" + baseUrl);
        }

        log.warn("火山方舟 HTTP 客户端 I/O 失败 baseUrl={}", baseUrl, e);
        String detail = cause.getMessage() != null ? cause.getMessage() : e.getMessage();
        return new BusinessException(
                "调用火山方舟接口失败（网络 I/O）：" + detail + "。请确认可访问公网且未拦截对「" + host + "」的请求。当前 base-url=" + baseUrl);
    }

    private static String extractHostForMessage(String baseUrl) {
        try {
            URI u = URI.create(baseUrl);
            return u.getHost() != null ? u.getHost() : baseUrl;
        } catch (Exception ignored) {
            return baseUrl;
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
