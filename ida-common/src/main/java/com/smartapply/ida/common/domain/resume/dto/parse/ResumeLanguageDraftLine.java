package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_language} */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeLanguageDraftLine implements Serializable {
    @JsonStringOrStringList
    private String languageName;
    @JsonStringOrStringList
    private String listenLevel;
    @JsonStringOrStringList
    private String speakLevel;
    @JsonStringOrStringList
    private String readLevel;
    @JsonStringOrStringList
    private String writeLevel;
    @JsonStringOrStringList
    private String certName;
    @JsonStringOrStringList
    private String certScore;
}
