package com.smartapply.ida.common.domain.plugin.jobApplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PluginJobApplicationParseRequest {
    @NotBlank(message = "网申地址不能为空")
    @Size(max = 512, message = "网申地址不能超过 512 字")
    private String pageUrl;
    @Size(max = 256, message = "网页标题不能超过 256 字")
    private String pageTitle;
    private String textContent;
}
