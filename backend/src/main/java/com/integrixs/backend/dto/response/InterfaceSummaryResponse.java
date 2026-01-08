package com.integrixs.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable response DTO for interface summary information.
 * Used in list views and overview displays.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class InterfaceSummaryResponse {
    
    private final UUID id;
    private final String name;
    private final String adapterType;
    private final String direction;
    private final boolean isActive;
    private final String status;
    private final LocalDateTime lastExecuted;
    private final Long totalExecutions;
    private final Long successfulExecutions;
    private final Long failedExecutions;
    private final Double successRate;
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    
    private InterfaceSummaryResponse(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.adapterType = builder.adapterType;
        this.direction = builder.direction;
        this.isActive = builder.isActive;
        this.status = builder.status;
        this.lastExecuted = builder.lastExecuted;
        this.totalExecutions = builder.totalExecutions;
        this.successfulExecutions = builder.successfulExecutions;
        this.failedExecutions = builder.failedExecutions;
        this.successRate = calculateSuccessRate(builder.successfulExecutions, builder.totalExecutions);
        this.description = builder.description;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }
    
    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getAdapterType() { return adapterType; }
    public String getDirection() { return direction; }
    public boolean isActive() { return isActive; }
    public String getStatus() { return status; }
    public LocalDateTime getLastExecuted() { return lastExecuted; }
    public Long getTotalExecutions() { return totalExecutions; }
    public Long getSuccessfulExecutions() { return successfulExecutions; }
    public Long getFailedExecutions() { return failedExecutions; }
    public Double getSuccessRate() { return successRate; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    
    /**
     * Check if interface has been executed recently (within last 24 hours).
     */
    public boolean hasRecentActivity() {
        return lastExecuted != null && lastExecuted.isAfter(LocalDateTime.now().minusDays(1));
    }
    
    /**
     * Check if interface has good performance (success rate > 95%).
     */
    public boolean hasGoodPerformance() {
        return successRate != null && successRate > 95.0;
    }
    
    /**
     * Check if interface requires attention (success rate < 80% or failures > 10%).
     */
    public boolean requiresAttention() {
        return (successRate != null && successRate < 80.0) || 
               (failedExecutions != null && failedExecutions > 10);
    }
    
    /**
     * Get status display color based on performance.
     */
    public String getStatusColor() {
        if (!isActive) return "gray";
        if (requiresAttention()) return "red";
        if (hasGoodPerformance()) return "green";
        return "yellow";
    }
    
    /**
     * Calculate success rate from successful and total executions.
     */
    private static Double calculateSuccessRate(Long successful, Long total) {
        if (total == null || total == 0) return null;
        if (successful == null) successful = 0L;
        return (double) successful / total * 100.0;
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for InterfaceSummaryResponse.
     */
    public static class Builder {
        private UUID id;
        private String name;
        private String adapterType;
        private String direction;
        private boolean isActive;
        private String status;
        private LocalDateTime lastExecuted;
        private Long totalExecutions;
        private Long successfulExecutions;
        private Long failedExecutions;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        private Builder() {}
        
        public Builder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
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
        
        public Builder lastExecuted(LocalDateTime lastExecuted) {
            this.lastExecuted = lastExecuted;
            return this;
        }
        
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
        
        public Builder description(String description) {
            this.description = description;
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
        
        public InterfaceSummaryResponse build() {
            if (id == null) {
                throw new IllegalArgumentException("ID is required");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Name is required");
            }
            if (adapterType == null || adapterType.trim().isEmpty()) {
                throw new IllegalArgumentException("Adapter type is required");
            }
            
            return new InterfaceSummaryResponse(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("InterfaceSummaryResponse{id=%s, name='%s', type='%s', " +
                           "direction='%s', active=%s, status='%s'}", 
                           id, name, adapterType, direction, isActive, status);
    }
}