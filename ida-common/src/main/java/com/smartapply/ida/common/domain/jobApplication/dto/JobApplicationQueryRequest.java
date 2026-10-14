package com.smartapply.ida.common.domain.jobApplication.dto;

import lombok.Data;

/** {@code GET /job-application} 查询参数：均可选、可组合，由 Spring 按查询串自动绑定。 */
@Data
public class JobApplicationQueryRequest {
    /** 投递公司（模糊匹配） */
    private String companyName;
    /** 投递岗位（模糊匹配） */
    private String position;
    /** 投递进度 code（精确匹配，见 {@code JobApplicationProgress}） */
    private String progress;
    /** 投递日期下界 YYYY-MM-DD（闭区间） */
    private String applyDateStart;
    /** 投递日期上界 YYYY-MM-DD（闭区间） */
    private String applyDateEnd;
}
