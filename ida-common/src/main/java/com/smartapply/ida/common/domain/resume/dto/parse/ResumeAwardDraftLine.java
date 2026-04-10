package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_award} */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeAwardDraftLine implements Serializable {
    @JsonStringOrStringList
    private String awardName;
    @JsonStringOrStringList
    private String awardLevel;
    @JsonStringOrStringList
    private String issuer;
    @JsonStringOrStringList
    private String awardDate;
    @JsonStringOrStringList
    private String description;
}
