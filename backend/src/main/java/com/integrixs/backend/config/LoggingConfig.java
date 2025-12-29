package com.integrixs.backend.config;

import ch.qos.logback.classic.LoggerContext;
import com.integrixs.backend.logging.DatabaseLogAppender;
import com.integrixs.core.service.DynamicLoggingService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Configuration for setting up database logging and dynamic logging configuration
 * Configures the custom database appender and initializes dynamic logging from database
 */
@Configuration
public class LoggingConfig {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * Create the database log appender bean
     */
    @Bean
    public DatabaseLogAppender databaseLogAppender() {
        DatabaseLogAppender appender = new DatabaseLogAppender();
        appender.setApplicationContext(applicationContext);
        return appender;
    }
    
    /**
     * Configure the database appender and dynamic logging after the application context is fully initialized
     * This ensures all dependencies are available before we start logging to database
     */
    @EventListener
    public void configureDatabaseLogging(ContextRefreshedEvent event) {
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // Get the database appender bean
            DatabaseLogAppender databaseAppender = applicationContext.getBean(DatabaseLogAppender.class);
            
            // Set the context and start the appender
            databaseAppender.setContext(loggerContext);
            databaseAppender.setName("DATABASE");
            databaseAppender.start();
            
            // Add the appender to the root logger
            ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(databaseAppender);
            
            // Initialize dynamic logging configuration from database
            try {
                DynamicLoggingService dynamicLoggingService = applicationContext.getBean(DynamicLoggingService.class);
                dynamicLoggingService.initializeLoggingConfiguration();
            } catch (Exception e) {
                // Log error but don't fail startup - dynamic logging is optional
                org.slf4j.Logger logger = LoggerFactory.getLogger(LoggingConfig.class);
                logger.warn("Failed to initialize dynamic logging configuration - using static configuration", e);
            }
            
            // Log that database logging is now active
            org.slf4j.Logger logger = LoggerFactory.getLogger(LoggingConfig.class);
            logger.info("Database logging appender configured and started with dynamic configuration");
            
        } catch (Exception e) {
            // Log error but don't fail application startup
            org.slf4j.Logger logger = LoggerFactory.getLogger(LoggingConfig.class);
            logger.error("Failed to configure database logging appender", e);
        }
    }
}