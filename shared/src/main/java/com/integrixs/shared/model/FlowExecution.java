package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * FlowExecution entity representing individual flow execution instances
 * Tracks the complete execution lifecycle of an integration flow
 */
public class FlowExecution {
    
    private UUID id;
    private UUID flowId;
    private String flowName; // Snapshot at execution time
    
    // Execution context
    private ExecutionStatus executionStatus;
    private TriggerType triggerType;
    private UUID triggeredBy;
    
    // Timing
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime timeoutAt;
    private Long durationMs;
    
    // Execution data
    private Map<String, Object> payload; // Input data for retry/debugging
    private Map<String, Object> executionContext; // Runtime variables, file paths, etc.
    
    // Results summary
    private Integer totalFilesProcessed;
    private Integer filesSuccessful;
    private Integer filesFailed;
    private Long totalBytesProcessed;
    
    // Error information
    private String errorMessage;
    private Map<String, Object> errorDetails;
    private String errorStepId; // Which step failed
    
    // Retry management
    private Integer retryAttempt;
    private Integer maxRetryAttempts;
    
    // Correlation and relationships
    private UUID correlationId;
    private UUID parentExecutionId; // For retry chains
    
    // Priority and scheduling
    private Integer priority; // 1-10 scale
    private LocalDateTime scheduledFor;
    
    // Enums
    public enum ExecutionStatus {
        PENDING("Execution is queued and waiting to start"),
        RUNNING("Execution is currently in progress"),
        COMPLETED("Execution completed successfully"),
        FAILED("Execution failed with errors"),
        CANCELLED("Execution was cancelled by user"),
        TIMEOUT("Execution exceeded timeout limit"),
        RETRY_PENDING("Execution will be retried");
        
        private final String description;
        
        ExecutionStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
        }
        
        public boolean isActive() {
            return this == PENDING || this == RUNNING;
        }
        
