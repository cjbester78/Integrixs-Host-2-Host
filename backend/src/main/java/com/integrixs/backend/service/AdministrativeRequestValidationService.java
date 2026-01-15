package com.integrixs.backend.service;

import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.backend.model.User;
import com.integrixs.shared.model.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Centralized request validation service for administrative controller operations.
 * Implements validation strategy pattern with immutable validation results.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@Service
public class AdministrativeRequestValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdministrativeRequestValidationService.class);
    
    static final Set<String> VALID_LOG_LEVELS = Set.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
    static final Set<String> VALID_EXPORT_FORMATS = Set.of("CSV", "JSON", "XML");
    static final Set<String> VALID_USER_ROLES = Set.of("ADMINISTRATOR", "VIEWER", "INTEGRATOR");
    static final int MAX_USERNAME_LENGTH = 50;
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_LIMIT = 10000;
    
    private final Map<String, AdministrativeRequestValidator> validators = new HashMap<>();
    
    public AdministrativeRequestValidationService() {
        // Register built-in validators
        registerValidator("system_health", new SystemHealthValidator());
        registerValidator("system_metrics", new SystemMetricsValidator());
        registerValidator("logs", new LogsValidator());
        registerValidator("logs_export", new LogsExportValidator());
        registerValidator("statistics", new StatisticsValidator());
        registerValidator("cleanup", new CleanupValidator());
        registerValidator("user_list", new UserListValidator());
        registerValidator("user_create", new UserCreateValidator());
        registerValidator("user_update", new UserUpdateValidator());
        registerValidator("user_delete", new UserDeleteValidator());
        registerValidator("configuration_list", new ConfigurationListValidator());
        registerValidator("configuration_update", new ConfigurationUpdateValidator());
        registerValidator("data_retention", new DataRetentionValidator());
        registerValidator("environment", new EnvironmentValidator());
    }
    
    /**
     * Validate system health request parameters.
     */
    public ExecutionValidationResult validateSystemHealthRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("system_health");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate system metrics request parameters.
     */
    public ExecutionValidationResult validateSystemMetricsRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("system_metrics");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate logs request parameters.
     */
    public ExecutionValidationResult validateLogsRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("logs");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate logs export request parameters.
     */
    public ExecutionValidationResult validateLogsExportRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("logs_export");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate statistics request parameters.
     */
    public ExecutionValidationResult validateStatisticsRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("statistics");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate cleanup request parameters.
     */
    public ExecutionValidationResult validateCleanupRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("cleanup");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate user list request parameters.
     */
    public ExecutionValidationResult validateUserListRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("user_list");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate user creation request.
     */
    public ExecutionValidationResult validateUserCreateRequest(User userData) {
        Map<String, Object> parameters = Map.of("userData", userData);
        AdministrativeRequestValidator validator = getValidator("user_create");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate user update request.
     */
    public ExecutionValidationResult validateUserUpdateRequest(String userId, User userData) {
        Map<String, Object> parameters = Map.of(
            "userId", userId,
            "userData", userData
        );
        AdministrativeRequestValidator validator = getValidator("user_update");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate user deletion request.
     */
    public ExecutionValidationResult validateUserDeleteRequest(String userId) {
        Map<String, Object> parameters = Map.of("userId", userId);
        AdministrativeRequestValidator validator = getValidator("user_delete");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate configuration list request parameters.
     */
    public ExecutionValidationResult validateConfigurationListRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("configuration_list");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate configuration update request.
     */
    public ExecutionValidationResult validateConfigurationUpdateRequest(String configKey, String configValue) {
        Map<String, Object> parameters = Map.of(
            "configKey", configKey,
            "configValue", configValue
        );
        AdministrativeRequestValidator validator = getValidator("configuration_update");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate data retention request parameters.
     */
    public ExecutionValidationResult validateDataRetentionRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("data_retention");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Validate environment request parameters.
     */
    public ExecutionValidationResult validateEnvironmentRequest(Map<String, Object> parameters) {
        AdministrativeRequestValidator validator = getValidator("environment");
        return validator.validateRequest(parameters);
    }
    
    /**
     * Register request validator.
     */
    public void registerValidator(String type, AdministrativeRequestValidator validator) {
        if (type != null && validator != null) {
            validators.put(type.toLowerCase(), validator);
            logger.debug("Registered administrative request validator: {}", type);
        }
    }
    
    /**
     * Get request validator by type.
     */
    private AdministrativeRequestValidator getValidator(String validatorType) {
        AdministrativeRequestValidator validator = validators.get(validatorType.toLowerCase());
        
        if (validator == null) {
            logger.warn("Unknown administrative request validator: {}, using system_health validator", validatorType);
            validator = validators.get("system_health");
        }
        
        return validator;
    }
    
    /**
     * Get available validator types.
     */
    public Set<String> getAvailableValidatorTypes() {
        return new HashSet<>(validators.keySet());
    }
}

/**
 * Interface for administrative request validation strategies.
 */
interface AdministrativeRequestValidator {
    ExecutionValidationResult validateRequest(Map<String, Object> parameters);
    String getValidatorName();
    String getValidatorDescription();
}

/**
 * System health request validator.
 */
class SystemHealthValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate parameters map is not null
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        
        // Validate health check detail level parameter
        if (parameters.containsKey("detailLevel")) {
            Object detailLevelObj = parameters.get("detailLevel");
            if (detailLevelObj != null) {
                String detailLevel = String.valueOf(detailLevelObj).trim().toUpperCase();
                if (detailLevel.isEmpty()) {
                    warnings.add("detailLevel parameter is empty, using default");
                } else {
                    Set<String> validDetailLevels = Set.of("BASIC", "DETAILED", "FULL", "DIAGNOSTIC");
                    if (!validDetailLevels.contains(detailLevel)) {
                        errors.add("Invalid detailLevel '" + detailLevel + "'. Must be one of: " + validDetailLevels);
                    }
                }
            }
        }
        
        // Validate component filter parameter
        if (parameters.containsKey("components")) {
            Object componentsObj = parameters.get("components");
            if (componentsObj != null) {
                String components = String.valueOf(componentsObj).trim();
                if (components.isEmpty()) {
                    warnings.add("components parameter is empty, checking all components");
                } else {
                    Set<String> validComponents = Set.of("database", "filesystem", "memory", "cpu", 
                                                       "network", "adapters", "services", "security", "all");
                    
                    // Split by comma and validate each component
                    String[] componentArray = components.toLowerCase().split(",");
                    Set<String> uniqueComponents = new HashSet<>();
                    
                    for (String component : componentArray) {
                        String trimmedComponent = component.trim();
                        if (!trimmedComponent.isEmpty()) {
                            if (!validComponents.contains(trimmedComponent)) {
                                errors.add("Invalid component: '" + trimmedComponent + "'. Valid components: " + validComponents);
                            } else {
                                uniqueComponents.add(trimmedComponent);
                            }
                        }
                    }
                    
                    // Check for duplicate components
                    if (uniqueComponents.size() != componentArray.length) {
                        warnings.add("Duplicate components detected, using unique values only");
                    }
                    
                    // Check for conflicting 'all' with specific components
                    if (uniqueComponents.contains("all") && uniqueComponents.size() > 1) {
                        warnings.add("Using 'all' with specific components - 'all' takes precedence");
                    }
                }
            }
        }
        
        // Validate timeout parameter for health checks
        if (parameters.containsKey("timeout")) {
            Object timeoutObj = parameters.get("timeout");
            if (timeoutObj != null) {
                try {
                    String timeoutStr = String.valueOf(timeoutObj).trim();
                    if (timeoutStr.isEmpty()) {
                        warnings.add("timeout parameter is empty, using default");
                    } else {
                        int timeout = Integer.parseInt(timeoutStr);
                        if (timeout <= 0) {
                            errors.add("Health check timeout must be positive, found: " + timeout);
                        } else if (timeout < 1000) {
                            warnings.add("Very short timeout (" + timeout + "ms) may cause false failures");
                            if (timeout < 100) {
                                errors.add("Health check timeout too short (minimum 100ms): " + timeout);
                            }
                        } else if (timeout > 300000) { // 5 minutes
                            errors.add("Health check timeout too long (maximum 300000ms/5 minutes): " + timeout);
                        } else if (timeout > 60000) { // 1 minute
                            warnings.add("Long timeout (" + timeout + "ms) may delay health check response");
                        }
                    }
                } catch (NumberFormatException e) {
                    errors.add("Invalid timeout value. Must be a number in milliseconds, found: " + timeoutObj);
                }
            }
        }
        
        // Validate include metrics flag
        if (parameters.containsKey("includeMetrics")) {
            Object includeMetricsObj = parameters.get("includeMetrics");
            if (includeMetricsObj != null) {
                String includeMetrics = String.valueOf(includeMetricsObj).trim().toLowerCase();
                if (includeMetrics.isEmpty()) {
                    warnings.add("includeMetrics parameter is empty, using default (false)");
                } else {
                    Set<String> validBooleans = Set.of("true", "false", "yes", "no", "1", "0", "on", "off");
                    if (!validBooleans.contains(includeMetrics)) {
                        warnings.add("includeMetrics should be a boolean value (true/false/yes/no/1/0). Found: " + includeMetricsObj);
                    }
                }
            }
        }
        
        // Validate format parameter for health check output
        if (parameters.containsKey("format")) {
            Object formatObj = parameters.get("format");
            if (formatObj != null) {
                String format = String.valueOf(formatObj).trim().toUpperCase();
                if (format.isEmpty()) {
                    warnings.add("format parameter is empty, using default (JSON)");
                } else {
                    Set<String> validFormats = Set.of("JSON", "XML", "PLAIN", "SUMMARY", "TEXT");
                    if (!validFormats.contains(format)) {
                        errors.add("Invalid format '" + format + "'. Must be one of: " + validFormats);
                    }
                }
            }
        }
        
        // Validate additional health check specific parameters
        if (parameters.containsKey("includeSystemInfo")) {
            Object includeSystemInfoObj = parameters.get("includeSystemInfo");
            if (includeSystemInfoObj != null) {
                String includeSystemInfo = String.valueOf(includeSystemInfoObj).trim().toLowerCase();
                if (!includeSystemInfo.isEmpty()) {
                    Set<String> validBooleans = Set.of("true", "false", "yes", "no", "1", "0");
                    if (!validBooleans.contains(includeSystemInfo)) {
                        warnings.add("includeSystemInfo should be a boolean value. Found: " + includeSystemInfoObj);
                    }
                }
            }
        }
        
        if (parameters.containsKey("maxResults")) {
            Object maxResultsObj = parameters.get("maxResults");
            if (maxResultsObj != null) {
                try {
                    int maxResults = Integer.parseInt(String.valueOf(maxResultsObj).trim());
                    if (maxResults <= 0) {
                        errors.add("maxResults must be positive, found: " + maxResults);
                    } else if (maxResults > 1000) {
                        warnings.add("Large maxResults value (" + maxResults + ") may impact performance");
                    }
                } catch (NumberFormatException e) {
                    errors.add("Invalid maxResults value. Must be a positive integer, found: " + maxResultsObj);
                }
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "System Health";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates system health check request parameters";
    }
}

/**
 * System metrics request validator.
 */
class SystemMetricsValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate metric type filter if provided
        Object metricTypeObj = parameters.get("metricType");
        if (metricTypeObj != null && !metricTypeObj.toString().trim().isEmpty()) {
            String metricType = metricTypeObj.toString();
            if (!Set.of("memory", "disk", "cpu", "network", "database").contains(metricType.toLowerCase())) {
                errors.add("Invalid metric type: " + metricType);
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "System Metrics";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates system metrics request parameters";
    }
}

/**
 * Logs request validator.
 */
class LogsValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate limit parameter
        Object limitObj = parameters.get("limit");
        if (limitObj != null) {
            try {
                int limit = Integer.parseInt(limitObj.toString());
                if (limit <= 0) {
                    errors.add("Limit must be greater than 0");
                } else if (limit > AdministrativeRequestValidationService.MAX_LIMIT) {
                    warnings.add("Limit is very high (" + limit + "), consider using pagination");
                }
            } catch (NumberFormatException e) {
                errors.add("Limit must be a valid integer");
            }
        }
        
        // Validate level parameter
        Object levelObj = parameters.get("level");
        if (levelObj != null && !levelObj.toString().trim().isEmpty()) {
            String level = levelObj.toString().toUpperCase();
            if (!AdministrativeRequestValidationService.VALID_LOG_LEVELS.contains(level)) {
                errors.add("Invalid log level: " + levelObj.toString());
            }
        }
        
        // Validate search parameter length
        Object searchObj = parameters.get("search");
        if (searchObj != null && searchObj.toString().length() > 1000) {
            errors.add("Search term too long (max 1000 characters)");
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Logs";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates logs request parameters including limit, level, and search";
    }
}

/**
 * Logs export request validator.
 */
class LogsExportValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Reuse logs validation
        LogsValidator logsValidator = new LogsValidator();
        ExecutionValidationResult logsResult = logsValidator.validateRequest(parameters);
        errors.addAll(logsResult.getErrors());
        warnings.addAll(logsResult.getWarnings());
        
        // Additional validation for export format
        Object formatObj = parameters.get("format");
        if (formatObj != null && !formatObj.toString().trim().isEmpty()) {
            String format = formatObj.toString().toUpperCase();
            if (!AdministrativeRequestValidationService.VALID_EXPORT_FORMATS.contains(format)) {
                errors.add("Invalid export format: " + formatObj.toString());
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Logs Export";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates logs export request parameters including format validation";
    }
}

/**
 * Statistics request validator.
 */
class StatisticsValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate hours parameter
        Object hoursObj = parameters.get("hours");
        if (hoursObj != null) {
            try {
                int hours = Integer.parseInt(hoursObj.toString());
                if (hours <= 0) {
                    errors.add("Hours must be greater than 0");
                } else if (hours > 8760) { // More than a year
                    warnings.add("Hours parameter is very high (" + hours + "), this may impact performance");
                }
            } catch (NumberFormatException e) {
                errors.add("Hours must be a valid integer");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Statistics";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates statistics request parameters";
    }
}

/**
 * Cleanup request validator.
 */
class CleanupValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate retention days parameter
        Object retentionDaysObj = parameters.get("retentionDays");
        if (retentionDaysObj != null) {
            try {
                int retentionDays = Integer.parseInt(retentionDaysObj.toString());
                if (retentionDays < 1) {
                    errors.add("Retention days must be at least 1");
                } else if (retentionDays < 7) {
                    warnings.add("Retention period is less than 7 days, this may delete recent important data");
                }
            } catch (NumberFormatException e) {
                errors.add("Retention days must be a valid integer");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Cleanup";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates cleanup operation parameters";
    }
}

/**
 * User list request validator.
 */
class UserListValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate role filter if provided
        Object roleObj = parameters.get("role");
        if (roleObj != null && !roleObj.toString().trim().isEmpty()) {
            String role = roleObj.toString().toUpperCase();
            if (!AdministrativeRequestValidationService.VALID_USER_ROLES.contains(role)) {
                errors.add("Invalid user role: " + roleObj.toString());
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "User List";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates user list request parameters";
    }
}

/**
 * User creation request validator.
 */
class UserCreateValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        Object userDataObj = parameters.get("userData");
        if (userDataObj == null) {
            errors.add("User data is required");
        } else if (userDataObj instanceof User) {
            User user = (User) userDataObj;
            
            // Validate username
            if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                errors.add("Username is required");
            } else {
                if (user.getUsername().length() > AdministrativeRequestValidationService.MAX_USERNAME_LENGTH) {
                    errors.add("Username cannot exceed " + AdministrativeRequestValidationService.MAX_USERNAME_LENGTH + " characters");
                }
                if (!user.getUsername().matches("^[a-zA-Z0-9._-]+$")) {
                    errors.add("Username can only contain letters, numbers, dots, underscores, and hyphens");
                }
            }
            
            // Validate email
            if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                errors.add("Email is required");
            } else if (!user.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                errors.add("Invalid email format");
            }
            
            // Validate role
            if (user.getRole() == null) {
                errors.add("User role is required");
            } else if (!AdministrativeRequestValidationService.VALID_USER_ROLES.contains(user.getRole().name())) {
                errors.add("Invalid user role: " + user.getRole().name());
            }
            
        } else {
            errors.add("Invalid user data format");
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "User Create";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates user creation request including username, email, and role validation";
    }
}

