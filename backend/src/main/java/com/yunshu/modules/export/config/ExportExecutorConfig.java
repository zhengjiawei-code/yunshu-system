package com.yunshu.modules.export.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 导出专用线程池：固定 2 线程，单机并发上限与「同一用户最多 2 个进行中任务」对应。
 * 队列容量有限，拒绝时由调用方标记任务失败，避免无上限堆积。
 */
@Configuration
public class ExportExecutorConfig {

    @Bean(name = "exportExecutor")
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
