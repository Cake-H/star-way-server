package com.smartapply.ida.common.domain.jobApplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** {@code POST /job-application} 请求体。 */
@Data
public class JobApplicationCreateRequest {
    @NotBlank(message = "投递公司不能为空")
    @Size(max = 128, message = "投递公司不能超过 128 字")
    private String companyName;
    @NotBlank(message = "投递岗位不能为空")
    @Size(max = 128, message = "投递岗位不能超过 128 字")
    private String position;
    /** YYYY-MM-DD，缺省取当天 */
    private String applyDate;
    /** 投递进度 code（见 {@code JobApplicationProgress}），缺省 APPLIED */
    private String progress;
    @Size(max = 512, message = "网申地址不能超过 512 字")
    private String applyUrl;
    @Size(max = 1024, message = "备注不能超过 1024 字")
    private String remark;
}
