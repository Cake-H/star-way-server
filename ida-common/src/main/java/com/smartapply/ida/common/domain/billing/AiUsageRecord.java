package com.smartapply.ida.common.domain.billing;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对应库表 {@code ida_ai_usage_record}：每次 LLM 调用一条用量记录（计费事实表）。
 * <p>
 * token 数由 LangChain4j {@code ChatModelListener} 在响应中捕获；
 * {@link #costPoints} 由计费组件按 {@code ida.billing.tokens-per-point} 换算。
 */
@SuppressWarnings("serial")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ida_ai_usage_record")
public class AiUsageRecord {

    /** 用量记录 ID */
    private Long id;

    /** 调用用户 ID */
    private Long userId;

    /** 业务类型：resume_parse/plugin_autofill/interview_summarize/mock_interview_generate */
    private String bizType;

    /** 关联业务 ID（场次/简历等，可空） */
    private Long bizId;

    /** 实际调用的模型 ID */
    private String model;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 总 token 数 */
    private Integer totalTokens;

    /** 本次扣减额度点数 */
    private Integer costPoints;

    private LocalDateTime createdAt;
}
