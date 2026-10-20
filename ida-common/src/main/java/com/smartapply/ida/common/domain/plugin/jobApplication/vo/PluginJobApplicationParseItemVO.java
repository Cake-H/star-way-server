package com.smartapply.ida.common.domain.plugin.jobApplication.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 插件识别入库后的单条投递记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginJobApplicationParseItemVO {
    private Long applicationId;
    private String companyName;
    private String position;
    /** YYYY-MM-DD */
    private String applyDate;
    private String progress;
    private String applyUrl;
    /** true = 与已有记录重复，本次未新建（applicationId 为已有记录的 id） */
    private boolean duplicated;
}
