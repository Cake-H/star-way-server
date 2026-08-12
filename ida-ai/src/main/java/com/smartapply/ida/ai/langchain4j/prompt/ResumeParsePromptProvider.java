package com.smartapply.ida.ai.langchain4j.prompt;

import org.springframework.stereotype.Component;

/**
 * 简历解析提示词 Provider。
 * <p>
 * 以原 ResumeParsePrompts 为基础，本类为唯一来源；M2 在 SYSTEM 中新增了
 * 长文本字段原文保留硬约束（禁止摘要/改写/丢换行）。
 */
@Component
public class ResumeParsePromptProvider {

    /**
     * 系统提示词：以原 ResumeParsePrompts.SYSTEM 为基础，新增长文本原文保留硬约束。
     */
    public static final String SYSTEM = """
            你是招聘简历信息抽取助手。只根据用户给出的简历纯文本推断字段，不得编造未出现的信息。
            输出必须是单个 JSON 对象，不要 Markdown，不要代码围栏，不要注释。
            无法确定的字段用 null 或省略；日期尽量为 yyyy-MM-dd；列表无内容时用 []。
            **长文本字段原文保留（极其重要）**：selfIntro、strengths、workDescription、dutyDescription、achievement、
            description、majorCourses 等段落型字段必须**逐字摘录简历原文**——禁止摘要、概括、改写、润色、合并段落；
            保留原文的全部换行（JSON 字符串中用 \\n 表示）与条目序号（如 1、2、3 或 1. 2. 等）；宁可字段很长也不得截断。
            原文没有的字段宁缺毋滥（null），但存在的段落必须完整。
            """;

    /**
     * 用户消息指令：与原 ResumeParsePrompts.USER_INSTRUCTION 完全一致。
     */
    public static final String USER_INSTRUCTION = """
            下面是一份求职者简历的全文（可能排版混乱）。请抽取信息并输出 JSON，顶层字段与子数组名称必须严格使用下列键名（camelCase）：

            顶层字符串/数字字段（与系统简历主表对应，可省略）：
            title, fullName, gender(0未知1男2女), birthDate, idDocType, idDocNumber, phone, email, qq, wechat,
            countryRegion, nativePlace, ethnicity, politicalStatus, hukouType, province, city, district, currentCity,
            detailAddress, mailingSameAsCurrent(0/1), mailingAddress,
            emergencyContactName, emergencyContactPhone, emergencyContactRelation,
            jobSeekingStatus, isFreshGraduate(0/1), workYears, expectedPositions, expectedCities, expectedIndustries,
            salaryExpectation, availability, acceptTransfer(0/1), selfIntro, strengths, portfolioLinks

            子数组（无则 []），元素字段见下：
            - educations: schoolName, collegeName, major, degree, academicDegree, isFullTime(0/1), enrollDate, graduateDate,
              countryRegion, gpa, rankingDesc, majorCourses, studentPosition, honors, description
            - works: companyName, department, jobTitle, workCity, companyIndustry, employmentNature, startDate, endDate,
              workDescription, leaveReason
            - projects: projectName, roleName, startDate, endDate, companyOrOrg, techKeywords, projectUrl,
              dutyDescription, achievement, description
            - campusExperiences: organizationName, roleTitle, startDate, endDate, description
            - skills: skillCategory, skillName, proficiency, monthsOfUse
            - languages: languageName, listenLevel, speakLevel, readLevel, writeLevel, certName, certScore
            - certificates: certName, issuer, obtainDate, certNo
            - awards: awardName, awardLevel, issuer, awardDate, description
            - publications: pubType(paper/patent/book/other), title, role, venue, publishDate, description

            ——— 简历正文开始 ———
            """;

    /**
     * 用户消息后缀：与原 ResumeParsePrompts.USER_SUFFIX 完全一致。
     */
    public static final String USER_SUFFIX = """

            ——— 简历正文结束 ———
            现在只输出 JSON：
            """;

    /**
     * 构建完整用户消息。
     * <p>
     * 与原简历解析服务中的拼接逻辑完全一致。
     */
    public String buildUserMessage(String resumeText) {
        return USER_INSTRUCTION + resumeText + USER_SUFFIX;
    }
}
