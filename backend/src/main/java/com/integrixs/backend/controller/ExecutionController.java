package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
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

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionController.class);
    
    private final FlowExecutionService executionService;
    private final FlowMonitoringService monitoringService;
    
    @Autowired
    public ExecutionController(FlowExecutionService executionService,
                              FlowMonitoringService monitoringService) {
        this.executionService = executionService;
        this.monitoringService = monitoringService;
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
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting execution history (page: {}, size: {}, flowId: {}, status: {})", 
                   currentUser, page, size, flowId, status);
        
        try {
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
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Execution history retrieved successfully", 
                history
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid parameter from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, "Invalid parameter: " + e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error retrieving execution history for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve execution history", null));
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExecutionDetails(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting execution details: {}", currentUser, id);
        
        try {
            UUID executionId = UUID.fromString(id);
            Map<String, Object> details = monitoringService.getExecutionDetails(executionId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Execution details retrieved successfully", 
                details
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid execution ID format: {}", id);
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, "Invalid execution ID format", null));
        } catch (Exception e) {
            logger.error("Error retrieving execution details {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve execution details", null));
        }
    }
    
    @GetMapping("/{id}/steps")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<FlowExecutionStep>>> getExecutionSteps(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting execution steps: {}", currentUser, id);
        
        try {
            UUID executionId = UUID.fromString(id);
            List<FlowExecutionStep> steps = executionService.getExecutionSteps(executionId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Execution steps retrieved successfully", 
                steps
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid execution ID format: {}", id);
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, "Invalid execution ID format", null));
        } catch (Exception e) {
            logger.error("Error retrieving execution steps {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve execution steps", null));
        }
    }
    
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<FlowExecution>> retryExecution(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting retry for execution: {}", currentUser, id);
        
        try {
            UUID executionId = UUID.fromString(id);
            UUID triggeredBy = UUID.fromString(currentUser);
            FlowExecution retryExecution = executionService.retryExecution(executionId, triggeredBy);
            
            logger.info("Successfully started retry execution {} for user {}", retryExecution.getId(), currentUser);
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Execution retry started successfully", 
                retryExecution
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for execution {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error retrying execution {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retry execution", null));
        }
    }
    
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> cancelExecution(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting cancel for execution: {}", currentUser, id);
        
        try {
            UUID executionId = UUID.fromString(id);
            UUID cancelledBy = UUID.fromString(currentUser);
            executionService.cancelExecution(executionId, cancelledBy);
            
            logger.info("Successfully cancelled execution {} by user {}", id, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Execution cancelled successfully", 
                null
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for execution {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error cancelling execution {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to cancel execution", null));
        }
    }
    
    @GetMapping("/running")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<FlowExecution>>> getRunningExecutions() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting running executions", currentUser);
        
        try {
            List<FlowExecution> runningExecutions = executionService.getRunningExecutions();
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Running executions retrieved successfully", 
                runningExecutions
            ));
        } catch (Exception e) {
            logger.error("Error retrieving running executions for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve running executions", null));
        }
    }
    
    @GetMapping("/failed")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<FlowExecution>>> getFailedExecutions() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting failed executions", currentUser);
        
        try {
            List<FlowExecution> failedExecutions = executionService.getFailedExecutionsForRetry();
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Failed executions retrieved successfully", 
                failedExecutions
            ));
        } catch (Exception e) {
            logger.error("Error retrieving failed executions for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve failed executions", null));
        }
    }
    
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExecutionStatistics() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting execution statistics", currentUser);
        
        try {
            Map<String, Object> statistics = executionService.getExecutionStatistics();
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Execution statistics retrieved successfully", 
                statistics
            ));
        } catch (Exception e) {
            logger.error("Error retrieving execution statistics for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve execution statistics", null));
        }
    }
    
    @GetMapping("/performance")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPerformanceMetrics(
            @RequestParam(defaultValue = "24") int hours) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting performance metrics for {} hours", currentUser, hours);
        
        try {
            Map<String, Object> metrics = monitoringService.getPerformanceMetrics(hours);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Performance metrics retrieved successfully", 
                metrics
            ));
        } catch (Exception e) {
            logger.error("Error retrieving performance metrics for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve performance metrics", null));
        }
    }
    
    
    /**
     * Get execution logs for flow monitoring
     */
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getExecutionLogs(
            @PathVariable String id,
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "limit", required = false, defaultValue = "200") int limit) {
        
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting execution logs for: {} (filter: {}, level: {})", 
                   currentUserId, id, filter, level);
        
        try {
            UUID executionId = UUID.fromString(id);
            
            // Get execution logs from monitoring service
            List<Map<String, Object>> logs = monitoringService.getExecutionLogs(executionId, filter, level, limit);
            
            logger.info("Retrieved {} logs for execution {} (filtered by: {}, level: {})", 
                       logs.size(), executionId, filter, level);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Execution logs retrieved successfully", 
                logs
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for execution {}: {}", currentUserId, id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error retrieving execution logs {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse<>(false, "Failed to retrieve execution logs", null));
        }
    }
    
    /**
     * Phase 5: Search logs by message ID for complete traceability across all executions
     */
    @GetMapping("/search/message-id/{messageId}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchLogsByMessageId(@PathVariable String messageId) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} searching logs by message ID: {}", currentUserId, messageId);
        
        try {
            // Get search results from monitoring service
            Map<String, Object> searchResults = monitoringService.searchLogsByMessageId(messageId);
            
            int totalLogs = (Integer) searchResults.getOrDefault("totalLogs", 0);
            int executionCount = ((List<?>) searchResults.getOrDefault("relatedExecutions", new ArrayList<>())).size();
            
            logger.info("Message ID search completed for {}: found {} logs across {} executions", 
                       messageId, totalLogs, executionCount);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                String.format("Found %d logs across %d executions", totalLogs, executionCount), 
                searchResults
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid message ID format from user {}: {}", currentUserId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "Invalid message ID format: " + e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error searching logs by message ID {} for user {}: {}", messageId, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse<>(false, "Failed to search logs by message ID", null));
        }
    }
    
    /**
     * Phase 5: Get comprehensive flow trace with timeline visualization
     */
    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFlowTrace(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting flow trace for execution: {}", currentUserId, id);
        
        try {
            UUID executionId = UUID.fromString(id);
            
            // Get comprehensive flow trace from monitoring service
            Map<String, Object> flowTrace = monitoringService.getFlowTrace(executionId);
            
            if (flowTrace.containsKey("error")) {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, (String) flowTrace.get("error"), null));
            }
            
            int totalSteps = (Integer) flowTrace.getOrDefault("totalSteps", 0);
            int completedSteps = (Integer) flowTrace.getOrDefault("completedSteps", 0);
            
            logger.info("Flow trace retrieved for execution {}: {} total steps, {} completed", 
                       executionId, totalSteps, completedSteps);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                String.format("Flow trace retrieved: %d steps (%d completed)", totalSteps, completedSteps), 
                flowTrace
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid execution ID format from user {} for trace: {}", currentUserId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "Invalid execution ID format", null));
        } catch (Exception e) {
            logger.error("Error retrieving flow trace {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse<>(false, "Failed to retrieve flow trace", null));
        }
    }
}