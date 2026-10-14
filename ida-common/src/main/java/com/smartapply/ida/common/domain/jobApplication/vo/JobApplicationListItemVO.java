package com.smartapply.ida.common.domain.jobApplication.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 投递记录列表项（表格化展示用，含全部可编辑字段）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplicationListItemVO {
    private Long applicationId;
    private String companyName;
    private String position;
    /** YYYY-MM-DD */
    private String applyDate;
    private String progress;
    private String progressLabel;
    private String applyUrl;
    private String source;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
