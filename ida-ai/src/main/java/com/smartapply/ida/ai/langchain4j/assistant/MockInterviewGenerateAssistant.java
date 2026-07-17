package com.smartapply.ida.ai.langchain4j.assistant;

import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewGenerateResultVO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 模拟面试题目生成 AI Service
 * <p>
 *     输入简历上下文，输出结构化面试题列表
 * </p>
 */
public interface MockInterviewGenerateAssistant {

    /**
     * 根据简历上下文生成面试题列表
     *
     * @param systemPrompt 系统提示词
     * @param userMessage 用户信息（含简历上下文）
     * @return 结构化题目列表
     */
    @SystemMessage("{{systemPrompt}}")
    MockInterviewGenerateResultVO generateQuestions(
            @V("systemPrompt") String systemPrompt,
            @UserMessage String userMessage
    );
}
