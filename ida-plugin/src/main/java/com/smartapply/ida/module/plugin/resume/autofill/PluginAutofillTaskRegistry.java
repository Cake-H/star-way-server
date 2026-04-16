package com.smartapply.ida.module.plugin.resume.autofill;

import com.smartapply.ida.ai.plugin.PluginAutofillProgressSink;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillProgressPhase;
import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillTaskStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存态异步填表任务表：taskId → 可变任务记录。
 * <p>
 * <b>实现取舍（产品/运维须知）：</b>
 * <ul>
 *   <li>数据仅存本 JVM 堆内，<b>进程重启或水平多实例时任务不可见</b>；单机插件服务场景可接受。
 *       若未来要多副本或无状态，应改为 Redis + TTL 或 DB 任务表。</li>
 *   <li>通过 {@link #evictExpired()} 定期清理超时任务，防止 Map 无限增长。</li>
 *   <li>所有对单条 {@link TaskRecord} 的字段更新在 {@link TaskRecord} 实例上 synchronized，
 *       保证轮询读到的 phase/message/result 一致；ConcurrentHashMap 只保证桶级并发。</li>
 * </ul>
 */
@Slf4j
@Component
public class PluginAutofillTaskRegistry {

    /** 任务超过该时间未再更新则可在清理时移除（客户端应在此之前完成轮询） */
    private static final Duration TASK_TTL = Duration.ofMinutes(30);

    /**
     * 任务表：key=taskId。选用 ConcurrentHashMap 保证并发 put/get 安全；
     * 单条 TaskRecord 内仍用 synchronized 方法，避免「只更新了 phase 尚未更新 percent」的撕裂读。
     * 未使用 Redis——进程重启或水平扩容后任务不可见，上线多副本前需改造存储。
     */
    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    /**
     * 创建任务占位：在异步线程真正开始前即返回 taskId，使客户端可立刻开始轮询（先看到 PENDING）。
     */
    public void register(String taskId, long userId) {
        long now = System.currentTimeMillis();
        TaskRecord rec = new TaskRecord(taskId, userId, now);
        tasks.put(taskId, rec);
        log.info("[autofill-task] 已注册 taskId={} userId={} status=PENDING", taskId, userId);
    }

    /**
     * 将任务标记为执行中（worker 线程入口调用一次即可）。
     */
    public void markRunning(String taskId) {
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) {
            return;
        }
        rec.markRunning();
        log.info("[autofill-task] 开始执行 taskId={} status=RUNNING", taskId);
    }

    /**
     * 构造与当前 taskId 绑定的进度回调：内部转发到 {@link #touchProgress(String, PluginAutofillProgressPhase, String, int)}。
     */
    public PluginAutofillProgressSink sinkFor(String taskId) {
        // 闭包捕获 taskId，使 PluginFormAutofillAiService 无需感知 Registry 类型（依赖倒置）
        return (phase, detail, percent) -> touchProgress(taskId, phase, detail, percent);
    }

    /**
     * 更新进度并输出控制台行，便于开发/运维 tail 日志观察「卡在哪一步」。
     */
    public void touchProgress(String taskId, PluginAutofillProgressPhase phase, String detail, int percent) {
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) {
            return;
        }
        rec.touch(phase, detail, percent);
        log.info("[autofill-task taskId={}] phase={} percent={}% — {}", taskId, phase.name(), percent, detail);
    }

    public void markSucceeded(String taskId, PluginAutofillPlanResultVO result) {
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) {
            return;
        }
        rec.markSucceeded(result);
        log.info("[autofill-task taskId={}] 成功结束 fillable={} assignments={}", taskId,
                result != null && result.isFillable(),
                result != null && result.getAssignments() != null ? result.getAssignments().size() : 0);
    }

    public void markFailed(String taskId, String errorMessage) {
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) {
            return;
        }
        rec.markFailed(errorMessage);
        log.warn("[autofill-task taskId={}] 失败: {}", taskId, errorMessage);
    }

    /**
     * 校验 userId 后返回只读快照；若任务不存在、已过期或越权则 empty。
     */
    public Optional<PluginAutofillTaskStatusVO> snapshotFor(long userId, String taskId) {
        evictExpired();
        TaskRecord rec = tasks.get(taskId);
        if (rec == null || rec.userId != userId) {
            return Optional.empty();
        }
        return Optional.of(rec.toStatusVo());
    }

    /**
     * 扫描全表移除过期任务，时间复杂度 O(n)，在提交/查询入口调用即可，避免后台定时器额外线程。
     */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        long deadline = now - TASK_TTL.toMillis();
        tasks.entrySet().removeIf(e -> e.getValue().updatedAt < deadline);
    }

    /**
     * 单条任务的可变状态；synchronized 方法保证多字段更新原子可见。
     */
    static final class TaskRecord {
        final String taskId;
        final long userId;
        final long createdAt;
        volatile long updatedAt;

        volatile String status; // PENDING | RUNNING | SUCCEEDED | FAILED
        volatile PluginAutofillProgressPhase phase;
        volatile String message;
        volatile int percent;
        volatile PluginAutofillPlanResultVO result;
        volatile String errorMessage;

        TaskRecord(String taskId, long userId, long createdAt) {
            this.taskId = taskId;
            this.userId = userId;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
            this.status = "PENDING";
            this.phase = PluginAutofillProgressPhase.RECEIVED;
            this.message = "任务已创建，等待调度";
            this.percent = 0;
        }

        synchronized void markRunning() {
            this.status = "RUNNING";
            this.phase = PluginAutofillProgressPhase.RECEIVED;
            this.message = "工作线程已接管";
            this.percent = Math.max(this.percent, 5);
            this.updatedAt = System.currentTimeMillis();
        }

        synchronized void touch(PluginAutofillProgressPhase phase, String detail, int percent) {
            this.phase = phase;
            this.message = detail != null ? detail : "";
            this.percent = Math.max(0, Math.min(100, percent));
            this.updatedAt = System.currentTimeMillis();
        }

        synchronized void markSucceeded(PluginAutofillPlanResultVO result) {
            this.status = "SUCCEEDED";
            this.phase = PluginAutofillProgressPhase.COMPLETED;
            this.message = "填表计划已生成";
            this.percent = 100;
            this.result = result;
            this.errorMessage = null;
            this.updatedAt = System.currentTimeMillis();
        }

        synchronized void markFailed(String errorMessage) {
            this.status = "FAILED";
            this.phase = PluginAutofillProgressPhase.FAILED;
            this.message = "任务失败";
            this.percent = 100;
            this.errorMessage = errorMessage;
            this.result = null;
            this.updatedAt = System.currentTimeMillis();
        }

        synchronized PluginAutofillTaskStatusVO toStatusVo() {
            return PluginAutofillTaskStatusVO.builder()
                    .taskId(taskId)
                    .status(status)
                    .phase(phase != null ? phase.name() : "")
                    .message(message)
                    .percent(percent)
                    .createdAtEpochMs(createdAt)
                    .updatedAtEpochMs(updatedAt)
                    .result(result)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
}
