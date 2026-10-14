package com.smartapply.ida.common.domain.jobApplication.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 投递进度选项（下拉筛选/编辑用）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplicationProgressOptionVO {
    private String code;
    private String label;
}
