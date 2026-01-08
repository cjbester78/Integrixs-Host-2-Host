package com.integrixs.backend.dto.response;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable response object for execution summary data.
 * Contains essential execution information for list views and summaries.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class ExecutionSummaryResponse {
    
    private final UUID id;
    private final UUID flowId;
    private final String flowName;
    private final String status;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private final long durationMs;
    private final int totalSteps;
    private final int completedSteps;
    private final int failedSteps;
    private final String triggeredBy;
    private final boolean hasErrors;
    private final String lastError;
    private final Map<String, Object> metadata;
    
    private ExecutionSummaryResponse(Builder builder) {
        this.id = builder.id;
        this.flowId = builder.flowId;
        this.flowName = builder.flowName;
        this.status = builder.status;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.durationMs = builder.durationMs;
        this.totalSteps = builder.totalSteps;
        this.completedSteps = builder.completedSteps;
        this.failedSteps = builder.failedSteps;
        this.triggeredBy = builder.triggeredBy;
        this.hasErrors = builder.hasErrors;
        this.lastError = builder.lastError;
        this.metadata = builder.metadata;
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getFlowId() { return flowId; }
    public String getFlowName() { return flowName; }
    public String getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public long getDurationMs() { return durationMs; }
    public int getTotalSteps() { return totalSteps; }
    public int getCompletedSteps() { return completedSteps; }
    public int getFailedSteps() { return failedSteps; }
    public String getTriggeredBy() { return triggeredBy; }
    public boolean isHasErrors() { return hasErrors; }
    public String getLastError() { return lastError; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    /**
     * Check if execution is currently running.
     */
    public boolean isRunning() {
        return "RUNNING".equalsIgnoreCase(status) || "IN_PROGRESS".equalsIgnoreCase(status);
    }
    
    /**
     * Check if execution completed successfully.
     */
    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);
    }
    
    /**
     * Check if execution failed.
     */
    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status);
    }
    
    /**
     * Check if execution was cancelled.
     */
    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status);
    }
    
    /**
     * Get completion percentage.
     */
    public double getCompletionPercentage() {
        if (totalSteps == 0) return 0.0;
        return (double) completedSteps / totalSteps * 100.0;
    }
    
    /**
     * Get failure percentage.
     */
    public double getFailurePercentage() {
        if (totalSteps == 0) return 0.0;
        return (double) failedSteps / totalSteps * 100.0;
    }
    
    /**
     * Get formatted duration.
     */
    public String getFormattedDuration() {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else if (durationMs < 3600000) {
            long minutes = durationMs / 60000;
            long seconds = (durationMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        } else {
            long hours = durationMs / 3600000;
            long minutes = (durationMs % 3600000) / 60000;
            return String.format("%dh %dm", hours, minutes);
        }
    }
    
    /**
     * Get status badge color for UI.
     */
    public String getStatusColor() {
        switch (status.toUpperCase()) {
            case "COMPLETED":
            case "SUCCESS":
                return "green";
            case "RUNNING":
            case "IN_PROGRESS":
                return "blue";
            case "FAILED":
            case "ERROR":
                return "red";
            case "CANCELLED":
            case "CANCELED":
                return "orange";
            case "PENDING":
                return "gray";
            default:
                return "gray";
        }
    }
    
    /**
     * Create builder instance for constructing ExecutionSummaryResponse.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ExecutionSummaryResponse following builder pattern.
     */
    public static class Builder {
        private UUID id;
        private UUID flowId;
        private String flowName;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private long durationMs;
        private int totalSteps;
        private int completedSteps;
        private int failedSteps;
        private String triggeredBy;
        private boolean hasErrors;
        private String lastError;
        private Map<String, Object> metadata;
        
        private Builder() {}
        
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public Builder flowId(UUID flowId) {
            this.flowId = flowId;
            return this;
        }
        
        public Builder flowName(String flowName) {
            this.flowName = flowName;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        
        public Builder startedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
            return this;
        }
        
        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }
        
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }
        
        public Builder totalSteps(int totalSteps) {
            this.totalSteps = totalSteps;
            return this;
        }
        
        public Builder completedSteps(int completedSteps) {
            this.completedSteps = completedSteps;
            return this;
        }
        
        public Builder failedSteps(int failedSteps) {
            this.failedSteps = failedSteps;
            return this;
        }
        
        public Builder triggeredBy(String triggeredBy) {
            this.triggeredBy = triggeredBy;
            return this;
        }
        
        public Builder hasErrors(boolean hasErrors) {
            this.hasErrors = hasErrors;
            return this;
        }
        
        public Builder lastError(String lastError) {
            this.lastError = lastError;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public ExecutionSummaryResponse build() {
            return new ExecutionSummaryResponse(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionSummaryResponse{id=%s, flowName='%s', status='%s', " +
                           "completionPercentage=%.1f%%, durationMs=%d, hasErrors=%b}", 
                           id, flowName, status, getCompletionPercentage(), durationMs, hasErrors);
    }
}