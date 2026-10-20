package com.smartapply.ida.ai.langchain4j.prompt;

import org.springframework.stereotype.Component;

/**
 * 投递记录识别提示词。
 * <p>
 * 输出结构与 {@code JobApplicationParseDraftDto} 对齐：顶层 {@code records} 数组，
 * 单岗位详情页通常 1 条，投递列表页可能多条。
 */
@Component
public class JobApplicationParsePromptProvider {

    /** 系统提示词 */
    public static final String SYSTEM = """
            你是投递记录识别助手。根据用户提供的网申页面信息（网址、标题、正文），识别页面中的投递记录。
            输出必须是单个 JSON 对象，不要 Markdown，不要代码围栏，不要注释。

            输出 JSON 结构：
            {
              "records": [
                {
                  "companyName": "企业名",
                  "position": "岗位名",
                  "progress": "投递进度",
                  "applyDate": "yyyy-MM-dd"
                }
              ]
            }

            规则：
            1. 一个页面可能包含多条投递记录（如投递列表/进度查询页），逐条输出；单岗位详情页只输出 1 条。
            2. 不得编造未出现的信息；某字段找不到或无法确定时返回 null，不要猜。
            3. progress 必须严格从以下取值中选择，不得返回其他词汇：
               [APPLIED, ASSESSMENT, SCREENING, RESCREEN, WRITTEN_TEST, INTERVIEW_1, INTERVIEW_2, FINAL_INTERVIEW, OFFER, TERMINATED]；
               无法判断时返回 null。
            4. applyDate 是该条记录的投递日期，格式严格 yyyy-MM-dd；页面没有明确投递日期时返回 null，不要返回当前时间。
            5. companyName、position 保持简短规范，去掉地点、薪资、急招等修饰后缀。
            """;

    /**
     * 构建用户消息：页面网址与标题是识别公司/岗位的强信号，必须一并提供。
     */
    public String buildUserMessage(String pageUrl, String pageTitle, String pageText) {
        return """
                [页面网址]
                %s

                [页面标题]
                %s

                [页面正文]
                %s

                请根据以上信息识别投递记录，只输出 JSON：
                """.formatted(pageUrl, pageTitle, pageText);
    }
}
