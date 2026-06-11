package com.smartapply.ida.ai.plugin.deterministic;

import com.smartapply.ida.common.domain.resume.dto.parse.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 {@link ResumeParseDraftDto} 中按 clientFieldKey 提取字段值。
 * <p>
 * 使用静态 lambda 注册表（非反射），编译期即可校验 getter 存在性。
 * clientFieldKey 格式约定：
 * <ul>
 *   <li>基本字段：{@code basic.fullName}、{@code location.province}、{@code job.workYears}</li>
 *   <li>集合字段：{@code education.0.schoolName}、{@code work.1.companyName}</li>
 * </ul>
 */
public final class ResumeFieldAccessor {

    private ResumeFieldAccessor() {}

    // clientFieldKey 中带索引的格式：prefix.index.field
    private static final Pattern INDEXED_KEY_PATTERN = Pattern.compile("^(\\w+)\\.(\\d+)\\.(\\w+)$");

    // ======================== 基本（非集合）字段映射 ========================

    private static final Map<String, Function<ResumeParseDraftDto, String>> FLAT_GETTERS;

    static {
        Map<String, Function<ResumeParseDraftDto, String>> m = new HashMap<>();
        // 基本信息
        m.put("basic.fullName",     d -> d.getFullName());
        m.put("basic.gender",       d -> intToStr(d.getGender()));
        m.put("basic.birthDate",    d -> d.getBirthDate());
        m.put("basic.idDocType",    d -> d.getIdDocType());
        m.put("basic.idDocNumber",  d -> d.getIdDocNumber());
        m.put("basic.phone",        d -> d.getPhone());
        m.put("basic.email",        d -> d.getEmail());
        m.put("basic.qq",           d -> d.getQq());
        m.put("basic.wechat",       d -> d.getWechat());

        // 地域
        m.put("location.countryRegion",   d -> d.getCountryRegion());
        m.put("location.nativePlace",     d -> d.getNativePlace());
        m.put("location.ethnicity",       d -> d.getEthnicity());
        m.put("location.politicalStatus", d -> d.getPoliticalStatus());
        m.put("location.hukouType",       d -> d.getHukouType());
        m.put("location.province",        d -> d.getProvince());
        m.put("location.city",            d -> d.getCity());
        m.put("location.district",        d -> d.getDistrict());
        m.put("location.currentCity",     d -> d.getCurrentCity());
        m.put("location.detailAddress",   d -> d.getDetailAddress());

        // 通讯/紧急联系
        m.put("mailing.mailingAddress",         d -> d.getMailingAddress());
        m.put("emergency.emergencyContactName",     d -> d.getEmergencyContactName());
        m.put("emergency.emergencyContactPhone",    d -> d.getEmergencyContactPhone());
        m.put("emergency.emergencyContactRelation", d -> d.getEmergencyContactRelation());

        // 求职意向
        m.put("job.workYears",          d -> d.getWorkYears());
        m.put("job.expectedPositions",  d -> d.getExpectedPositions());
        m.put("job.expectedCities",     d -> d.getExpectedCities());
        m.put("job.expectedIndustries", d -> d.getExpectedIndustries());
        m.put("job.salaryExpectation",  d -> d.getSalaryExpectation());
        m.put("job.availability",       d -> d.getAvailability());
        m.put("job.isFreshGraduate",    d -> intToStr(d.getIsFreshGraduate()));
        m.put("job.acceptTransfer",     d -> intToStr(d.getAcceptTransfer()));

        // 自我介绍（长文本，通常交 LLM，但仍注册以供缓存或简单场景使用）
        m.put("profile.selfIntro",      d -> d.getSelfIntro());
        m.put("profile.strengths",      d -> d.getStrengths());
        m.put("profile.portfolioLinks", d -> d.getPortfolioLinks());

        FLAT_GETTERS = Map.copyOf(m);
    }

    // ======================== 集合字段映射 ========================
    // key = "集合前缀.字段名"（如 "education.schoolName"），value = (draft, index) -> String

    private static final Map<String, BiFunction<ResumeParseDraftDto, Integer, String>> INDEXED_GETTERS;

