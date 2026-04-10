package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_campus} */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeCampusDraftLine implements Serializable {
    @JsonStringOrStringList
    private String organizationName;
    @JsonStringOrStringList
    private String roleTitle;
    @JsonStringOrStringList
    private String startDate;
    @JsonStringOrStringList
    private String endDate;
    @JsonStringOrStringList
    private String description;
}
