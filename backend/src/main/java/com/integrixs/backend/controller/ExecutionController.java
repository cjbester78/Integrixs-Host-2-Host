package com.integrixs.backend.controller;

import com.integrixs.backend.dto.*;
import com.integrixs.backend.dto.request.*;
import com.integrixs.backend.dto.response.*;
import com.integrixs.backend.service.ExecutionRequestValidationService;
import com.integrixs.backend.service.ResponseStandardizationService;
import com.integrixs.core.service.FlowExecutionService;
import com.integrixs.core.service.FlowMonitoringService;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.FlowExecutionStep;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Refactored ExecutionController following OOP best practices.
 * Uses dependency injection for validation, response standardization, and proper DTOs.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionController.class);
    
    private final FlowExecutionService executionService;
    private final FlowMonitoringService monitoringService;
    private final ExecutionRequestValidationService validationService;
    private final ResponseStandardizationService responseService;
    
    @Autowired
    public ExecutionController(FlowExecutionService executionService,
                              FlowMonitoringService monitoringService,
                              ExecutionRequestValidationService validationService,
                              ResponseStandardizationService responseService) {
        this.executionService = executionService;
        this.monitoringService = monitoringService;
        this.validationService = validationService;
        this.responseService = responseService;
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExecutionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String flowId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting execution history (page: {}, size: {}, flowId: {}, status: {})", 
                   currentUserId, page, size, flowId, status);
        
        // Validate request using our validation service
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("page", page);
        parameters.put("size", size);
        if (flowId != null) parameters.put("flowId", flowId);
        if (status != null) parameters.put("status", status);
        if (startDate != null) parameters.put("startDate", startDate);
        if (endDate != null) parameters.put("endDate", endDate);
        
        ExecutionValidationResult validation = validationService.validateHistoryRequest(parameters);
        
        if (!validation.isValid()) {
            logger.warn("Invalid execution history request from user {}: {}", currentUserId, validation.getErrorsAsString());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request validation failed: " + validation.getErrorsAsString()));
        }
        
        // Process request with validated parameters
        UUID flowUuid = null;
        FlowExecution.ExecutionStatus executionStatus = null;
        
        if (flowId != null && !flowId.isEmpty()) {
            flowUuid = UUID.fromString(flowId);
        }
        
        if (status != null && !status.isEmpty()) {
            executionStatus = FlowExecution.ExecutionStatus.valueOf(status.toUpperCase());
        }
        
        Map<String, Object> history = monitoringService.getExecutionHistory(
            page, size, flowUuid, executionStatus, startDate, endDate);
        
        logger.info("Retrieved execution history for user {} (page: {}, size: {})", currentUserId, page, size);
        return responseService.success(history, "Execution history retrieved successfully");
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExecutionDetails(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting execution details: {}", currentUserId, id);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateDetailsRequest(id);
        if (!validation.isValid()) {
            logger.warn("Invalid execution details request from user {}: {}", currentUserId, validation.getErrorsAsString());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request validation failed: " + validation.getErrorsAsString()));
        }
        
        UUID executionId = UUID.fromString(id);
        Map<String, Object> details = monitoringService.getExecutionDetails(executionId);
        
        logger.info("Retrieved execution details for {} by user {}", executionId, currentUserId);
        return responseService.success(details, "Execution details retrieved successfully");
    }
    
    @GetMapping("/{id}/steps")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<FlowExecutionStep>>> getExecutionSteps(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting execution steps: {}", currentUserId, id);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateStepsRequest(id);
        if (!validation.isValid()) {
            logger.warn("Invalid execution steps request from user {}: {}", currentUserId, validation.getErrorsAsString());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request validation failed: " + validation.getErrorsAsString()));
        }
        
        UUID executionId = UUID.fromString(id);
        List<FlowExecutionStep> steps = executionService.getExecutionSteps(executionId);
        
        logger.info("Retrieved {} execution steps for {} by user {}", steps.size(), executionId, currentUserId);
        return responseService.success(steps, "Execution steps retrieved successfully");
    }
    
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<FlowExecution>> retryExecution(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        String currentUserStr = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting retry for execution: {}", currentUserId, id);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateRetryRequest(id, currentUserStr);
        if (!validation.isValid()) {
            logger.warn("Invalid execution retry request from user {}: {}", currentUserId, validation.getErrorsAsString());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request validation failed: " + validation.getErrorsAsString()));
        }
        
        UUID executionId = UUID.fromString(id);
        UUID triggeredBy = UUID.fromString(currentUserStr);
        FlowExecution retryExecution = executionService.retryExecution(executionId, triggeredBy);
        
        logger.info("Successfully started retry execution {} for user {}", retryExecution.getId(), currentUserId);
        return responseService.success(retryExecution, "Execution retry started successfully");
    }
    
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> cancelExecution(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        String currentUserStr = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting cancel for execution: {}", currentUserId, id);
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateCancelRequest(id, currentUserStr);
        if (!validation.isValid()) {
            logger.warn("Invalid execution cancel request from user {}: {}", currentUserId, validation.getErrorsAsString());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request validation failed: " + validation.getErrorsAsString()));
        }
        
        UUID executionId = UUID.fromString(id);
        UUID cancelledBy = UUID.fromString(currentUserStr);
        executionService.cancelExecution(executionId, cancelledBy);
        
        logger.info("Successfully cancelled execution {} by user {}", id, currentUserId);
        return responseService.success(null, "Execution cancelled successfully");
    }
    
    @GetMapping("/running")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<FlowExecution>>> getRunningExecutions() {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting running executions", currentUserId);
        
        List<FlowExecution> runningExecutions = executionService.getRunningExecutions();
        
        logger.info("Retrieved {} running executions for user {}", runningExecutions.size(), currentUserId);
        return responseService.success(runningExecutions, "Running executions retrieved successfully");
    }
    
    @GetMapping("/failed")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<FlowExecution>>> getFailedExecutions() {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting failed executions", currentUserId);
        
        List<FlowExecution> failedExecutions = executionService.getFailedExecutionsForRetry();
        
        logger.info("Retrieved {} failed executions for user {}", failedExecutions.size(), currentUserId);
        return responseService.success(failedExecutions, "Failed executions retrieved successfully");
    }
    
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExecutionStatistics() {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting execution statistics", currentUserId);
        
        Map<String, Object> statistics = executionService.getExecutionStatistics();
        
        logger.info("Retrieved execution statistics for user {}", currentUserId);
        return responseService.success(statistics, "Execution statistics retrieved successfully");
    }
    
    @GetMapping("/performance")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPerformanceMetrics(
            @RequestParam(defaultValue = "24") int hours) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting performance metrics for {} hours", currentUserId, hours);
        
        // Basic validation for hours parameter
        if (hours < 1 || hours > 8760) { // Max 1 year
            logger.warn("Invalid hours parameter from user {}: {}", currentUserId, hours);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Hours parameter must be between 1 and 8760 (1 year)"));
        }
        
        Map<String, Object> metrics = monitoringService.getPerformanceMetrics(hours);
        
        logger.info("Retrieved performance metrics for {} hours for user {}", hours, currentUserId);
        return responseService.success(metrics, "Performance metrics retrieved successfully");
    }
    
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getExecutionLogs(
            @PathVariable String id,
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit) {
        
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting execution logs for: {} (filter: {}, level: {})", 
                   currentUserId, id, filter, level);
        
        // Validate request using our validation service
        ExecutionValidationResult validation = validationService.validateLogsRequest(id, filter, level, limit);
        if (!validation.isValid()) {
            logger.warn("Invalid execution logs request from user {}: {}", currentUserId, validation.getErrorsAsString());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request validation failed: " + validation.getErrorsAsString()));
        }
        
        UUID executionId = UUID.fromString(id);
        List<Map<String, Object>> logs = monitoringService.getExecutionLogs(executionId, filter, level, limit != null ? limit : 200);
        
        logger.info("Retrieved {} logs for execution {} (filtered by: {}, level: {})", 
                   logs.size(), executionId, filter, level);
        
        return responseService.success(logs, "Execution logs retrieved successfully");
    }
    
    @GetMapping("/search/message-id/{messageId}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchLogsByMessageId(@PathVariable String messageId) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} searching logs by message ID: {}", currentUserId, messageId);
        
        // Validate request using our validation service
        ExecutionValidationResult validation = validationService.validateSearchRequest(messageId);
        if (!validation.isValid()) {
            logger.warn("Invalid message ID search request from user {}: {}", currentUserId, validation.getErrorsAsString());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request validation failed: " + validation.getErrorsAsString()));
        }
        
        Map<String, Object> searchResults = monitoringService.searchLogsByMessageId(messageId);
        
        int totalLogs = (Integer) searchResults.getOrDefault("totalLogs", 0);
        int executionCount = ((List<?>) searchResults.getOrDefault("relatedExecutions", new ArrayList<>())).size();
        
        logger.info("Message ID search completed for {}: found {} logs across {} executions", 
                   messageId, totalLogs, executionCount);
        
        return responseService.success(
            searchResults, 
            String.format("Found %d logs across %d executions", totalLogs, executionCount)
        );
    }
    
    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFlowTrace(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting flow trace for execution: {}", currentUserId, id);
        
        // Validate request using our validation service
        ExecutionValidationResult validation = validationService.validateTraceRequest(id);
        if (!validation.isValid()) {
            logger.warn("Invalid flow trace request from user {}: {}", currentUserId, validation.getErrorsAsString());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request validation failed: " + validation.getErrorsAsString()));
        }
        
        UUID executionId = UUID.fromString(id);
        Map<String, Object> flowTrace = monitoringService.getFlowTrace(executionId);
        
        if (flowTrace.containsKey("error")) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error((String) flowTrace.get("error")));
        }
        
        int totalSteps = (Integer) flowTrace.getOrDefault("totalSteps", 0);
        int completedSteps = (Integer) flowTrace.getOrDefault("completedSteps", 0);
        
        logger.info("Flow trace retrieved for execution {}: {} total steps, {} completed", 
                   executionId, totalSteps, completedSteps);
        
        return responseService.success(
            flowTrace, 
            String.format("Flow trace retrieved: %d steps (%d completed)", totalSteps, completedSteps)
        );
    }
}