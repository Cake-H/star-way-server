package com.smartapply.ida.ai.usage;

/**
 * AI 用量计量的业务类型常量，与 {@code ida_ai_usage_record.biz_type} 对应。
 */
public final class AiUsageBizType {

    /** 简历 AI 解析（上传简历 → 结构化草稿） */
    public static final String RESUME_PARSE = "resume_parse";

    /** 插件一键填表（LLM 兜底匹配阶段；模板/确定性匹配不消耗） */
    public static final String PLUGIN_AUTOFILL = "plugin_autofill";

    /** 面试复盘语音总结 */
    public static final String INTERVIEW_SUMMARIZE = "interview_summarize";

    /** 模拟面试题目生成 */
    public static final String MOCK_INTERVIEW_GENERATE = "mock_interview_generate";
    /** 投递记录获取 */
    public static final String JOB_APPLICATION_PARSE = "job_application_parse";

    private AiUsageBizType() {
    }
}
