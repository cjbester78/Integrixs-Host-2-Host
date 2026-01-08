package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.backend.dto.request.AdminConfigRequest;
import com.integrixs.backend.dto.response.AdminConfigResponse;
import com.integrixs.backend.service.AdministrativeRequestValidationService;
import com.integrixs.backend.service.ResponseStandardizationService;
import com.integrixs.backend.service.SystemConfigurationService;
import com.integrixs.shared.model.SystemConfiguration;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for system configuration management.
 * Allows administrators to view and modify application settings.
 * Refactored following OOP principles with proper validation, DTOs, and error handling.
 */
@RestController
@RequestMapping("/api/configuration")
public class SystemConfigurationController {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationController.class);

    private final SystemConfigurationService configService;
    private final AdministrativeRequestValidationService validationService;
    private final ResponseStandardizationService responseService;

    @Autowired
    public SystemConfigurationController(SystemConfigurationService configService,
                                       AdministrativeRequestValidationService validationService,
                                       ResponseStandardizationService responseService) {
        this.configService = configService;
        this.validationService = validationService;
        this.responseService = responseService;
    }
    
    /**
     * Get current user ID from security context.
     */
    private UUID getCurrentUserId() {
        return SecurityContextHelper.getCurrentUserId();
    }

    /**
     * Get all configurations (Admin only).
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> getAllConfigurations() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest configRequest = AdminConfigRequest.builder()
            .operation("list_configs")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid configuration list request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            List<SystemConfiguration> configurations = configService.getAllConfigurations();
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("list_configs")
                .status("SUCCESS")
                .message("Found " + configurations.size() + " configurations")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get all configurations for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve configurations", e);
        }
    }

    /**
     * Get configurations by category.
     */
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> getConfigurationsByCategory(@PathVariable String category) {
        
        UUID currentUserId = getCurrentUserId();
        
        try {
            SystemConfiguration.ConfigCategory configCategory = SystemConfiguration.ConfigCategory.valueOf(category.toUpperCase());
            
            // Create immutable request DTO
            AdminConfigRequest configRequest = AdminConfigRequest.builder()
                .operation("list_configs_by_category")
                .category(configCategory.toString())
                .requestedBy(currentUserId)
                .build();
            
            // Validate request
            ExecutionValidationResult validation = validationService.validateConfigurationListRequest(
                Map.of("category", category)
            );
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Invalid configuration category request: " + String.join(", ", validation.getErrors()));
            }
            
            List<SystemConfiguration> configurations = configService.getConfigurationsByCategory(configCategory);
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("list_configs_by_category")
                .status("SUCCESS")
                .category(configCategory.toString())
                .message("Found " + configurations.size() + " configurations in category " + configCategory)
                .build();
            
            return responseService.success(response);
            
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid category: " + category);
        } catch (Exception e) {
            logger.error("Failed to get configurations by category '{}' for user: {}", category, currentUserId, e);
            throw new RuntimeException("Failed to retrieve configurations", e);
        }
    }

    /**
     * Get dashboard refresh intervals for frontend use.
     */
    @GetMapping("/dashboard/intervals")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> getDashboardRefreshIntervals() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest intervalRequest = AdminConfigRequest.builder()
            .operation("get_dashboard_intervals")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid dashboard intervals request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            Map<String, Integer> intervals = configService.getDashboardRefreshIntervals();
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("get_dashboard_intervals")
                .status("SUCCESS")
                .message("Retrieved dashboard refresh intervals")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get dashboard refresh intervals for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve dashboard intervals", e);
        }
    }

    /**
     * Get configuration by key.
     */
    @GetMapping("/key/{configKey}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> getConfigurationByKey(@PathVariable String configKey) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest configRequest = AdminConfigRequest.builder()
            .operation("get_config_by_key")
            .configKey(configKey)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(
            Map.of("configKey", configKey)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid config key request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            Optional<SystemConfiguration> configuration = configService.getConfigurationByKey(configKey);
            
            if (configuration.isEmpty()) {
                throw new IllegalArgumentException("Configuration not found with key: " + configKey);
            }
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("get_config_by_key")
                .status("SUCCESS")
                .configKey(configKey)
                .configValue(configuration.get().getConfigValue())
                .message("Retrieved configuration for key: " + configKey)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get configuration by key '{}' for user: {}", configKey, currentUserId, e);
            throw new RuntimeException("Failed to retrieve configuration", e);
        }
    }

    /**
     * Update configuration value.
     */
    @PutMapping("/key/{configKey}/value")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> updateConfigurationValue(
            @PathVariable String configKey, 
            @RequestBody Map<String, String> request) {
        
        UUID currentUserId = getCurrentUserId();
        String newValue = request.get("value");
        
        if (newValue == null) {
            throw new IllegalArgumentException("Value is required");
        }
        
        // Create immutable request DTO
        AdminConfigRequest updateRequest = AdminConfigRequest.builder()
            .operation("update_config_value")
            .configKey(configKey)
            .configValue(newValue)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationUpdateRequest(
            configKey, newValue
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid config update request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            configService.updateConfigurationValue(configKey, newValue, currentUserId);
            
            logger.info("Updated configuration '{}' to '{}' by user: {}", configKey, newValue, currentUserId);
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("update_config_value")
                .status("SUCCESS")
                .configKey(configKey)
                .configValue(newValue)
                .message("Configuration updated successfully")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to update configuration value for key '{}' for user: {}", configKey, currentUserId, e);
            throw new RuntimeException("Failed to update configuration", e);
        }
    }

    /**
     * Reset configuration to default value.
     */
    @PutMapping("/key/{configKey}/reset")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> resetConfigurationToDefault(@PathVariable String configKey) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest resetRequest = AdminConfigRequest.builder()
            .operation("reset_config")
            .configKey(configKey)
            .reason("Reset to default value")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(
            Map.of("configKey", configKey)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid config reset request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            configService.resetToDefault(configKey, currentUserId);
            
            logger.info("Reset configuration '{}' to default by user: {}", configKey, currentUserId);
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("reset_config")
                .status("SUCCESS")
                .configKey(configKey)
                .message("Configuration reset to default value")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to reset configuration '{}' to default for user: {}", configKey, currentUserId, e);
            throw new RuntimeException("Failed to reset configuration", e);
        }
    }

    /**
     * Create new configuration.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> createConfiguration(@RequestBody SystemConfiguration configuration) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest createRequest = AdminConfigRequest.builder()
            .operation("create_config")
            .configKey(configuration.getConfigKey())
            .configValue(configuration.getConfigValue())
            .category(configuration.getCategory().toString())
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(
            Map.of(
                "configKey", configuration.getConfigKey() != null ? configuration.getConfigKey() : "",
                "configValue", configuration.getConfigValue() != null ? configuration.getConfigValue() : ""
            )
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid config create request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            SystemConfiguration saved = configService.saveConfiguration(configuration, currentUserId);
            
            logger.info("Created configuration '{}' by user: {}", saved.getConfigKey(), currentUserId);
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("create_config")
                .status("CREATED")
                .configKey(saved.getConfigKey())
                .configValue(saved.getConfigValue())
                .message("Configuration created successfully")
                .build();
            
            return responseService.created(response, "Configuration created successfully");
            
        } catch (Exception e) {
            logger.error("Failed to create configuration for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to create configuration", e);
        }
    }

    /**
     * Update entire configuration.
     */
    @PutMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> updateConfiguration(@RequestBody SystemConfiguration configuration) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest updateRequest = AdminConfigRequest.builder()
            .operation("update_config_full")
            .configKey(configuration.getConfigKey())
            .configValue(configuration.getConfigValue())
            .category(configuration.getCategory() != null ? configuration.getCategory().toString() : null)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationUpdateRequest(
            configuration.getConfigKey(), configuration.getConfigValue()
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid config update request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            SystemConfiguration saved = configService.saveConfiguration(configuration, currentUserId);
            
            logger.info("Updated configuration '{}' by user: {}", saved.getConfigKey(), currentUserId);
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("update_config_full")
                .status("SUCCESS")
                .configKey(saved.getConfigKey())
                .configValue(saved.getConfigValue())
                .message("Configuration updated successfully")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to update configuration for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to update configuration", e);
        }
    }

    /**
     * Delete configuration (if not readonly).
     */
    @DeleteMapping("/key/{configKey}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> deleteConfiguration(@PathVariable String configKey) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest deleteRequest = AdminConfigRequest.builder()
            .operation("delete_config")
            .configKey(configKey)
            .reason("Configuration deletion")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(
            Map.of("configKey", configKey)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid config delete request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            boolean deleted = configService.deleteConfiguration(configKey);
            
            if (!deleted) {
                throw new IllegalArgumentException("Configuration cannot be deleted (may be readonly or not found)");
            }
            
            logger.info("Deleted configuration '{}' by user: {}", configKey, currentUserId);
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("delete_config")
                .status("SUCCESS")
                .configKey(configKey)
                .message("Configuration deleted successfully")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to delete configuration '{}' for user: {}", configKey, currentUserId, e);
            throw new RuntimeException("Failed to delete configuration", e);
        }
    }

    /**
     * Get configuration statistics.
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> getConfigurationStatistics() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest statsRequest = AdminConfigRequest.builder()
            .operation("get_config_statistics")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid config statistics request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            Map<String, Object> statistics = configService.getConfigurationStatistics();
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("get_config_statistics")
                .status("SUCCESS")
                .message("Retrieved configuration statistics")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get configuration statistics for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve configuration statistics", e);
        }
    }

    /**
     * Clear configuration cache (for development/testing).
     */
    @PostMapping("/cache/clear")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> clearConfigurationCache() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest cacheRequest = AdminConfigRequest.builder()
            .operation("clear_cache")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid cache clear request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            configService.clearCache();
            
            logger.info("Configuration cache cleared by user: {}", currentUserId);
            
            // Create response using builder pattern
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("clear_cache")
                .status("SUCCESS")
                .message("Configuration cache cleared successfully")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to clear configuration cache for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to clear configuration cache", e);
        }
    }

    /**
     * Get available configuration categories and types.
     */
    @GetMapping("/metadata")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<AdminConfigResponse>> getConfigurationMetadata() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminConfigRequest metadataRequest = AdminConfigRequest.builder()
            .operation("get_metadata")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateConfigurationListRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid metadata request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            // Build metadata response
            AdminConfigResponse response = AdminConfigResponse.builder()
                .operation("get_metadata")
                .status("SUCCESS")
                .message("Retrieved configuration metadata")
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get configuration metadata for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve configuration metadata", e);
        }
    }
}