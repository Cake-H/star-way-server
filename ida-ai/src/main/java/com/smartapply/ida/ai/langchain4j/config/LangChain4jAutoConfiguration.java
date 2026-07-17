package com.smartapply.ida.ai.langchain4j.config;

import com.smartapply.ida.ai.langchain4j.assistant.InterviewSummarizeAssistant;
import com.smartapply.ida.ai.langchain4j.assistant.MockInterviewGenerateAssistant;
import com.smartapply.ida.ai.langchain4j.assistant.PluginAutofillAssistant;
import com.smartapply.ida.ai.langchain4j.assistant.ResumeParseAssistant;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * LangChain4j 自动配置。
 * <p>
 * 为不同业务场景创建专用 ChatModel 和 AI Service，
 * 与现有 volcengine 模块并存，渐进式迁移。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(LangChain4jProperties.class)
@RequiredArgsConstructor
public class LangChain4jAutoConfiguration {

    private final LangChain4jProperties properties;

    // ==================== ChatModel Beans ====================

    @Bean("resumeParseChatModel")
    @ConditionalOnMissingBean(name = "resumeParseChatModel")
    public OpenAiChatModel resumeParseChatModel() {
        return buildChatModel(properties.getResumeParse(), "简历解析");
    }

    @Bean("pluginAutofillChatModel")
    @ConditionalOnMissingBean(name = "pluginAutofillChatModel")
    public OpenAiChatModel pluginAutofillChatModel() {
        return buildChatModel(properties.getPluginAutofill(), "一键填表");
    }

    @Bean("interviewSummarizeChatModel")
    @ConditionalOnMissingBean(name = "interviewSummarizeChatModel")
    public OpenAiChatModel interviewSummarizeChatModel() {
        return buildChatModel(properties.getInterviewSummarize(), "面试总结");
    }

    @Bean("mockInterviewChatModel")
    @ConditionalOnMissingBean(name = "mockInterviewChatModel")
    public OpenAiChatModel mockInterviewChatModel() {
        return buildChatModel(properties.getMockInterview(), "模拟面试题目生成");
    }

    // ==================== AI Service Beans ====================

    @Bean
    @ConditionalOnMissingBean
    public PluginAutofillAssistant pluginAutofillAssistant(
            @Qualifier("pluginAutofillChatModel") OpenAiChatModel chatModel) {
        log.info("[LangChain4j] 创建 PluginAutofillAssistant");
        return AiServices.builder(PluginAutofillAssistant.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public InterviewSummarizeAssistant interviewSummarizeAssistant(
            @Qualifier("interviewSummarizeChatModel") OpenAiChatModel chatModel) {
        log.info("[LangChain4j] 创建 InterviewSummarizeAssistant");
        return AiServices.builder(InterviewSummarizeAssistant.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResumeParseAssistant resumeParseAssistant(
            @Qualifier("resumeParseChatModel") OpenAiChatModel chatModel) {
        log.info("[LangChain4j] 创建 ResumeParseAssistant");
        return AiServices.builder(ResumeParseAssistant.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public MockInterviewGenerateAssistant mockInterviewGenerateAssistant(
            @Qualifier("mockInterviewChatModel") OpenAiChatModel chatModel) {
        log.info("[LangChain4j] 创建 MockInterviewGenerateAssistant");
        return AiServices.builder(MockInterviewGenerateAssistant.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    // ==================== Private Methods ====================

    private OpenAiChatModel buildChatModel(LangChain4jProperties.ModelConfig config, String sceneName) {
        String apiKey = properties.resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[LangChain4j] {} 未配置 API Key，请设置环境变量 {}",
                    sceneName, properties.getEnvApiKeyName());
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(apiKey != null ? apiKey : "placeholder")
                .modelName(config.getModel())
                .temperature(config.getTemperature())
                .timeout(Duration.ofMillis(config.getReadTimeoutMs()));

        if (config.getMaxTokens() != null && config.getMaxTokens() > 0) {
            builder.maxTokens(config.getMaxTokens());
        }

        if (config.isResponseFormatJsonObject()) {
            builder.responseFormat("json_object");
        }

        log.info("[LangChain4j] 已创建 {} ChatModel: baseUrl={}, model={}, temperature={}",
                sceneName, properties.getBaseUrl(), config.getModel(), config.getTemperature());

        return builder.build();
    }
}
