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
    
    // Enhanced with value objects for better encapsulation
    private FlowConfiguration configuration;
    private ExecutionMetrics executionMetrics;
    private Integer flowVersion;
    
    // Status and control
    private Boolean active;
    
    // Package context
    private UUID packageId;  // Package this flow belongs to
    private UUID deployedFromPackageId;  // Original package for audit trail
    
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
    
    public IntegrationFlow(String name, String description, Map<String, Object> flowDefinition, UUID packageId, UUID createdBy) {
        this(name, description, flowDefinition, createdBy);
        this.packageId = Objects.requireNonNull(packageId, "Package ID cannot be null");
        this.deployedFromPackageId = packageId; // Initially same as package ID
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
            
            // Calculate next run time based on cron expression
            LocalDateTime nextRun = calculateNextRunTime(schedule);
            
            if (nextRun != null) {
                ScheduleSettings updatedSchedule = schedule.withNextRun(nextRun);
                this.configuration = configuration.withScheduleSettings(updatedSchedule);
            }
        }
    }
    
    private LocalDateTime calculateNextRunTime(ScheduleSettings schedule) {
        if (!schedule.isEnabled() || !schedule.getCronExpression().isPresent()) {
            return null;
        }
        
        String cronExpression = schedule.getCronExpression().get();
        LocalDateTime now = LocalDateTime.now();
        
        try {
            // Parse and calculate next run time from cron expression
            // Using simplified cron parsing for common patterns
            return parseCronAndGetNextRun(cronExpression, now);
        } catch (Exception e) {
            // If cron parsing fails, default to 1 hour from now
            return now.plusHours(1);
        }
    }
    
    private LocalDateTime parseCronAndGetNextRun(String cronExpression, LocalDateTime from) {
        // Support common cron patterns
        // Format: second minute hour day month dayOfWeek
        String[] parts = cronExpression.trim().split("\\s+");
        
        if (parts.length != 6) {
            // Invalid cron format, default to 1 hour
            return from.plusHours(1);
        }
        
        try {
            String minutePart = parts[1];
            String hourPart = parts[2];
            String dayPart = parts[3];
            String monthPart = parts[4];
            
            // Handle simple cases first
            if ("*".equals(minutePart) && "*".equals(hourPart)) {
                // Every minute - not practical, default to every hour
                return from.plusHours(1);
            }
            
            // Fixed time daily (e.g., "0 30 14 * * ?" = 2:30 PM daily)
            if (!"*".equals(minutePart) && !"*".equals(hourPart) && "*".equals(dayPart)) {
                int minute = Integer.parseInt(minutePart);
                int hour = Integer.parseInt(hourPart);
                
                LocalDateTime nextRun = from.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
                
                // If time has passed today, schedule for tomorrow
                if (nextRun.isBefore(from) || nextRun.isEqual(from)) {
                    nextRun = nextRun.plusDays(1);
                }
                
                return nextRun;
            }
            
            // Every N minutes (e.g., "0 */15 * * * ?" = every 15 minutes)
            if (minutePart.startsWith("*/")) {
                int interval = Integer.parseInt(minutePart.substring(2));
                return from.plusMinutes(interval);
            }
            
            // Every N hours (e.g., "0 0 */2 * * ?" = every 2 hours)
            if (hourPart.startsWith("*/")) {
                int interval = Integer.parseInt(hourPart.substring(2));
                return from.plusHours(interval);
            }
            
            // Fixed intervals
            if ("0".equals(parts[0]) && "0".equals(minutePart)) {
                // Hourly at the top of the hour
                if ("*".equals(hourPart)) {
                    return from.plusHours(1).withMinute(0).withSecond(0).withNano(0);
                }
            }
            
            // Default fallback for complex expressions
            return from.plusHours(1);
            
        } catch (NumberFormatException e) {
            // Invalid numbers in cron expression
            return from.plusHours(1);
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
    
    /**
     * Associate flow with a package during deployment
     */
    public void deployToPackage(UUID packageId, UUID updatedBy) {
        this.packageId = Objects.requireNonNull(packageId, "Package ID cannot be null");
        if (this.deployedFromPackageId == null) {
            this.deployedFromPackageId = packageId;
        }
        markAsUpdated(updatedBy);
    }
    
    /**
     * Check if flow belongs to a specific package
     */
    public boolean belongsToPackage(UUID packageId) {
        return this.packageId != null && this.packageId.equals(packageId);
    }
    
    /**
     * Check if flow was originally deployed from a different package
     */
    public boolean wasMovedBetweenPackages() {
        return deployedFromPackageId != null && packageId != null &&
               !deployedFromPackageId.equals(packageId);
    }

    /**
     * Check if this flow was imported from another system/environment.
     * Returns true if originalFlowId is set, false if it's a locally created flow.
     *
     * This matches the legacy app behavior where originalFlowId being blank
     * indicated a local flow vs imported flow.
     */
    public boolean isImported() {
        return originalFlowId != null;
    }

    /**
     * Check if this flow was created locally (not imported).
     * This is the inverse of isImported() for clearer semantics.
     */
    public boolean isLocallyCreated() {
        return originalFlowId == null;
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
    
    public UUID getPackageId() {
        return packageId;
    }
    
    public void setPackageId(UUID packageId) {
        this.packageId = packageId;
    }
    
    public UUID getDeployedFromPackageId() {
        return deployedFromPackageId;
    }
    
    public void setDeployedFromPackageId(UUID deployedFromPackageId) {
        this.deployedFromPackageId = deployedFromPackageId;
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