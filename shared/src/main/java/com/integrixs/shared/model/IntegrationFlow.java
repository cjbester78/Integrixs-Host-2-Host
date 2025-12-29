package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * IntegrationFlow entity representing visual flow definitions
 * Links adapters and utilities in configurable workflows
 */
public class IntegrationFlow {
    
    private UUID id;
    private String name;
    private String description;
    private String bankName;
    
    // Flow definition and metadata
    private Map<String, Object> flowDefinition; // JSON structure with nodes and connections
    private Integer flowVersion;
    private String flowType; // STANDARD, PARALLEL, CONDITIONAL
    
    // Execution settings
    private Integer maxParallelExecutions;
    private Integer timeoutMinutes;
    private Map<String, Object> retryPolicy; // JSON retry configuration
    
    // Performance tracking
    private Long totalExecutions;
    private Long successfulExecutions;
    private Long failedExecutions;
    private Long averageExecutionTimeMs;
    
    // Scheduling
    private Boolean scheduleEnabled;
    private String scheduleCron;
    private LocalDateTime nextScheduledRun;
    
    // Status and control
    private Boolean active;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    
    // Enums for flow types
    public enum FlowType {
        STANDARD("Standard sequential flow"),
        PARALLEL("Parallel execution flow"),
        CONDITIONAL("Conditional branching flow"),
        LOOP("Loop/iteration flow"),
        BATCH("Batch processing flow");
        
        private final String description;
        
