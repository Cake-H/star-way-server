package com.smartapply.ida.ai.mockInterview;

import com.smartapply.ida.ai.langchain4j.assistant.MockInterviewGenerateAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.MockInterviewPromptProvider;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewGenerateResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewGenerateAiService {
    private final MockInterviewGenerateAssistant assistant;
    private final MockInterviewPromptProvider promptProvider;

    public MockInterviewGenerateResultVO generateQuestions(String resumeContext) {
        String userMessage = promptProvider.buildUserMessage(resumeContext);
        try {
            return assistant.generateQuestions(MockInterviewPromptProvider.SYSTEM, userMessage);
        } catch (Exception e) {
            log.error("生成模拟面试题目失败", e);
            throw new BusinessException("生成模拟面试题目失败", e);
        }
    }
}
