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
        List<String> errors = new ArrayList<>();
        
        // Check for security patterns
        if (containsSuspiciousContent(sanitizedValue)) {
            warnings.add("Value contains potentially unsafe content");
        }
        
        // Implement pattern validation for known configuration keys
        String key = config.getConfigKey();
        String validationPattern = getValidationPattern(key);
        if (validationPattern != null && !validationPattern.isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(validationPattern);
                if (!pattern.matcher(sanitizedValue).matches()) {
                    errors.add("Value does not match required pattern: " + getPatternDescription(key));
                }
            } catch (Exception e) {
                logger.warn("Invalid validation pattern for key {}: {}", key, validationPattern);
                warnings.add("Pattern validation failed - invalid regex");
            }
        }
        
        if (!errors.isEmpty()) {
            return ConfigurationValidationResult.failure(errors);
        } else if (warnings.isEmpty()) {
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
    
    /**
     * Get validation pattern for a configuration key
     */
    private String getValidationPattern(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        
        // Define patterns for common configuration keys
        switch (key.toLowerCase().trim()) {
            case "email":
            case "admin.email":
            case "notification.email":
            case "mail.username":
                return "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
            case "host":
            case "hostname":
            case "database.host":
            case "smtp.host":
            case "sftp.host":
                return "^[a-zA-Z0-9.-]+$";
            case "port":
            case "database.port":
            case "smtp.port":
            case "sftp.port":
                return "^([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$";
            case "url":
            case "base.url":
            case "api.url":
            case "service.url":
                return "^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$";
            case "username":
            case "database.username":
            case "sftp.username":
            case "user":
                return "^[a-zA-Z0-9_.-]{1,64}$";
            case "password":
            case "database.password":
            case "sftp.password":
                // Password should be at least 8 characters, contain letters and numbers
                return "^(?=.*[a-zA-Z])(?=.*[0-9]).{8,}$";
            case "timeout":
            case "connection.timeout":
            case "read.timeout":
            case "execution.timeout":
                return "^[1-9][0-9]{0,8}$"; // 1 to 999999999 (positive numbers only)
            case "max.connections":
            case "max.pool.size":
            case "max.file.size":
                return "^[1-9][0-9]*$"; // Positive integers only
            case "log.level":
            case "logging.level":
                return "^(TRACE|DEBUG|INFO|WARN|ERROR|FATAL|OFF)$";
            case "enabled":
            case "active":
            case "ssl.enabled":
            case "debug.enabled":
            case "compression.enabled":
                return "^(true|false)$";
            case "file.pattern":
            case "filename.pattern":
                // Basic filename pattern validation (no path separators or dangerous chars)
                return "^[a-zA-Z0-9._*?-]+$";
            case "directory":
            case "base.directory":
            case "temp.directory":
            case "archive.directory":
                // Directory path pattern (Unix/Windows compatible)
                return "^([a-zA-Z]:|/)?([/\\\\]?[a-zA-Z0-9._-]+)*[/\\\\]?$";
            case "encryption.key":
            case "api.key":
                // API key format: alphanumeric, minimum length
                return "^[a-zA-Z0-9]{16,}$";
            case "ip.address":
            case "server.ip":
                // IPv4 address pattern
                return "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
            case "cron.expression":
                // Basic cron expression validation (simplified)
                return "^(\\*|[0-5]?[0-9]|\\*/[0-9]+)\\s+(\\*|[01]?[0-9]|2[0-3]|\\*/[0-9]+)\\s+(\\*|[0-2]?[0-9]|3[01]|\\*/[0-9]+)\\s+(\\*|[0-9]|1[0-2]|\\*/[0-9]+)\\s+(\\*|[0-6]|\\*/[0-9]+)$";
            default:
                return null; // No specific pattern
        }
    }
    
    /**
     * Get human-readable description of validation pattern
     */
    private String getPatternDescription(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "required format";
        }
        
        switch (key.toLowerCase().trim()) {
            case "email":
            case "admin.email":
            case "notification.email":
            case "mail.username":
                return "valid email address format (user@domain.com)";
            case "host":
            case "hostname":
            case "database.host":
            case "smtp.host":
            case "sftp.host":
                return "valid hostname (alphanumeric, dots, hyphens)";
            case "port":
            case "database.port":
            case "smtp.port":
            case "sftp.port":
                return "valid port number (1-65535)";
            case "url":
            case "base.url":
            case "api.url":
            case "service.url":
                return "valid HTTP/HTTPS URL format";
            case "username":
            case "database.username":
            case "sftp.username":
            case "user":
                return "valid username (alphanumeric, underscore, dot, hyphen, max 64 chars)";
            case "password":
            case "database.password":
            case "sftp.password":
                return "strong password (min 8 chars, letters and numbers required)";
            case "timeout":
            case "connection.timeout":
            case "read.timeout":
            case "execution.timeout":
                return "positive integer in milliseconds (1-999999999)";
            case "max.connections":
            case "max.pool.size":
            case "max.file.size":
                return "positive integer greater than 0";
            case "log.level":
            case "logging.level":
                return "valid log level (TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF)";
            case "enabled":
            case "active":
            case "ssl.enabled":
            case "debug.enabled":
            case "compression.enabled":
                return "boolean value (true or false)";
            case "file.pattern":
            case "filename.pattern":
                return "valid filename pattern (letters, numbers, dots, wildcards, no path separators)";
            case "directory":
            case "base.directory":
            case "temp.directory":
            case "archive.directory":
                return "valid directory path (Unix/Windows compatible)";
            case "encryption.key":
            case "api.key":
                return "alphanumeric key (minimum 16 characters)";
            case "ip.address":
            case "server.ip":
                return "valid IPv4 address (xxx.xxx.xxx.xxx)";
            case "cron.expression":
                return "valid cron expression (minute hour day month dayofweek)";
            default:
                return "required format";
        }
    }
}