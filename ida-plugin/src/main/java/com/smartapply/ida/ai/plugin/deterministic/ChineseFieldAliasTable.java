package com.smartapply.ida.ai.plugin.deterministic;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 中文表单常见标签 → 简历字段路径的静态别名映射表，以及下拉枚举值的同义词组。
 * <p>
 * 用于两个场景：
 * <ol>
 *   <li>当 clientFieldKey 缺失时，根据 labelText/placeholder 启发式匹配简历字段</li>
 *   <li>下拉选项模糊匹配时，通过枚举别名将简历值映射到平台特定选项文案</li>
 * </ol>
 * 别名表通过反馈接口收集的 failReason=option_not_found 案例持续迭代补充。
 */
public final class ChineseFieldAliasTable {

    private ChineseFieldAliasTable() {}

    // ======================== 标签 → 简历字段路径 ========================
    // 路径格式必须与 ResumeFieldAccessor 的 FLAT_GETTERS / INDEXED_GETTERS 一致：
    //   基本字段：basic.fieldName  / location.fieldName  / job.fieldName 等
    //   集合字段：prefix.0.fieldName（默认取第 0 条，tagName/index 留空时表示第一条经历）

    private static final Map<String, String> LABEL_TO_FIELD;

    static {
        Map<String, String> m = new HashMap<>();
        // ===== 基本信息 =====
        m.put("姓名", "basic.fullName");
        m.put("全名", "basic.fullName");
        m.put("真实姓名", "basic.fullName");
        m.put("名字", "basic.fullName");
        m.put("性别", "basic.gender");
        m.put("出生日期", "basic.birthDate");
        m.put("出生年月", "basic.birthDate");
        m.put("生日", "basic.birthDate");
        m.put("证件类型", "basic.idDocType");
        m.put("证件号码", "basic.idDocNumber");
        m.put("身份证号", "basic.idDocNumber");
        m.put("身份证号码", "basic.idDocNumber");

        // ===== 联系方式 =====
        m.put("手机号", "basic.phone");
        m.put("手机号码", "basic.phone");
        m.put("联系电话", "basic.phone");
        m.put("电话", "basic.phone");
        m.put("电话号码", "basic.phone");
        m.put("移动电话", "basic.phone");
        m.put("邮箱", "basic.email");
        m.put("电子邮箱", "basic.email");
        m.put("电子邮件", "basic.email");
        m.put("email", "basic.email");
        m.put("qq", "basic.qq");
        m.put("qq号", "basic.qq");
        m.put("微信", "basic.wechat");
        m.put("微信号", "basic.wechat");

        // ===== 籍贯/户口/地址 =====
        m.put("国籍", "location.countryRegion");
        m.put("国家", "location.countryRegion");
        m.put("国家/地区", "location.countryRegion");
        m.put("籍贯", "location.nativePlace");
        m.put("民族", "location.ethnicity");
        m.put("政治面貌", "location.politicalStatus");
        m.put("户口类型", "location.hukouType");
        m.put("户籍类型", "location.hukouType");
        m.put("户籍所在地", "location.province");
        m.put("省份", "location.province");
        m.put("城市", "location.city");
        m.put("区/县", "location.district");
        m.put("现居城市", "location.currentCity");
        m.put("目前所在城市", "location.currentCity");
        m.put("所在城市", "location.currentCity");
        m.put("详细地址", "location.detailAddress");
        m.put("通讯地址", "mailing.mailingAddress");
        m.put("邮寄地址", "mailing.mailingAddress");

        // ===== 紧急联系人 =====
        m.put("紧急联系人", "emergency.emergencyContactName");
        m.put("紧急联系人姓名", "emergency.emergencyContactName");
        m.put("紧急联系人电话", "emergency.emergencyContactPhone");
        m.put("紧急联系人关系", "emergency.emergencyContactRelation");

        // ===== 求职状态 =====
        m.put("工作年限", "job.workYears");
        m.put("工作经验", "job.workYears");
        m.put("期望职位", "job.expectedPositions");
        m.put("期望城市", "job.expectedCities");
        m.put("期望行业", "job.expectedIndustries");
        m.put("期望薪资", "job.salaryExpectation");
        m.put("薪资期望", "job.salaryExpectation");
        m.put("到岗时间", "job.availability");
        m.put("入职时间", "job.availability");
        m.put("最快到岗时间", "job.availability");

        // ===== 自我介绍 =====
        m.put("个人简介", "profile.selfIntro");
        m.put("自我介绍", "profile.selfIntro");
        m.put("自我评价", "profile.selfIntro");
        m.put("个人优势", "profile.strengths");
        m.put("个人网站", "profile.portfolioLinks");
        m.put("作品链接", "profile.portfolioLinks");

        // ===== 教育经历（集合字段，默认取索引 0 = 最高学历） =====
        m.put("学校", "education.0.schoolName");
        m.put("学校名称", "education.0.schoolName");
        m.put("院校", "education.0.schoolName");
        m.put("院校名称", "education.0.schoolName");
        m.put("学院", "education.0.collegeName");
        m.put("专业", "education.0.major");
        m.put("专业名称", "education.0.major");
        m.put("学历", "education.0.degree");
        m.put("最高学历", "education.0.degree");
        m.put("学位", "education.0.academicDegree");
        m.put("入学时间", "education.0.enrollDate");
        m.put("毕业时间", "education.0.graduateDate");
        m.put("gpa", "education.0.gpa");
        m.put("GPA", "education.0.gpa");

        // ===== 工作经历（集合字段，默认取索引 0 = 最近一份工作） =====
        m.put("公司", "work.0.companyName");
        m.put("公司名称", "work.0.companyName");
        m.put("部门", "work.0.department");
        m.put("所属部门", "work.0.department");
        m.put("职位", "work.0.jobTitle");
        m.put("职位名称", "work.0.jobTitle");
        m.put("岗位", "work.0.jobTitle");
        m.put("岗位名称", "work.0.jobTitle");
        m.put("工作城市", "work.0.workCity");
        m.put("所在行业", "work.0.companyIndustry");
        m.put("入职时间", "work.0.startDate");
        m.put("离职时间", "work.0.endDate");
        m.put("工作开始时间", "work.0.startDate");
        m.put("工作结束时间", "work.0.endDate");

        // ===== 项目经历（默认索引 0） =====
        m.put("项目名称", "project.0.projectName");
        m.put("项目角色", "project.0.roleName");
        m.put("项目开始时间", "project.0.startDate");
        m.put("项目结束时间", "project.0.endDate");

        LABEL_TO_FIELD = Collections.unmodifiableMap(m);
    }

