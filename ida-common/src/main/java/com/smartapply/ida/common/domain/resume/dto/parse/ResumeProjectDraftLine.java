package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_project} */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeProjectDraftLine implements Serializable {
    @JsonStringOrStringList
    private String projectName;
    @JsonStringOrStringList
    private String roleName;
    @JsonStringOrStringList
    private String startDate;
    @JsonStringOrStringList
    private String endDate;
    @JsonStringOrStringList
    private String companyOrOrg;
    @JsonStringOrStringList
    private String techKeywords;
    @JsonStringOrStringList
    private String projectUrl;
    @JsonStringOrStringList
    private String dutyDescription;
    @JsonStringOrStringList
    private String achievement;
    @JsonStringOrStringList
    private String description;
}
