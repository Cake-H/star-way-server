package com.smartapply.ida.config.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 专为 [简历解析] 提供的线程池，与 Tomcat 工作线程解耦。
 */
@Configuration
public class ResumeParseExecutorConfig {

    public static final String BEAN_NAME = "resumeParseExecutor";

    @Bean(name = BEAN_NAME)
    public ThreadPoolTaskExecutor resumeParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("resume-parse-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
