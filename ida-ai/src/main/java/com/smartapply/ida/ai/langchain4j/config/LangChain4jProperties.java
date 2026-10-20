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
     * 投递记录获取配置
     */
    private JobApplicationParseConfig jobApplicationParse = new JobApplicationParseConfig();
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

        /**
         * 是否打印模型原始请求体日志（排查用，含完整 prompt，体积大，仅排障时开启）。
         */
        private boolean logRequests = false;

        /**
         * 是否打印模型原始响应日志（排查解析失败/截断时开启，可看 finish_reason 与原始内容）。
         */
        private boolean logResponses = false;

        /**
         * 透传给网关的自定义请求参数（OpenAI 兼容协议的扩展字段，原样并入请求体）。
         * 例如关闭推理模型的思考：{@code thinking: {type: disabled}}（火山方舟/DeepSeek 网关）。
         */
        private java.util.Map<String, Object> customParameters;
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
     * 投递信息获取专用配置
     */
    @Data
    public static class JobApplicationParseConfig extends ModelConfig {
        private int maxPlainTextChars = 8000;
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
