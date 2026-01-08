package com.integrixs.core.service;

import com.integrixs.shared.model.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.integrixs.core.repository.SystemConfigurationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service for validating configuration values and changes.
 * Implements comprehensive validation chains with immutable result objects.
 * Follows OOP principles with proper encapsulation and type safety.
 */
@Service
public class ConfigurationValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationValidationService.class);
    
    private final SystemConfigurationRepository configRepository;
    
    public ConfigurationValidationService(SystemConfigurationRepository configRepository) {
        this.configRepository = configRepository;
    }
    
    /**
     * Immutable configuration validation result
     */
    public static class ConfigurationValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final String sanitizedValue;
        private final SystemConfiguration.ConfigType detectedType;
        
        private ConfigurationValidationResult(boolean valid, List<String> errors, List<String> warnings, 
                                           String sanitizedValue, SystemConfiguration.ConfigType detectedType) {
            this.valid = valid;
            this.errors = new ArrayList<>(errors != null ? errors : new ArrayList<>());
            this.warnings = new ArrayList<>(warnings != null ? warnings : new ArrayList<>());
            this.sanitizedValue = sanitizedValue;
            this.detectedType = detectedType;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public String getSanitizedValue() { return sanitizedValue; }
        public SystemConfiguration.ConfigType getDetectedType() { return detectedType; }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        
        public static ConfigurationValidationResult success(String sanitizedValue, 
                                                          SystemConfiguration.ConfigType detectedType) {
            return new ConfigurationValidationResult(true, null, null, sanitizedValue, detectedType);
        }
        
        public static ConfigurationValidationResult successWithWarnings(String sanitizedValue, 
                                                                       SystemConfiguration.ConfigType detectedType,
                                                                       List<String> warnings) {
            return new ConfigurationValidationResult(true, null, warnings, sanitizedValue, detectedType);
        }
        
        public static ConfigurationValidationResult failure(List<String> errors) {
            return new ConfigurationValidationResult(false, errors, null, null, null);
        }
        
        public static ConfigurationValidationResult failure(String error) {
            List<String> errors = new ArrayList<>();
            errors.add(error);
            return new ConfigurationValidationResult(false, errors, null, null, null);
        }
    }
    
    /**
     * Validate configuration value with comprehensive checks
     */
    public ConfigurationValidationResult validateConfigurationValue(SystemConfiguration config, String value) {
        if (config == null) {
            return ConfigurationValidationResult.failure("Configuration object cannot be null");
        }
        
        if (value == null) {
            return ConfigurationValidationResult.failure("Configuration value cannot be null");
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic validation
        int maxStringLength = getMaxStringLength();
        if (value.length() > maxStringLength) {
            errors.add("Value too long (max " + maxStringLength + " characters)");
        }
        
        // Type-specific validation
        SystemConfiguration.ConfigType configType = config.getConfigType();
        String sanitizedValue = value.trim();
        
        switch (configType) {
            case STRING:
                return validateStringValue(config, sanitizedValue, warnings);
            case INTEGER:
                return validateIntegerValue(config, sanitizedValue, errors, warnings);
            case BOOLEAN:
                return validateBooleanValue(config, sanitizedValue, errors, warnings);
            case JSON:
                return validateJsonValue(config, sanitizedValue, errors, warnings);
            default:
                warnings.add("Unknown config type, treating as string");
                return validateStringValue(config, sanitizedValue, warnings);
        }
    }
    
    /**
     * Validate string configuration value
     */
    private ConfigurationValidationResult validateStringValue(SystemConfiguration config, String value, 
                                                            List<String> warnings) {
        String sanitizedValue = sanitizeStringValue(value);
        
        // Check for security patterns
        if (containsSuspiciousContent(sanitizedValue)) {
            warnings.add("Value contains potentially unsafe content");
        }
        
        // Note: Pattern validation not implemented in current SystemConfiguration model
        
        if (warnings.isEmpty()) {
            return ConfigurationValidationResult.success(sanitizedValue, SystemConfiguration.ConfigType.STRING);
        } else {
            return ConfigurationValidationResult.successWithWarnings(sanitizedValue, 
                SystemConfiguration.ConfigType.STRING, warnings);
        }
    }
    
    /**
     * Validate integer configuration value
     */
    private ConfigurationValidationResult validateIntegerValue(SystemConfiguration config, String value, 
                                                             List<String> errors, List<String> warnings) {
        try {
            long longValue = Long.parseLong(value);
            
            long minIntegerValue = getMinIntegerValue();
            long maxIntegerValue = getMaxIntegerValue();
            
            if (longValue < minIntegerValue || longValue > maxIntegerValue) {
                errors.add("Integer value out of range (" + minIntegerValue + " to " + maxIntegerValue + ")");
                return ConfigurationValidationResult.failure(errors);
            }
            
            // Special validation for timeout values
            if (config.getConfigKey().toLowerCase().contains("timeout")) {
                long minTimeoutSeconds = getMinTimeoutSeconds();
                long maxTimeoutSeconds = getMaxTimeoutSeconds();
                
                if (longValue < minTimeoutSeconds || longValue > maxTimeoutSeconds) {
                    errors.add("Timeout value out of range (" + minTimeoutSeconds + " to " + maxTimeoutSeconds + " seconds)");
                    return ConfigurationValidationResult.failure(errors);
                }
            }
            
            if (warnings.isEmpty()) {
                return ConfigurationValidationResult.success(String.valueOf(longValue), SystemConfiguration.ConfigType.INTEGER);
            } else {
                return ConfigurationValidationResult.successWithWarnings(String.valueOf(longValue), 
                    SystemConfiguration.ConfigType.INTEGER, warnings);
            }
            
        } catch (NumberFormatException e) {
            errors.add("Invalid integer value: " + value);
            return ConfigurationValidationResult.failure(errors);
        }
    }
    
    /**
     * Validate boolean configuration value
     */
    private ConfigurationValidationResult validateBooleanValue(SystemConfiguration config, String value, 
                                                             List<String> errors, List<String> warnings) {
        String lowerValue = value.toLowerCase();
        if ("true".equals(lowerValue) || "false".equals(lowerValue)) {
            return ConfigurationValidationResult.success(lowerValue, SystemConfiguration.ConfigType.BOOLEAN);
        }
        
        // Try to parse common boolean representations
        if ("yes".equals(lowerValue) || "y".equals(lowerValue) || "1".equals(lowerValue)) {
            warnings.add("Converting '" + value + "' to 'true'");
            return ConfigurationValidationResult.successWithWarnings("true", SystemConfiguration.ConfigType.BOOLEAN, warnings);
        }
        
        if ("no".equals(lowerValue) || "n".equals(lowerValue) || "0".equals(lowerValue)) {
            warnings.add("Converting '" + value + "' to 'false'");
            return ConfigurationValidationResult.successWithWarnings("false", SystemConfiguration.ConfigType.BOOLEAN, warnings);
        }
        
        errors.add("Invalid boolean value: " + value + " (expected: true/false)");
        return ConfigurationValidationResult.failure(errors);
    }
    
    
    /**
     * Validate JSON configuration value
     */
    private ConfigurationValidationResult validateJsonValue(SystemConfiguration config, String value, 
                                                          List<String> errors, List<String> warnings) {
        try {
            // Basic JSON validation - check for proper brackets/braces
            String trimmed = value.trim();
            if ((!trimmed.startsWith("{") || !trimmed.endsWith("}")) && 
                (!trimmed.startsWith("[") || !trimmed.endsWith("]"))) {
                errors.add("Invalid JSON format");
                return ConfigurationValidationResult.failure(errors);
            }
            
            // Additional validation could be added here using Jackson ObjectMapper
            warnings.add("JSON validation is basic - verify structure manually");
            return ConfigurationValidationResult.successWithWarnings(trimmed, SystemConfiguration.ConfigType.JSON, warnings);
            
        } catch (Exception e) {
            errors.add("Invalid JSON value: " + e.getMessage());
            return ConfigurationValidationResult.failure(errors);
        }
    }
    
    
    /**
     * Validate configuration key format
     */
    public ConfigurationValidationResult validateConfigurationKey(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return ConfigurationValidationResult.failure("Configuration key cannot be empty");
        }
        
        String sanitizedKey = configKey.trim().toLowerCase();
        
        // Check key format
        if (!sanitizedKey.matches("[a-z0-9._-]+")) {
            return ConfigurationValidationResult.failure("Key can only contain letters, numbers, dots, hyphens, and underscores");
        }
        
        if (sanitizedKey.length() > 100) {
            return ConfigurationValidationResult.failure("Configuration key too long (max 100 characters)");
        }
        
        return ConfigurationValidationResult.success(sanitizedKey, SystemConfiguration.ConfigType.STRING);
    }
    
    /**
     * Batch validate multiple configuration changes
     */
    public Map<String, ConfigurationValidationResult> validateBatch(Map<String, String> configChanges, 
                                                                   Map<String, SystemConfiguration> existingConfigs) {
        Map<String, ConfigurationValidationResult> results = new java.util.HashMap<>();
        
        for (Map.Entry<String, String> entry : configChanges.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            SystemConfiguration config = existingConfigs.get(key);
            if (config == null) {
                results.put(key, ConfigurationValidationResult.failure("Configuration key not found: " + key));
            } else {
                results.put(key, validateConfigurationValue(config, value));
            }
        }
        
        return results;
    }
    
    /**
     * Sanitize string value for security
     */
    private String sanitizeStringValue(String value) {
        if (value == null) return "";
        
        // Remove potential script injection patterns
        return value.replaceAll("(?i)<script[^>]*>.*?</script>", "")
                   .replaceAll("(?i)javascript:", "")
                   .replaceAll("(?i)on\\w+\\s*=", "")
                   .trim();
    }
    
    /**
     * Check for suspicious content in configuration values
     */
    private boolean containsSuspiciousContent(String value) {
        if (value == null) return false;
        
        String lower = value.toLowerCase();
        return lower.contains("<script") || 
               lower.contains("javascript:") || 
               lower.contains("eval(") ||
               lower.contains("exec(") ||
               lower.contains("system(") ||
               lower.contains("${");
    }
    
    /**
     * Helper methods to retrieve validation settings from database
     */
    private int getMaxStringLength() {
        try {
            return configRepository.getIntegerValue("config.validation.max-string-length", 1000);
        } catch (Exception e) {
            logger.warn("Error reading max-string-length configuration, using default: {}", e.getMessage());
            return 1000;
        }
    }
    
    private long getMaxIntegerValue() {
        try {
            return configRepository.getIntegerValue("config.validation.max-integer-value", 999999999).longValue();
        } catch (Exception e) {
            logger.warn("Error reading max-integer-value configuration, using default: {}", e.getMessage());
            return 999999999L;
        }
    }
    
    private long getMinIntegerValue() {
        try {
            return configRepository.getIntegerValue("config.validation.min-integer-value", -999999999).longValue();
        } catch (Exception e) {
            logger.warn("Error reading min-integer-value configuration, using default: {}", e.getMessage());
            return -999999999L;
        }
    }
    
    private long getMaxTimeoutSeconds() {
        try {
            return configRepository.getIntegerValue("config.validation.max-timeout-seconds", 3600).longValue();
        } catch (Exception e) {
            logger.warn("Error reading max-timeout-seconds configuration, using default: {}", e.getMessage());
            return 3600L;
        }
    }
    
    private long getMinTimeoutSeconds() {
        try {
            return configRepository.getIntegerValue("config.validation.min-timeout-seconds", 1).longValue();
        } catch (Exception e) {
            logger.warn("Error reading min-timeout-seconds configuration, using default: {}", e.getMessage());
            return 1L;
        }
    }
}