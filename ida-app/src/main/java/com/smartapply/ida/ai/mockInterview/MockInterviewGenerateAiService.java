package com.smartapply.ida.ai.mockInterview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.langchain4j.assistant.MockInterviewGenerateAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.MockInterviewPromptProvider;
import com.smartapply.ida.ai.usage.AiUsageBizType;
import com.smartapply.ida.common.domain.mockInterview.vo.MockInterviewGenerateResultVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.security.mask.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 模拟面试题目生成（LangChain4j）。
 * <p>
 * <b>隐私（1.4 基线）：</b>简历上下文含手机号/身份证/邮箱等 PII，发送 LLM 前替换为
 * {@code [PHONE_1]} 等占位符（映射仅存本次请求内存），模型返回后还原为真值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewGenerateAiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockInterviewGenerateAssistant assistant;
    private final MockInterviewPromptProvider promptProvider;
    private final BillingService billingService;

    public MockInterviewGenerateResultVO generateQuestions(long userId, String resumeContext) {
        // 隐私：prompt 前脱敏（手机号/身份证/邮箱 → 占位符）
        SensitiveDataMasker.MaskSession maskSession = SensitiveDataMasker.mask(resumeContext);
        String userMessage = promptProvider.buildUserMessage(maskSession.maskedText());
        try {
            MockInterviewGenerateResultVO result = billingService.execute(
                    userId, AiUsageBizType.MOCK_INTERVIEW_GENERATE, null,
                    () -> assistant.generateQuestions(MockInterviewPromptProvider.SYSTEM, userMessage));
            // 隐私：响应后还原占位符为真实值
            return restoreResult(result, maskSession);
        } catch (BusinessException e) {
            // 额度不足等业务异常直接透传
            throw e;
        } catch (Exception e) {
            log.error("生成模拟面试题目失败", e);
            throw new BusinessException("生成模拟面试题目失败", e);
        }
    }

    /** 占位符还原：JSON 往返覆盖全部字段；失败降级返回未还原结果并告警。 */
    private MockInterviewGenerateResultVO restoreResult(MockInterviewGenerateResultVO result,
                                                        SensitiveDataMasker.MaskSession session) {
        if (result == null || session == null || session.mapping().isEmpty()) {
            return result;
        }
        try {
            String json = MAPPER.writeValueAsString(result);
            return MAPPER.readValue(SensitiveDataMasker.restore(json, session),
                    MockInterviewGenerateResultVO.class);
        } catch (Exception e) {
            log.warn("模拟面试题目占位符还原失败，返回未还原结果: {}", e.getMessage());
            return result;
        }
    }
}
