package com.smartapply.ida.common.domain.jobApplication.dto;

import lombok.Data;

/** {@code PUT /job-application/{applicationId}}：仅更新传入的非 null 字段。 */
@Data
public class JobApplicationUpdateRequest {
    private String companyName;
    private String position;
    /** YYYY-MM-DD */
    private String applyDate;
    /** 投递进度 code（见 {@code JobApplicationProgress}） */
    private String progress;
    private String applyUrl;
    private String remark;
}
