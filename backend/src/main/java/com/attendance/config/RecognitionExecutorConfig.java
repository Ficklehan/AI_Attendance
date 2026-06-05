package com.attendance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class RecognitionExecutorConfig {

    private final RecognitionProperties recognitionProperties;

    public RecognitionExecutorConfig(RecognitionProperties recognitionProperties) {
        this.recognitionProperties = recognitionProperties;
    }

    @Bean(name = "recognitionExecutor")
    public Executor recognitionExecutor() {
        RecognitionProperties.Executor cfg = recognitionProperties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("recognition-");
        executor.setCorePoolSize(Math.max(1, cfg.getCorePoolSize()));
        executor.setMaxPoolSize(Math.max(cfg.getCorePoolSize(), cfg.getMaxPoolSize()));
        executor.setQueueCapacity(Math.max(10, cfg.getQueueCapacity()));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
