package com.smartapply.ida.module.app.interview.service.impl;

import com.smartapply.ida.common.domain.interview.vo.InterviewVoiceSummarizeResultVO;
import com.smartapply.ida.common.domain.interview.vo.InterviewVoiceSummarizeTaskStatusVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.executor.VoiceSummarizeExecutorConfig;
import com.smartapply.ida.integration.resume.InMemoryMultipartFile;
import com.smartapply.ida.module.app.interview.registry.AppInterviewSummarizeTaskRegistry;
import com.smartapply.ida.module.app.interview.service.AppInterviewService;
import com.smartapply.ida.module.app.interview.service.AppInterviewSummarizeTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 面试复盘语音总结异步任务实现。
 * <p>
 * 工作线程内整体调用 {@link AppInterviewService#voiceSummarize}：
 * 鉴权、ASR 听写（可能分钟级）、LLM 总结与计费计量（ThreadLocal）全部在同一线程内完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppInterviewSummarizeTaskServiceImpl implements AppInterviewSummarizeTaskService {

    private final AppInterviewSummarizeTaskRegistry taskRegistry;
    private final AppInterviewService appInterviewService;
    /** 专用线程池：单任务分钟级，core/max 刻意调小以保护 NLS 并发路数与 LLM QPS 配额 */
    @Qualifier(VoiceSummarizeExecutorConfig.BEAN_NAME)
    private final ThreadPoolTaskExecutor voiceSummarizeExecutor;

    @Override
    public String submit(long userId, long sessionId, MultipartFile file, String rawTranscript) throws IOException {
        taskRegistry.evictExpired();

        // MultipartFile 的临时文件随请求结束被删除，有文件则必须此刻读入内存；纯文本模式无文件
        final byte[] bytes;
        final String filename;
        final String contentType;
        if (file != null && !file.isEmpty()) {
            bytes = file.getBytes();
            filename = file.getOriginalFilename();
            contentType = file.getContentType();
        } else {
            bytes = null;
            filename = null;
            contentType = null;
        }
        final long uid = userId;
        final long sid = sessionId;
        final String transcript = rawTranscript;

        String taskId = UUID.randomUUID().toString();
        taskRegistry.register(taskId, userId, sessionId);

        // execute 仅入队即返回，HTTP 连接立即释放；实际听写+总结在工作线程进行
        voiceSummarizeExecutor.execute(() -> runTask(uid, sid, taskId, bytes, filename, contentType, transcript));
        return taskId;
    }

    /**
     * 工作线程入口：任何异常都必须落到 FAILED，避免任务永远停在 RUNNING。
     */
    private void runTask(long userId, long sessionId, String taskId,
                         byte[] bytes, String filename, String contentType, String rawTranscript) {
        try {
            MultipartFile memFile = bytes == null ? null : new InMemoryMultipartFile(bytes, filename, contentType);
            InterviewVoiceSummarizeResultVO result =
                    appInterviewService.voiceSummarize(userId, sessionId, memFile, rawTranscript);
            taskRegistry.markSucceeded(taskId, result);
        } catch (BusinessException e) {
            // 业务可预期错误（无权访问、听写未启用、识别失败等）：文案直接展示给用户
            taskRegistry.markFailed(taskId, e.getMessage());
        } catch (Throwable t) {
            log.error("[voice-summarize taskId={}] 未捕获异常", taskId, t);
            taskRegistry.markFailed(taskId, "系统异常，请稍后重试");
        }
    }

    @Override
    public InterviewVoiceSummarizeTaskStatusVO query(long userId, long sessionId, String taskId) {
        return taskRegistry.snapshotFor(userId, taskId, sessionId)
                .orElseThrow(() -> new BusinessException("任务不存在、已过期或无权访问"));
    }
}
