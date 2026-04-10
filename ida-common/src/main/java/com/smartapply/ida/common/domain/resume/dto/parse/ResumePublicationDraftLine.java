package com.smartapply.ida.common.domain.resume.dto.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartapply.ida.common.jackson.JsonStringOrStringList;
import lombok.Data;

import java.io.Serializable;

/** 对应表 {@code ida_resume_publication} */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumePublicationDraftLine implements Serializable {
    @JsonStringOrStringList
    private String pubType;
    @JsonStringOrStringList
    private String title;
    @JsonStringOrStringList
    private String role;
    @JsonStringOrStringList
    private String venue;
    @JsonStringOrStringList
    private String publishDate;
    @JsonStringOrStringList
    private String description;
}
