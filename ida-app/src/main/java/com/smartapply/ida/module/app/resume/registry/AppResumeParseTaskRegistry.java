package com.smartapply.ida.module.app.resume.registry;

import com.smartapply.ida.common.domain.resume.vo.ResumeParseResultVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeParseTaskStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存态异步简历解析任务表：taskId → 可变任务记录。
 * <p>
 * <b>实现取舍（产品/运维须知）：</b>
 * <ul>
 *   <li>数据仅存本 JVM 堆内，<b>进程重启或水平多实例时任务不可见</b>；单机部署场景可接受，
 *       客户端轮询不到任务时重新上传即可。若未来要多副本或无状态，应改为 Redis + TTL 或 DB 任务表。</li>
 *   <li>通过 {@link #evictExpired()} 在读路径顺带清理超时任务，防止 Map 无限增长，不占用额外线程。</li>
 *   <li>所有对单条 {@link TaskRecord} 的字段更新在 {@link TaskRecord} 实例上 synchronized，
 *       保证轮询读到的 status/result/errorMessage 一致；ConcurrentHashMap 只保证桶级并发。</li>
 *   <li>与插件填表任务表（PluginAutofillTaskRegistry）的差异：解析无中间进度，
 *       故无 phase/percent/partialResult，登记即为 RUNNING，状态机仅 RUNNING → SUCCESS / FAILED。</li>
 * </ul>
 */
@Slf4j
@Component
public class AppResumeParseTaskRegistry {

    /** 任务超过该时间未再更新则可在清理时移除（客户端应在此之前完成轮询） */
    private static final Duration TASK_TTL = Duration.ofMinutes(30);

    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    public void register(String taskId, long userId) {
        long now = System.currentTimeMillis();
        TaskRecord rec = new TaskRecord(taskId, userId, now);
        tasks.put(taskId, rec);
        log.info("[resume-parse] 已注册 taskId={} userId={} status=RUNNING", taskId, userId);
    }

    public void markSucceeded(String taskId, ResumeParseResultVO result) {
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) {
            return;
        }
        rec.markSuccess(result); // 写入终态 + result
        log.info("[resume-parse] taskId={} 解析成功", taskId);
    }

    public void markFailed(String taskId, String errorMessage) {
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) {
            return;
        }
        rec.markFailed(errorMessage); // 写入终态 + errorMessage
        log.warn("[resume-parse] taskId={} 解析失败: {}", taskId, errorMessage);
    }

    public Optional<ResumeParseTaskStatusVO> snapshotFor(long userId, String taskId) {
        evictExpired(); // 读前顺带清理，减轻 Map 体积
        TaskRecord rec = tasks.get(taskId);
        if (rec == null || rec.userId != userId) {
            return Optional.empty();
        }
        return Optional.of(rec.toStatusVo());
    }

    public void evictExpired() {
        long now = System.currentTimeMillis();
        long deadline = now - TASK_TTL.toMillis();
        tasks.entrySet().removeIf(e -> e.getValue().updatedAt < deadline);
    }

    static final class TaskRecord {
        final String taskId;
        final long userId;
        final long createdAt;
        volatile long updatedAt;

        volatile String status; // RUNNING / SUCCESS /FAILED
        volatile ResumeParseResultVO result;
        volatile String errorMessage;

        TaskRecord(String taskId, long userId, long createdAt) {
            this.taskId = taskId;
            this.userId = userId;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
            this.status = "RUNNING";
        }

        synchronized void markSuccess(ResumeParseResultVO result) {
            this.status = "SUCCESS";
            this.result = result;
            this.errorMessage = null;
            this.updatedAt = System.currentTimeMillis();
        }

        synchronized void markFailed(String errorMessage) {
            this.status = "FAILED";
            this.errorMessage = errorMessage;
            this.result = null;
            this.updatedAt = System.currentTimeMillis();
        }

        synchronized ResumeParseTaskStatusVO toStatusVo() {
            return ResumeParseTaskStatusVO.builder()
                    .taskId(taskId)
                    .status(status)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .result(result)
                    .errorMessage(errorMessage)
                    .build();
        }
    }

}
