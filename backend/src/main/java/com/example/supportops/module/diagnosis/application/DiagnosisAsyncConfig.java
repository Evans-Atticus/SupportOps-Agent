package com.example.supportops.module.diagnosis.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 独立诊断线程池，避免耗时模型调用占用 Tomcat 请求线程。 */
@Configuration
public class DiagnosisAsyncConfig {
    @Bean("diagnosisTaskExecutor")
    TaskExecutor diagnosisTaskExecutor(@Value("${supportops.async.core-pool-size:4}") int corePoolSize,
                                       @Value("${supportops.async.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(Math.max(corePoolSize, corePoolSize * 2));
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("diagnosis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
