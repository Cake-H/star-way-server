package com.smartapply.ida.module.plugin.resume.autofill;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 为「异步一键填表」提供专用线程池，与 Tomcat 工作线程解耦。
 * <p>
 * <b>为何单独线程池：</b>若使用默认 {@code ForkJoinPool.commonPool()} 或随意 new Thread，
 * 在高并发下会与业务线程争抢资源，且难以通过 Spring 统一管理优雅停机。
 * 本池线程名前缀 {@code plugin-autofill-}，便于在 jstack / 日志中识别。
 */
@Configuration
public class PluginAutofillExecutorConfig {

    public static final String BEAN_NAME = "pluginAutofillExecutor";

    /**
     * 专用于执行 {@link PluginAutofillTaskService} 提交的填表任务。
     * 队列容量 200：超出后 {@link ThreadPoolTaskExecutor#submit} 会按拒绝策略处理（默认抛异常），
     * 防止无界队列撑爆内存；若线上排队过长应配合前端限流或扩容实例。
     */
    @Bean(name = BEAN_NAME)
    public ThreadPoolTaskExecutor pluginAutofillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("plugin-autofill-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        // 有界队列：防止瞬时海量提交撑爆内存；满队列时默认拒绝策略会抛异常，需监控与前端限流
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
