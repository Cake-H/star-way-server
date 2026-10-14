package com.smartapply.ida.config.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 为 「简历详情查询」提供专用线程池，与 Tomcat 工作线程解耦。
 */
@Configuration
public class ResumeQueryExecutorConfig {

    public static final String BEAN_NAME = "resumeQueryExecutor";

    /**
     * 专用于简历详情 9 张子表的查询任务。
     * 队列容量 100：超出后 {@link ThreadPoolTaskExecutor#submit} 会按拒绝策略处理（默认抛异常）
     */
    @Bean(name = BEAN_NAME)
    public ThreadPoolTaskExecutor resumeQueryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("resume-query-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
