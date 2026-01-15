package com.integrixs.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Utility class for adapter configuration validation and helper methods.
 * Provides common configuration operations used across different adapters.
 */
public class AdapterConfigUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterConfigUtil.class);
    
    /**
     * Validate that a required string configuration field is present and not empty.
     * 
     * @param config The configuration map
     * @param fieldName The field name to validate
     * @param adapterType The adapter type for error messages
     * @throws IllegalArgumentException if field is missing or empty
     */
    public static void validateRequiredString(Map<String, Object> config, String fieldName, String adapterType) {
        Object value = config.get(fieldName);
        
        if (value == null) {
            throw new IllegalArgumentException(String.format(
                "%s configuration field '%s' is required", adapterType, fieldName));
        }
        
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(String.format(
                "%s configuration field '%s' must be a string", adapterType, fieldName));
        }
        
        String stringValue = (String) value;
        if (stringValue.trim().isEmpty()) {
            throw new IllegalArgumentException(String.format(
                "%s configuration field '%s' cannot be empty", adapterType, fieldName));
        }
    }
    
    /**
     * Validate that a required integer configuration field is present and valid.
     * 
     * @param config The configuration map
     * @param fieldName The field name to validate
     * @param adapterType The adapter type for error messages
     * @param minValue Minimum allowed value (inclusive)
     * @param maxValue Maximum allowed value (inclusive)
     * @throws IllegalArgumentException if field is missing or invalid
     */
    public static void validateRequiredInteger(Map<String, Object> config, String fieldName, 
                                             String adapterType, int minValue, int maxValue) {
        Object value = config.get(fieldName);
        
        if (value == null) {
            throw new IllegalArgumentException(String.format(
                "%s configuration field '%s' is required", adapterType, fieldName));
        }
        
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(String.format(
                "%s configuration field '%s' must be a number", adapterType, fieldName));
        }
        
        int intValue = ((Number) value).intValue();
        if (intValue < minValue || intValue > maxValue) {
            throw new IllegalArgumentException(String.format(
                "%s configuration field '%s' must be between %d and %d, got %d", 
                adapterType, fieldName, minValue, maxValue, intValue));
        }
    }
    
    /**
     * Get string configuration value with validation.
     * 
     * @param config The configuration map
     * @param fieldName The field name
     * @param required Whether the field is required
     * @param defaultValue Default value if field is not present (used only if not required)
     * @return The configuration value
     * @throws IllegalArgumentException if required field is missing
     */
    public static String getStringConfig(Map<String, Object> config, String fieldName, 
                                       boolean required, String defaultValue) {
        Object value = config.get(fieldName);
        
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Required configuration field missing: " + fieldName);
            }
            return defaultValue;
        }
        
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Configuration field must be a string: " + fieldName);
        }
        
        String stringValue = (String) value;
        if (required && stringValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Required configuration field cannot be empty: " + fieldName);
        }
        
        return stringValue;
    }
    
    /**
     * Get integer configuration value with validation.
     * 
     * @param config The configuration map
     * @param fieldName The field name
     * @param required Whether the field is required
     * @param defaultValue Default value if field is not present
     * @return The configuration value
     * @throws IllegalArgumentException if required field is missing or invalid
     */
    public static int getIntegerConfig(Map<String, Object> config, String fieldName, 
                                     boolean required, int defaultValue) {
        Object value = config.get(fieldName);
        
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Required configuration field missing: " + fieldName);
            }
            return defaultValue;
        }
        
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Configuration field must be a number: " + fieldName);
        }
        
        return ((Number) value).intValue();
    }
    
    /**
     * Get boolean configuration value with validation.
     * 
     * @param config The configuration map
     * @param fieldName The field name
     * @param defaultValue Default value if field is not present
     * @return The configuration value
     */
    public static boolean getBooleanConfig(Map<String, Object> config, String fieldName, boolean defaultValue) {
        Object value = config.get(fieldName);

        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }

        logger.warn("Configuration field '{}' is not a boolean, using default: {}", fieldName, defaultValue);
        return defaultValue;
    }

    /**
     * Get integer configuration value (simplified version without required flag).
     *
     * @param config The configuration map
     * @param fieldName The field name
     * @param defaultValue Default value if field is not present
     * @return The configuration value
     */
    public static int getIntConfig(Map<String, Object> config, String fieldName, int defaultValue) {
        Object value = config.get(fieldName);

        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Configuration field '{}' is not a valid integer, using default: {}", fieldName, defaultValue);
                return defaultValue;
            }
        }

        logger.warn("Configuration field '{}' is not a number, using default: {}", fieldName, defaultValue);
        return defaultValue;
    }

    /**
     * Get long configuration value with validation.
     *
     * @param config The configuration map
     * @param fieldName The field name
     * @param defaultValue Default value if field is not present
     * @return The configuration value
     */
    public static long getLongConfig(Map<String, Object> config, String fieldName, long defaultValue) {
        Object value = config.get(fieldName);

        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Configuration field '{}' is not a valid long, using default: {}", fieldName, defaultValue);
                return defaultValue;
            }
        }

        logger.warn("Configuration field '{}' is not a number, using default: {}", fieldName, defaultValue);
        return defaultValue;
    }

    /**
     * Validate SFTP-specific configuration fields.
     * 
     * @param config The configuration map
     * @param adapterType The adapter type for error messages
     */
    public static void validateSftpConfig(Map<String, Object> config, String adapterType) {
        validateRequiredString(config, "host", adapterType);
        validateRequiredString(config, "username", adapterType);
        
        // Validate port if present
        Object portObj = config.get("port");
        if (portObj != null) {
            validateRequiredInteger(config, "port", adapterType, 1, 65535);
        }
        
        // Validate authentication configuration
        String authType = getStringConfig(config, "authType", false, "PASSWORD");
        
        switch (authType.toUpperCase()) {
            case "PASSWORD":
                validateRequiredString(config, "password", adapterType);
                break;
                
            case "SSH_KEY":
            case "PRIVATE_KEY":
                validateRequiredString(config, "privateKeyPath", adapterType);
                break;
                
            case "DUAL":
                // For dual auth, at least one of password or private key must be present
                String password = getStringConfig(config, "password", false, null);
                String privateKeyPath = getStringConfig(config, "privateKeyPath", false, null);
                
                if ((password == null || password.trim().isEmpty()) && 
                    (privateKeyPath == null || privateKeyPath.trim().isEmpty())) {
                    throw new IllegalArgumentException(
                        adapterType + " dual authentication requires either password or private key");
                }
                break;
                
            default:
                throw new IllegalArgumentException(
                    adapterType + " unsupported authentication type: " + authType);
        }
        
        // Validate timeout values if present
        int sessionTimeout = getIntegerConfig(config, "sessionTimeout", false, 60000);
        int channelTimeout = getIntegerConfig(config, "channelTimeout", false, 60000);
        
        if (sessionTimeout < 1000 || sessionTimeout > 300000) { // 1 second to 5 minutes
            throw new IllegalArgumentException(
                adapterType + " sessionTimeout must be between 1000 and 300000 milliseconds");
        }
        
        if (channelTimeout < 1000 || channelTimeout > 300000) { // 1 second to 5 minutes
            throw new IllegalArgumentException(
                adapterType + " channelTimeout must be between 1000 and 300000 milliseconds");
        }
    }
    
    /**
     * Validate File-specific configuration fields.
     * 
     * @param config The configuration map
     * @param adapterType The adapter type for error messages
     * @param direction The adapter direction (SENDER or RECEIVER)
     */
    public static void validateFileConfig(Map<String, Object> config, String adapterType, String direction) {
        if ("SENDER".equalsIgnoreCase(direction)) {
            validateRequiredString(config, "sourceDirectory", adapterType + " sender");
            validateRequiredString(config, "filePattern", adapterType + " sender");
        } else if ("RECEIVER".equalsIgnoreCase(direction)) {
            validateRequiredString(config, "targetDirectory", adapterType + " receiver");
        }
        
        // Validate polling interval if present
        int pollingInterval = getIntegerConfig(config, "pollingInterval", false, 5000);
        if (pollingInterval < 1000) { // Minimum 1 second
            throw new IllegalArgumentException(
                adapterType + " pollingInterval must be at least 1000 milliseconds");
        }
    }
    
    /**
     * Log configuration summary for debugging (without sensitive information).
     * 
     * @param config The configuration map
     * @param adapterType The adapter type
     * @param direction The adapter direction
     */
    public static void logConfigSummary(Map<String, Object> config, String adapterType, String direction) {
        logger.debug("=== {} {} Configuration Summary ===", adapterType, direction);
        
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Hide sensitive values
            if (key.toLowerCase().contains("password") || 
                key.toLowerCase().contains("secret") ||
                key.toLowerCase().contains("key")) {
                logger.debug("  {}: ***HIDDEN***", key);
            } else {
                logger.debug("  {}: {}", key, value);
            }
        }
        
        logger.debug("=== End Configuration Summary ===");
    }
}