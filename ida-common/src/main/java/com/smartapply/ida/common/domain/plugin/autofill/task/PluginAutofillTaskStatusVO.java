package com.smartapply.ida.common.domain.plugin.autofill.task;

import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步填表任务的当前状态快照，供扩展轮询展示进度。
 * <p>
 * <b>状态机（简化）：</b>
 * <pre>
 * PENDING ──提交后立即──▶ RUNNING ──成功──▶ SUCCEEDED（带 result）
 *                    └──失败──▶ FAILED（带 errorMessage）
 * </pre>
 * {@link #percent} 为 0～100 的近似进度，仅供 UI 进度条；与 {@link #phase} 组合可展示「当前在做什么」。
 * <p>
 * 全流程说明见仓库文档 {@code docs/plugin-autofill-async-architecture.md}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginAutofillTaskStatusVO {

    /** 与提交响应中的 taskId 一致 */
    private String taskId;

    /**
     * 任务总状态：PENDING（刚创建）、RUNNING（执行中）、SUCCEEDED、FAILED。
     * 使用字符串便于前端 switch，避免枚举序列化名不一致。
     */
    private String status;

    /** 当前细粒度阶段，见 {@link PluginAutofillProgressPhase} 的 name() */
    private String phase;

    /** 给用户看的短句，可与 phase 一起展示 */
    private String message;

    /** 近似完成度 0～100；模型调用阶段可能长时间停在某一区间属正常现象 */
    private int percent;

    /** 任务创建时间（毫秒时间戳，便于前端展示耗时） */
    private long createdAtEpochMs;

    /** 最近一次状态更新时间 */
    private long updatedAtEpochMs;

    /** 仅当 status=SUCCEEDED 时有值，结构与同步 {@code /autofill-plan} 的 data 一致 */
    private PluginAutofillPlanResultVO result;

    /** 仅当 status=FAILED 时有值，为用户可读错误说明 */
    private String errorMessage;
}
