package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_certificate} */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeCertificateDraftLine implements Serializable {
    @JsonStringOrStringList
    private String certName;
    @JsonStringOrStringList
    private String issuer;
    @JsonStringOrStringList
    private String obtainDate;
    @JsonStringOrStringList
    private String certNo;
}
