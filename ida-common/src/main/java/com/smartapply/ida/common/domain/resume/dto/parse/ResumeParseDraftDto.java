package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型返回、并经服务端反序列化后的「简历草稿」根对象。
 * <p>
 * 字段命名与 {@link com.smartapply.ida.common.domain.resume.Resume} 及子表列尽量对齐，便于前端一次绑定表单；
 * 日期类统一用字符串（ISO yyyy-MM-dd），由前端或后续保存接口再转 {@code java.util.Date} / {@code LocalDate}。
 * <p>
 * 根字段上 {@link JsonStringOrStringList}：模型可能输出字符串或字符串数组，统一反序列化为单行字符串。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeParseDraftDto {

    // —— 与 ida_resume 主表对应 —— //
    @JsonStringOrStringList
    private String title;
    @JsonStringOrStringList
    private String fullName;
    /** 0未知 1男 2女；模型无法判断时可 null */
    private Integer gender;
    @JsonStringOrStringList
    private String birthDate;
    @JsonStringOrStringList
    private String idDocType;
    @JsonStringOrStringList
    private String idDocNumber;
    @JsonStringOrStringList
    private String phone;
    @JsonStringOrStringList
    private String email;
    @JsonStringOrStringList
    private String qq;
    @JsonStringOrStringList
    private String wechat;
    @JsonStringOrStringList
    private String countryRegion;
    @JsonStringOrStringList
    private String nativePlace;
    @JsonStringOrStringList
    private String ethnicity;
    @JsonStringOrStringList
    private String politicalStatus;
    @JsonStringOrStringList
    private String hukouType;
    @JsonStringOrStringList
    private String province;
    @JsonStringOrStringList
    private String city;
    @JsonStringOrStringList
    private String district;
    @JsonStringOrStringList
    private String currentCity;
    @JsonStringOrStringList
    private String detailAddress;
    private Integer mailingSameAsCurrent;
    @JsonStringOrStringList
    private String mailingAddress;
    @JsonStringOrStringList
    private String emergencyContactName;
    @JsonStringOrStringList
    private String emergencyContactPhone;
    @JsonStringOrStringList
    private String emergencyContactRelation;
    private Integer jobSeekingStatus;
    private Integer isFreshGraduate;
    @JsonStringOrStringList
    private String workYears;
    @JsonStringOrStringList
    private String expectedPositions;
    @JsonStringOrStringList
    private String expectedCities;
    @JsonStringOrStringList
    private String expectedIndustries;
    @JsonStringOrStringList
    private String salaryExpectation;
    @JsonStringOrStringList
    private String availability;
    private Integer acceptTransfer;
    @JsonStringOrStringList
    private String selfIntro;
    @JsonStringOrStringList
    private String strengths;
    @JsonStringOrStringList
    private String portfolioLinks;

    // —— 子表：多条记录 —— //
    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumeEducationDraftLine> educations = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumeWorkDraftLine> works = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumeProjectDraftLine> projects = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumeCampusDraftLine> campusExperiences = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumeSkillDraftLine> skills = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumeLanguageDraftLine> languages = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumeCertificateDraftLine> certificates = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumeAwardDraftLine> awards = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private List<ResumePublicationDraftLine> publications = new ArrayList<>();
}
