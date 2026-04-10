package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_education} 的一行草稿 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeEducationDraftLine implements Serializable {
    @JsonStringOrStringList
    private String schoolName;
    @JsonStringOrStringList
    private String collegeName;
    @JsonStringOrStringList
    private String major;
    @JsonStringOrStringList
    private String degree;
    @JsonStringOrStringList
    private String academicDegree;
    private Integer isFullTime;
    @JsonStringOrStringList
    private String enrollDate;
    @JsonStringOrStringList
    private String graduateDate;
    @JsonStringOrStringList
    private String countryRegion;
    @JsonStringOrStringList
    private String gpa;
    @JsonStringOrStringList
    private String rankingDesc;
    @JsonStringOrStringList
    private String majorCourses;
    @JsonStringOrStringList
    private String studentPosition;
    @JsonStringOrStringList
    private String honors;
    @JsonStringOrStringList
    private String description;
}
