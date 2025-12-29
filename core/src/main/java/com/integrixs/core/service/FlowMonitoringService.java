package com.integrixs.core.service;

import com.integrixs.core.repository.FlowExecutionRepository;
import com.integrixs.core.repository.FlowExecutionStepRepository;
import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.SystemLogRepository;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.shared.model.SystemLog;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for monitoring flow executions and providing real-time status updates
 * Handles performance metrics, alerts, and dashboard statistics
 */
@Service
public class FlowMonitoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowMonitoringService.class);
    private final EnhancedLogger enhancedLogger = EnhancedLogger.getLogger(FlowMonitoringService.class);
    
    private final FlowExecutionRepository executionRepository;
    private final FlowExecutionStepRepository stepRepository;
    private final IntegrationFlowRepository flowRepository;
    private final AdapterRepository adapterRepository;
    private final SystemLogRepository systemLogRepository;
    
    @Autowired
    public FlowMonitoringService(FlowExecutionRepository executionRepository,
                                FlowExecutionStepRepository stepRepository,
                                IntegrationFlowRepository flowRepository,
                                AdapterRepository adapterRepository,
                                SystemLogRepository systemLogRepository) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.flowRepository = flowRepository;
        this.adapterRepository = adapterRepository;
        this.systemLogRepository = systemLogRepository;
    }
    
    /**
     * Get comprehensive dashboard overview
     */
    public Map<String, Object> getDashboardOverview() {
        logger.debug("Generating dashboard overview");
        
        Map<String, Object> overview = new HashMap<>();
        
        // Flow statistics
        Map<String, Object> flowStats = flowRepository.getFlowStatistics();
        overview.put("flows", flowStats);
        
        // Execution statistics
        Map<String, Object> executionStats = executionRepository.getExecutionStatistics();
        overview.put("executions", executionStats);
        
        // Step statistics
        Map<String, Object> stepStats = stepRepository.getStepStatistics();
        overview.put("steps", stepStats);
        
        // Adapter statistics
        Map<String, Object> adapterStats = adapterRepository.getAdapterStatistics();
        overview.put("adapters", adapterStats);
        
        // Real-time status
        overview.put("realTimeStatus", getRealTimeStatus());
        
        // Recent activity
        overview.put("recentActivity", getRecentActivity(10));
        
        // Alerts
        overview.put("alerts", getActiveAlerts());
        
        overview.put("timestamp", LocalDateTime.now());
        
        return overview;
    }
    
    /**
     * Get real-time execution status
     */
    public Map<String, Object> getRealTimeStatus() {
        logger.debug("Getting real-time execution status");
        
        Map<String, Object> status = new HashMap<>();
        
        // Running executions
        List<FlowExecution> runningExecutions = executionRepository.findRunningExecutions();
        status.put("runningExecutions", runningExecutions.size());
        status.put("runningExecutionDetails", runningExecutions.stream()
            .map(this::createExecutionSummary)
            .toList());
        
        // Pending executions (if we had a queue)
        status.put("pendingExecutions", 0);
        
        // Failed executions needing attention
        List<FlowExecution> failedExecutions = executionRepository.findFailedExecutionsForRetry();
        status.put("failedExecutionsAwaitingRetry", failedExecutions.size());
        
        // System health indicators
        status.put("systemHealth", getSystemHealthIndicators());
        
        return status;
    }
    
    /**
     * Get execution details with step information
     */
    public Map<String, Object> getExecutionDetails(UUID executionId) {
        logger.debug("Getting execution details for: {}", executionId);
        
        Optional<FlowExecution> executionOpt = executionRepository.findById(executionId);
        if (executionOpt.isEmpty()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }
        
        FlowExecution execution = executionOpt.get();
        Map<String, Object> details = createExecutionDetails(execution);
        
        // Add step details
        List<FlowExecutionStep> steps = stepRepository.findByExecutionId(executionId);
        details.put("steps", steps.stream()
            .map(this::createStepSummary)
            .toList());
        
        details.put("totalSteps", steps.size());
        details.put("completedSteps", steps.stream()
            .mapToInt(step -> step.isCompleted() ? 1 : 0)
            .sum());
        details.put("failedSteps", steps.stream()
            .mapToInt(step -> step.isFailed() ? 1 : 0)
            .sum());
        
        return details;
    }
    
    /**
     * Get execution history with filtering and pagination
     */
    public Map<String, Object> getExecutionHistory(int page, int size, UUID flowId, 
                                                  FlowExecution.ExecutionStatus status,
                                                  LocalDateTime startDate, LocalDateTime endDate) {
        logger.debug("Getting execution history - page: {}, size: {}, flowId: {}, status: {}", 
                    page, size, flowId, status);
        
        Map<String, Object> history = new HashMap<>();
        
        // Get executions (simplified - in production would implement proper pagination)
        List<FlowExecution> executions;
        if (flowId != null) {
            executions = executionRepository.findByFlowId(flowId);
        } else if (status != null) {
            executions = executionRepository.findByStatus(status);
        } else {
            executions = executionRepository.findAll();
        }
        
        // Filter by date range
        if (startDate != null || endDate != null) {
            executions = executions.stream()
                .filter(execution -> {
                    LocalDateTime startedAt = execution.getStartedAt();
                    if (startedAt == null) return false;
                    
                    if (startDate != null && startedAt.isBefore(startDate)) return false;
                    if (endDate != null && startedAt.isAfter(endDate)) return false;
                    
                    return true;
                })
                .toList();
        }
        
        // Simple pagination
        int total = executions.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, total);
        
        List<FlowExecution> pagedExecutions = fromIndex < total ? 
            executions.subList(fromIndex, toIndex) : new ArrayList<>();
        
        history.put("executions", pagedExecutions.stream()
            .map(this::createExecutionSummary)
            .toList());
        history.put("totalElements", total);
        history.put("totalPages", (total + size - 1) / size);
        history.put("currentPage", page);
        history.put("pageSize", size);
        
        return history;
    }
    
    /**
     * Get performance metrics for flows
     */
    public Map<String, Object> getPerformanceMetrics(int hours) {
        logger.debug("Getting performance metrics for last {} hours", hours);
        
        Map<String, Object> metrics = new HashMap<>();
        
        // Execution statistics from repository
        Map<String, Object> executionStats = executionRepository.getExecutionStatistics();
        metrics.put("executionStatistics", executionStats);
        
        // Step statistics from repository  
        Map<String, Object> stepStats = stepRepository.getStepStatistics();
        metrics.put("stepStatistics", stepStats);
        
        // Calculate additional metrics
        metrics.put("timeRange", hours + " hours");
        metrics.put("timestamp", LocalDateTime.now());
        
        // Success rates
        Object totalExecutions = executionStats.get("total_executions");
        Object completedExecutions = executionStats.get("completed_executions");
        Object failedExecutions = executionStats.get("failed_executions");
        
        if (totalExecutions instanceof Number && ((Number) totalExecutions).longValue() > 0) {
            double successRate = ((Number) completedExecutions).doubleValue() / 
                                ((Number) totalExecutions).doubleValue() * 100.0;
            double failureRate = ((Number) failedExecutions).doubleValue() / 
                                 ((Number) totalExecutions).doubleValue() * 100.0;
            
            metrics.put("successRate", Math.round(successRate * 100.0) / 100.0);
            metrics.put("failureRate", Math.round(failureRate * 100.0) / 100.0);
        }
        
        // Average execution time
        Object avgDuration = executionStats.get("avg_duration_ms");
        if (avgDuration instanceof Number) {
            metrics.put("averageExecutionTime", formatDuration(((Number) avgDuration).longValue()));
        }
        
        return metrics;
    }
    
    /**
     * Get execution logs for detailed monitoring view
     */
    public List<Map<String, Object>> getExecutionLogs(UUID executionId, String filter, String level, int limit) {
        logger.debug("Getting execution logs for execution: {} (filter: {}, level: {}, limit: {})", 
                    executionId, filter, level, limit);
        
        try {
            // Convert string level to SystemLog.LogLevel
            SystemLog.LogLevel minLevel = null;
            if (level != null && !level.isEmpty()) {
                try {
                    minLevel = SystemLog.LogLevel.valueOf(level.toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid log level specified: {}. Using default (no filtering)", level);
                }
            }
            
            // Get logs from SystemLogRepository
            List<SystemLog> logs = systemLogRepository.findExecutionLogs(executionId, limit, 0);
            
            // Filter by text if specified
            if (filter != null && !filter.trim().isEmpty()) {
                String filterLower = filter.toLowerCase();
                logs = logs.stream()
                    .filter(log -> log.getMessage() != null && 
                                  log.getMessage().toLowerCase().contains(filterLower))
                    .toList();
            }
            
            // Filter by log level if specified
            if (minLevel != null) {
                final SystemLog.LogLevel finalMinLevel = minLevel;
                logs = logs.stream()
                    .filter(log -> log.getLogLevel() != null && 
                                  log.getLogLevel().ordinal() >= finalMinLevel.ordinal())
                    .toList();
            }
            
            // Convert to response format
            return logs.stream()
                .map(this::convertLogToResponseFormat)
                .toList();
                
        } catch (Exception e) {
            logger.error("Error retrieving execution logs for execution {}: {}", executionId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Convert SystemLog to API response format
     */
    private Map<String, Object> convertLogToResponseFormat(SystemLog log) {
        Map<String, Object> logMap = new HashMap<>();
        
        logMap.put("id", log.getId());
        logMap.put("timestamp", log.getTimestamp());
        logMap.put("level", log.getLogLevel() != null ? log.getLogLevel().name() : "INFO");
        logMap.put("category", log.getLogCategory() != null ? log.getLogCategory().name() : "SYSTEM");
        logMap.put("message", log.getFormattedMessage() != null ? log.getFormattedMessage() : log.getMessage());
        logMap.put("loggerName", log.getLoggerName());
        logMap.put("threadName", log.getThreadName());
        
        // Add context information if available
        if (log.getAdapterId() != null) {
            logMap.put("adapterId", log.getAdapterId());
        }
        if (log.getAdapterName() != null) {
            logMap.put("adapterName", log.getAdapterName());
        }
        if (log.getFlowId() != null) {
            logMap.put("flowId", log.getFlowId());
        }
        if (log.getFlowName() != null) {
            logMap.put("flowName", log.getFlowName());
        }
        if (log.getExecutionId() != null) {
            logMap.put("executionId", log.getExecutionId());
        }
        
        // Add exception details if available
        if (log.getExceptionClass() != null) {
            logMap.put("exceptionClass", log.getExceptionClass());
            logMap.put("exceptionMessage", log.getExceptionMessage());
            if (log.getStackTrace() != null && logger.isDebugEnabled()) {
                logMap.put("stackTrace", log.getStackTrace());
            }
        }
        
        // Add execution time if available
        if (log.getExecutionTimeMs() != null) {
            logMap.put("executionTimeMs", log.getExecutionTimeMs());
        }
        
        // Add MDC data if available
        if (log.getMdcData() != null && !log.getMdcData().isEmpty()) {
            logMap.put("mdcData", log.getMdcData());
        }
        
        // Add request context if available
        if (log.getRequestMethod() != null) {
            logMap.put("requestMethod", log.getRequestMethod());
        }
        if (log.getRequestUri() != null) {
            logMap.put("requestUri", log.getRequestUri());
        }
        
        return logMap;
    }
    
    /**
     * Get active alerts and notifications
     */
    public List<Map<String, Object>> getActiveAlerts() {
        logger.debug("Getting active alerts");
        
        List<Map<String, Object>> alerts = new ArrayList<>();
        
        // Check for long-running executions
        List<FlowExecution> runningExecutions = executionRepository.findRunningExecutions();
        for (FlowExecution execution : runningExecutions) {
            if (execution.isOverdue()) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("type", "EXECUTION_TIMEOUT");
                alert.put("severity", "HIGH");
                alert.put("message", "Execution " + execution.getId() + " has exceeded timeout");
                alert.put("executionId", execution.getId());
                alert.put("flowName", execution.getFlowName());
                alert.put("startedAt", execution.getStartedAt());
                alert.put("timeoutAt", execution.getTimeoutAt());
                alerts.add(alert);
            }
        }
        
        // Check for failed executions
        List<FlowExecution> failedExecutions = executionRepository.findByStatus(FlowExecution.ExecutionStatus.FAILED);
        if (failedExecutions.size() > 0) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "FAILED_EXECUTIONS");
            alert.put("severity", "MEDIUM");
            alert.put("message", failedExecutions.size() + " failed executions require attention");
            alert.put("count", failedExecutions.size());
            alerts.add(alert);
        }
        
        return alerts;
    }
    
    /**
     * Get recent activity summary
     */
    public List<Map<String, Object>> getRecentActivity(int limit) {
        logger.debug("Getting recent activity (limit: {})", limit);
        
        // Get recent executions
        List<FlowExecution> recentExecutions = executionRepository.findAll()
            .stream()
            .sorted((e1, e2) -> e2.getStartedAt().compareTo(e1.getStartedAt()))
            .limit(limit)
            .toList();
        
        return recentExecutions.stream()
            .map(execution -> {
                Map<String, Object> activity = new HashMap<>();
                activity.put("type", "EXECUTION");
                activity.put("executionId", execution.getId());
                activity.put("flowName", execution.getFlowName());
                activity.put("status", execution.getExecutionStatus().name());
                activity.put("startedAt", execution.getStartedAt());
                activity.put("triggeredBy", execution.getTriggeredBy());
                activity.put("duration", execution.getFormattedDuration());
                
                if (execution.isTerminal()) {
                    activity.put("completedAt", execution.getCompletedAt());
                }
                
                return activity;
            })
            .toList();
    }
    
    /**
     * Get system health indicators
     */
    public Map<String, Object> getSystemHealthIndicators() {
        Map<String, Object> health = new HashMap<>();
        
        // Check for stuck executions
        List<FlowExecution> runningExecutions = executionRepository.findRunningExecutions();
        long stuckExecutions = runningExecutions.stream()
            .mapToLong(execution -> execution.isOverdue() ? 1 : 0)
            .sum();
        
        health.put("stuckExecutions", stuckExecutions);
        health.put("totalRunningExecutions", runningExecutions.size());
        
        // Overall health status
        String status = "HEALTHY";
        if (stuckExecutions > 0) {
            status = "WARNING";
        }
        if (stuckExecutions > 5) {
            status = "CRITICAL";
        }
        
        health.put("overallStatus", status);
        health.put("lastCheck", LocalDateTime.now());
        
        return health;
    }
    
    /**
     * Create execution summary
     */
    private Map<String, Object> createExecutionSummary(FlowExecution execution) {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("id", execution.getId());
        summary.put("flowId", execution.getFlowId());
        summary.put("flowName", execution.getFlowName());
        summary.put("status", execution.getExecutionStatus().name());
        summary.put("statusIcon", execution.getStatusIcon());
        summary.put("triggerType", execution.getTriggerType().name());
        summary.put("startedAt", execution.getStartedAt());
        summary.put("priority", execution.getPriority());
        summary.put("priorityLabel", execution.getPriorityLabel());
        
        if (execution.isTerminal()) {
            summary.put("completedAt", execution.getCompletedAt());
            summary.put("duration", execution.getFormattedDuration());
        }
        
        if (execution.isFailed()) {
            summary.put("errorMessage", execution.getErrorMessage());
        }
        
        summary.put("filesProcessed", execution.getTotalFilesProcessed());
        summary.put("bytesProcessed", execution.getFormattedBytes());
        summary.put("isRetry", execution.isRetry());
        
        return summary;
    }
    
    /**
     * Create execution details
     */
    private Map<String, Object> createExecutionDetails(FlowExecution execution) {
        Map<String, Object> details = createExecutionSummary(execution);
        
        // Add detailed fields
        details.put("correlationId", execution.getCorrelationId());
        details.put("parentExecutionId", execution.getParentExecutionId());
        details.put("retryAttempt", execution.getRetryAttempt());
        details.put("maxRetryAttempts", execution.getMaxRetryAttempts());
        details.put("payload", execution.getPayload());
        details.put("executionContext", execution.getExecutionContext());
        
        if (execution.getTimeoutAt() != null) {
            details.put("timeoutAt", execution.getTimeoutAt());
            details.put("isOverdue", execution.isOverdue());
        }
        
        if (execution.getErrorDetails() != null) {
            details.put("errorDetails", execution.getErrorDetails());
            details.put("errorStepId", execution.getErrorStepId());
        }
        
        return details;
    }
    
    /**
     * Create step summary
     */
    private Map<String, Object> createStepSummary(FlowExecutionStep step) {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("id", step.getId());
        summary.put("stepId", step.getStepId());
        summary.put("stepName", step.getStepName());
        summary.put("stepType", step.getStepType().name());
        summary.put("stepOrder", step.getStepOrder());
        summary.put("status", step.getStepStatus().name());
        summary.put("statusIcon", step.getStatusIcon());
        summary.put("typeIcon", step.getTypeIcon());
        summary.put("startedAt", step.getStartedAt());
        
        if (step.isTerminal()) {
            summary.put("completedAt", step.getCompletedAt());
            summary.put("duration", step.getFormattedDuration());
        }
        
        if (step.isFailed()) {
            summary.put("errorMessage", step.getErrorMessage());
        }
        
        summary.put("filesCount", step.getFilesCount());
        summary.put("bytesProcessed", step.getFormattedBytes());
        
        return summary;
    }
    
    /**
     * Format duration in milliseconds to human readable format
     */
    private String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else {
            long minutes = durationMs / 60000;
            long seconds = (durationMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    /**
     * Get adapter execution history - last N executions for a specific adapter
     * Returns enterprise-style execution summaries with clickable execution IDs
     */
    public Map<String, Object> getAdapterExecutionHistory(UUID adapterId, int limit) {
        logger.debug("Getting adapter execution history for adapter: {} (limit: {})", adapterId, limit);
        
        Map<String, Object> history = new HashMap<>();
        
        try {
            // Find all executions that involved this adapter
            List<FlowExecution> adapterExecutions = findExecutionsForAdapter(adapterId);
            
            // Sort by start time (most recent first) and limit
            List<FlowExecution> recentExecutions = adapterExecutions.stream()
                .sorted((e1, e2) -> {
                    if (e1.getStartedAt() == null && e2.getStartedAt() == null) return 0;
                    if (e1.getStartedAt() == null) return 1;
                    if (e2.getStartedAt() == null) return -1;
                    return e2.getStartedAt().compareTo(e1.getStartedAt());
                })
                .limit(limit)
                .toList();
            
            // Convert to enterprise-style execution summaries with clickable IDs
            List<Map<String, Object>> executionSummaries = recentExecutions.stream()
                .map(this::createEnterpriseExecutionSummary)
                .toList();
            
            history.put("adapterId", adapterId);
            history.put("executions", executionSummaries);
            history.put("totalExecutions", adapterExecutions.size());
            history.put("recentExecutionsShown", recentExecutions.size());
            history.put("timestamp", LocalDateTime.now());
            
            enhancedLogger.adapterMessageEntry("MONITORING", "system");
            enhancedLogger.moduleProcessing("h2h/adapter-monitoring/execution-history");
            
            return history;
            
        } catch (Exception e) {
            logger.error("Error retrieving adapter execution history for adapter {}: {}", adapterId, e.getMessage(), e);
            
            // Return empty history on error
            history.put("adapterId", adapterId);
            history.put("executions", new ArrayList<>());
            history.put("totalExecutions", 0);
            history.put("recentExecutionsShown", 0);
            history.put("error", e.getMessage());
            history.put("timestamp", LocalDateTime.now());
            
            return history;
        }
    }
    
    /**
     * Find all executions that involved a specific adapter (as sender or receiver)
     */
    private List<FlowExecution> findExecutionsForAdapter(UUID adapterId) {
        // Get all executions
        List<FlowExecution> allExecutions = executionRepository.findAll();
        
        List<FlowExecution> adapterExecutions = new ArrayList<>();
        
        for (FlowExecution execution : allExecutions) {
            // Check if this execution used the specified adapter
            if (executionUsedAdapter(execution, adapterId)) {
                adapterExecutions.add(execution);
            }
        }
        
        return adapterExecutions;
    }
    
    /**
     * Check if a flow execution used a specific adapter
     */
    private boolean executionUsedAdapter(FlowExecution execution, UUID adapterId) {
        try {
            // Check execution context for adapter IDs
            Map<String, Object> executionContext = execution.getExecutionContext();
            if (executionContext != null) {
                Object senderAdapterIdObj = executionContext.get("senderAdapterId");
                Object receiverAdapterIdObj = executionContext.get("receiverAdapterId");
                
                if (senderAdapterIdObj instanceof UUID && adapterId.equals(senderAdapterIdObj)) {
                    return true;
                }
                if (receiverAdapterIdObj instanceof UUID && adapterId.equals(receiverAdapterIdObj)) {
                    return true;
                }
                
                // Also check string representations
                if (senderAdapterIdObj instanceof String && adapterId.toString().equals(senderAdapterIdObj)) {
                    return true;
                }
                if (receiverAdapterIdObj instanceof String && adapterId.toString().equals(receiverAdapterIdObj)) {
                    return true;
                }
            }
            
            // Check execution steps for adapter activity
            List<FlowExecutionStep> steps = stepRepository.findByExecutionId(execution.getId());
            for (FlowExecutionStep step : steps) {
                Map<String, Object> stepConfig = step.getStepConfiguration();
                if (stepConfig != null) {
                    Object stepAdapterIdObj = stepConfig.get("adapterId");
                    if (stepAdapterIdObj instanceof UUID && adapterId.equals(stepAdapterIdObj)) {
                        return true;
                    }
                    if (stepAdapterIdObj instanceof String && adapterId.toString().equals(stepAdapterIdObj)) {
                        return true;
                    }
                }
                
                Map<String, Object> outputData = step.getOutputData();
                if (outputData != null) {
                    Object outputAdapterIdObj = outputData.get("adapterId");
                    if (outputAdapterIdObj instanceof UUID && adapterId.equals(outputAdapterIdObj)) {
                        return true;
                    }
                    if (outputAdapterIdObj instanceof String && adapterId.toString().equals(outputAdapterIdObj)) {
                        return true;
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.warn("Error checking if execution {} used adapter {}: {}", execution.getId(), adapterId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Create enterprise-style execution summary for adapter monitoring
     * Format: MSG-20251210-220430-001 (✅ Success - 2.3s - 5 files)
     */
    private Map<String, Object> createEnterpriseExecutionSummary(FlowExecution execution) {
        Map<String, Object> summary = new HashMap<>();
        
        // Generate enterprise message ID from execution ID if not available in context
        String messageId = null;
        if (execution.getExecutionContext() != null) {
            messageId = (String) execution.getExecutionContext().get("messageId");
        }
        if (messageId == null) {
            // Generate enterprise-style ID from execution timestamp
            LocalDateTime startTime = execution.getStartedAt();
            if (startTime != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
                String timestamp = startTime.format(formatter);
                String sequence = String.format("%03d", Math.abs(execution.getId().hashCode() % 1000));
                messageId = "MSG-" + timestamp + "-" + sequence;
            } else {
                messageId = "MSG-" + execution.getId().toString().substring(0, 8).toUpperCase();
            }
        }
        
        // Status with icon
        String statusIcon = execution.getStatusIcon();
        String statusText = execution.getExecutionStatus().name();
        if ("COMPLETED".equals(statusText)) {
            statusText = "Success";
        } else if ("FAILED".equals(statusText)) {
            statusText = "Failed";
        }
        
        // Duration
        String durationText = execution.getFormattedDuration();
        
        // Files processed
        int filesProcessed = execution.getTotalFilesProcessed() != null ? execution.getTotalFilesProcessed() : 0;
        String filesText = filesProcessed + (filesProcessed == 1 ? " file" : " files");
        
        // Create clickable summary
        String clickableSummary = String.format("%s (%s %s - %s - %s)", 
            messageId, statusIcon, statusText, durationText, filesText);
        
        summary.put("executionId", execution.getId());
        summary.put("messageId", messageId);
        summary.put("clickableSummary", clickableSummary);
        summary.put("status", execution.getExecutionStatus().name());
        summary.put("statusIcon", statusIcon);
        summary.put("statusText", statusText);
        summary.put("startedAt", execution.getStartedAt());
        summary.put("duration", durationText);
        summary.put("durationMs", execution.getDurationMs());
        summary.put("filesProcessed", filesProcessed);
        summary.put("filesText", filesText);
        summary.put("flowName", execution.getFlowName());
        summary.put("triggerType", execution.getTriggerType().name());
        summary.put("isClickable", true);
        summary.put("navigateUrl", "/flow-monitoring/executions/" + execution.getId() + "/logs");
        
        if (execution.isTerminal()) {
            summary.put("completedAt", execution.getCompletedAt());
        }
        
        if (execution.isFailed()) {
            summary.put("errorMessage", execution.getErrorMessage());
        }
        
        return summary;
    }
    
    /**
     * Search logs by message ID across all executions for complete traceability
     */
    public Map<String, Object> searchLogsByMessageId(String messageId) {
        logger.debug("Searching logs by message ID: {}", messageId);
        
        Map<String, Object> searchResults = new HashMap<>();
        
        try {
            // Search system logs for the message ID
            List<SystemLog> matchingLogs = systemLogRepository.searchByMessageId(messageId);
            
            // Group logs by execution ID
            Map<UUID, List<SystemLog>> logsByExecution = new HashMap<>();
            Set<UUID> relatedExecutionIds = new HashSet<>();
            
            for (SystemLog log : matchingLogs) {
                if (log.getExecutionId() != null) {
                    relatedExecutionIds.add(log.getExecutionId());
                    logsByExecution.computeIfAbsent(log.getExecutionId(), k -> new ArrayList<>()).add(log);
                }
            }
            
            // Get execution details for each related execution
            List<Map<String, Object>> relatedExecutions = new ArrayList<>();
            for (UUID executionId : relatedExecutionIds) {
                Optional<FlowExecution> executionOpt = executionRepository.findById(executionId);
                if (executionOpt.isPresent()) {
                    FlowExecution execution = executionOpt.get();
                    Map<String, Object> executionInfo = createExecutionSummary(execution);
                    executionInfo.put("logCount", logsByExecution.get(executionId).size());
                    relatedExecutions.add(executionInfo);
                }
            }
            
            // Convert logs to response format
            List<Map<String, Object>> formattedLogs = matchingLogs.stream()
                .map(this::convertLogToResponseFormat)
                .toList();
            
            searchResults.put("messageId", messageId);
            searchResults.put("totalLogs", matchingLogs.size());
            searchResults.put("relatedExecutions", relatedExecutions);
            searchResults.put("logs", formattedLogs);
            searchResults.put("searchTimestamp", LocalDateTime.now());
            
            enhancedLogger.info("Message ID search completed: {} - found {} logs across {} executions", 
                              messageId, matchingLogs.size(), relatedExecutionIds.size());
            
            return searchResults;
            
        } catch (Exception e) {
            logger.error("Error searching logs by message ID {}: {}", messageId, e.getMessage(), e);
            
            // Return empty results on error
            searchResults.put("messageId", messageId);
            searchResults.put("totalLogs", 0);
            searchResults.put("relatedExecutions", new ArrayList<>());
            searchResults.put("logs", new ArrayList<>());
            searchResults.put("error", e.getMessage());
            searchResults.put("searchTimestamp", LocalDateTime.now());
            
            return searchResults;
        }
    }
    
    /**
     * Get comprehensive flow trace showing all steps and timing
     */
    public Map<String, Object> getFlowTrace(UUID executionId) {
        logger.debug("Getting comprehensive flow trace for execution: {}", executionId);
        
        Map<String, Object> trace = new HashMap<>();
        
        try {
            // Get execution details
            Optional<FlowExecution> executionOpt = executionRepository.findById(executionId);
            if (executionOpt.isEmpty()) {
                throw new IllegalArgumentException("Execution not found: " + executionId);
            }
            
            FlowExecution execution = executionOpt.get();
            trace.put("execution", createExecutionDetails(execution));
            
            // Get all steps for this execution
            List<FlowExecutionStep> steps = stepRepository.findByExecutionId(executionId);
            
            // Create timeline entries
            List<Map<String, Object>> timeline = new ArrayList<>();
            
            // Add execution start event
            Map<String, Object> startEvent = new HashMap<>();
            startEvent.put("type", "EXECUTION_START");
            startEvent.put("timestamp", execution.getStartedAt());
            startEvent.put("description", "Flow execution started");
            startEvent.put("status", "INFO");
            timeline.add(startEvent);
            
            // Add step events
            for (FlowExecutionStep step : steps) {
                Map<String, Object> stepEvent = new HashMap<>();
                stepEvent.put("type", "STEP");
                stepEvent.put("stepId", step.getId());
                stepEvent.put("stepName", step.getStepName());
                stepEvent.put("stepType", step.getStepType().name());
                stepEvent.put("timestamp", step.getStartedAt());
                stepEvent.put("status", step.getStepStatus().name());
                stepEvent.put("duration", step.getDurationMs());
                
                if (step.getCompletedAt() != null) {
                    stepEvent.put("completedAt", step.getCompletedAt());
                }
                
                if (step.isFailed() && step.getErrorMessage() != null) {
                    stepEvent.put("errorMessage", step.getErrorMessage());
                }
                
                timeline.add(stepEvent);
            }
            
            // Add execution end event if completed
            if (execution.isTerminal()) {
                Map<String, Object> endEvent = new HashMap<>();
                endEvent.put("type", "EXECUTION_END");
                endEvent.put("timestamp", execution.getCompletedAt());
                endEvent.put("description", "Flow execution " + execution.getExecutionStatus().name().toLowerCase());
                endEvent.put("status", execution.getExecutionStatus().name());
                endEvent.put("duration", execution.getDurationMs());
                timeline.add(endEvent);
            }
            
            // Sort timeline by timestamp
            timeline.sort((a, b) -> {
                LocalDateTime aTime = (LocalDateTime) a.get("timestamp");
                LocalDateTime bTime = (LocalDateTime) b.get("timestamp");
                if (aTime == null && bTime == null) return 0;
                if (aTime == null) return 1;
                if (bTime == null) return -1;
                return aTime.compareTo(bTime);
            });
            
            trace.put("timeline", timeline);
            trace.put("totalSteps", steps.size());
            trace.put("completedSteps", steps.stream().mapToInt(s -> s.isCompleted() ? 1 : 0).sum());
            trace.put("failedSteps", steps.stream().mapToInt(s -> s.isFailed() ? 1 : 0).sum());
            
            // Calculate bottlenecks and performance metrics
            Map<String, Object> performance = new HashMap<>();
            if (!steps.isEmpty()) {
                OptionalLong maxDuration = steps.stream()
                    .filter(s -> s.getDurationMs() != null)
                    .mapToLong(FlowExecutionStep::getDurationMs)
                    .max();
                
                if (maxDuration.isPresent()) {
                    performance.put("slowestStepDuration", maxDuration.getAsLong());
                    
                    // Find the slowest step
                    steps.stream()
                        .filter(s -> s.getDurationMs() != null && s.getDurationMs().equals(maxDuration.getAsLong()))
                        .findFirst()
                        .ifPresent(slowestStep -> {
                            performance.put("slowestStepName", slowestStep.getStepName());
                            performance.put("slowestStepType", slowestStep.getStepType().name());
                        });
                }
                
                double avgDuration = steps.stream()
                    .filter(s -> s.getDurationMs() != null)
                    .mapToLong(FlowExecutionStep::getDurationMs)
                    .average()
                    .orElse(0.0);
                performance.put("averageStepDuration", Math.round(avgDuration));
            }
            trace.put("performance", performance);
            
            return trace;
            
        } catch (Exception e) {
            logger.error("Error getting flow trace for execution {}: {}", executionId, e.getMessage(), e);
            trace.put("error", e.getMessage());
            return trace;
        }
    }
}