    static {
        Map<String, BiFunction<ResumeParseDraftDto, Integer, String>> m = new HashMap<>();

        // 教育经历
        m.put("education.schoolName",    (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getSchoolName));
        m.put("education.collegeName",   (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getCollegeName));
        m.put("education.major",         (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getMajor));
        m.put("education.degree",        (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getDegree));
        m.put("education.academicDegree",(d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getAcademicDegree));
        m.put("education.enrollDate",    (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getEnrollDate));
        m.put("education.graduateDate",  (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getGraduateDate));
        m.put("education.countryRegion", (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getCountryRegion));
        m.put("education.gpa",          (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getGpa));
        m.put("education.isFullTime",   (d, i) -> safeGet(d.getEducations(), i, e -> intToStr(e.getIsFullTime())));
        m.put("education.rankingDesc",  (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getRankingDesc));
        m.put("education.majorCourses", (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getMajorCourses));
        m.put("education.description",  (d, i) -> safeGet(d.getEducations(), i, ResumeEducationDraftLine::getDescription));

        // 工作经历
        m.put("work.companyName",     (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getCompanyName));
        m.put("work.department",      (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getDepartment));
        m.put("work.jobTitle",        (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getJobTitle));
        m.put("work.workCity",        (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getWorkCity));
        m.put("work.companyIndustry", (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getCompanyIndustry));
        m.put("work.employmentNature",(d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getEmploymentNature));
        m.put("work.startDate",       (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getStartDate));
        m.put("work.endDate",         (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getEndDate));
        m.put("work.workDescription", (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getWorkDescription));
        m.put("work.leaveReason",     (d, i) -> safeGet(d.getWorks(), i, ResumeWorkDraftLine::getLeaveReason));

        // 项目经历
        m.put("project.projectName",    (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getProjectName));
        m.put("project.roleName",       (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getRoleName));
        m.put("project.startDate",      (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getStartDate));
        m.put("project.endDate",        (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getEndDate));
        m.put("project.companyOrOrg",   (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getCompanyOrOrg));
        m.put("project.techKeywords",   (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getTechKeywords));
        m.put("project.projectUrl",     (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getProjectUrl));
        m.put("project.dutyDescription",(d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getDutyDescription));
        m.put("project.achievement",    (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getAchievement));
        m.put("project.description",    (d, i) -> safeGet(d.getProjects(), i, ResumeProjectDraftLine::getDescription));

        // 校园/社团经历
        m.put("campus.organizationName",(d, i) -> safeGet(d.getCampusExperiences(), i, ResumeCampusDraftLine::getOrganizationName));
        m.put("campus.roleTitle",       (d, i) -> safeGet(d.getCampusExperiences(), i, ResumeCampusDraftLine::getRoleTitle));
        m.put("campus.startDate",       (d, i) -> safeGet(d.getCampusExperiences(), i, ResumeCampusDraftLine::getStartDate));
        m.put("campus.endDate",         (d, i) -> safeGet(d.getCampusExperiences(), i, ResumeCampusDraftLine::getEndDate));
        m.put("campus.description",     (d, i) -> safeGet(d.getCampusExperiences(), i, ResumeCampusDraftLine::getDescription));

        // 技能
        m.put("skill.skillName",    (d, i) -> safeGet(d.getSkills(), i, ResumeSkillDraftLine::getSkillName));
        m.put("skill.skillCategory",(d, i) -> safeGet(d.getSkills(), i, ResumeSkillDraftLine::getSkillCategory));
        m.put("skill.proficiency",  (d, i) -> safeGet(d.getSkills(), i, ResumeSkillDraftLine::getProficiency));

        // 语言
        m.put("language.languageName",(d, i) -> safeGet(d.getLanguages(), i, ResumeLanguageDraftLine::getLanguageName));
        m.put("language.certName",   (d, i) -> safeGet(d.getLanguages(), i, ResumeLanguageDraftLine::getCertName));
        m.put("language.certScore",  (d, i) -> safeGet(d.getLanguages(), i, ResumeLanguageDraftLine::getCertScore));

        // 证书
        m.put("certificate.certName", (d, i) -> safeGet(d.getCertificates(), i, ResumeCertificateDraftLine::getCertName));
        m.put("certificate.issuer",   (d, i) -> safeGet(d.getCertificates(), i, ResumeCertificateDraftLine::getIssuer));
        m.put("certificate.obtainDate",(d, i) -> safeGet(d.getCertificates(), i, ResumeCertificateDraftLine::getObtainDate));
        m.put("certificate.certNo",   (d, i) -> safeGet(d.getCertificates(), i, ResumeCertificateDraftLine::getCertNo));

        // 奖项
        m.put("award.awardName",  (d, i) -> safeGet(d.getAwards(), i, ResumeAwardDraftLine::getAwardName));
        m.put("award.awardLevel", (d, i) -> safeGet(d.getAwards(), i, ResumeAwardDraftLine::getAwardLevel));
        m.put("award.issuer",     (d, i) -> safeGet(d.getAwards(), i, ResumeAwardDraftLine::getIssuer));
        m.put("award.awardDate",  (d, i) -> safeGet(d.getAwards(), i, ResumeAwardDraftLine::getAwardDate));

        // 发表成果
        m.put("publication.title",      (d, i) -> safeGet(d.getPublications(), i, ResumePublicationDraftLine::getTitle));
        m.put("publication.role",       (d, i) -> safeGet(d.getPublications(), i, ResumePublicationDraftLine::getRole));
        m.put("publication.venue",      (d, i) -> safeGet(d.getPublications(), i, ResumePublicationDraftLine::getVenue));
        m.put("publication.publishDate",(d, i) -> safeGet(d.getPublications(), i, ResumePublicationDraftLine::getPublishDate));

        INDEXED_GETTERS = Map.copyOf(m);
    }

