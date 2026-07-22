package com.smartapply.ida.common.domain.billing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用量看板：按业务类型聚合行。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UsageBizStatVO {

    /** 业务类型：resume_parse/plugin_autofill/interview_summarize/mock_interview_generate */
    private String bizType;

    /** AI 调用次数 */
    private Long callCount;

    /** 消耗 token 总数 */
    private Long totalTokens;

    /** 消耗额度点数 */
    private Long costPoints;
}