    /**
     * 根据归一化后的标签文本查找简历字段路径。
     *
     * @return 字段路径（如 "fullName" 或 "educations[?].schoolName"），未命中返回 null
     */
    public static String resolveByLabel(String normalizedLabel) {
        return LABEL_TO_FIELD.get(normalizedLabel);
    }

    // ======================== 下拉枚举值同义词组 ========================

    /** 性别：简历中的 Integer 值 → 可能出现的选项文案 */
    private static final Map<String, List<String>> GENDER_ALIASES = Map.of(
            "1", List.of("男", "男性", "male", "Male", "M"),
            "2", List.of("女", "女性", "female", "Female", "F"),
            "0", List.of("未知", "其他", "不愿透露", "other")
    );

    /** 学历 */
    private static final Map<String, List<String>> DEGREE_ALIASES = Map.of(
            "博士", List.of("博士研究生", "博士", "PhD", "博士研究生(PhD)", "Doctor"),
            "硕士", List.of("硕士研究生", "硕士", "Master", "硕士研究生(Master)", "研究生"),
            "本科", List.of("大学本科", "本科", "学士", "Bachelor", "大学本科(Bachelor)"),
            "大专", List.of("大专", "专科", "大学专科", "College"),
            "高中", List.of("高中", "普通高中", "High School"),
            "中专", List.of("中专", "中等专业", "中等职业")
    );

    /** 政治面貌 */
    private static final Map<String, List<String>> POLITICAL_ALIASES = Map.of(
            "中共党员", List.of("中国共产党党员", "中共党员", "党员"),
            "中共预备党员", List.of("中共预备党员", "预备党员"),
            "共青团员", List.of("中国共产主义青年团团员", "共青团员", "团员"),
            "民主党派", List.of("民主党派", "民主党派人士"),
            "群众", List.of("群众", "无党派", "无党派人士")
    );

    /** 户口类型 */
    private static final Map<String, List<String>> HUKOU_ALIASES = Map.of(
            "农村", List.of("农村", "农业", "农村户口"),
            "城镇", List.of("城镇", "非农业", "城市", "城镇户口", "非农业户口")
    );

    /** 所有枚举别名类别，key 为字段路径关键词（用于判断一个字段属于哪类枚举） */
    private static final Map<String, Map<String, List<String>>> ENUM_ALIAS_CATEGORIES = Map.of(
            "gender", GENDER_ALIASES,
            "degree", DEGREE_ALIASES,
            "politicalStatus", POLITICAL_ALIASES,
            "hukouType", HUKOU_ALIASES
    );

    /**
     * 根据字段类别和简历原始值，尝试从别名列表中找到匹配的下拉选项。
     *
     * @param fieldCategory 字段类别关键词（如 "gender"、"degree"）
     * @param resumeValue   简历中的原始值（如 "1"、"本科"）
     * @param options       平台下拉选项列表
     * @return 匹配到的选项文案，未匹配返回 null
     */
    public static String matchByEnumAlias(String fieldCategory, String resumeValue, List<String> options) {
        Map<String, List<String>> aliasMap = ENUM_ALIAS_CATEGORIES.get(fieldCategory);
        if (aliasMap == null || resumeValue == null || options == null) {
            return null;
        }
        List<String> aliases = aliasMap.get(resumeValue);
        if (aliases == null) {
            // 尝试用值本身在各别名组中反向查找
            for (var entry : aliasMap.entrySet()) {
                if (entry.getValue().stream().anyMatch(a -> a.equalsIgnoreCase(resumeValue))) {
                    aliases = entry.getValue();
                    break;
                }
            }
        }
        if (aliases == null) {
            return null;
        }
        // 在下拉选项中查找别名命中
        for (String alias : aliases) {
            for (String opt : options) {
                if (opt.equalsIgnoreCase(alias) || opt.contains(alias) || alias.contains(opt)) {
                    return opt;
                }
            }
        }
        return null;
    }

    /**
     * 推断一个字段路径属于哪个枚举类别，用于决定是否可以做枚举别名匹配。
     *
     * @return 类别名（如 "gender"、"degree"），不属于任何枚举类别返回 null
     */
    public static String inferEnumCategory(String fieldPath) {
        if (fieldPath == null) return null;
        String lower = fieldPath.toLowerCase();
        if (lower.contains("gender") || lower.equals("性别")) return "gender";
        if (lower.contains("degree")) return "degree";
        if (lower.contains("politicalstatus") || lower.contains("political")) return "politicalStatus";
        if (lower.contains("hukoutype") || lower.contains("hukou")) return "hukouType";
        return null;
    }
}
