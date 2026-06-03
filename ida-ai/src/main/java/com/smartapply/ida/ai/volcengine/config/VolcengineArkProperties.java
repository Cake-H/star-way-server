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

    /**
     * 浏览器插件「一键填表」：根据简历 JSON 与页面控件快照生成填值计划（与 {@link ResumeParse} 提示词/模型可分开调优）。
     */
    private PluginAutofill pluginAutofill = new PluginAutofill();

    /**
     * 正式客户端「面试复盘」：口述/转写文本 → 场次元信息 + 多题结构化 JSON。
     */
    private InterviewSummarize interviewSummarize = new InterviewSummarize();

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

    @Data
    public static class PluginAutofill {
        /**
         * 专用模型 ID；留空则沿用 {@link ResumeParse#getModel()}。
         */
        private String model = "";
        private double temperature = 0.15;

        /**
         * 对应请求体 {@code thinking.type}。与简历解析独立配置；填表默认开启低强度思考以提升对齐质量。
         * 留空则不下发 {@code thinking} 字段。
         */
        private String thinkingType = "enabled";

        /**
         * 对应请求体 {@code reasoning_effort}，如 {@code low}、{@code minimal}；
         * 仅在 {@code thinkingType} 非 {@code disabled} 且非空时下发。
         */
        private String reasoningEffort = "low";

        /**
         * 对应请求体 {@code max_tokens}。填表 JSON 控件多且开启思考时，默认上限易截断导致非法 JSON，故单独设较大默认值；
         * 设为 0 或负数表示不下发该字段（沿用接口默认）。
         */
        private int maxTokens = 16384;

        /**
         * 为 true 时在请求体中下发 {@code response_format: {type: json_object}}（OpenAI 兼容），约束模型只输出合法 JSON 对象；
         * 若当前接入点/模型报错不支持，可改为 false 仅依赖提示词。
         */
        private boolean responseFormatJsonObject = true;
    }

    @Data
    public static class InterviewSummarize {
        /** 留空则回落 {@link ResumeParse#getModel()} */
        private String model = "";
        private double temperature = 0.3;
        private String thinkingType = "disabled";
        private String reasoningEffort = "";
        /** 多题 + 长评价时建议 ≥8192，避免 JSON 截断 */
        private int maxTokens = 8192;
        private boolean responseFormatJsonObject = true;
        /** 参与 {@link com.smartapply.ida.ai.volcengine.autoconfigure.VolcengineArkAutoConfiguration} 中 arkRestClient 读超时取 max */
        private int readTimeoutMs = 120000;
    }

    /** 从环境中解析 API Key；未配置时返回 null */
    public String resolveApiKey() {
        String name = envApiKey == null || envApiKey.isBlank() ? "ARK_API_KEY" : envApiKey.trim();
        // 优先：操作系统环境变量（生产推荐）
        String v = System.getenv(name);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        // 次选：JVM -D 同名属性（本地 IDE 运行常用）
        v = System.getProperty(name);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        // 兼容历史固定键名
        v = System.getProperty("ark.api.key");
        return v != null && !v.isBlank() ? v.trim() : null;
    }
}
