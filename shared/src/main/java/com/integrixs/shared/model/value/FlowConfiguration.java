package com.integrixs.shared.model.value;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable value object representing flow configuration settings.
 * Part of Phase 5 model layer refactoring following OOP principles.
 */
public final class FlowConfiguration {
    
    private final Map<String, Object> flowDefinition;
    private final FlowType flowType;
    private final int maxParallelExecutions;
    private final int timeoutMinutes;
    private final Map<String, Object> retryPolicy;
    private final ScheduleSettings scheduleSettings;
    
    private FlowConfiguration(Builder builder) {
        this.flowDefinition = builder.flowDefinition != null ? 
            Collections.unmodifiableMap(builder.flowDefinition) : Collections.emptyMap();
        this.flowType = builder.flowType != null ? builder.flowType : FlowType.STANDARD;
        this.maxParallelExecutions = Math.max(1, builder.maxParallelExecutions);
        this.timeoutMinutes = Math.max(1, builder.timeoutMinutes);
        this.retryPolicy = builder.retryPolicy != null ? 
            Collections.unmodifiableMap(builder.retryPolicy) : Collections.emptyMap();
        this.scheduleSettings = builder.scheduleSettings != null ? 
            builder.scheduleSettings : ScheduleSettings.disabled();
    }
    
    // Getters with defensive copying
    public Map<String, Object> getFlowDefinition() {
        return Collections.unmodifiableMap(flowDefinition);
    }
    
    public FlowType getFlowType() { return flowType; }
    public int getMaxParallelExecutions() { return maxParallelExecutions; }
    public int getTimeoutMinutes() { return timeoutMinutes; }
    
    public Map<String, Object> getRetryPolicy() {
        return Collections.unmodifiableMap(retryPolicy);
    }
    
    public ScheduleSettings getScheduleSettings() { return scheduleSettings; }
    
    /**
     * Check if flow supports parallel execution.
     */
    public boolean supportsParallelExecution() {
        return maxParallelExecutions > 1;
    }
    
    /**
     * Check if flow is ready for execution.
     */
    public boolean isReadyForExecution() {
        return flowDefinition != null && !flowDefinition.isEmpty();
    }
    
    /**
     * Check if flow is scheduled.
     */
    public boolean isScheduled() {
        return scheduleSettings.isEnabled();
    }
    
    /**
     * Get retry configuration value.
     */
    public Optional<Object> getRetryConfigValue(String key) {
        return Optional.ofNullable(retryPolicy.get(key));
    }
    
    /**
     * Get maximum retry attempts.
     */
    public int getMaxRetryAttempts() {
        return getRetryConfigValue("maxAttempts")
            .map(v -> v instanceof Number ? ((Number) v).intValue() : 3)
            .orElse(3);
    }
    
    /**
     * Get retry delay in milliseconds.
     */
    public long getRetryDelayMs() {
        return getRetryConfigValue("delayMs")
            .map(v -> v instanceof Number ? ((Number) v).longValue() : 1000L)
            .orElse(1000L);
    }
    
    /**
     * Check if flow has retry policy configured.
     */
    public boolean hasRetryPolicy() {
        return !retryPolicy.isEmpty();
    }
    
    /**
     * Create updated configuration with new settings.
     */
    public FlowConfiguration withFlowType(FlowType newFlowType) {
        return builder().from(this).flowType(newFlowType).build();
    }
    
    /**
     * Create updated configuration with new parallel execution limit.
     */
    public FlowConfiguration withMaxParallelExecutions(int newLimit) {
        return builder().from(this).maxParallelExecutions(newLimit).build();
    }
    
    /**
     * Create updated configuration with new timeout.
     */
    public FlowConfiguration withTimeout(int newTimeoutMinutes) {
        return builder().from(this).timeoutMinutes(newTimeoutMinutes).build();
    }
    
