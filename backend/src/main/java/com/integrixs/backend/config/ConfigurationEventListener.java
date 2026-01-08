package com.integrixs.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Configuration change event listener
 * Handles configuration change events with proper OOP design and audit logging
 */
@Component
public class ConfigurationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationEventListener.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("CONFIG_AUDIT");

    /**
     * Handle configuration change events
     */
    @EventListener
    public void handleConfigurationChange(ConfigurationChangeEvent event) {
        try {
            logConfigurationChange(event);
            
            // Handle specific configuration types
            if (isSecurityConfiguration(event.getKey())) {
                handleSecurityConfigurationChange(event);
            } else if (isPerformanceConfiguration(event.getKey())) {
                handlePerformanceConfigurationChange(event);
            } else if (isApplicationConfiguration(event.getKey())) {
                handleApplicationConfigurationChange(event);
            }
            
        } catch (Exception e) {
            logger.error("Error handling configuration change event for key: {}", event.getKey(), e);
        }
    }

    /**
     * Handle configuration validation events
     */
    @EventListener
    public void handleConfigurationValidation(ConfigurationValidationEvent event) {
        try {
            if (!event.isValid()) {
                auditLogger.warn("Configuration validation failed for key: {} - Errors: {}", 
                    event.getKey(), event.getValidationErrors());
            } else {
                logger.debug("Configuration validation passed for key: {}", event.getKey());
            }
            
        } catch (Exception e) {
            logger.error("Error handling configuration validation event for key: {}", event.getKey(), e);
        }
    }

    /**
     * Handle configuration initialization events
     */
    @EventListener
    public void handleConfigurationInitialization(ConfigurationInitializationEvent event) {
        try {
            auditLogger.info("Configuration initialized: {} configurations loaded successfully", 
                event.getInitializedCount());
            
            if (event.hasErrors()) {
                auditLogger.warn("Configuration initialization completed with {} errors: {}", 
                    event.getErrorCount(), event.getErrors());
            }
            
        } catch (Exception e) {
            logger.error("Error handling configuration initialization event", e);
        }
    }

    // Private helper methods

    private void logConfigurationChange(ConfigurationChangeEvent event) {
        auditLogger.info("Configuration changed - Key: {} - Operation: {} - User: {} - Environment: {} - Previous: {} - New: {}", 
            event.getKey(), 
            event.getOperation(),
            event.getUserId(),
            event.getEnvironment(),
            maskSensitiveValue(event.getKey(), event.getOldValue()),
            maskSensitiveValue(event.getKey(), event.getNewValue())
        );
    }

    private boolean isSecurityConfiguration(String key) {
        return key.startsWith("security.") || 
               key.contains("password") || 
               key.contains("secret") || 
               key.contains("key") ||
               key.contains("token");
    }

    private boolean isPerformanceConfiguration(String key) {
        return key.contains("pool") || 
               key.contains("timeout") || 
               key.contains("cache") ||
               key.contains("thread") ||
               key.contains("connection");
    }

    private boolean isApplicationConfiguration(String key) {
        return key.startsWith("app.") || 
               key.startsWith("h2h.") ||
               key.startsWith("system.");
    }

    private void handleSecurityConfigurationChange(ConfigurationChangeEvent event) {
        logger.warn("Security configuration change detected for key: {} - Additional security review may be required", 
            event.getKey());
        
        // In a real implementation, you might trigger additional security checks,
        // notify security administrators, or invalidate sessions if necessary
    }

    private void handlePerformanceConfigurationChange(ConfigurationChangeEvent event) {
        logger.info("Performance configuration change detected for key: {} - System performance may be affected", 
            event.getKey());
        
        // In a real implementation, you might trigger performance metric recalculation,
        // adjust connection pools, or restart certain services
    }

    private void handleApplicationConfigurationChange(ConfigurationChangeEvent event) {
        logger.debug("Application configuration change detected for key: {}", event.getKey());
        
        // Handle application-specific configuration changes
    }

    private String maskSensitiveValue(String key, String value) {
        if (value == null) return null;
        
        if (isSensitiveKey(key)) {
            return "*".repeat(Math.min(value.length(), 8));
        }
        
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("secret") || 
               lowerKey.contains("key") ||
               lowerKey.contains("token") ||
               lowerKey.contains("credential");
    }

    /**
     * Configuration change event
     */
    public static class ConfigurationChangeEvent {
        private final String key;
        private final String oldValue;
        private final String newValue;
        private final ConfigurationOperation operation;
        private final String userId;
        private final String environment;
        private final LocalDateTime timestamp;
        private final Map<String, Object> metadata;

        public ConfigurationChangeEvent(String key, String oldValue, String newValue, 
                                      ConfigurationOperation operation, String userId, 
                                      String environment) {
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.operation = operation;
            this.userId = userId;
            this.environment = environment;
            this.timestamp = LocalDateTime.now();
            this.metadata = Map.of();
        }

        // Getters
        public String getKey() { return key; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public ConfigurationOperation getOperation() { return operation; }
        public String getUserId() { return userId; }
        public String getEnvironment() { return environment; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * Configuration validation event
     */
    public static class ConfigurationValidationEvent {
        private final String key;
        private final String value;
        private final boolean valid;
        private final java.util.List<String> validationErrors;
        private final LocalDateTime timestamp;

        public ConfigurationValidationEvent(String key, String value, boolean valid, 
                                          java.util.List<String> validationErrors) {
            this.key = key;
            this.value = value;
            this.valid = valid;
            this.validationErrors = validationErrors != null ? validationErrors : java.util.List.of();
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getKey() { return key; }
        public String getValue() { return value; }
        public boolean isValid() { return valid; }
        public java.util.List<String> getValidationErrors() { return validationErrors; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * Configuration initialization event
     */
    public static class ConfigurationInitializationEvent {
        private final int initializedCount;
        private final int errorCount;
        private final java.util.List<String> errors;
        private final LocalDateTime timestamp;

        public ConfigurationInitializationEvent(int initializedCount, int errorCount, 
                                               java.util.List<String> errors) {
            this.initializedCount = initializedCount;
            this.errorCount = errorCount;
            this.errors = errors != null ? errors : java.util.List.of();
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public int getInitializedCount() { return initializedCount; }
        public int getErrorCount() { return errorCount; }
        public java.util.List<String> getErrors() { return errors; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public boolean hasErrors() { return errorCount > 0; }
    }

    /**
     * Configuration operation enumeration
     */
    public enum ConfigurationOperation {
        CREATE, UPDATE, DELETE, RELOAD
    }
}