/**
 * User update request validator.
 */
class UserUpdateValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate user ID first
        Object userIdObj = parameters.get("userId");
        if (userIdObj == null || userIdObj.toString().trim().isEmpty()) {
            errors.add("User ID is required");
        } else {
            try {
                UUID.fromString(userIdObj.toString());
            } catch (IllegalArgumentException e) {
                errors.add("User ID must be a valid UUID");
            }
        }
        
        // Then validate user data using create validator
        UserCreateValidator createValidator = new UserCreateValidator();
        ExecutionValidationResult dataValidation = createValidator.validateRequest(parameters);
        errors.addAll(dataValidation.getErrors());
        warnings.addAll(dataValidation.getWarnings());
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "User Update";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates user update request including ID and user data validation";
    }
}

/**
 * User deletion request validator.
 */
class UserDeleteValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        Object userIdObj = parameters.get("userId");
        if (userIdObj == null || userIdObj.toString().trim().isEmpty()) {
            errors.add("User ID is required");
        } else {
            try {
                UUID.fromString(userIdObj.toString());
            } catch (IllegalArgumentException e) {
                errors.add("User ID must be a valid UUID");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "User Delete";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates user deletion request parameters";
    }
}

/**
 * Configuration list request validator.
 */
class ConfigurationListValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate category parameter if provided
        Object categoryObj = parameters.get("category");
        if (categoryObj != null && !categoryObj.toString().trim().isEmpty()) {
            try {
                SystemConfiguration.ConfigCategory.valueOf(categoryObj.toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("Invalid configuration category: " + categoryObj.toString());
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Configuration List";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates configuration list request parameters";
    }
}

/**
 * Configuration update request validator.
 */
class ConfigurationUpdateValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        Object configKeyObj = parameters.get("configKey");
        if (configKeyObj == null || configKeyObj.toString().trim().isEmpty()) {
            errors.add("Configuration key is required");
        }
        
        Object configValueObj = parameters.get("configValue");
        if (configValueObj == null) {
            errors.add("Configuration value is required");
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Configuration Update";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates configuration update request parameters";
    }
}

