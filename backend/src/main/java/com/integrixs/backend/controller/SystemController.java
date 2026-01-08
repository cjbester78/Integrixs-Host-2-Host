package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.backend.dto.request.AdminSystemRequest;
import com.integrixs.backend.dto.response.AdminSystemResponse;
import com.integrixs.backend.service.AdministrativeRequestValidationService;
import com.integrixs.backend.service.ResponseStandardizationService;
import com.integrixs.backend.service.SystemService;
import com.integrixs.core.repository.SystemLogRepository;
import com.integrixs.shared.dto.SystemHealth;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for system monitoring and health checks.
 * Refactored following OOP principles with proper validation, DTOs, and error handling.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);

    private final SystemService systemService;
    private final SystemLogRepository systemLogRepository;
    private final AdministrativeRequestValidationService validationService;
    private final ResponseStandardizationService responseService;

    @Autowired
    public SystemController(SystemService systemService,
                          SystemLogRepository systemLogRepository,
                          AdministrativeRequestValidationService validationService,
                          ResponseStandardizationService responseService) {
        this.systemService = systemService;
        this.systemLogRepository = systemLogRepository;
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
     * Get overall system health status.
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getSystemHealth() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest healthRequest = AdminSystemRequest.builder()
            .operation("system_health")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateSystemHealthRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid system health request: " + validation.getErrors());
        }
        
        try {
            SystemHealth health = systemService.getSystemHealth();
            
            Map<String, Object> healthData = Map.of(
                "status", health.getStatus(),
                "timestamp", health.getTimestamp(),
                "uptimeMs", health.getUptimeMs(),
                "connectionStatus", health.getConnectionStatus() != null ? health.getConnectionStatus() : Map.of(),
                "activeOperations", health.getActiveOperations(),
                "successRate", health.getSuccessRate(),
                "overallStatus", health.getStatus().toString()
            );
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.healthResponse(healthData);
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get system health for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve system health", e);
        }
    }

    /**
     * Get application version and build information.
     */
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getSystemInfo() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest infoRequest = AdminSystemRequest.builder()
            .operation("system_info")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateSystemMetricsRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid system info request: " + validation.getErrors());
        }
        
        try {
            Map<String, Object> info = systemService.getSystemInfo();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("system_info")
                .status("SUCCESS")
                .metrics(info)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get system info for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve system information", e);
        }
    }

    /**
     * Get system metrics (memory usage, disk space, etc.).
     */
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getSystemMetrics() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest metricsRequest = AdminSystemRequest.builder()
            .operation("system_metrics")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateSystemMetricsRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid system metrics request: " + validation.getErrors());
        }
        
        try {
            Map<String, Object> metrics = systemService.getSystemMetrics();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.metricsResponse(metrics);
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get system metrics for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve system metrics", e);
        }
    }

    /**
     * Get recent operation logs from database.
     */
    @GetMapping("/logs/recent")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getRecentLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest logsRequest = AdminSystemRequest.logsRequest(limit, level, category, currentUserId);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateLogsRequest(
            Map.of("limit", limit, "level", level != null ? level : "", "category", category != null ? category : "", "search", search != null ? search : "")
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid logs request: " + validation.getErrors());
        }
        
        try {
            List<Map<String, Object>> logs = systemLogRepository.getRecentLogsForApi(limit, level, category, search);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.logsResponse(logs, logs.size());
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get recent logs for user: {} with params limit={}, level={}, category={}, search={}", 
                        currentUserId, limit, level, category, search, e);
            throw new RuntimeException("Failed to retrieve recent logs", e);
        }
    }

    /**
     * Export logs as CSV file.
     */
    @GetMapping("/logs/export")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> exportLogs(
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(required = false) String bankName,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "csv") String format) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest exportRequest = AdminSystemRequest.logsExportRequest(limit, format, currentUserId);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateLogsExportRequest(
            Map.of("limit", limit, "format", format, "level", level != null ? level : "", "bankName", bankName != null ? bankName : "")
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid logs export request: " + validation.getErrors());
        }
        
        try {
            String exportData = systemService.exportLogs(limit, bankName, level, format);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.exportResponse(format, exportData, limit);
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to export logs for user: {} with params limit={}, bankName={}, level={}, format={}", 
                        currentUserId, limit, bankName, level, format, e);
            throw new RuntimeException("Failed to export logs", e);
        }
    }

    /**
     * Get operation statistics.
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> getOperationStatistics(
            @RequestParam(required = false) String bankName,
            @RequestParam(defaultValue = "24") int hours) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest statsRequest = AdminSystemRequest.statisticsRequest(hours, bankName, currentUserId);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateStatisticsRequest(
            Map.of("hours", hours, "bankName", bankName != null ? bankName : "")
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid statistics request: " + validation.getErrors());
        }
        
        try {
            Map<String, Object> stats = systemService.getOperationStatistics(bankName, hours);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.statisticsResponse(stats);
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to get operation statistics for user: {} with params bankName={}, hours={}", 
                        currentUserId, bankName, hours, e);
            throw new RuntimeException("Failed to retrieve operation statistics", e);
        }
    }

    /**
     * Clear old log files.
     */
    @PostMapping("/cleanup/logs")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> cleanupLogs(
            @RequestParam(defaultValue = "30") int retentionDays) {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest cleanupRequest = AdminSystemRequest.cleanupRequest(retentionDays, currentUserId);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateCleanupRequest(
            Map.of("retentionDays", retentionDays)
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid cleanup request: " + validation.getErrors());
        }
        
        try {
            Map<String, Object> result = systemService.cleanupLogs(retentionDays);
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.cleanupResponse(result);
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup logs for user: {} with retentionDays={}", 
                        currentUserId, retentionDays, e);
            throw new RuntimeException("Failed to cleanup logs", e);
        }
    }

    /**
     * Test all configured bank connections.
     */
    @PostMapping("/test-all-connections")
    public ResponseEntity<ApiResponse<AdminSystemResponse>> testAllConnections() {
        
        UUID currentUserId = getCurrentUserId();
        
        // Create immutable request DTO
        AdminSystemRequest testRequest = AdminSystemRequest.builder()
            .operation("connection_test")
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateSystemHealthRequest(Map.of());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid connection test request: " + validation.getErrors());
        }
        
        try {
            Map<String, Object> results = systemService.testAllBankConnections();
            
            // Create response using builder pattern
            AdminSystemResponse response = AdminSystemResponse.builder()
                .operation("connection_test")
                .status("SUCCESS")
                .metrics(results)
                .build();
            
            return responseService.success(response);
            
        } catch (Exception e) {
            logger.error("Failed to test bank connections for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to test bank connections", e);
        }
    }
}