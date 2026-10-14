package com.smartapply.ida.module.app.resume.service.impl;

import com.smartapply.ida.common.domain.resume.vo.ResumeParseResultVO;
import com.smartapply.ida.common.domain.resume.vo.ResumeParseTaskStatusVO;
import com.smartapply.ida.common.exception.BusinessException;
import com.smartapply.ida.config.executor.ResumeParseExecutorConfig;
import com.smartapply.ida.integration.resume.InMemoryMultipartFile;
import com.smartapply.ida.integration.resume.ResumePlainTextExtractor;
import com.smartapply.ida.module.app.resume.registry.AppResumeParseTaskRegistry;
import com.smartapply.ida.module.app.resume.service.AppResumeParseTaskService;
import com.smartapply.ida.module.app.resume.service.AppResumeUploadParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppResumeParseTaskServiceImpl implements AppResumeParseTaskService {
    private final AppResumeParseTaskRegistry taskRegistry;

    private final AppResumeUploadParseService uploadParseService;

    @Qualifier(ResumeParseExecutorConfig.BEAN_NAME)
    private final ThreadPoolTaskExecutor resumeParseExecutor;

    @Override
    public String submit(long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        // 前置大小校验：超限在请求线程直接拒绝，不注册任务、不占线程池与 OSS 流量
        if (file.getSize() > ResumePlainTextExtractor.MAX_BYTES) {
            throw new BusinessException(ResumePlainTextExtractor.SIZE_EXCEEDED_MESSAGE);
        }
        taskRegistry.evictExpired();

        // MultipartFile 的临时文件随请求结束被删除，必须此刻读入内存
        final byte[] bytes = file.getBytes();
        final String filename = file.getOriginalFilename();
        final String contentType = file.getContentType();
        final long uid = userId;

        String taskId = UUID.randomUUID().toString();
        taskRegistry.register(taskId, userId);

        // execute 仅入队即返回，HTTP连接立即释放；实际解析在工作线程进行
        resumeParseExecutor.execute(() -> runTask(uid, taskId, bytes, filename, contentType));
        return taskId;
    }

    /**
     * 工作线程入口：任何异常都必须落到 FAILED，避免任务永远停在RUNNING。
     */
    private void runTask(long userId, String taskId, byte[] bytes, String filename, String contentType) {
        try {
            MultipartFile memFile = new InMemoryMultipartFile(bytes, filename, contentType);
            ResumeParseResultVO result = uploadParseService.uploadAndParse(userId, memFile);
            taskRegistry.markSucceeded(taskId, result);
        } catch (BusinessException e) {
            log.warn("[resume-parse] taskId={} 解析失败: {}", taskId, e.getMessage());
            taskRegistry.markFailed(taskId, e.getMessage());
        } catch (Throwable t) {
            log.error("[resume-parse] taskId={} 解析异常", taskId, t);
            taskRegistry.markFailed(taskId, "系统异常，请稍后重试");
        }
    }

    @Override
    public ResumeParseTaskStatusVO query(long userId, String taskId) {
        return taskRegistry.snapshotFor(userId, taskId)
                .orElseThrow(() -> new BusinessException("任务不存在、已过期或无权访问"));
    }
}
