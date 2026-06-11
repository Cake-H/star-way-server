package com.smartapply.ida.ai.resume;

import com.smartapply.ida.ai.langchain4j.assistant.ResumeParseAssistant;
import com.smartapply.ida.ai.langchain4j.prompt.ResumeParsePromptProvider;
import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import com.smartapply.ida.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 调用 LangChain4j AI Service，将简历纯文本转为 {@link ResumeParseDraftDto}。
 * <p>
 * 基于 LangChain4j AI Services 声明式调用，框架自动处理 JSON 解析与 Markdown 清洗。
 * 提示词由 {@link ResumeParsePromptProvider} 提供。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeArkStructuringService {

    private final ResumeParseAssistant assistant;
    private final ResumeParsePromptProvider promptProvider;

    /**
     * @param plainText 已截断后的简历全文
     */
    public ResumeParseDraftDto structure(String plainText) {
        String userMessage = promptProvider.buildUserMessage(plainText);

        log.info("简历解析开始 plainTextLen={}", plainText.length());

        ResumeParseDraftDto result;
        try {
            result = assistant.parseResume(ResumeParsePromptProvider.SYSTEM, userMessage);
        } catch (Exception e) {
            log.error("简历解析调用大模型失败", e);
            throw new BusinessException("简历智能解析失败：" + e.getMessage());
        }

        if (result == null) {
            throw new BusinessException("模型未返回有效结果");
        }

        log.info("简历解析完成");
        return result;
    }
}
