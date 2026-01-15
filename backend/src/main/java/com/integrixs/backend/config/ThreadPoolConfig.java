package com.integrixs.backend.config;

import com.integrixs.core.repository.SystemConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool configuration for H2H application.
 * Provides dedicated thread pools for different use cases.
 * All values are read from system_configuration table for runtime configurability.
 */
@Configuration
public class ThreadPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolConfig.class);

    private final SystemConfigurationRepository configRepository;

    @Autowired
    public ThreadPoolConfig(SystemConfigurationRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Get integer configuration value with fallback to default
     */
    private int getConfigInt(String key, int defaultValue) {
        try {
            return configRepository.getIntegerValue(key, defaultValue);
        } catch (Exception e) {
            log.warn("Failed to read config '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Primary task executor for general async operations
     */
    @Bean
    @Primary
    public TaskExecutor primaryTaskExecutor() {
        int coreSize = getConfigInt("thread.pool.primary.core.size", 10);
        int maxSize = getConfigInt("thread.pool.primary.max.size", 25);
        int queueCapacity = getConfigInt("thread.pool.primary.queue.capacity", 100);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("H2H-Primary-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("Configured primary task executor from DB config: core={}, max={}, queue={}",
                coreSize, maxSize, queueCapacity);
        return executor;
    }

    /**
     * Dedicated executor for adapter operations.
     * Allows multiple adapters to execute concurrently.
     */
    @Bean(name = "adapterTaskExecutor")
    public Executor adapterTaskExecutor() {
        int coreSize = getConfigInt("thread.pool.adapter.core.size", 20);
        int maxSize = getConfigInt("thread.pool.adapter.max.size", 50);
        int queueCapacity = getConfigInt("thread.pool.adapter.queue.capacity", 200);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("H2H-Adapter-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("Configured adapter task executor from DB config: core={}, max={}, queue={}",
                coreSize, maxSize, queueCapacity);
        return executor;
    }

    /**
     * Dedicated executor for flow execution.
     * Allows multiple flows to execute concurrently.
     */
    @Bean(name = "flowExecutionExecutor")
    public Executor flowExecutionExecutor() {
        int coreSize = getConfigInt("thread.pool.flow.core.size", 15);
        int maxSize = getConfigInt("thread.pool.flow.max.size", 30);
        int queueCapacity = getConfigInt("thread.pool.flow.queue.capacity", 150);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("H2H-Flow-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // Longer timeout for flow completion
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("Configured flow execution executor from DB config: core={}, max={}, queue={}",
                coreSize, maxSize, queueCapacity);
        return executor;
    }

    /**
     * Dedicated executor for monitoring and health checks.
     * Non-critical tasks that can be discarded if pool is full.
     */
    @Bean(name = "monitoringExecutor")
    public Executor monitoringExecutor() {
        int coreSize = getConfigInt("thread.pool.monitoring.core.size", 5);
        int maxSize = getConfigInt("thread.pool.monitoring.max.size", 10);
        int queueCapacity = getConfigInt("thread.pool.monitoring.queue.capacity", 50);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("H2H-Monitor-");
        executor.setWaitForTasksToCompleteOnShutdown(false); // Don't wait for monitoring tasks
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();

        log.info("Configured monitoring executor from DB config: core={}, max={}, queue={}",
                coreSize, maxSize, queueCapacity);
        return executor;
    }
}
