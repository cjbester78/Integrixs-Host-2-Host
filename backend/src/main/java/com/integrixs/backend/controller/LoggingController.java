package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.backend.dto.request.AdminSystemRequest;
import com.integrixs.backend.dto.response.AdminSystemResponse;
import com.integrixs.backend.service.AdministrativeRequestValidationService;
import com.integrixs.backend.service.ResponseStandardizationService;
import com.integrixs.core.service.DynamicLoggingService;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing dynamic logging configuration.
 * Allows administrators to view and modify logging settings at runtime.
 * Refactored following OOP principles with proper validation, DTOs, and error handling.
 */
@RestController
@RequestMapping("/api/admin/logging")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class LoggingController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingController.class);
    private final DynamicLoggingService dynamicLoggingService;
    private final AdministrativeRequestValidationService validationService;
    private final ResponseStandardizationService responseService;
    
    @Autowired
    public LoggingController(DynamicLoggingService dynamicLoggingService,
                           AdministrativeRequestValidationService validationService,
                           ResponseStandardizationService responseService) {
        this.dynamicLoggingService = dynamicLoggingService;
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
     * Get current logging configuration.
     */
    @GetMapping("/configuration")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getLoggingConfiguration() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest configRequest = AdminSystemRequest.builder()
            .operation("logging_config")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateSystemHealthRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid logging configuration request: " + validation.getErrors());
        }
        
        try {
            Map<String, String> config = dynamicLoggingService.getCurrentLoggingConfiguration();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("logging_config")
                .status("SUCCESS")
                .metrics(Map.of("configurations", config))
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get logging configuration for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve logging configuration", e);
        }
    }
    
    /**
     * Get logging configuration summary for monitoring.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getLoggingConfigurationSummary() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest summaryRequest = AdminSystemRequest.builder()
            .operation("logging_summary")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateSystemMetricsRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid logging summary request: " + validation.getErrors());
        }
        
        try {
            Map<String, Object> summary = dynamicLoggingService.getLoggingConfigurationSummary();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("logging_summary")
                .status("SUCCESS")
                .statistics(summary)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get logging configuration summary for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve logging summary", e);
        }
    }
    
    /**
     * Get current effective logging level for a specific logger.
     */
    @GetMapping("/loggers/{loggerName}")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getLoggerLevel(@PathVariable String loggerName) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest loggerRequest = AdminSystemRequest.builder()
            .operation("logger_level")
            .search(loggerName)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateLogsRequest(
            Map.of("search", loggerName)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid logger level request: " + validation.getErrors());
        }
        
        try {
            String level = dynamicLoggingService.getLoggerLevel(loggerName);
            
            Map<String, Object> loggerData = Map.of(
                "loggerName", loggerName,
                "effectiveLevel", level
            );
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("logger_level")
                .status("SUCCESS")
                .metrics(loggerData)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get logger level for '{}' requested by user: {}", loggerName, currentUserId, e);
            throw new RuntimeException("Failed to retrieve logger level", e);
        }
    }
    
    /**
     * Set logging level for a specific logger.
     */
    @PutMapping("/loggers/{loggerName}")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> setLoggerLevel(
            @PathVariable String loggerName, 
            @RequestBody Map<String, String> request) {
        
        UUID currentUserId = getCurrentUserId();
        String level = request.get("level");
        
        // Create immutable request DTO
        AdminSystemRequest updateRequest = AdminSystemRequest.builder()
            .operation("logger_update")
            .search(loggerName)
            .level(level)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateLogsRequest(
            Map.of("search", loggerName, "level", level)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid logger update request: " + validation.getErrors());
        }
        
        try {
            String previousLevel = dynamicLoggingService.getLoggerLevel(loggerName);
            dynamicLoggingService.setLoggerLevel(loggerName, level.toUpperCase());
            String newLevel = dynamicLoggingService.getLoggerLevel(loggerName);
            
            logger.info("Updated logger '{}' from '{}' to '{}' via API by user: {}", 
                       loggerName, previousLevel, level, currentUserId);
            
            Map<String, Object> updateResult = Map.of(
                "loggerName", loggerName,
                "previousLevel", previousLevel,
                "newLevel", newLevel,
                "effectiveLevel", newLevel
            );
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("logger_update")
                .status("SUCCESS")
                .metrics(updateResult)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to set logger level for '{}' to '{}' requested by user: {}", 
                        loggerName, level, currentUserId, e);
            throw new RuntimeException("Failed to update logger level", e);
        }
    }
    
    /**
     * Reload logging configuration from database.
     */
    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> reloadConfiguration() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest reloadRequest = AdminSystemRequest.builder()
            .operation("config_reload")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateSystemHealthRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid configuration reload request: " + validation.getErrors());
        }
        
        try {
            long startTime = System.currentTimeMillis();
            dynamicLoggingService.reloadConfiguration();
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("Logging configuration reloaded via API in {}ms by user: {}", duration, currentUserId);
            
            Map<String, Object> reloadResult = Map.of(
                "success", true,
                "message", "Logging configuration reloaded successfully",
                "reloadTimeMs", duration,
                "timestamp", System.currentTimeMillis()
            );
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("config_reload")
                .status("SUCCESS")
                .metrics(reloadResult)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to reload logging configuration for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to reload configuration", e);
        }
    }
    
}