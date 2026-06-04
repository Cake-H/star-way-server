package com.smartapply.ida.ai.interview;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 面试复盘大模型提示词：一次调用输出 {@code extractedSession}（公司/岗位/轮次/日期）+ {@code questions[]}（每题五字段）。
 * <p>
 * 维护说明：若前端字段或产品规则变更，须同步修改 {@link #SYSTEM} 中的 JSON 结构描述。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InterviewSummarizePrompts {

    /** System 角色：约束输出为单个 JSON 对象，禁止 Markdown 围栏。 */
    public static final String SYSTEM = """
            你是「面试复盘结构化助手」。用户会提供一场面试后的口述或转写文本。
            你的任务有四部分：
            （1）从口述开头或文中识别本场面试的「场次元信息」：公司、岗位、轮次、面试日期；
            （2）识别若干道独立的「面试问答」，为每一道输出结构化记录。
            （3）对用户的回答进行有效的点评，重在技术内容方面，答得好的给予肯定，答得不好的指出不足。
            （4）优化用户的回答，针对每条问题给出一个详细、正确、易理解的参考答案，供用户对照学习
            禁止编造口述中未出现的事实；无法推断的字段用空字符串 ""，不要 null。
            只输出一个 JSON 对象，不要 Markdown、不要代码围栏、不要注释。
            
            场次元信息 extractedSession 规则：
            - 出现公司、岗位、轮次、时间状语（如「今天面试了字节跳动的后端开发一面」）须抽取；
            - companyName、positionName、interviewRound、interviewAt（仅 YYYY-MM-DD）；
            - suggestedTitle 建议格式：{公司} · {岗位} · {轮次}，缺项省略；
            - 相对日期按用户消息中的【服务端参考日期】解析：今天=当天、昨天=减1天、前天=减2天；
            - 口述与【当前场次已保存信息】冲突时，以口述为准写入 extractedSession。
            
            多题 questions[] 规则：
            - 话题切换、又问、接着问、不同技术主题 → 拆成多条；
            - 同一题的追问写入该题 otherNotes，不要随意拆成两题；
            - question/myAnswer/answerEvaluation/referenceAnswer/otherNotes 语义与字段名一致；
            - 若返回的文本较长，在必要的地方换行；有1、2、3、等类似序号分点时，在其前面换行
            - myAnswer 保留技术细节，不要过度摘要；
            - 顺序与口述一致；
            - 无法识别任何问答时 questions 为 []，hint 说明原因。
            
            输出 JSON 结构（camelCase）：
            {
              "hint": "",
              "extractedSession": {
                "companyName": "",
                "positionName": "",
                "interviewRound": "",
                "interviewAt": "",
                "suggestedTitle": ""
              },
              "questions": [
                {
                  "question": "",
                  "myAnswer": "",
                  "answerEvaluation": "",
                  "referenceAnswer": "",
                  "otherNotes": ""
                }
              ]
            }
            """;

    /**
     * User 角色：附带服务端「今天」用于解析相对日期，以及库内已有场次（仅作对照，抽取以口述为准）。
     */
    public static String buildUserMessage(
            String today,
            String dbCompany,
            String dbPosition,
            String dbRound,
            String dbInterviewAt,
            String transcript) {
        return """
                【服务端参考日期】%s（Asia/Shanghai，用于解析「今天/昨天」）
                
                【当前场次已保存信息】（可能为空，仅供对照；抽取结果以口述为准）
                公司：%s
                岗位：%s
                轮次：%s
                日期：%s
                
                【口述/转写全文】
                %s
                """.formatted(
                today,
                nullToEmpty(dbCompany),
                nullToEmpty(dbPosition),
                nullToEmpty(dbRound),
                nullToEmpty(dbInterviewAt),
                transcript);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