/**
 * Data retention request validator.
 */
class DataRetentionValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate retention policy parameters
        Object retentionDaysObj = parameters.get("retentionDays");
        if (retentionDaysObj != null) {
            try {
                int retentionDays = Integer.parseInt(retentionDaysObj.toString());
                if (retentionDays < 30) {
                    warnings.add("Retention period less than 30 days may not meet compliance requirements");
                }
            } catch (NumberFormatException e) {
                errors.add("Retention days must be a valid integer");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Data Retention";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates data retention request parameters";
    }
}

/**
 * Environment request validator.
 */
class EnvironmentValidator implements AdministrativeRequestValidator {
    
    @Override
    public ExecutionValidationResult validateRequest(Map<String, Object> parameters) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate environment name if provided
        Object envNameObj = parameters.get("environmentName");
        if (envNameObj != null && !envNameObj.toString().trim().isEmpty()) {
            String envName = envNameObj.toString();
            if (!envName.matches("^[a-zA-Z0-9_-]+$")) {
                errors.add("Environment name can only contain letters, numbers, underscores, and hyphens");
            }
        }
        
        return new ExecutionValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    @Override
    public String getValidatorName() {
        return "Environment";
    }
    
    @Override
    public String getValidatorDescription() {
        return "Validates environment request parameters";
    }
}