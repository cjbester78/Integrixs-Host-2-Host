package com.integrixs.core.service;

import com.integrixs.shared.model.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service for publishing configuration change events.
 * Implements proper event handling with immutable event objects.
 * Follows OOP principles with clear separation of concerns and type safety.
 */
@Service
public class ConfigurationEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationEventPublisher.class);
    
    private final ApplicationEventPublisher eventPublisher;
    
    public ConfigurationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Abstract base class for configuration events
     */
    public abstract static class ConfigurationEvent {
        private final String configKey;
        private final UUID userId;
        private final LocalDateTime timestamp;
        private final String correlationId;
        
        protected ConfigurationEvent(String configKey, UUID userId, String correlationId) {
            this.configKey = configKey;
            this.userId = userId;
            this.timestamp = LocalDateTime.now();
            this.correlationId = correlationId;
        }
        
        public String getConfigKey() { return configKey; }
        public UUID getUserId() { return userId; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getCorrelationId() { return correlationId; }
    }
    
    /**
     * Configuration value changed event
     */
    public static class ConfigurationChangedEvent extends ConfigurationEvent {
        private final String oldValue;
        private final String newValue;
        private final SystemConfiguration.ConfigCategory category;
        private final boolean sensitive;
        
        public ConfigurationChangedEvent(String configKey, String oldValue, String newValue,
                                       SystemConfiguration.ConfigCategory category, boolean sensitive,
                                       UUID userId, String correlationId) {
            super(configKey, userId, correlationId);
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.category = category;
            this.sensitive = sensitive;
        }
        
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public SystemConfiguration.ConfigCategory getCategory() { return category; }
        public boolean isSensitive() { return sensitive; }
        
        public String getDisplayOldValue() { 
            return sensitive ? "***" : oldValue; 
        }
        
        public String getDisplayNewValue() { 
            return sensitive ? "***" : newValue; 
        }
    }
    
    /**
     * Configuration created event
     */
    public static class ConfigurationCreatedEvent extends ConfigurationEvent {
        private final SystemConfiguration configuration;
        
        public ConfigurationCreatedEvent(SystemConfiguration configuration, UUID userId, String correlationId) {
            super(configuration.getConfigKey(), userId, correlationId);
            this.configuration = configuration;
        }
        
        public SystemConfiguration getConfiguration() { return configuration; }
    }
    
    /**
     * Configuration deleted event
     */
    public static class ConfigurationDeletedEvent extends ConfigurationEvent {
        private final SystemConfiguration.ConfigCategory category;
        private final String deletedValue;
        
        public ConfigurationDeletedEvent(String configKey, SystemConfiguration.ConfigCategory category,
                                       String deletedValue, UUID userId, String correlationId) {
            super(configKey, userId, correlationId);
            this.category = category;
            this.deletedValue = deletedValue;
        }
        
        public SystemConfiguration.ConfigCategory getCategory() { return category; }
        public String getDeletedValue() { return deletedValue; }
    }
    
    /**
     * Configuration reset to default event
     */
    public static class ConfigurationResetEvent extends ConfigurationEvent {
        private final String oldValue;
        private final String defaultValue;
        private final SystemConfiguration.ConfigCategory category;
        
        public ConfigurationResetEvent(String configKey, String oldValue, String defaultValue,
                                     SystemConfiguration.ConfigCategory category, UUID userId, 
                                     String correlationId) {
            super(configKey, userId, correlationId);
            this.oldValue = oldValue;
            this.defaultValue = defaultValue;
            this.category = category;
        }
        
        public String getOldValue() { return oldValue; }
        public String getDefaultValue() { return defaultValue; }
        public SystemConfiguration.ConfigCategory getCategory() { return category; }
    }
    
    /**
     * Batch configuration changed event
     */
    public static class ConfigurationBatchChangedEvent extends ConfigurationEvent {
        private final Map<String, String> changes;
        private final int totalChanges;
        
        public ConfigurationBatchChangedEvent(Map<String, String> changes, UUID userId, String correlationId) {
            super("batch_update", userId, correlationId);
            this.changes = Map.copyOf(changes);
            this.totalChanges = changes.size();
        }
        
        public Map<String, String> getChanges() { return changes; }
        public int getTotalChanges() { return totalChanges; }
    }
    
    /**
     * Configuration validation failed event
     */
    public static class ConfigurationValidationFailedEvent extends ConfigurationEvent {
        private final String attemptedValue;
        private final String validationError;
        private final SystemConfiguration.ConfigCategory category;
        
        public ConfigurationValidationFailedEvent(String configKey, String attemptedValue,
                                                 String validationError, SystemConfiguration.ConfigCategory category,
                                                 UUID userId, String correlationId) {
            super(configKey, userId, correlationId);
            this.attemptedValue = attemptedValue;
            this.validationError = validationError;
            this.category = category;
        }
        
        public String getAttemptedValue() { return attemptedValue; }
        public String getValidationError() { return validationError; }
        public SystemConfiguration.ConfigCategory getCategory() { return category; }
    }
    
    /**
     * Publish configuration changed event
     */
    public void publishConfigurationChanged(String configKey, String oldValue, String newValue,
                                          SystemConfiguration config, UUID userId, String correlationId) {
        try {
            boolean sensitive = isSensitiveConfiguration(configKey);
            ConfigurationChangedEvent event = new ConfigurationChangedEvent(
                configKey, oldValue, newValue, config.getCategory(), sensitive, userId, correlationId
            );
            
            eventPublisher.publishEvent(event);
            
            if (sensitive) {
                logger.info("Configuration '{}' changed by user {} (values hidden for security)", 
                          configKey, userId);
            } else {
                logger.info("Configuration '{}' changed from '{}' to '{}' by user {}", 
                          configKey, oldValue, newValue, userId);
            }
            
        } catch (Exception e) {
            logger.error("Error publishing configuration changed event for {}: {}", configKey, e.getMessage());
        }
    }
    
    /**
     * Publish configuration created event
     */
    public void publishConfigurationCreated(SystemConfiguration configuration, UUID userId, String correlationId) {
        try {
            ConfigurationCreatedEvent event = new ConfigurationCreatedEvent(configuration, userId, correlationId);
            eventPublisher.publishEvent(event);
            
            logger.info("Configuration '{}' created by user {}", configuration.getConfigKey(), userId);
            
        } catch (Exception e) {
            logger.error("Error publishing configuration created event for {}: {}", 
                        configuration.getConfigKey(), e.getMessage());
        }
    }
    
    /**
     * Publish configuration deleted event
     */
    public void publishConfigurationDeleted(String configKey, SystemConfiguration config, UUID userId, 
                                          String correlationId) {
        try {
            ConfigurationDeletedEvent event = new ConfigurationDeletedEvent(
                configKey, config.getCategory(), config.getConfigValue(), userId, correlationId
            );
            
            eventPublisher.publishEvent(event);
            
            logger.info("Configuration '{}' deleted by user {}", configKey, userId);
            
        } catch (Exception e) {
            logger.error("Error publishing configuration deleted event for {}: {}", configKey, e.getMessage());
        }
    }
    
    /**
     * Publish configuration reset event
     */
    public void publishConfigurationReset(String configKey, String oldValue, String defaultValue,
                                        SystemConfiguration config, UUID userId, String correlationId) {
        try {
            ConfigurationResetEvent event = new ConfigurationResetEvent(
                configKey, oldValue, defaultValue, config.getCategory(), userId, correlationId
            );
            
            eventPublisher.publishEvent(event);
            
            logger.info("Configuration '{}' reset to default value by user {}", configKey, userId);
            
        } catch (Exception e) {
            logger.error("Error publishing configuration reset event for {}: {}", configKey, e.getMessage());
        }
    }
    
    /**
     * Publish batch configuration changes event
     */
    public void publishBatchConfigurationChanged(Map<String, String> changes, UUID userId, String correlationId) {
        try {
            ConfigurationBatchChangedEvent event = new ConfigurationBatchChangedEvent(changes, userId, correlationId);
            eventPublisher.publishEvent(event);
            
            logger.info("Batch configuration update with {} changes by user {}", changes.size(), userId);
            
        } catch (Exception e) {
            logger.error("Error publishing batch configuration changed event: {}", e.getMessage());
        }
    }
    
    /**
     * Publish configuration validation failed event
     */
    public void publishConfigurationValidationFailed(String configKey, String attemptedValue, 
                                                    String validationError, SystemConfiguration config,
                                                    UUID userId, String correlationId) {
        try {
            ConfigurationValidationFailedEvent event = new ConfigurationValidationFailedEvent(
                configKey, attemptedValue, validationError, config.getCategory(), userId, correlationId
            );
            
            eventPublisher.publishEvent(event);
            
            logger.warn("Configuration validation failed for '{}': {} (attempted by user {})", 
                       configKey, validationError, userId);
            
        } catch (Exception e) {
            logger.error("Error publishing configuration validation failed event for {}: {}", 
                        configKey, e.getMessage());
        }
    }
    
    /**
     * Check if configuration contains sensitive information
     */
    private boolean isSensitiveConfiguration(String configKey) {
        if (configKey == null) return false;
        
        String lowerKey = configKey.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("secret") || 
               lowerKey.contains("key") || 
               lowerKey.contains("token") || 
               lowerKey.contains("credential") ||
               lowerKey.contains("private") ||
               lowerKey.contains("auth");
    }
}