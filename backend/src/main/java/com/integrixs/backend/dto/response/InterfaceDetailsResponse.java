package com.integrixs.backend.dto.response;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable response DTO for detailed interface information.
 * Contains comprehensive interface details, configuration, and metrics.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class InterfaceDetailsResponse {
    
    private final UUID id;
    private final String name;
    private final String description;
    private final String adapterType;
    private final String direction;
    private final boolean isActive;
    private final String status;
    private final Map<String, Object> configuration;
    private final InterfaceMetrics metrics;
    private final List<InterfaceExecutionSummary> recentExecutions;
    private final LocalDateTime lastExecuted;
    private final LocalDateTime nextScheduled;
    private final String scheduleExpression;
    private final UUID createdBy;
    private final UUID updatedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final String version;
    
    private InterfaceDetailsResponse(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.adapterType = builder.adapterType;
        this.direction = builder.direction;
        this.isActive = builder.isActive;
        this.status = builder.status;
        this.configuration = builder.configuration != null ? 
            Collections.unmodifiableMap(builder.configuration) : Collections.emptyMap();
        this.metrics = builder.metrics;
        this.recentExecutions = builder.recentExecutions != null ?
            Collections.unmodifiableList(builder.recentExecutions) : Collections.emptyList();
        this.lastExecuted = builder.lastExecuted;
        this.nextScheduled = builder.nextScheduled;
        this.scheduleExpression = builder.scheduleExpression;
        this.createdBy = builder.createdBy;
        this.updatedBy = builder.updatedBy;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.version = builder.version;
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAdapterType() { return adapterType; }
    public String getDirection() { return direction; }
    public boolean isActive() { return isActive; }
    public String getStatus() { return status; }
    public Map<String, Object> getConfiguration() { return configuration; }
    public InterfaceMetrics getMetrics() { return metrics; }
    public List<InterfaceExecutionSummary> getRecentExecutions() { return recentExecutions; }
    public LocalDateTime getLastExecuted() { return lastExecuted; }
    public LocalDateTime getNextScheduled() { return nextScheduled; }
    public String getScheduleExpression() { return scheduleExpression; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getVersion() { return version; }
    
    /**
     * Check if interface has configuration.
     */
    public boolean hasConfiguration() {
        return !configuration.isEmpty();
    }
    
    /**
     * Check if interface is scheduled.
     */
    public boolean isScheduled() {
        return scheduleExpression != null && !scheduleExpression.trim().isEmpty();
    }
    
    /**
     * Check if interface has recent activity.
     */
    public boolean hasRecentActivity() {
        return lastExecuted != null && lastExecuted.isAfter(LocalDateTime.now().minusDays(1));
    }
    
    /**
     * Get configuration value by key.
     */
    public Object getConfigurationValue(String key) {
        return configuration.get(key);
    }
    
    /**
     * Get configuration value as string.
     */
    public String getConfigurationValueAsString(String key, String defaultValue) {
        Object value = configuration.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for InterfaceDetailsResponse.
     */
    public static class Builder {
        private UUID id;
        private String name;
        private String description;
        private String adapterType;
        private String direction;
        private boolean isActive;
        private String status;
        private Map<String, Object> configuration;
        private InterfaceMetrics metrics;
        private List<InterfaceExecutionSummary> recentExecutions;
        private LocalDateTime lastExecuted;
        private LocalDateTime nextScheduled;
        private String scheduleExpression;
        private UUID createdBy;
        private UUID updatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String version;
        
        private Builder() {}
        
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder adapterType(String adapterType) {
            this.adapterType = adapterType;
            return this;
        }
        
        public Builder direction(String direction) {
            this.direction = direction;
            return this;
        }
        
        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        
        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration;
            return this;
        }
        
        public Builder metrics(InterfaceMetrics metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder recentExecutions(List<InterfaceExecutionSummary> recentExecutions) {
            this.recentExecutions = recentExecutions;
            return this;
        }
        
        public Builder lastExecuted(LocalDateTime lastExecuted) {
            this.lastExecuted = lastExecuted;
            return this;
        }
        
        public Builder nextScheduled(LocalDateTime nextScheduled) {
            this.nextScheduled = nextScheduled;
            return this;
        }
        
        public Builder scheduleExpression(String scheduleExpression) {
            this.scheduleExpression = scheduleExpression;
            return this;
        }
        
        public Builder createdBy(UUID createdBy) {
            this.createdBy = createdBy;
            return this;
        }
        
        public Builder updatedBy(UUID updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }
        
        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public InterfaceDetailsResponse build() {
            if (id == null) {
                throw new IllegalArgumentException("ID is required");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Name is required");
            }
            if (adapterType == null || adapterType.trim().isEmpty()) {
                throw new IllegalArgumentException("Adapter type is required");
            }
            
            return new InterfaceDetailsResponse(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("InterfaceDetailsResponse{id=%s, name='%s', type='%s', " +
                           "direction='%s', active=%s, status='%s', version='%s'}", 
                           id, name, adapterType, direction, isActive, status, version);
    }
}

/**
 * Immutable metrics object for interface performance data.
 */
class InterfaceMetrics {
    
    private final Long totalExecutions;
    private final Long successfulExecutions;
    private final Long failedExecutions;
    private final Double successRate;
    private final Long averageExecutionTimeMs;
    private final Long totalFilesProcessed;
    private final Long totalBytesProcessed;
    private final LocalDateTime metricsUpdatedAt;
    
    private InterfaceMetrics(Builder builder) {
        this.totalExecutions = builder.totalExecutions;
        this.successfulExecutions = builder.successfulExecutions;
        this.failedExecutions = builder.failedExecutions;
        this.successRate = calculateSuccessRate(builder.successfulExecutions, builder.totalExecutions);
        this.averageExecutionTimeMs = builder.averageExecutionTimeMs;
        this.totalFilesProcessed = builder.totalFilesProcessed;
        this.totalBytesProcessed = builder.totalBytesProcessed;
        this.metricsUpdatedAt = builder.metricsUpdatedAt;
    }
    
    // Getters
    public Long getTotalExecutions() { return totalExecutions; }
    public Long getSuccessfulExecutions() { return successfulExecutions; }
    public Long getFailedExecutions() { return failedExecutions; }
    public Double getSuccessRate() { return successRate; }
    public Long getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
    public Long getTotalFilesProcessed() { return totalFilesProcessed; }
    public Long getTotalBytesProcessed() { return totalBytesProcessed; }
    public LocalDateTime getMetricsUpdatedAt() { return metricsUpdatedAt; }
    
    private static Double calculateSuccessRate(Long successful, Long total) {
        if (total == null || total == 0) return null;
        if (successful == null) successful = 0L;
        return (double) successful / total * 100.0;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Long totalExecutions;
        private Long successfulExecutions;
        private Long failedExecutions;
        private Long averageExecutionTimeMs;
        private Long totalFilesProcessed;
        private Long totalBytesProcessed;
        private LocalDateTime metricsUpdatedAt;
        
        public Builder totalExecutions(Long totalExecutions) {
            this.totalExecutions = totalExecutions;
            return this;
        }
        
        public Builder successfulExecutions(Long successfulExecutions) {
            this.successfulExecutions = successfulExecutions;
            return this;
        }
        
        public Builder failedExecutions(Long failedExecutions) {
            this.failedExecutions = failedExecutions;
            return this;
        }
        
        public Builder averageExecutionTimeMs(Long averageExecutionTimeMs) {
            this.averageExecutionTimeMs = averageExecutionTimeMs;
            return this;
        }
        
        public Builder totalFilesProcessed(Long totalFilesProcessed) {
            this.totalFilesProcessed = totalFilesProcessed;
            return this;
        }
        
        public Builder totalBytesProcessed(Long totalBytesProcessed) {
            this.totalBytesProcessed = totalBytesProcessed;
            return this;
        }
        
        public Builder metricsUpdatedAt(LocalDateTime metricsUpdatedAt) {
            this.metricsUpdatedAt = metricsUpdatedAt;
            return this;
        }
        
        public InterfaceMetrics build() {
            return new InterfaceMetrics(this);
        }
    }
}

/**
 * Immutable execution summary for recent interface executions.
 */
class InterfaceExecutionSummary {
    
    private final UUID executionId;
    private final String status;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Long durationMs;
    private final String errorMessage;
    
    private InterfaceExecutionSummary(Builder builder) {
        this.executionId = builder.executionId;
        this.status = builder.status;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.durationMs = builder.durationMs;
        this.errorMessage = builder.errorMessage;
    }
    
    // Getters
    public UUID getExecutionId() { return executionId; }
    public String getStatus() { return status; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Long getDurationMs() { return durationMs; }
    public String getErrorMessage() { return errorMessage; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID executionId;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationMs;
        private String errorMessage;
        
        public Builder executionId(UUID executionId) {
            this.executionId = executionId;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        
        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public InterfaceExecutionSummary build() {
            return new InterfaceExecutionSummary(this);
        }
    }
}