package com.smartapply.ida.ai.mockInterview;

import com.smartapply.ida.ai.langchain4j.assistant.MockInterviewGenerateAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.MockInterviewPromptProvider;
import com.smartapply.ida.ai.usage.AiUsageBizType;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewGenerateResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewGenerateAiService {
    private final MockInterviewGenerateAssistant assistant;
    private final MockInterviewPromptProvider promptProvider;
    private final BillingService billingService;

    public MockInterviewGenerateResultVO generateQuestions(long userId, String resumeContext) {
        String userMessage = promptProvider.buildUserMessage(resumeContext);
        try {
            return billingService.execute(
                    userId, AiUsageBizType.MOCK_INTERVIEW_GENERATE, null,
                    () -> assistant.generateQuestions(MockInterviewPromptProvider.SYSTEM, userMessage));
        } catch (BusinessException e) {
            // 额度不足等业务异常直接透传
            throw e;
        } catch (Exception e) {
            log.error("生成模拟面试题目失败", e);
            throw new BusinessException("生成模拟面试题目失败", e);
        }
    }
}
