package com.integrixs.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable response object for detailed execution information.
 * Contains comprehensive execution data including steps, logs, and metrics.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class ExecutionDetailsResponse {
    
    private final UUID id;
    private final UUID flowId;
    private final String flowName;
    private final String flowVersion;
    private final String status;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private final long durationMs;
    private final UUID triggeredBy;
    private final String triggerType;
    private final Map<String, Object> inputParameters;
    private final Map<String, Object> outputResults;
    private final List<ExecutionStepSummary> steps;
    private final List<String> errors;
    private final List<String> warnings;
    private final ExecutionMetrics metrics;
    private final Map<String, Object> metadata;
    private final String correlationId;
    
    private ExecutionDetailsResponse(Builder builder) {
        this.id = builder.id;
        this.flowId = builder.flowId;
        this.flowName = builder.flowName;
        this.flowVersion = builder.flowVersion;
        this.status = builder.status;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.durationMs = builder.durationMs;
        this.triggeredBy = builder.triggeredBy;
        this.triggerType = builder.triggerType;
        this.inputParameters = builder.inputParameters;
        this.outputResults = builder.outputResults;
        this.steps = builder.steps;
        this.errors = builder.errors;
        this.warnings = builder.warnings;
        this.metrics = builder.metrics;
        this.metadata = builder.metadata;
        this.correlationId = builder.correlationId;
    }
    
    // Getters
    public UUID getId() { return id; }
    public UUID getFlowId() { return flowId; }
    public String getFlowName() { return flowName; }
    public String getFlowVersion() { return flowVersion; }
    public String getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public long getDurationMs() { return durationMs; }
    public UUID getTriggeredBy() { return triggeredBy; }
    public String getTriggerType() { return triggerType; }
    public Map<String, Object> getInputParameters() { return inputParameters; }
    public Map<String, Object> getOutputResults() { return outputResults; }
    public List<ExecutionStepSummary> getSteps() { return steps; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public ExecutionMetrics getMetrics() { return metrics; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getCorrelationId() { return correlationId; }
    
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
     * Check if execution has errors.
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    /**
     * Check if execution has warnings.
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    /**
     * Check if execution has input parameters.
     */
    public boolean hasInputParameters() {
        return inputParameters != null && !inputParameters.isEmpty();
    }
    
    /**
     * Check if execution has output results.
     */
    public boolean hasOutputResults() {
        return outputResults != null && !outputResults.isEmpty();
    }
    
    /**
     * Get total step count.
     */
    public int getTotalSteps() {
        return steps != null ? steps.size() : 0;
    }
    
    /**
     * Get completed step count.
     */
    public int getCompletedSteps() {
        if (steps == null) return 0;
        return (int) steps.stream().filter(step -> "COMPLETED".equalsIgnoreCase(step.getStatus())).count();
    }
    
    /**
     * Get failed step count.
     */
    public int getFailedSteps() {
        if (steps == null) return 0;
        return (int) steps.stream().filter(step -> "FAILED".equalsIgnoreCase(step.getStatus())).count();
    }
    
    /**
     * Get completion percentage.
     */
    public double getCompletionPercentage() {
        int total = getTotalSteps();
        if (total == 0) return 0.0;
        return (double) getCompletedSteps() / total * 100.0;
    }
    
    /**
     * Get formatted duration.
     */
    public String getFormattedDuration() {
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
     * Get current step (first non-completed step).
     */
    public ExecutionStepSummary getCurrentStep() {
        if (steps == null) return null;
        
        return steps.stream()
            .filter(step -> !"COMPLETED".equalsIgnoreCase(step.getStatus()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Create builder instance for constructing ExecutionDetailsResponse.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ExecutionDetailsResponse following builder pattern.
     */
    public static class Builder {
        private UUID id;
        private UUID flowId;
        private String flowName;
        private String flowVersion;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private long durationMs;
        private UUID triggeredBy;
        private String triggerType;
        private Map<String, Object> inputParameters;
        private Map<String, Object> outputResults;
        private List<ExecutionStepSummary> steps;
        private List<String> errors;
        private List<String> warnings;
        private ExecutionMetrics metrics;
        private Map<String, Object> metadata;
        private String correlationId;
        
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
        
        public Builder flowVersion(String flowVersion) {
            this.flowVersion = flowVersion;
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
        
        public Builder triggeredBy(UUID triggeredBy) {
            this.triggeredBy = triggeredBy;
            return this;
        }
        
        public Builder triggerType(String triggerType) {
            this.triggerType = triggerType;
            return this;
        }
        
        public Builder inputParameters(Map<String, Object> inputParameters) {
            this.inputParameters = inputParameters;
            return this;
        }
        
        public Builder outputResults(Map<String, Object> outputResults) {
            this.outputResults = outputResults;
            return this;
        }
        
        public Builder steps(List<ExecutionStepSummary> steps) {
            this.steps = steps;
            return this;
        }
        
        public Builder errors(List<String> errors) {
            this.errors = errors;
            return this;
        }
        
        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }
        
        public Builder metrics(ExecutionMetrics metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public ExecutionDetailsResponse build() {
            return new ExecutionDetailsResponse(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionDetailsResponse{id=%s, flowName='%s', status='%s', " +
                           "totalSteps=%d, completedSteps=%d, hasErrors=%b, correlationId='%s'}", 
                           id, flowName, status, getTotalSteps(), getCompletedSteps(), hasErrors(), correlationId);
    }
}

/**
 * Immutable execution step summary for inclusion in execution details.
 */
class ExecutionStepSummary {
    private final UUID id;
    private final String name;
    private final String type;
    private final String status;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private final long durationMs;
    private final String error;
    
    public ExecutionStepSummary(UUID id, String name, String type, String status, 
                               LocalDateTime startedAt, LocalDateTime completedAt, 
                               long durationMs, String error) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
        this.error = error;
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public long getDurationMs() { return durationMs; }
    public String getError() { return error; }
}

/**
 * Immutable execution metrics for performance tracking.
 */
class ExecutionMetrics {
    private final long totalBytesProcessed;
    private final long totalRecordsProcessed;
    private final int adapterExecutions;
    private final double avgStepDurationMs;
    private final long peakMemoryUsage;
    private final int retryCount;
    
    public ExecutionMetrics(long totalBytesProcessed, long totalRecordsProcessed, 
                           int adapterExecutions, double avgStepDurationMs, 
                           long peakMemoryUsage, int retryCount) {
        this.totalBytesProcessed = totalBytesProcessed;
        this.totalRecordsProcessed = totalRecordsProcessed;
        this.adapterExecutions = adapterExecutions;
        this.avgStepDurationMs = avgStepDurationMs;
        this.peakMemoryUsage = peakMemoryUsage;
        this.retryCount = retryCount;
    }
    
    // Getters
    public long getTotalBytesProcessed() { return totalBytesProcessed; }
    public long getTotalRecordsProcessed() { return totalRecordsProcessed; }
    public int getAdapterExecutions() { return adapterExecutions; }
    public double getAvgStepDurationMs() { return avgStepDurationMs; }
    public long getPeakMemoryUsage() { return peakMemoryUsage; }
    public int getRetryCount() { return retryCount; }
}