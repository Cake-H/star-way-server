package com.smartapply.ida.ai.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartapply.ida.ai.langchain4j.assistant.ResumeParseAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.ResumeParsePromptProvider;
import com.smartapply.ida.ai.usage.AiUsageBizType;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.module.app.billing.service.BillingService;
import com.smartapply.ida.security.mask.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 调用 LangChain4j AI Service，将简历纯文本转为 {@link ResumeParseDraftDto}。
 * <p>
 * 基于 LangChain4j AI Services 声明式调用，框架自动处理 JSON 解析与 Markdown 清洗。
 * 提示词由 {@link ResumeParsePromptProvider} 提供。
 * <p>
 * <b>隐私（1.4 基线）：</b>简历全文含手机号/身份证/邮箱等 PII，发送 LLM 前先替换为
 * {@code [PHONE_1]} 等占位符（映射仅存本次请求内存），模型返回后再还原为真值，
 * 保证第三方模型永远接触不到明文 PII 且解析结果功能无损。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeArkStructuringService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ResumeParseAssistant assistant;
    private final ResumeParsePromptProvider promptProvider;
    private final BillingService billingService;

    /**
     * @param userId    当前登录用户，用于额度守卫与用量计量
     * @param plainText 已截断后的简历全文
     */
    public ResumeParseDraftDto structure(long userId, String plainText) {
        // 隐私：prompt 前脱敏（手机号/身份证/邮箱 → 占位符）
        SensitiveDataMasker.MaskSession maskSession = SensitiveDataMasker.mask(plainText);
        String userMessage = promptProvider.buildUserMessage(maskSession.maskedText());

        log.info("简历解析开始 userId={} plainTextLen={}", userId, plainText.length());

        ResumeParseDraftDto result;
        try {
            result = billingService.execute(
                    userId, AiUsageBizType.RESUME_PARSE, null,
                    () -> assistant.parseResume(ResumeParsePromptProvider.SYSTEM, userMessage));
        } catch (BusinessException e) {
            // 额度不足等业务异常直接透传
            throw e;
        } catch (Exception e) {
            log.error("简历解析调用大模型失败", e);
            throw new BusinessException("简历智能解析失败：" + e.getMessage());
        }

        if (result == null) {
            throw new BusinessException("模型未返回有效结果");
        }

        // 隐私：响应后还原占位符为真实值
        result = restoreDraft(result, maskSession);

        log.info("简历解析完成 userId={}", userId);
        return result;
    }

    /**
     * 占位符还原：整体序列化为 JSON → 还原 → 反序列化，覆盖 DTO 内全部字段（含嵌套列表），
     * 避免逐字段维护遗漏。失败时降级返回未还原结果并告警。
     */
    private ResumeParseDraftDto restoreDraft(ResumeParseDraftDto dto, SensitiveDataMasker.MaskSession session) {
        if (dto == null || session == null || session.mapping().isEmpty()) {
            return dto;
        }
        try {
            String json = MAPPER.writeValueAsString(dto);
            return MAPPER.readValue(SensitiveDataMasker.restore(json, session), ResumeParseDraftDto.class);
        } catch (Exception e) {
            log.warn("简历解析结果占位符还原失败，返回未还原结果: {}", e.getMessage());
            return dto;
        }
    }
}
