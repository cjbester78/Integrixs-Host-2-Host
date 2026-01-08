package com.integrixs.shared.model;

import com.integrixs.shared.model.value.ExecutionMetrics;
import com.integrixs.shared.model.value.FlowConfiguration;
import com.integrixs.shared.model.value.ScheduleSettings;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * IntegrationFlow entity representing visual flow definitions.
 * Links adapters and utilities in configurable workflows.
 * Enhanced with proper encapsulation and immutable value objects following OOP principles.
 */
public class IntegrationFlow {
    
    private UUID id;
    private String name;
    private String description;
    private String bankName;
    
    // Enhanced with value objects for better encapsulation
    private FlowConfiguration configuration;
    private ExecutionMetrics executionMetrics;
    private Integer flowVersion;
    
    // Status and control
    private Boolean active;
    
    // Import tracking
    private UUID originalFlowId;  // ID of the original flow when imported
    
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
        this.configuration = FlowConfiguration.defaultConfiguration();
        this.executionMetrics = ExecutionMetrics.empty();
        this.active = true;
        this.createdAt = LocalDateTime.now();
        // updatedAt and updatedBy should be NULL on creation - only set on actual updates
        this.updatedAt = null;
        this.updatedBy = null;
    }
    
    public IntegrationFlow(String name, String description, Map<String, Object> flowDefinition, UUID createdBy) {
        this();
        this.name = validateName(name);
        this.description = description;
        this.configuration = FlowConfiguration.builder()
            .flowDefinition(flowDefinition)
            .build();
        this.createdBy = Objects.requireNonNull(createdBy, "Created by cannot be null");
        // Do not set updatedBy on creation - only set on actual updates
    }
    
    public IntegrationFlow(String name, String description, String bankName, Map<String, Object> flowDefinition, UUID createdBy) {
        this(name, description, flowDefinition, createdBy);
        this.bankName = bankName;
    }
    
    /**
     * Validate flow name.
     */
    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Flow name cannot be null or empty");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Flow name cannot exceed 100 characters");
        }
        return name.trim();
    }
    
    // Business logic methods using value objects
    public boolean isScheduled() {
        return configuration != null && configuration.isScheduled();
    }
    
    public boolean isReadyForExecution() {
        return isActive() && configuration != null && configuration.isReadyForExecution();
    }
    
    public boolean supportsParallelExecution() {
        return configuration != null && configuration.supportsParallelExecution();
    }
    
    public double getSuccessRate() {
        return executionMetrics != null ? executionMetrics.getSuccessRate() : 100.0;
    }
    
    public double getFailureRate() {
        return executionMetrics != null ? executionMetrics.getFailureRate() : 0.0;
    }
    
    public String getFormattedAverageExecutionTime() {
        return executionMetrics != null ? executionMetrics.getFormattedAverageExecutionTime() : "0ms";
    }
    
    public void incrementVersion() {
        this.flowVersion = (this.flowVersion != null ? this.flowVersion : 0) + 1;
    }
    
    public void recordExecution(long executionTimeMs, boolean successful) {
        if (this.executionMetrics == null) {
            this.executionMetrics = ExecutionMetrics.empty();
        }
        this.executionMetrics = this.executionMetrics.recordExecution(executionTimeMs, successful);
    }
    
    public void updateNextScheduledRun() {
        if (configuration != null && configuration.isScheduled()) {
            ScheduleSettings schedule = configuration.getScheduleSettings();
            // Update schedule with next run time (placeholder implementation)
            ScheduleSettings updatedSchedule = schedule.withNextRun(LocalDateTime.now().plusHours(1));
            this.configuration = configuration.withScheduleSettings(updatedSchedule);
        }
    }
    
    public boolean isOverdue() {
        return configuration != null && 
               configuration.getScheduleSettings().isOverdue();
    }
    
    /**
     * Mark entity as updated by specified user. Should be called for all business logic updates.
     * This properly maintains the audit trail for UPDATE operations.
     */
    public void markAsUpdated(UUID updatedBy) {
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = Objects.requireNonNull(updatedBy, "Updated by cannot be null");
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
    
    // Enhanced getters using value objects with defensive copying
    public Map<String, Object> getFlowDefinition() {
        return configuration != null ? configuration.getFlowDefinition() : Collections.emptyMap();
    }
    
    public void setFlowDefinition(Map<String, Object> flowDefinition) {
        if (this.configuration == null) {
            this.configuration = FlowConfiguration.builder().flowDefinition(flowDefinition).build();
        } else {
            this.configuration = FlowConfiguration.builder().from(configuration).flowDefinition(flowDefinition).build();
        }
    }
    
    public Integer getFlowVersion() {
        return flowVersion;
    }
    
    public void setFlowVersion(Integer flowVersion) {
        this.flowVersion = flowVersion;
    }
    
    public String getFlowType() {
        return configuration != null ? configuration.getFlowType().name() : FlowConfiguration.FlowType.STANDARD.name();
    }
    
    public void setFlowType(String flowType) {
        FlowConfiguration.FlowType type;
        try {
            type = FlowConfiguration.FlowType.valueOf(flowType.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = FlowConfiguration.FlowType.STANDARD;
        }
        
        if (this.configuration == null) {
            this.configuration = FlowConfiguration.builder().flowType(type).build();
        } else {
            this.configuration = this.configuration.withFlowType(type);
        }
    }
    
    public Integer getMaxParallelExecutions() {
        return configuration != null ? configuration.getMaxParallelExecutions() : 1;
    }
    
    public void setMaxParallelExecutions(Integer maxParallelExecutions) {
        if (maxParallelExecutions != null && maxParallelExecutions > 0) {
            if (this.configuration == null) {
                this.configuration = FlowConfiguration.builder().maxParallelExecutions(maxParallelExecutions).build();
            } else {
                this.configuration = this.configuration.withMaxParallelExecutions(maxParallelExecutions);
            }
            }
    }
    
    public Integer getTimeoutMinutes() {
        return configuration != null ? configuration.getTimeoutMinutes() : 60;
    }
    
    public void setTimeoutMinutes(Integer timeoutMinutes) {
        if (timeoutMinutes != null && timeoutMinutes > 0) {
            if (this.configuration == null) {
                this.configuration = FlowConfiguration.builder().timeoutMinutes(timeoutMinutes).build();
            } else {
                this.configuration = this.configuration.withTimeout(timeoutMinutes);
            }
            }
    }
    
    public Map<String, Object> getRetryPolicy() {
        return configuration != null ? configuration.getRetryPolicy() : Collections.emptyMap();
    }
    
    public void setRetryPolicy(Map<String, Object> retryPolicy) {
        if (this.configuration == null) {
            this.configuration = FlowConfiguration.builder().retryPolicy(retryPolicy).build();
        } else {
            this.configuration = FlowConfiguration.builder().from(configuration).retryPolicy(retryPolicy).build();
        }
    }
    
    public Long getTotalExecutions() {
        return executionMetrics != null ? executionMetrics.getTotalExecutions() : 0L;
    }
    
    public void setTotalExecutions(Long totalExecutions) {
        // Setting individual metrics should update the value object
        if (executionMetrics == null) {
            executionMetrics = ExecutionMetrics.empty();
        }
        // This is for legacy compatibility - create new metrics with updated total
        this.executionMetrics = ExecutionMetrics.builder()
            .totalExecutions(totalExecutions != null ? totalExecutions : 0L)
            .successfulExecutions(executionMetrics.getSuccessfulExecutions())
            .failedExecutions(executionMetrics.getFailedExecutions())
            .averageExecutionTimeMs(executionMetrics.getAverageExecutionTimeMs())
            .build();
    }
    
    public Long getSuccessfulExecutions() {
        return executionMetrics != null ? executionMetrics.getSuccessfulExecutions() : 0L;
    }
    
    public void setSuccessfulExecutions(Long successfulExecutions) {
        if (executionMetrics == null) {
            executionMetrics = ExecutionMetrics.empty();
        }
        this.executionMetrics = ExecutionMetrics.builder()
            .totalExecutions(executionMetrics.getTotalExecutions())
            .successfulExecutions(successfulExecutions != null ? successfulExecutions : 0L)
            .failedExecutions(executionMetrics.getFailedExecutions())
            .averageExecutionTimeMs(executionMetrics.getAverageExecutionTimeMs())
            .build();
    }
    
    public Long getFailedExecutions() {
        return executionMetrics != null ? executionMetrics.getFailedExecutions() : 0L;
    }
    
    public void setFailedExecutions(Long failedExecutions) {
        if (executionMetrics == null) {
            executionMetrics = ExecutionMetrics.empty();
        }
        this.executionMetrics = ExecutionMetrics.builder()
            .totalExecutions(executionMetrics.getTotalExecutions())
            .successfulExecutions(executionMetrics.getSuccessfulExecutions())
            .failedExecutions(failedExecutions != null ? failedExecutions : 0L)
            .averageExecutionTimeMs(executionMetrics.getAverageExecutionTimeMs())
            .build();
    }
    
    public Long getAverageExecutionTimeMs() {
        return executionMetrics != null ? executionMetrics.getAverageExecutionTimeMs() : 0L;
    }
    
    public void setAverageExecutionTimeMs(Long averageExecutionTimeMs) {
        if (executionMetrics == null) {
            executionMetrics = ExecutionMetrics.empty();
        }
        this.executionMetrics = ExecutionMetrics.builder()
            .totalExecutions(executionMetrics.getTotalExecutions())
            .successfulExecutions(executionMetrics.getSuccessfulExecutions())
            .failedExecutions(executionMetrics.getFailedExecutions())
            .averageExecutionTimeMs(averageExecutionTimeMs != null ? averageExecutionTimeMs : 0L)
            .build();
    }
    
    public Boolean getScheduleEnabled() {
        return configuration != null ? configuration.getScheduleSettings().isEnabled() : false;
    }
    
    public void setScheduleEnabled(Boolean scheduleEnabled) {
        ScheduleSettings newSchedule = scheduleEnabled != null && scheduleEnabled ?
            ScheduleSettings.enabled("0 0 2 * * ?") : ScheduleSettings.disabled();
        
        if (this.configuration == null) {
            this.configuration = FlowConfiguration.builder().scheduleSettings(newSchedule).build();
        } else {
            this.configuration = this.configuration.withScheduleSettings(newSchedule);
        }
    }
    
    public String getScheduleCron() {
        return configuration != null ? 
            configuration.getScheduleSettings().getCronExpression().orElse(null) : null;
    }
    
    public void setScheduleCron(String scheduleCron) {
        if (scheduleCron != null && !scheduleCron.trim().isEmpty()) {
            ScheduleSettings newSchedule = ScheduleSettings.enabled(scheduleCron.trim());
            if (this.configuration == null) {
                this.configuration = FlowConfiguration.builder().scheduleSettings(newSchedule).build();
            } else {
                this.configuration = this.configuration.withScheduleSettings(newSchedule);
            }
            }
    }
    
    public LocalDateTime getNextScheduledRun() {
        return configuration != null ? 
            configuration.getScheduleSettings().getNextScheduledRun().orElse(null) : null;
    }
    
    public void setNextScheduledRun(LocalDateTime nextScheduledRun) {
        if (configuration != null && configuration.isScheduled()) {
            ScheduleSettings currentSchedule = configuration.getScheduleSettings();
            ScheduleSettings updatedSchedule = currentSchedule.withNextRun(nextScheduledRun);
            this.configuration = this.configuration.withScheduleSettings(updatedSchedule);
            }
    }
    
    public Boolean getActive() {
        return active;
    }
    
    public void setActive(Boolean active) {
        this.active = active;
        this.updatedAt = LocalDateTime.now();
    }
    
    public UUID getOriginalFlowId() {
        return originalFlowId;
    }
    
    public void setOriginalFlowId(UUID originalFlowId) {
        this.originalFlowId = originalFlowId;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return active != null && active;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Sets the creation timestamp. Should only be used during INSERT operations by persistence layer.
     * NOTE: For persistence layer use only - not for business logic.
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Sets the update timestamp. Should only be used during UPDATE operations by persistence layer.
     * NOTE: For persistence layer use only - not for business logic.
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
    /**
     * Sets the user who created this entity. Should only be used during INSERT operations by persistence layer.
     * NOTE: For persistence layer use only - not for business logic.
     */
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
    
    public UUID getUpdatedBy() {
        return updatedBy;
    }
    
    /**
     * Sets the user who last updated this entity. Should only be used during UPDATE operations by persistence layer.
     * NOTE: For persistence layer use only - not for business logic.
     */
    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
    
    
    @Override
    public String toString() {
        return "IntegrationFlow{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", flowType='" + getFlowType() + '\'' +
                ", version=" + flowVersion +
                ", active=" + active +
                ", totalExecutions=" + getTotalExecutions() +
                ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy=" + createdBy +
                ", updatedBy=" + updatedBy +
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