    /**
     * 根据 clientFieldKey 从简历 draft 中提取值。
     *
     * @return 提取的字符串值；字段不存在、值为空、或索引越界时返回 null
     */
    public static String extractValue(ResumeParseDraftDto draft, String clientFieldKey) {
        if (draft == null || clientFieldKey == null || clientFieldKey.isBlank()) {
            return null;
        }
        // 先尝试基本字段直接查找
        Function<ResumeParseDraftDto, String> flatGetter = FLAT_GETTERS.get(clientFieldKey);
        if (flatGetter != null) {
            return blankToNull(flatGetter.apply(draft));
        }
        // 尝试带索引的集合字段
        Matcher m = INDEXED_KEY_PATTERN.matcher(clientFieldKey);
        if (m.matches()) {
            String prefix = m.group(1);      // e.g., "education"
            int index = Integer.parseInt(m.group(2));
            String fieldName = m.group(3);   // e.g., "schoolName"
            String indexedKey = prefix + "." + fieldName;
            BiFunction<ResumeParseDraftDto, Integer, String> indexedGetter = INDEXED_GETTERS.get(indexedKey);
            if (indexedGetter != null) {
                return blankToNull(indexedGetter.apply(draft, index));
            }
        }
        return null;
    }

    /**
     * 判断 clientFieldKey 是否在映射表中已知（不考虑具体索引值）。
     */
    public static boolean isKnownKey(String clientFieldKey) {
        if (clientFieldKey == null) return false;
        if (FLAT_GETTERS.containsKey(clientFieldKey)) return true;
        Matcher m = INDEXED_KEY_PATTERN.matcher(clientFieldKey);
        if (m.matches()) {
            return INDEXED_GETTERS.containsKey(m.group(1) + "." + m.group(3));
        }
        return false;
    }

    /**
     * 推断字段路径的末尾字段名（用于枚举类别推断）。
     * 如 "basic.gender" → "gender"，"education.0.degree" → "degree"
     */
    public static String extractFieldName(String clientFieldKey) {
        if (clientFieldKey == null) return null;
        int lastDot = clientFieldKey.lastIndexOf('.');
        return lastDot >= 0 ? clientFieldKey.substring(lastDot + 1) : clientFieldKey;
    }

    // ======================== 工具方法 ========================

    private static <T> String safeGet(List<T> list, int index, Function<T, String> getter) {
        if (list == null || index < 0 || index >= list.size()) return null;
        T item = list.get(index);
        return item == null ? null : blankToNull(getter.apply(item));
    }

    private static <T> String safeGet(List<T> list, int index, Function<T, Object> getter, boolean dummy) {
        if (list == null || index < 0 || index >= list.size()) return null;
        T item = list.get(index);
        if (item == null) return null;
        Object v = getter.apply(item);
        return v == null ? null : blankToNull(v.toString());
    }

    private static String intToStr(Integer v) {
        return v == null ? null : v.toString();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
