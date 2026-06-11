package com.smartapply.ida.ai.langchain4j.assistant;

import com.smartapply.ida.common.domain.interview.vo.InterviewVoiceSummarizeResultVO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 面试复盘总结 AI Service。
 * <p>
 * 直接返回结构化 {@link InterviewVoiceSummarizeResultVO}。
 * <p>
 * 输入输出与原 {@link com.smartapply.ida.ai.interview.InterviewSummarizeAiService} 完全一致。
 */
public interface InterviewSummarizeAssistant {

    /**
     * 结构化面试口述文本。
     *
     * @param systemPrompt 系统提示词（由 InterviewSummarizePromptProvider.SYSTEM 提供）
     * @param userMessage  用户消息（含参考日期、已保存信息、口述全文）
     * @return 结构化面试复盘结果
     */
    @SystemMessage("{{systemPrompt}}")
    InterviewVoiceSummarizeResultVO summarize(
            @V("systemPrompt") String systemPrompt,
            @UserMessage String userMessage
    );
}
