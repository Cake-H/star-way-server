package com.smartapply.ida.ai.volcengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 火山方舟（豆包等）HTTP 调用配置。
 * <p>
 * API Key：在控制台「API Key 管理」创建，通过环境变量注入（见 {@link # getEnvApiKeyName()}）。
 */
@Data
@ConfigurationProperties(prefix = "ida.volcengine-ark")
public class VolcengineArkProperties {

    /**
     * 存放 API Key 的环境变量<b>名称</b>，默认 {@code ARK_API_KEY}。
     */
    private String envApiKey = "ARK_API_KEY";

    /**
     * OpenAPI 兼容网关根路径，不含尾斜杠。
     */
    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";

    /** 简历纯文本 → 结构化 JSON，走 {@code /chat/completions} */
    private ResumeParse resumeParse = new ResumeParse();

    @Data
    public static class ResumeParse {
        private String model = "doubao-seed-2-0-lite-260215";
        private int maxPlainTextChars = 80000;
        private double temperature = 0.2;
        /**
         * 等待方舟返回完整 HTTP 响应的读超时（毫秒），仅作用于方舟专用 {@link org.springframework.web.client.RestClient}。
         * 长简历或大 JSON 输出可能超过 2 分钟，默认 5 分钟；仍超时可在配置中继续加大。
         */
        private int readTimeoutMs = 300000;

        /**
         * 对应请求体 {@code thinking.type}（深度思考开关）。如 {@code disabled} 关闭链式推理以加快响应；
         * 留空则不下发 {@code thinking} 字段。
         */
        private String thinkingType = "disabled";

        /**
         * 对应请求体 {@code reasoning_effort}。仅在与 {@code thinking.type} 组合合法时下发：
         * 方舟不允许 {@code disabled} 与 {@code reasoning_effort} 同时出现，故 {@code thinkingType} 为 {@code disabled} 时本字段会被忽略。
         * 需在开启思考（如 {@code enabled}）时再配置 {@code minimal}/{@code low} 等。
         */
        private String reasoningEffort = "";
    }

    /** 从环境中解析 API Key；未配置时返回 null */
    public String resolveApiKey() {
        String name = envApiKey == null || envApiKey.isBlank() ? "ARK_API_KEY" : envApiKey.trim();
        String v = System.getenv(name);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        v = System.getProperty(name);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        v = System.getProperty("ark.api.key");
        return v != null && !v.isBlank() ? v.trim() : null;
    }
}
