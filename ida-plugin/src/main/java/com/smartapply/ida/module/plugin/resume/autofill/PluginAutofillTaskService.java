package com.smartapply.ida.module.plugin.resume.autofill;

import com.smartapply.ida.ai.plugin.PluginAutofillProgressSink;
import com.smartapply.ida.ai.plugin.PluginFormAutofillAiService;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanRequest;
import com.smartapply.ida.common.domain.plugin.autofill.PluginAutofillPlanResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillTaskSubmitResultVO;
import com.smartapply.ida.common.domain.plugin.autofill.task.PluginAutofillTaskStatusVO;
import com.smartapply.ida.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 异步填表任务编排：「立即返回 taskId + 后台跑模型」的入口。
 * <p>
 * <b>执行路径：</b>
 * <ol>
 *   <li>{@link #submitTask(long, PluginAutofillPlanRequest)} 注册任务、提交 Runnable 到线程池后立即返回；HTTP 连接立即释放。</li>
 *   <li>Runnable 内调用 {@link PluginFormAutofillAiService#planAutofill(long, PluginAutofillPlanRequest, PluginAutofillProgressSink)}，
 *       进度通过 registry sink 写回内存快照。</li>
 *   <li>客户端通过 {@link #getTaskStatus(long, String)} 轮询同一 taskId，直到 status 为 SUCCEEDED/FAILED。</li>
 * </ol>
 * 与「流式 SSE」区别：当前仍是一次性 HTTP 调方舟；异步仅解决浏览器/网关超时与用户体验，模型内部仍阻塞至全文返回。
 */
@Slf4j
@Service
public class PluginAutofillTaskService {

    private final PluginAutofillTaskRegistry taskRegistry;
    private final PluginFormAutofillAiService pluginFormAutofillAiService;
    private final ThreadPoolTaskExecutor pluginAutofillExecutor;

    /**
     * 显式构造函数：为线程池 Bean 标注 {@link Qualifier}，避免与 Spring Boot 其他 {@link ThreadPoolTaskExecutor} 冲突。
     */
    public PluginAutofillTaskService(
            PluginAutofillTaskRegistry taskRegistry,
            PluginFormAutofillAiService pluginFormAutofillAiService,
            @Qualifier(PluginAutofillExecutorConfig.BEAN_NAME) ThreadPoolTaskExecutor pluginAutofillExecutor) {
        this.taskRegistry = taskRegistry;
        this.pluginFormAutofillAiService = pluginFormAutofillAiService;
        this.pluginAutofillExecutor = pluginAutofillExecutor;
    }

    /**
     * 提交异步任务：只做注册 + 投递线程池，<b>不做</b>参数校验（与同步接口一致，由 Service 层在 worker 内抛业务异常）。
     */
    public PluginAutofillTaskSubmitResultVO submitTask(long userId, PluginAutofillPlanRequest req) {
        taskRegistry.evictExpired();
        String taskId = UUID.randomUUID().toString();
        taskRegistry.register(taskId, userId);

        // Lambda 内只能引用 effectively final 变量，故拷贝一份再传入异步 Runnable
        final long uid = userId;
        final String tid = taskId;
        final PluginAutofillPlanRequest body = req;
        /*
         * execute(Runnable) 将任务放入线程池队列并立即返回当前线程（通常为 Tomcat worker），
         * 不会等待 runTask 执行完毕——这就是「异步提交」与同步 planAutofill 的核心区别。
         */
        pluginAutofillExecutor.execute(() -> runTask(uid, tid, body));

        return PluginAutofillTaskSubmitResultVO.builder()
                .taskId(taskId)
                .pollPathTemplate("/api/v1/plugin/resume/autofill-plan/tasks/{taskId}")
                .build();
    }

    /**
     * 在工作线程中执行完整填表流水线；任何异常都转为 FAILED 状态写回 registry，避免客户端永久 RUNNING。
     */
    private void runTask(long userId, String taskId, PluginAutofillPlanRequest req) {
        try {
            // 进入running阶段，此时轮询接口会看到 status=RUNNING；后续无论成功或失败都不会再变回 PENDING
            taskRegistry.markRunning(taskId);
            // sink 把 planAutofill 内的 onProgress 转发到内存任务表 + 打 [autofill-task] 日志
            PluginAutofillProgressSink sink = taskRegistry.sinkFor(taskId);
            // 阻塞发生在此处（线程池线程），而非 Tomcat 线程；其中含方舟 HTTP 长等待
            PluginAutofillPlanResultVO result = pluginFormAutofillAiService.planAutofill(userId, req, sink);
            taskRegistry.markSucceeded(taskId, result);
        } catch (BusinessException e) {
            taskRegistry.markFailed(taskId, e.getMessage());
        } catch (Throwable t) {
            log.error("[autofill-task taskId={}] 未捕获异常", taskId, t);
            taskRegistry.markFailed(taskId,
                    t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : "系统异常，请稍后重试");
        }
    }

    /**
     * 供 GET 轮询：校验任务归属，防止通过猜测 taskId 读取他人结果。
     */
    public PluginAutofillTaskStatusVO getTaskStatus(long userId, String taskId) {
        return taskRegistry.snapshotFor(userId, taskId)
                .orElseThrow(() -> new BusinessException("任务不存在、已过期或无权访问"));
    }
}
