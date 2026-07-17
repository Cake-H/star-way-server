package com.smartapply.ida.ai.langchain4j.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LangChain4j 配置属性。
 * <p>
 * 统一管理大模型调用配置，替代原 volcengine 模块。
 */
@Data
@ConfigurationProperties(prefix = "ida.langchain4j")
public class LangChain4jProperties {

    /**
     * API Key 环境变量名称。
     */
    private String envApiKeyName = "ARK_API_KEY_DEEPSEEK";

    /**
     * OpenAI 兼容网关根路径。
     */
    private String baseUrl = "https://api.deepseek.com";

    /**
     * 简历解析配置。
     */
    private ResumeParseConfig resumeParse = new ResumeParseConfig();

    /**
     * 插件一键填表配置。
     */
    private ModelConfig pluginAutofill = new ModelConfig();

    /**
     * 面试复盘总结配置。
     */
    private ModelConfig interviewSummarize = new ModelConfig();

    /**
     * 模拟面试题目生成配置
     */
    private ModelConfig mockInterview = new ModelConfig();
    /**
     * 通用模型配置。
     */
    @Data
    public static class ModelConfig {
        /**
         * 模型 ID。
         */
        private String model = "deepseek-v4-flash";

        /**
         * 温度参数。
         */
        private double temperature = 0.2;

        /**
         * 最大输出 token 数。
         */
        private Integer maxTokens;

        /**
         * 读超时（毫秒）。
         */
        private int readTimeoutMs = 300000;

        /**
         * 是否启用 JSON 模式。
         */
        private boolean responseFormatJsonObject = true;
    }

    /**
     * 简历解析专用配置（含纯文本长度限制）。
     */
    @Data
    public static class ResumeParseConfig extends ModelConfig {
        /**
         * 简历纯文本最大字符数，超出则截断。
         */
        private int maxPlainTextChars = 80000;
    }

    /**
     * 从环境中解析 API Key。
     */
    public String resolveApiKey() {
        String name = envApiKeyName == null || envApiKeyName.isBlank()
            ? "ARK_API_KEY_DEEPSEEK"
            : envApiKeyName.trim();
        String v = System.getenv(name);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        v = System.getProperty(name);
        return v != null && !v.isBlank() ? v.trim() : null;
    }
}
