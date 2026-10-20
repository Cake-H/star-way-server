package com.smartapply.ida.ai.langchain4j.assistant;

import com.smartapply.ida.common.domain.plugin.jobApplication.JobApplicationParseDraftDto;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 投递页面识别 Assistant：输入网申页面快照（网址/标题/正文），输出结构化投递记录草稿。
 * 一个页面可能含多条记录（投递列表页），由 {@link JobApplicationParseDraftDto#getRecords()} 承载。
 */
public interface JobApplicationParseAssistant {

    /**
     * 从页面快照中提取投递记录。
     *
     * @param systemPrompt 系统提示词（见 {@code JobApplicationParsePromptProvider#SYSTEM}）
     * @param userMessage  页面快照（网址 + 标题 + 正文）
     * @return 提取草稿；模型未识别到有效记录时 records 为空或元素字段为 null
     */
    @SystemMessage("{{systemPrompt}}")
    JobApplicationParseDraftDto parse(
            @V("systemPrompt") String systemPrompt,
            @UserMessage String userMessage
    );
}
