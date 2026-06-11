package com.smartapply.ida.ai.langchain4j.assistant;

import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 插件「一键填表」AI Service。
 * <p>
 * 基于 LangChain4j AI Services 声明式定义，框架自动处理：
 * <ul>
 *   <li>JSON 结构化输出 → 直接返回 {@link PluginAutofillPlanResultVO}</li>
 *   <li>Markdown 代码块清洗</li>
 *   <li>JSON 解析异常重试</li>
 * </ul>
 * <p>
 * 输入输出与原 {@link com.smartapply.ida.ai.plugin.PluginFormAutofillAiService} 完全一致。
 */
public interface PluginAutofillAssistant {

    /**
     * 生成填表计划。
     *
     * @param systemPrompt 系统提示词（由 PluginAutofillPromptProvider.SYSTEM 提供）
     * @param userMessage  用户消息（PART_A + PART_B + PART_C）
     * @return 填表计划结果，直接映射为强类型对象
     */
    @SystemMessage("{{systemPrompt}}")
    PluginAutofillPlanResultVO planAutofill(
            @V("systemPrompt") String systemPrompt,
            @UserMessage String userMessage
    );
}
