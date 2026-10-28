package com.smartapply.ida.config.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 专为【面试复盘】提供的线程池，与 Tomcat 工作线程解耦。
 */
@Configuration
public class VoiceSummarizeExecutorConfig {
    public static final String BEAN_NAME = "voiceSummarizeExecutor";

    @Bean(name = BEAN_NAME)
    public ThreadPoolTaskExecutor voiceSummarizeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("voice-summarize-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