    /**
     * Create updated configuration with new schedule settings.
     */
    public FlowConfiguration withScheduleSettings(ScheduleSettings newScheduleSettings) {
        return builder().from(this).scheduleSettings(newScheduleSettings).build();
    }
    
    /**
     * Create default configuration.
     */
    public static FlowConfiguration defaultConfiguration() {
        return builder().build();
    }
    
    /**
     * Create configuration from legacy values.
     */
    public static FlowConfiguration fromLegacy(Map<String, Object> flowDefinition, String flowType,
                                             Integer maxParallel, Integer timeout, Map<String, Object> retry,
                                             Boolean scheduleEnabled, String scheduleCron, LocalDateTime nextRun) {
        FlowType type;
        try {
            type = flowType != null ? FlowType.valueOf(flowType.toUpperCase()) : FlowType.STANDARD;
        } catch (IllegalArgumentException e) {
            type = FlowType.STANDARD;
        }
        
        ScheduleSettings schedule = scheduleEnabled != null && scheduleEnabled ?
            ScheduleSettings.enabled(scheduleCron != null ? scheduleCron : "0 0 2 * * ?", nextRun) :
            ScheduleSettings.disabled();
        
        return builder()
            .flowDefinition(flowDefinition)
            .flowType(type)
            .maxParallelExecutions(maxParallel != null ? maxParallel : 1)
            .timeoutMinutes(timeout != null ? timeout : 60)
            .retryPolicy(retry)
            .scheduleSettings(schedule)
            .build();
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for FlowConfiguration.
     */
    public static class Builder {
        private Map<String, Object> flowDefinition;
        private FlowType flowType = FlowType.STANDARD;
        private int maxParallelExecutions = 1;
        private int timeoutMinutes = 60;
        private Map<String, Object> retryPolicy;
        private ScheduleSettings scheduleSettings;
        
        private Builder() {}
        
        public Builder flowDefinition(Map<String, Object> flowDefinition) {
            this.flowDefinition = flowDefinition;
            return this;
        }
        
        public Builder flowType(FlowType flowType) {
            this.flowType = flowType;
            return this;
        }
        
        public Builder maxParallelExecutions(int maxParallelExecutions) {
            this.maxParallelExecutions = maxParallelExecutions;
            return this;
        }
        
        public Builder timeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return this;
        }
        
        public Builder retryPolicy(Map<String, Object> retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }
        
        public Builder scheduleSettings(ScheduleSettings scheduleSettings) {
            this.scheduleSettings = scheduleSettings;
            return this;
        }
        
        public Builder from(FlowConfiguration existing) {
            this.flowDefinition = existing.flowDefinition.isEmpty() ? null : existing.flowDefinition;
            this.flowType = existing.flowType;
            this.maxParallelExecutions = existing.maxParallelExecutions;
            this.timeoutMinutes = existing.timeoutMinutes;
            this.retryPolicy = existing.retryPolicy.isEmpty() ? null : existing.retryPolicy;
            this.scheduleSettings = existing.scheduleSettings;
            return this;
        }
        
        public FlowConfiguration build() {
            return new FlowConfiguration(this);
        }
    }
    
    /**
     * Flow type enumeration.
     */
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
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowConfiguration that = (FlowConfiguration) o;
        return maxParallelExecutions == that.maxParallelExecutions &&
               timeoutMinutes == that.timeoutMinutes &&
               Objects.equals(flowDefinition, that.flowDefinition) &&
               flowType == that.flowType &&
               Objects.equals(retryPolicy, that.retryPolicy) &&
               Objects.equals(scheduleSettings, that.scheduleSettings);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(flowDefinition, flowType, maxParallelExecutions, timeoutMinutes, retryPolicy, scheduleSettings);
    }
    
    @Override
    public String toString() {
        return String.format("FlowConfiguration{type=%s, parallel=%d, timeout=%dm, scheduled=%s}",
                           flowType, maxParallelExecutions, timeoutMinutes, scheduleSettings.isEnabled());
    }
}