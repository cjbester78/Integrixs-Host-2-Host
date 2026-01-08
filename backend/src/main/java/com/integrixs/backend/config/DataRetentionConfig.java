package com.integrixs.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.logging.Logger;

/**
 * Configuration class for Data Retention functionality
 * 
 * Enables Spring's task scheduling for automated data retention execution.
 * This configuration is only active when integrix.data-retention.enabled=true
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = "integrix.data-retention.enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionConfig {

    private static final Logger log = Logger.getLogger(DataRetentionConfig.class.getName());

    public DataRetentionConfig() {
        log.info("Data Retention Configuration initialized - scheduling enabled");
    }
}