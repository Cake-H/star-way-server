package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_work} */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeWorkDraftLine implements Serializable {
    @JsonStringOrStringList
    private String companyName;
    @JsonStringOrStringList
    private String department;
    @JsonStringOrStringList
    private String jobTitle;
    @JsonStringOrStringList
    private String workCity;
    @JsonStringOrStringList
    private String companyIndustry;
    @JsonStringOrStringList
    private String employmentNature;
    @JsonStringOrStringList
    private String startDate;
    @JsonStringOrStringList
    private String endDate;
    @JsonStringOrStringList
    private String workDescription;
    @JsonStringOrStringList
    private String leaveReason;
}
