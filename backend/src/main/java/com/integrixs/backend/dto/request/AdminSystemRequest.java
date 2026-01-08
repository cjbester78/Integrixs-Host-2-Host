package com.integrixs.backend.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request DTO for administrative system operations.
 * Contains parameters for system health, metrics, logs, and cleanup operations.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class AdminSystemRequest {
    
    private final String operation;
    private final Integer limit;
    private final String level;
    private final String category;
    private final String search;
    private final String format;
    private final String bankName;
    private final Integer hours;
    private final Integer retentionDays;
    private final String metricType;
    private final UUID requestedBy;
    private final LocalDateTime requestedAt;
    
    private AdminSystemRequest(Builder builder) {
        this.operation = builder.operation;
        this.limit = builder.limit;
        this.level = builder.level;
        this.category = builder.category;
        this.search = builder.search;
        this.format = builder.format;
        this.bankName = builder.bankName;
        this.hours = builder.hours;
        this.retentionDays = builder.retentionDays;
        this.metricType = builder.metricType;
        this.requestedBy = builder.requestedBy;
        this.requestedAt = builder.requestedAt != null ? builder.requestedAt : LocalDateTime.now();
    }
    
    // Getters
    public String getOperation() { return operation; }
    public Integer getLimit() { return limit; }
    public String getLevel() { return level; }
    public String getCategory() { return category; }
    public String getSearch() { return search; }
    public String getFormat() { return format; }
    public String getBankName() { return bankName; }
    public Integer getHours() { return hours; }
    public Integer getRetentionDays() { return retentionDays; }
    public String getMetricType() { return metricType; }
    public UUID getRequestedBy() { return requestedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    
    /**
     * Check if request has filtering parameters.
     */
    public boolean hasFilters() {
        return level != null || category != null || search != null || bankName != null;
    }
    
    /**
     * Check if request is for log export.
     */
    public boolean isExportRequest() {
        return format != null && !format.trim().isEmpty();
    }
    
    /**
     * Check if request is for cleanup operation.
     */
    public boolean isCleanupRequest() {
        return retentionDays != null && retentionDays > 0;
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create request for log retrieval.
     */
    public static AdminSystemRequest logsRequest(int limit, String level, String category, UUID requestedBy) {
        return builder()
            .operation("logs")
            .limit(limit)
            .level(level)
            .category(category)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for log export.
     */
    public static AdminSystemRequest logsExportRequest(int limit, String format, UUID requestedBy) {
        return builder()
            .operation("logs_export")
            .limit(limit)
            .format(format)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for statistics.
     */
    public static AdminSystemRequest statisticsRequest(int hours, String bankName, UUID requestedBy) {
        return builder()
            .operation("statistics")
            .hours(hours)
            .bankName(bankName)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for cleanup operation.
     */
    public static AdminSystemRequest cleanupRequest(int retentionDays, UUID requestedBy) {
        return builder()
            .operation("cleanup")
            .retentionDays(retentionDays)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for system metrics.
     */
    public static AdminSystemRequest metricsRequest(String metricType, UUID requestedBy) {
        return builder()
            .operation("metrics")
            .metricType(metricType)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Builder for AdminSystemRequest.
     */
    public static class Builder {
        private String operation;
        private Integer limit;
        private String level;
        private String category;
        private String search;
        private String format;
        private String bankName;
        private Integer hours;
        private Integer retentionDays;
        private String metricType;
        private UUID requestedBy;
        private LocalDateTime requestedAt;
        
        private Builder() {}
        
        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }
        
        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }
        
        public Builder level(String level) {
            this.level = level != null && !level.trim().isEmpty() ? level.toUpperCase() : null;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder search(String search) {
            this.search = search;
            return this;
        }
        
        public Builder format(String format) {
            this.format = format != null ? format.toUpperCase() : null;
            return this;
        }
        
        public Builder bankName(String bankName) {
            this.bankName = bankName;
            return this;
        }
        
        public Builder hours(Integer hours) {
            this.hours = hours;
            return this;
        }
        
        public Builder retentionDays(Integer retentionDays) {
            this.retentionDays = retentionDays;
            return this;
        }
        
        public Builder metricType(String metricType) {
            this.metricType = metricType;
            return this;
        }
        
        public Builder requestedBy(UUID requestedBy) {
            this.requestedBy = requestedBy;
            return this;
        }
        
        public Builder requestedAt(LocalDateTime requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }
        
        public AdminSystemRequest build() {
            return new AdminSystemRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("AdminSystemRequest{operation='%s', limit=%d, level='%s', " +
                           "requestedBy=%s, requestedAt=%s}", 
                           operation, limit, level, requestedBy, requestedAt);
    }
}