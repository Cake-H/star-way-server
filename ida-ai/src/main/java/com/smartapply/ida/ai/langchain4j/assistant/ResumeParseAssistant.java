package com.smartapply.ida.ai.langchain4j.assistant;

import com.smartapply.ida.common.domain.resume.dto.parse.ResumeParseDraftDto;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 简历解析 AI Service。
 * <p>
 * 直接返回结构化 {@link ResumeParseDraftDto}。
 * <p>
 * 输入输出与原简历解析服务完全一致。
 */
public interface ResumeParseAssistant {

    /**
     * 解析简历纯文本为结构化数据。
     *
     * @param systemPrompt 系统提示词（由 ResumeParsePromptProvider.SYSTEM 提供）
     * @param userMessage  用户消息（指令 + 简历正文）
     * @return 结构化简历草稿
     */
    @SystemMessage("{{systemPrompt}}")
    ResumeParseDraftDto parseResume(
            @V("systemPrompt") String systemPrompt,
            @UserMessage String userMessage
    );
}
