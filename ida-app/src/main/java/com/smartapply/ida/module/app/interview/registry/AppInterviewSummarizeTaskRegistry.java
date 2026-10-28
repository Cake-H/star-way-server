package com.smartapply.ida.module.app.interview.registry;

import com.smartapply.ida.common.domain.interview.vo.InterviewVoiceSummarizeResultVO;
import com.smartapply.ida.common.domain.interview.vo.InterviewVoiceSummarizeTaskStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AppInterviewSummarizeTaskRegistry {
    /**
     * 任务超过该时间未再更新则可在清理时移除（客户端应在此之前完成轮询）
     */
    private static final Duration TASK_TTL = Duration.ofMinutes(30);

    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    public void register(String taskId, long userId, long sessionId) {
        long now = System.currentTimeMillis();
        TaskRecord rec = new TaskRecord(taskId, userId, sessionId, now);
        tasks.put(taskId, rec);
        log.info("[voice-summarize] 已注册 taskId={} userId={} sessionId={} status=RUNNING", taskId, userId, sessionId);
    }

    public void markSucceeded(String taskId, InterviewVoiceSummarizeResultVO result) {
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) {
            return;
        }
        rec.markSuccess(result); // 写入终态 + result
        log.info("[voice-summarize] taskId={} 总结成功", taskId);
    }

    public void markFailed(String taskId, String errorMessage) {
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) {
            return;
        }
        rec.markFailed(errorMessage); // 写入终态 + errorMessage
        log.warn("[voice-summarize] taskId={} 总结失败: {}", taskId, errorMessage);
    }

    public Optional<InterviewVoiceSummarizeTaskStatusVO> snapshotFor(long userId, String taskId, long sessionId) {
        evictExpired(); // 读前顺带清理，减轻 Map 体积
        TaskRecord rec = tasks.get(taskId);
        if (rec == null || rec.userId != userId || rec.sessionId != sessionId) {
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
        final long sessionId;
        final long userId;
        final long createdAt;
        volatile long updatedAt;

        volatile String status; // RUNNING / SUCCESS /FAILED
        volatile InterviewVoiceSummarizeResultVO result;
        volatile String errorMessage;

        TaskRecord(String taskId, long userId, long sessionId, long createdAt) {
            this.taskId = taskId;
            this.userId = userId;
            this.sessionId = sessionId;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
            this.status = "RUNNING";
        }

        synchronized void markSuccess(InterviewVoiceSummarizeResultVO result) {
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

        synchronized InterviewVoiceSummarizeTaskStatusVO toStatusVo() {
            return InterviewVoiceSummarizeTaskStatusVO.builder()
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
