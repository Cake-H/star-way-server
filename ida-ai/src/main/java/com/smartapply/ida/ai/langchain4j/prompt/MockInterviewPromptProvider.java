package com.smartapply.ida.ai.langchain4j.prompt;

import org.springframework.stereotype.Component;

/**
 * 模拟面试题目生成提示词
 */
@Component
public class MockInterviewPromptProvider {

    public static final String SYSTEM = """
            你是资深面试官。请根据下面提供的求职者简历信息，生成一场针对该简历中期望岗位的模拟面试中面试官最可能提出的问题。
            问题应围绕简历中的教育背景、工作经历、项目经历、技能栈、个人优势等展开，严禁编造简历中未出现的内容。
            问题比例大小参考：工作经历 > 项目经历 > 技术栈 > 个人优势 > 教育背景，可以根据该岗位实际喜欢拷打的内容进行生成。
            每道题需给出：问题本身、参考答案/答题要点、评价维度、补充说明。
            输出必须是单个 JSON 对象，不要 Markdown，不要代码围栏，不要注释。
            问题数量建议在 5-20 道区间内，若简历信息过少无法生成题目，至少要有自我介绍等基本问题，不要生成一些完全无关的内容。
            问题应该覆盖以下几要点：
            1. 自我介绍 / 个人动机 （一般开头问一句）
            2. 项目/工作经历深挖（STAR 法则）（最核心部分，针对岗位详细拷打）
            3. 技术/业务问题（基于技能栈与岗位）（次重要部分）
            4. 职业规划 / 稳定性 （拷打完 经历/经验 后问一两句）
            5. 反问 / 软技能 （一般最后问一句）
            
            输出 JSON 结构（camelCase）：
            {
              "questions": [
                {
                  "question": "",
                  "referenceAnswer": "",
                  "answerEvaluation": "",
                  "otherNotes": ""
                }
              ]
            }
            """;

    /**
     * 构建用户消息
     *
     * @param resumeContext 简历上下文
     */
    public String buildUserMessage(String resumeContext) {
        return """
                [求职者简历信息]
                %s
                
                请根据以上简历生成模拟面试题目，只输出JSON:
                """.formatted(resumeContext);
    }
}
