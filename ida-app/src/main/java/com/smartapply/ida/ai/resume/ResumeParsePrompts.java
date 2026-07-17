package com.smartapply.ida.ai.resume;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 简历结构化提示词：要求模型只输出一段 JSON，键名与 {@code ResumeParseDraftDto} 对齐，便于 Jackson 反序列化。
 * <p>
 * 维护说明：若数据库或 DTO 字段调整，需同步修改本常量中的「输出 JSON 结构」描述，否则模型仍按旧键输出会导致部分字段丢失。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResumeParsePrompts {

    /**
     * System 角色：约束输出格式与安全边界（不杜撰证书编号等敏感信息时可留空）。
     */
    public static final String SYSTEM = """
            你是招聘简历信息抽取助手。只根据用户给出的简历纯文本推断字段，不得编造未出现的信息。
            输出必须是单个 JSON 对象，不要 Markdown，不要代码围栏，不要注释。
            无法确定的字段用 null 或省略；日期尽量为 yyyy-MM-dd；列表无内容时用 []。
            简历内容中如果有“· · · ”、“1、2、3”、“1. 2. 3.”等分点的段落，必须在每一个分点前加上换行符，确保每个分点独占一行。
            简历内容必须完整保留，不能删减或改写。
            """;

    /**
     * User 内容前缀：拼接在简历正文之前，明确 JSON 顶层与子数组字段名（与 Java DTO 一致）。
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

    public static final String USER_SUFFIX = """
            
            ——— 简历正文结束 ———
            现在只输出 JSON：
            """;
}