        public boolean canRetry() {
            return this == FAILED || this == TIMEOUT;
        }
    }
    
    public enum TriggerType {
        MANUAL("Manually triggered by user"),
        SCHEDULED("Triggered by schedule"),
        API("Triggered via API call"),
        RETRY("Automatic retry execution"),
        WEBHOOK("Triggered by webhook");
        
        private final String description;
        
        TriggerType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Constructors
    public FlowExecution() {
        this.id = UUID.randomUUID();
        this.executionStatus = ExecutionStatus.PENDING;
        this.startedAt = LocalDateTime.now();
        this.correlationId = UUID.randomUUID();
        this.retryAttempt = 0;
        this.maxRetryAttempts = 3;
        this.priority = 5;
        this.totalFilesProcessed = 0;
        this.filesSuccessful = 0;
        this.filesFailed = 0;
        this.totalBytesProcessed = 0L;
        this.durationMs = 0L;
    }
    
    public FlowExecution(UUID flowId, String flowName, TriggerType triggerType, UUID triggeredBy) {
        this();
        this.flowId = flowId;
        this.flowName = flowName;
        this.triggerType = triggerType;
        this.triggeredBy = triggeredBy;
    }
    
    // Business logic methods
    public void start() {
        if (this.executionStatus == ExecutionStatus.PENDING) {
            this.executionStatus = ExecutionStatus.RUNNING;
            this.startedAt = LocalDateTime.now();
        }
    }
    
    public void complete() {
        if (this.executionStatus == ExecutionStatus.RUNNING) {
            this.executionStatus = ExecutionStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
            calculateDuration();
        }
    }
    
    public void fail(String errorMessage) {
        fail(errorMessage, null, null);
    }
    
    public void fail(String errorMessage, String errorStepId, Map<String, Object> errorDetails) {
        this.executionStatus = ExecutionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.errorStepId = errorStepId;
        this.errorDetails = errorDetails;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }
    
    public void cancel() {
        if (this.executionStatus.isActive()) {
            this.executionStatus = ExecutionStatus.CANCELLED;
            this.completedAt = LocalDateTime.now();
            calculateDuration();
        }
    }
    
    public void timeout() {
        if (this.executionStatus.isActive()) {
            this.executionStatus = ExecutionStatus.TIMEOUT;
            this.completedAt = LocalDateTime.now();
            calculateDuration();
        }
    }
    
    public void markForRetry() {
        if (canRetry()) {
            this.executionStatus = ExecutionStatus.RETRY_PENDING;
            this.retryAttempt++;
        }
    }
    
    public boolean canRetry() {
        return this.executionStatus.canRetry() && 
               this.retryAttempt < this.maxRetryAttempts;
    }
    
    public boolean isRetry() {
        return this.retryAttempt > 0 || this.parentExecutionId != null;
    }
    
    public boolean isRunning() {
        return this.executionStatus == ExecutionStatus.RUNNING;
    }
    
    public boolean isCompleted() {
        return this.executionStatus == ExecutionStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return this.executionStatus == ExecutionStatus.FAILED;
    }
    
    public boolean isTerminal() {
        return this.executionStatus.isTerminal();
    }
    
    public boolean isOverdue() {
        return this.timeoutAt != null && LocalDateTime.now().isAfter(this.timeoutAt);
    }
    
    public double getSuccessRate() {
        if (totalFilesProcessed == 0) {
            return 100.0;
        }
        return (double) filesSuccessful / totalFilesProcessed * 100.0;
    }
    
    public void addFileResult(boolean successful, long bytes) {
        this.totalFilesProcessed++;
        this.totalBytesProcessed += bytes;
        
        if (successful) {
            this.filesSuccessful++;
        } else {
            this.filesFailed++;
        }
    }
    
    private void calculateDuration() {
        if (this.startedAt != null && this.completedAt != null) {
            this.durationMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
    
    public String getFormattedDuration() {
        if (durationMs == null || durationMs == 0) {
            return "0ms";
        }
        
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
    
    public String getFormattedBytes() {
        if (totalBytesProcessed == null || totalBytesProcessed < 1024) {
            return totalBytesProcessed + " B";
        }
        if (totalBytesProcessed < 1024 * 1024) {
            return String.format("%.1f KB", totalBytesProcessed / 1024.0);
        }
        if (totalBytesProcessed < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", totalBytesProcessed / (1024.0 * 1024.0));
        }
        return String.format("%.1f GB", totalBytesProcessed / (1024.0 * 1024.0 * 1024.0));
    }
    
    public String getStatusIcon() {
        switch (executionStatus) {
            case PENDING: return "⏳";
            case RUNNING: return "🟡";
            case COMPLETED: return "✅";
            case FAILED: return "❌";
            case CANCELLED: return "⏹️";
            case TIMEOUT: return "⏰";
            case RETRY_PENDING: return "🔄";
            default: return "❓";
        }
    }
    
    public String getPriorityLabel() {
        if (priority >= 8) return "HIGH";
        if (priority >= 6) return "NORMAL";
        if (priority >= 3) return "LOW";
        return "LOWEST";
    }
    
    /**
     * Calculate execution progress as percentage
     * Based on execution status and file processing progress
     */
    public double calculateProgress() {
        switch (this.executionStatus) {
            case PENDING:
                return 0.0;
            case RUNNING:
                // If we have file processing data, use it for more granular progress
                if (totalFilesProcessed != null && totalFilesProcessed > 0) {
                    // Assume we're processing files progressively
                    // This is a simple calculation - could be enhanced with step-based progress
                    return Math.min(90.0, (double) totalFilesProcessed * 10.0);
                }
                return 25.0; // Generic running progress
            case COMPLETED:
                return 100.0;
            case FAILED:
            case CANCELLED:
            case TIMEOUT:
                // For failed states, show progress up to failure point
                if (totalFilesProcessed != null && totalFilesProcessed > 0) {
                    return Math.min(95.0, (double) totalFilesProcessed * 10.0);
                }
                return 50.0; // Generic failure progress
            case RETRY_PENDING:
                return 10.0; // Minimal progress for retry state
            default:
                return 0.0;
        }
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getFlowId() {
        return flowId;
    }
    
    public void setFlowId(UUID flowId) {
        this.flowId = flowId;
    }
    
    public String getFlowName() {
        return flowName;
    }
    
    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }
    
    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }
    
    public void setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }
    
    public TriggerType getTriggerType() {
        return triggerType;
    }
    
    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }
    
    public UUID getTriggeredBy() {
        return triggeredBy;
    }
    
    public void setTriggeredBy(UUID triggeredBy) {
        this.triggeredBy = triggeredBy;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public LocalDateTime getTimeoutAt() {
        return timeoutAt;
    }
    
    public void setTimeoutAt(LocalDateTime timeoutAt) {
        this.timeoutAt = timeoutAt;
    }
    
    public Long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
    
    public Map<String, Object> getPayload() {
        return payload;
    }
    
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
    
    public Map<String, Object> getExecutionContext() {
        return executionContext;
    }
    
    public void setExecutionContext(Map<String, Object> executionContext) {
        this.executionContext = executionContext;
    }
    
    public Integer getTotalFilesProcessed() {
        return totalFilesProcessed;
    }
    
    public void setTotalFilesProcessed(Integer totalFilesProcessed) {
        this.totalFilesProcessed = totalFilesProcessed;
    }
    
    public Integer getFilesSuccessful() {
        return filesSuccessful;
    }
    
    public void setFilesSuccessful(Integer filesSuccessful) {
        this.filesSuccessful = filesSuccessful;
    }
    
    public Integer getFilesFailed() {
        return filesFailed;
    }
    
    public void setFilesFailed(Integer filesFailed) {
        this.filesFailed = filesFailed;
    }
    
    public Long getTotalBytesProcessed() {
        return totalBytesProcessed;
    }
    
    public void setTotalBytesProcessed(Long totalBytesProcessed) {
        this.totalBytesProcessed = totalBytesProcessed;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Map<String, Object> getErrorDetails() {
        return errorDetails;
    }
    
    public void setErrorDetails(Map<String, Object> errorDetails) {
        this.errorDetails = errorDetails;
    }
    
    public String getErrorStepId() {
        return errorStepId;
    }
    
    public void setErrorStepId(String errorStepId) {
        this.errorStepId = errorStepId;
    }
    
    public Integer getRetryAttempt() {
        return retryAttempt;
    }
    
    public void setRetryAttempt(Integer retryAttempt) {
        this.retryAttempt = retryAttempt;
    }
    
    public Integer getMaxRetryAttempts() {
        return maxRetryAttempts;
    }
    
    public void setMaxRetryAttempts(Integer maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }
    
    public UUID getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }
    
    public UUID getParentExecutionId() {
        return parentExecutionId;
    }
    
    public void setParentExecutionId(UUID parentExecutionId) {
        this.parentExecutionId = parentExecutionId;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public LocalDateTime getScheduledFor() {
        return scheduledFor;
    }
    
    public void setScheduledFor(LocalDateTime scheduledFor) {
        this.scheduledFor = scheduledFor;
    }
    
    @Override
    public String toString() {
        return "FlowExecution{" +
                "id=" + id +
                ", flowName='" + flowName + '\'' +
                ", status=" + executionStatus +
                ", triggerType=" + triggerType +
                ", filesProcessed=" + totalFilesProcessed +
                ", duration=" + getFormattedDuration() +
                ", startedAt=" + startedAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowExecution that = (FlowExecution) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}