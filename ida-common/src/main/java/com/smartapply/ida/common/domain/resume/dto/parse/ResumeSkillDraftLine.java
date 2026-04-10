package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_skill} */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeSkillDraftLine implements Serializable {
    @JsonStringOrStringList
    private String skillCategory;
    @JsonStringOrStringList
    private String skillName;
    @JsonStringOrStringList
    private String proficiency;
    private Integer monthsOfUse;
}