        FlowType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Constructors
    public IntegrationFlow() {
        this.flowVersion = 1;
        this.flowType = FlowType.STANDARD.name();
        this.maxParallelExecutions = 1;
        this.timeoutMinutes = 60;
        this.totalExecutions = 0L;
        this.successfulExecutions = 0L;
        this.failedExecutions = 0L;
        this.averageExecutionTimeMs = 0L;
        this.scheduleEnabled = false;
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public IntegrationFlow(String name, String description, Map<String, Object> flowDefinition, UUID createdBy) {
        this();
        this.name = name;
        this.description = description;
        this.flowDefinition = flowDefinition;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }
    
    public IntegrationFlow(String name, String description, String bankName, Map<String, Object> flowDefinition, UUID createdBy) {
        this();
        this.name = name;
        this.description = description;
        this.bankName = bankName;
        this.flowDefinition = flowDefinition;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }
    
    // Business logic methods
    public boolean isScheduled() {
        return scheduleEnabled != null && scheduleEnabled && scheduleCron != null;
    }
    
    public boolean isReadyForExecution() {
        return active != null && active && flowDefinition != null;
    }
    
    public boolean supportsParallelExecution() {
        return maxParallelExecutions != null && maxParallelExecutions > 1;
    }
    
    public double getSuccessRate() {
        if (totalExecutions == null || totalExecutions == 0) {
            return 100.0;
        }
        return (double) (successfulExecutions != null ? successfulExecutions : 0) / totalExecutions * 100.0;
    }
    
    public double getFailureRate() {
        return 100.0 - getSuccessRate();
    }
    
    public String getFormattedAverageExecutionTime() {
        if (averageExecutionTimeMs == null || averageExecutionTimeMs == 0) {
            return "0ms";
        }
        
        if (averageExecutionTimeMs < 1000) {
            return averageExecutionTimeMs + "ms";
        } else if (averageExecutionTimeMs < 60000) {
            return String.format("%.1fs", averageExecutionTimeMs / 1000.0);
        } else {
            long minutes = averageExecutionTimeMs / 60000;
            long seconds = (averageExecutionTimeMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    public void incrementVersion() {
        this.flowVersion = (this.flowVersion != null ? this.flowVersion : 0) + 1;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void recordExecution(long executionTimeMs, boolean successful) {
        this.totalExecutions = (this.totalExecutions != null ? this.totalExecutions : 0) + 1;
        
        if (successful) {
            this.successfulExecutions = (this.successfulExecutions != null ? this.successfulExecutions : 0) + 1;
        } else {
            this.failedExecutions = (this.failedExecutions != null ? this.failedExecutions : 0) + 1;
        }
        
        // Update average execution time using weighted average
        if (this.averageExecutionTimeMs == null || this.averageExecutionTimeMs == 0) {
            this.averageExecutionTimeMs = executionTimeMs;
        } else {
            // 80% previous average + 20% current execution
            this.averageExecutionTimeMs = (long) (this.averageExecutionTimeMs * 0.8 + executionTimeMs * 0.2);
        }
        
        this.updatedAt = LocalDateTime.now();
    }
    
    public void updateNextScheduledRun() {
        if (isScheduled()) {
            // In a real implementation, this would calculate the next run based on the cron expression
            // For now, just set it to next hour as placeholder
            this.nextScheduledRun = LocalDateTime.now().plusHours(1);
        } else {
            this.nextScheduledRun = null;
        }
    }
    
    public boolean isOverdue() {
        return isScheduled() && nextScheduledRun != null && nextScheduledRun.isBefore(LocalDateTime.now());
    }
    
    public String getDisplayName() {
        return String.format("%s (v%d)", name, flowVersion);
    }
    
    public String getStatusIcon() {
        if (!isReadyForExecution()) {
            return "⚠️"; // Warning - not ready
        } else if (isScheduled()) {
            return "⏰"; // Scheduled
        } else {
            return "▶️"; // Ready to run
        }
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getBankName() {
        return bankName;
    }
    
    public void setBankName(String bankName) {
        this.bankName = bankName;
    }
    
    public Map<String, Object> getFlowDefinition() {
        return flowDefinition;
    }
    
    public void setFlowDefinition(Map<String, Object> flowDefinition) {
        this.flowDefinition = flowDefinition;
        this.updatedAt = LocalDateTime.now();
    }
    
    public Integer getFlowVersion() {
        return flowVersion;
    }
    
    public void setFlowVersion(Integer flowVersion) {
        this.flowVersion = flowVersion;
    }
    
    public String getFlowType() {
        return flowType;
    }
    
    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }
    
    public Integer getMaxParallelExecutions() {
        return maxParallelExecutions;
    }
    
    public void setMaxParallelExecutions(Integer maxParallelExecutions) {
        this.maxParallelExecutions = maxParallelExecutions;
    }
    
    public Integer getTimeoutMinutes() {
        return timeoutMinutes;
    }
    
    public void setTimeoutMinutes(Integer timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }
    
    public Map<String, Object> getRetryPolicy() {
        return retryPolicy;
    }
    
    public void setRetryPolicy(Map<String, Object> retryPolicy) {
        this.retryPolicy = retryPolicy;
    }
    
    public Long getTotalExecutions() {
        return totalExecutions;
    }
    
    public void setTotalExecutions(Long totalExecutions) {
        this.totalExecutions = totalExecutions;
    }
    
    public Long getSuccessfulExecutions() {
        return successfulExecutions;
    }
    
    public void setSuccessfulExecutions(Long successfulExecutions) {
        this.successfulExecutions = successfulExecutions;
    }
    
    public Long getFailedExecutions() {
        return failedExecutions;
    }
    
    public void setFailedExecutions(Long failedExecutions) {
        this.failedExecutions = failedExecutions;
    }
    
    public Long getAverageExecutionTimeMs() {
        return averageExecutionTimeMs;
    }
    
    public void setAverageExecutionTimeMs(Long averageExecutionTimeMs) {
        this.averageExecutionTimeMs = averageExecutionTimeMs;
    }
    
    public Boolean getScheduleEnabled() {
        return scheduleEnabled;
    }
    
    public void setScheduleEnabled(Boolean scheduleEnabled) {
        this.scheduleEnabled = scheduleEnabled;
        if (!scheduleEnabled) {
            this.nextScheduledRun = null;
        }
    }
    
    public String getScheduleCron() {
        return scheduleCron;
    }
    
    public void setScheduleCron(String scheduleCron) {
        this.scheduleCron = scheduleCron;
        updateNextScheduledRun();
    }
    
    public LocalDateTime getNextScheduledRun() {
        return nextScheduledRun;
    }
    
    public void setNextScheduledRun(LocalDateTime nextScheduledRun) {
        this.nextScheduledRun = nextScheduledRun;
    }
    
    public Boolean getActive() {
        return active;
    }
    
    public void setActive(Boolean active) {
        this.active = active;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return active != null && active;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
    
    public UUID getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }
    
    
    @Override
    public String toString() {
        return "IntegrationFlow{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", flowType='" + flowType + '\'' +
                ", version=" + flowVersion +
                ", active=" + active +
                ", totalExecutions=" + totalExecutions +
                ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                ", createdAt=" + createdAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntegrationFlow that = (IntegrationFlow) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}