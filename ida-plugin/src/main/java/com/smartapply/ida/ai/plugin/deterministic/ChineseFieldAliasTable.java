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
        put(m,"姓名", "basic.fullName");
        put(m,"全名", "basic.fullName");
        put(m,"真实姓名", "basic.fullName");
        put(m,"名字", "basic.fullName");
        put(m,"性别", "basic.gender");
        put(m,"出生日期", "basic.birthDate");
        put(m,"出生年月", "basic.birthDate");
        put(m,"生日", "basic.birthDate");
        put(m,"证件类型", "basic.idDocType");
        put(m,"证件号码", "basic.idDocNumber");
        put(m,"身份证号", "basic.idDocNumber");
        put(m,"身份证号码", "basic.idDocNumber");

        // ===== 联系方式 =====
        put(m,"手机号", "basic.phone");
        put(m,"手机号码", "basic.phone");
        put(m,"联系电话", "basic.phone");
        put(m,"电话", "basic.phone");
        put(m,"电话号码", "basic.phone");
        put(m,"移动电话", "basic.phone");
        put(m,"邮箱", "basic.email");
        put(m,"电子邮箱", "basic.email");
        put(m,"电子邮件", "basic.email");
        put(m,"个人邮箱", "basic.email");
        put(m,"qq邮箱", "basic.email");
        put(m,"email", "basic.email");
        put(m,"qq", "basic.qq");
        put(m,"qq号", "basic.qq");
        put(m,"微信", "basic.wechat");
        put(m,"微信号", "basic.wechat");

        // ===== 籍贯/户口/地址 =====
        put(m,"国籍", "location.countryRegion");
        put(m,"国家", "location.countryRegion");
        put(m,"国家/地区", "location.countryRegion");
        put(m,"籍贯", "location.nativePlace");
        put(m,"民族", "location.ethnicity");
        put(m,"政治面貌", "location.politicalStatus");
        put(m,"户口类型", "location.hukouType");
        put(m,"户籍类型", "location.hukouType");
        put(m,"户籍所在地", "location.province");
        put(m,"户口所在地", "location.province");
        put(m,"省份", "location.province");
        put(m,"城市", "location.city");
        put(m,"区/县", "location.district");
        put(m,"现居城市", "location.currentCity");
        put(m,"目前所在城市", "location.currentCity");
        put(m,"所在城市", "location.currentCity");
        put(m,"详细地址", "location.detailAddress");
        put(m,"家庭住址", "location.detailAddress");
        put(m,"联系地址", "location.detailAddress");
        put(m,"现居住地址", "location.detailAddress");
        put(m,"常住地址", "location.detailAddress");
        put(m,"通讯地址", "mailing.mailingAddress");
        put(m,"邮寄地址", "mailing.mailingAddress");

        // ===== 紧急联系人 =====
        put(m,"紧急联系人", "emergency.emergencyContactName");
        put(m,"紧急联系人姓名", "emergency.emergencyContactName");
        put(m,"紧急联系人电话", "emergency.emergencyContactPhone");
        put(m,"紧急联系人关系", "emergency.emergencyContactRelation");

        // ===== 求职状态 =====
        put(m,"工作年限", "job.workYears");
        put(m,"工作经验", "job.workYears");
        put(m,"期望职位", "job.expectedPositions");
        put(m,"求职意向", "job.expectedPositions");
        put(m,"意向职位", "job.expectedPositions");
        put(m,"期望城市", "job.expectedCities");
        put(m,"期望工作地点", "job.expectedCities");
        put(m,"意向城市", "job.expectedCities");
        put(m,"期望行业", "job.expectedIndustries");
        put(m,"期望薪资", "job.salaryExpectation");
        put(m,"期望月薪", "job.salaryExpectation");
        put(m,"薪资期望", "job.salaryExpectation");
        put(m,"到岗时间", "job.availability");
        put(m,"可到岗时间", "job.availability");
        put(m,"何时到岗", "job.availability");
        put(m,"最快到岗时间", "job.availability");

        // ===== 自我介绍 =====
        put(m,"个人简介", "profile.selfIntro");
        put(m,"自我介绍", "profile.selfIntro");
        put(m,"自我评价", "profile.selfIntro");
        put(m,"个人优势", "profile.strengths");
        put(m,"个人网站", "profile.portfolioLinks");
        put(m,"作品链接", "profile.portfolioLinks");

        // ===== 教育经历（集合字段，默认取索引 0 = 最高学历） =====
        put(m,"学校", "education.0.schoolName");
        put(m,"学校名称", "education.0.schoolName");
        put(m,"院校", "education.0.schoolName");
        put(m,"院校名称", "education.0.schoolName");
        put(m,"毕业院校", "education.0.schoolName");
        put(m,"毕业学校", "education.0.schoolName");
        put(m,"就读学校", "education.0.schoolName");
        put(m,"学院", "education.0.collegeName");
        put(m,"专业", "education.0.major");
        put(m,"专业名称", "education.0.major");
        put(m,"所学专业", "education.0.major");
        put(m,"学历", "education.0.degree");
        put(m,"最高学历", "education.0.degree");
        put(m,"学位", "education.0.academicDegree");
        put(m,"入学时间", "education.0.enrollDate");
        put(m,"入学年份", "education.0.enrollDate");
        put(m,"毕业时间", "education.0.graduateDate");
        put(m,"毕业年份", "education.0.graduateDate");
        put(m,"gpa", "education.0.gpa");

        // ===== 工作经历（集合字段，默认取索引 0 = 最近一份工作） =====
        put(m,"公司", "work.0.companyName");
        put(m,"公司名称", "work.0.companyName");
        put(m,"工作单位", "work.0.companyName");
        put(m,"工作公司", "work.0.companyName");
        put(m,"部门", "work.0.department");
        put(m,"所属部门", "work.0.department");
        put(m,"职位", "work.0.jobTitle");
        put(m,"职位名称", "work.0.jobTitle");
        put(m,"当前职位", "work.0.jobTitle");
        put(m,"现任职位", "work.0.jobTitle");
        put(m,"岗位", "work.0.jobTitle");
        put(m,"岗位名称", "work.0.jobTitle");
        put(m,"工作城市", "work.0.workCity");
        put(m,"所在行业", "work.0.companyIndustry");
        put(m,"入职时间", "work.0.startDate");
        put(m,"离职时间", "work.0.endDate");
        put(m,"工作开始时间", "work.0.startDate");
        put(m,"工作结束时间", "work.0.endDate");

        // ===== 项目经历（默认索引 0） =====
        put(m,"项目名称", "project.0.projectName");
        put(m,"项目角色", "project.0.roleName");
        put(m,"项目链接", "project.0.projectUrl");
        put(m,"项目地址", "project.0.projectUrl");
        put(m,"项目开始时间", "project.0.startDate");
        put(m,"项目结束时间", "project.0.endDate");

        LABEL_TO_FIELD = Collections.unmodifiableMap(m);
    }

    /** 注册静态别名；重复 key 说明运营配错，类加载时直接抛异常暴露，不允许静默覆盖 */
    private static void put(Map<String, String> m, String key, String path) {
        if (m.put(key, path) != null) {
            throw new IllegalStateException("静态别名表重复 key: " + key);
        }
    }

    /**
     * 根据归一化后的标签文本查找简历字段路径。
     *
     * @return 字段路径（如 "basic.fullName" 或 "education.0.schoolName"），未命中返回 null
     */
    public static String resolveByLabel(String normalizedLabel) {
        return LABEL_TO_FIELD.get(normalizedLabel);
    }

    /**
     * 包含匹配：返回「被 label 包含的最长别名 key」对应的字段路径。
     * 用于标签带前缀/后缀修饰（如「您的姓名」）时静态精确匹配未命中的兜底。
     * <p>
     * 规则与 {@code DynamicFieldAliasService#resolveContains} 一致：
     * 比较前 label 与 key 均转小写（静态 key 已统一小写，英文别名大小写不敏感）；
     * key 长度 &lt; 2 不参与；并列最长视为歧义返回 null 降级交 LLM，
     * 但并列最长映射到<b>同一字段路径</b>时不算歧义，直接返回该路径。
     */
    public static String resolveByLabelContains(String normalizedLabel) {
        if (normalizedLabel == null || normalizedLabel.isEmpty()) {
            return null;
        }
        String lowerLabel = normalizedLabel.toLowerCase();
        int bestLen = 0;
        String bestPath = null;
        boolean ambiguous = false;
        for (Map.Entry<String, String> entry : LABEL_TO_FIELD.entrySet()) {
            String key = entry.getKey();
            if (key.length() < 2 || !lowerLabel.contains(key.toLowerCase())) {
                continue;
            }
            if (key.length() > bestLen) {
                bestLen = key.length();
                bestPath = entry.getValue();
                ambiguous = false;
            } else if (key.length() == bestLen && !entry.getValue().equals(bestPath)) {
                // 并列最长且映射到不同路径 → 歧义
                ambiguous = true;
            }
        }
        if (ambiguous || bestPath == null) {
            return null;
        }
        return bestPath;
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

    /** 证件类型 */
    private static final Map<String, List<String>> ID_DOC_TYPE_ALIASES = Map.of(
            "身份证", List.of("居民身份证", "身份证", "中华人民共和国居民身份证"),
            "护照", List.of("护照", "普通护照", "Passport"),
            "港澳通行证", List.of("港澳通行证", "港澳居民来往内地通行证"),
            "台湾通行证", List.of("台湾通行证", "台湾居民来往大陆通行证")
    );

    /** 所有枚举别名类别，key 为字段路径关键词（用于判断一个字段属于哪类枚举） */
    private static final Map<String, Map<String, List<String>>> ENUM_ALIAS_CATEGORIES = Map.of(
            "gender", GENDER_ALIASES,
            "degree", DEGREE_ALIASES,
            "politicalStatus", POLITICAL_ALIASES,
            "hukouType", HUKOU_ALIASES,
            "idDocType", ID_DOC_TYPE_ALIASES
    );

    /**
     * 判断枚举类别名是否已知（在 {@link #ENUM_ALIAS_CATEGORIES} 中注册）。
     * 供站点模板加载校验使用：模板里写了未知类别说明运营配错。
     */
    public static boolean isKnownEnumCategory(String category) {
        return category != null && ENUM_ALIAS_CATEGORIES.containsKey(category);
    }

    /**
     * 返回某枚举类别下简历原始值对应的展示别名组（组内首个为最常用展示文案）。
     * 用于选择类控件缺少选项样本时，把原始值（如性别 "1"）转成可键入搜索的显示文案（"男"）。
     *
     * @return 别名列表，类别未知或值不属于任何别名组时返回 null
     */
    public static List<String> enumAliasesOf(String fieldCategory, String resumeValue) {
        Map<String, List<String>> aliasMap = ENUM_ALIAS_CATEGORIES.get(fieldCategory);
        if (aliasMap == null || resumeValue == null) {
            return null;
        }
        List<String> aliases = aliasMap.get(resumeValue);
        if (aliases == null) {
            // 用值本身在各别名组中反向查找
            for (var entry : aliasMap.entrySet()) {
                if (entry.getValue().stream().anyMatch(a -> a.equalsIgnoreCase(resumeValue))) {
                    aliases = entry.getValue();
                    break;
                }
            }
        }
        return aliases;
    }

    /**
     * 根据字段类别和简历原始值，尝试从别名列表中找到匹配的下拉选项。
     * <p>
     * 分两阶段：
     * <ol>
     *   <li>所有 alias × option 做忽略大小写精确比较，先比完所有精确命中，
     *       避免 contains 误配（如 "Female".contains("male") 把「男」填成「女」）；</li>
     *   <li>包含比较统一先转小写，并按 {@link OptionMatcher} 相同的打分竞争：
     *       分数 = 较短串长度 / 较长串长度，取最高分；并列最高且归一化后不同 → 歧义返回 null。</li>
     * </ol>
     *
     * @param fieldCategory 字段类别关键词（如 "gender"、"degree"）
     * @param resumeValue   简历中的原始值（如 "1"、"本科"）
     * @param options       平台下拉选项列表
     * @return 匹配到的选项文案，未匹配或并列歧义返回 null
     */
    public static String matchByEnumAlias(String fieldCategory, String resumeValue, List<String> options) {
        List<String> aliases = enumAliasesOf(fieldCategory, resumeValue);
        if (aliases == null || options == null) {
            return null;
        }
        // 第一阶段：忽略大小写精确匹配
        for (String alias : aliases) {
            for (String opt : options) {
                if (opt.equalsIgnoreCase(alias)) {
                    return opt;
                }
            }
        }
        // 第二阶段：统一小写后的包含关系打分竞争（分数 = 较短串/较长串，取最高分；并列最高 → 歧义返回 null）
        String best = null;
        String bestNormalized = null;
        double bestScore = 0d;
        boolean tie = false;
        for (String alias : aliases) {
            String a = alias.toLowerCase();
            for (String opt : options) {
                String o = opt.toLowerCase();
                if (o.isEmpty() || (!o.contains(a) && !a.contains(o))) {
                    continue;
                }
                double score = (double) Math.min(a.length(), o.length()) / Math.max(a.length(), o.length());
                if (score > bestScore) {
                    best = opt;
                    bestNormalized = o;
                    bestScore = score;
                    tie = false;
                } else if (score == bestScore && !o.equals(bestNormalized)) {
                    tie = true;
                }
            }
        }
        return tie ? null : best;
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
        if (lower.contains("iddoctype")) return "idDocType";
        return null;
    }
}
