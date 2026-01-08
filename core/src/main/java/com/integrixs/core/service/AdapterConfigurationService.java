package com.integrixs.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Service for adapter configuration validation and processing.
 * Follows OOP principles:
 * - Single Responsibility: Only handles configuration operations
 * - Dependency Injection: Injectable Spring service
 * - Type Safety: Proper validation and Optional usage
 * - Immutability: Defensive copying and validation
 */
@Service
public class AdapterConfigurationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterConfigurationService.class);
    
    /**
     * Configuration validation result holder with immutable state
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
        
        public boolean isValid() { return valid; }
        public Optional<String> getErrorMessage() { return Optional.ofNullable(errorMessage); }
    }
    
    /**
     * Configuration field descriptor for type-safe configuration handling
     */
    public static class ConfigField<T> {
        private final String name;
        private final Class<T> type;
        private final boolean required;
        private final T defaultValue;
        private final Predicate<T> validator;
        
        private ConfigField(String name, Class<T> type, boolean required, T defaultValue, Predicate<T> validator) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.validator = validator != null ? validator : (value -> true);
        }
        
        public static <T> ConfigField<T> required(String name, Class<T> type) {
            return new ConfigField<>(name, type, true, null, null);
        }
        
        public static <T> ConfigField<T> required(String name, Class<T> type, Predicate<T> validator) {
            return new ConfigField<>(name, type, true, null, validator);
        }
        
        public static <T> ConfigField<T> optional(String name, Class<T> type, T defaultValue) {
            return new ConfigField<>(name, type, false, defaultValue, null);
        }
        
        public static <T> ConfigField<T> optional(String name, Class<T> type, T defaultValue, Predicate<T> validator) {
            return new ConfigField<>(name, type, false, defaultValue, validator);
        }
        
        public String getName() { return name; }
        public Class<T> getType() { return type; }
        public boolean isRequired() { return required; }
        public Optional<T> getDefaultValue() { return Optional.ofNullable(defaultValue); }
        public Predicate<T> getValidator() { return validator; }
    }
    
    /**
     * Validate a configuration field according to its descriptor
     */
    public <T> ValidationResult validateField(Map<String, Object> config, ConfigField<T> field, String adapterType) {
        if (config == null || config.isEmpty()) {
            return ValidationResult.failure(String.format("%s configuration cannot be null or empty", adapterType));
        }
        
        Object value = config.get(field.getName());
        
        // Check if required field is missing
        if (field.isRequired() && value == null) {
            return ValidationResult.failure(String.format(
                "%s configuration field '%s' is required", adapterType, field.getName()));
        }
        
        // If field is optional and missing, that's valid
        if (!field.isRequired() && value == null) {
            return ValidationResult.success();
        }
        
        // Validate type if value is present
        if (value != null && !field.getType().isInstance(value)) {
            return ValidationResult.failure(String.format(
                "%s configuration field '%s' must be of type %s, but was %s", 
                adapterType, field.getName(), field.getType().getSimpleName(), 
                value.getClass().getSimpleName()));
        }
        
        // Special validation for String fields
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            return ValidationResult.failure(String.format(
                "%s configuration field '%s' cannot be empty", adapterType, field.getName()));
        }
        
        // Run custom validation
        if (value != null) {
            @SuppressWarnings("unchecked")
            T typedValue = (T) value;
            if (!field.getValidator().test(typedValue)) {
                return ValidationResult.failure(String.format(
                    "%s configuration field '%s' failed validation", adapterType, field.getName()));
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Extract and validate a string configuration value
     */
    public String getStringConfig(Map<String, Object> config, String fieldName, boolean required, String defaultValue) {
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
     * Extract and validate an integer configuration value
     */
    public int getIntegerConfig(Map<String, Object> config, String fieldName, boolean required, int defaultValue) {
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
     * Extract and validate a boolean configuration value
     */
    public boolean getBooleanConfig(Map<String, Object> config, String fieldName, boolean defaultValue) {
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
     * Validate SFTP-specific configuration with type-safe approach
     */
    public ValidationResult validateSftpConfiguration(Map<String, Object> config, String adapterType) {
        // Define required fields with validators
        ConfigField<String> hostField = ConfigField.required("host", String.class);
        ConfigField<String> usernameField = ConfigField.required("username", String.class);
        
        // Validate required fields
        ValidationResult hostValidation = validateField(config, hostField, adapterType);
        if (!hostValidation.isValid()) {
            return hostValidation;
        }
        
        ValidationResult usernameValidation = validateField(config, usernameField, adapterType);
        if (!usernameValidation.isValid()) {
            return usernameValidation;
        }
        
        // Validate port with range
        ConfigField<Integer> portField = ConfigField.optional("port", Integer.class, 22, 
            port -> port >= 1 && port <= 65535);
        ValidationResult portValidation = validateField(config, portField, adapterType);
        if (!portValidation.isValid()) {
            return ValidationResult.failure(adapterType + " port must be between 1 and 65535");
        }
        
        // Validate authentication configuration
        return validateSftpAuthentication(config, adapterType);
    }
    
    /**
     * Validate File-specific configuration
     */
    public ValidationResult validateFileConfiguration(Map<String, Object> config, String adapterType, String direction) {
        if ("SENDER".equalsIgnoreCase(direction)) {
            ConfigField<String> sourceDirField = ConfigField.required("sourceDirectory", String.class);
            ValidationResult sourceDirValidation = validateField(config, sourceDirField, adapterType + " sender");
            if (!sourceDirValidation.isValid()) {
                return sourceDirValidation;
            }
            
            ConfigField<String> filePatternField = ConfigField.required("filePattern", String.class);
            ValidationResult filePatternValidation = validateField(config, filePatternField, adapterType + " sender");
            if (!filePatternValidation.isValid()) {
                return filePatternValidation;
            }
        } else if ("RECEIVER".equalsIgnoreCase(direction)) {
            ConfigField<String> targetDirField = ConfigField.required("targetDirectory", String.class);
            ValidationResult targetDirValidation = validateField(config, targetDirField, adapterType + " receiver");
            if (!targetDirValidation.isValid()) {
                return targetDirValidation;
            }
        }
        
        // Validate polling interval
        ConfigField<Integer> pollingField = ConfigField.optional("pollingInterval", Integer.class, 5000,
            interval -> interval >= 1000);
        ValidationResult pollingValidation = validateField(config, pollingField, adapterType);
        if (!pollingValidation.isValid()) {
            return ValidationResult.failure(adapterType + " pollingInterval must be at least 1000 milliseconds");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Log configuration summary without sensitive information
     */
    public void logConfigurationSummary(Map<String, Object> config, String adapterType, String direction) {
        logger.debug("=== {} {} Configuration Summary ===", adapterType, direction);
        
        config.entrySet().stream()
            .forEach(entry -> {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                // Hide sensitive values
                if (isSensitiveField(key)) {
                    logger.debug("  {}: ***HIDDEN***", key);
                } else {
                    logger.debug("  {}: {}", key, value);
                }
            });
        
        logger.debug("=== End Configuration Summary ===");
    }
    
    // Private helper methods
    
    private ValidationResult validateSftpAuthentication(Map<String, Object> config, String adapterType) {
        String authType = getStringConfig(config, "authType", false, "PASSWORD");
        
        switch (authType.toUpperCase()) {
            case "PASSWORD":
                ConfigField<String> passwordField = ConfigField.required("password", String.class);
                return validateField(config, passwordField, adapterType);
                
            case "SSH_KEY":
            case "PRIVATE_KEY":
                ConfigField<String> privateKeyField = ConfigField.required("privateKeyPath", String.class);
                return validateField(config, privateKeyField, adapterType);
                
            case "DUAL":
                return validateDualAuthentication(config, adapterType);
                
            default:
                return ValidationResult.failure(adapterType + " unsupported authentication type: " + authType);
        }
    }
    
    private ValidationResult validateDualAuthentication(Map<String, Object> config, String adapterType) {
        String password = getStringConfig(config, "password", false, null);
        String privateKeyPath = getStringConfig(config, "privateKeyPath", false, null);
        
        if ((password == null || password.trim().isEmpty()) && 
            (privateKeyPath == null || privateKeyPath.trim().isEmpty())) {
            return ValidationResult.failure(
                adapterType + " dual authentication requires either password or private key");
        }
        
        return ValidationResult.success();
    }
    
    private boolean isSensitiveField(String fieldName) {
        String lowerKey = fieldName.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("secret") ||
               lowerKey.contains("key");
    }
}