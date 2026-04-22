package com.actionow.collab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步配置
 * 为通知分发提供有界线程池，防止无限制创建线程导致 OOM
 *
 * @author Actionow
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 通知分发线程池
     * <p>
     * 核心线程: 4（常驻，应对日常通知）
     * 最大线程: 16（高峰时扩展）
     * 队列容量: 500（缓冲突发通知）
     * 拒绝策略: CallerRunsPolicy（队列满时由调用方线程降级执行，不丢通知）
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
