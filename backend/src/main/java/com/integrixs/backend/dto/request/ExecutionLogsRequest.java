package com.integrixs.backend.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request object for execution logs queries.
 * Contains execution ID, filtering options, and limits.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class ExecutionLogsRequest {
    
    private final UUID executionId;
    private final String filter;
    private final String level;
    private final int limit;
    private final LocalDateTime fromTimestamp;
    private final LocalDateTime toTimestamp;
    private final boolean includeStackTraces;
    private final String correlationId;
    
    private ExecutionLogsRequest(Builder builder) {
        this.executionId = builder.executionId;
        this.filter = builder.filter;
        this.level = builder.level;
        this.limit = builder.limit;
        this.fromTimestamp = builder.fromTimestamp;
        this.toTimestamp = builder.toTimestamp;
        this.includeStackTraces = builder.includeStackTraces;
        this.correlationId = builder.correlationId != null ? builder.correlationId : UUID.randomUUID().toString();
    }
    
    // Getters
    public UUID getExecutionId() { return executionId; }
    public String getFilter() { return filter; }
    public String getLevel() { return level; }
    public int getLimit() { return limit; }
    public LocalDateTime getFromTimestamp() { return fromTimestamp; }
    public LocalDateTime getToTimestamp() { return toTimestamp; }
    public boolean isIncludeStackTraces() { return includeStackTraces; }
    public String getCorrelationId() { return correlationId; }
    
    /**
     * Check if request has text filter.
     */
    public boolean hasTextFilter() {
        return filter != null && !filter.trim().isEmpty();
    }
    
    /**
     * Check if request has level filter.
     */
    public boolean hasLevelFilter() {
        return level != null && !level.trim().isEmpty();
    }
    
    /**
     * Check if request has timestamp range filter.
     */
    public boolean hasTimestampFilter() {
        return fromTimestamp != null || toTimestamp != null;
    }
    
    /**
     * Get normalized log level for filtering.
     */
    public String getNormalizedLevel() {
        return hasLevelFilter() ? level.toUpperCase() : null;
    }
    
    /**
     * Get normalized filter text.
     */
    public String getNormalizedFilter() {
        return hasTextFilter() ? filter.trim() : null;
    }
    
    /**
     * Check if this is a large query (high limit).
     */
    public boolean isLargeQuery() {
        return limit > 1000;
    }
    
    /**
     * Create builder instance for constructing ExecutionLogsRequest.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create simple logs request with just execution ID.
     */
    public static ExecutionLogsRequest simple(UUID executionId) {
        return builder().executionId(executionId).build();
    }
    
    /**
     * Create simple logs request with execution ID and limit.
     */
    public static ExecutionLogsRequest simple(UUID executionId, int limit) {
        return builder().executionId(executionId).limit(limit).build();
    }
    
    /**
     * Builder for ExecutionLogsRequest following builder pattern.
     */
    public static class Builder {
        private UUID executionId;
        private String filter;
        private String level;
        private int limit = 200;
        private LocalDateTime fromTimestamp;
        private LocalDateTime toTimestamp;
        private boolean includeStackTraces = false;
        private String correlationId;
        
        private Builder() {}
        
        public Builder executionId(UUID executionId) {
            this.executionId = executionId;
            return this;
        }
        
        public Builder executionId(String executionId) {
            this.executionId = executionId != null && !executionId.trim().isEmpty() ? 
                UUID.fromString(executionId) : null;
            return this;
        }
        
        public Builder filter(String filter) {
            this.filter = filter != null && !filter.trim().isEmpty() ? filter.trim() : null;
            return this;
        }
        
        public Builder level(String level) {
            this.level = level != null && !level.trim().isEmpty() ? level.trim().toUpperCase() : null;
            return this;
        }
        
        public Builder limit(int limit) {
            this.limit = Math.min(Math.max(1, limit), 10000);
            return this;
        }
        
        public Builder fromTimestamp(LocalDateTime fromTimestamp) {
            this.fromTimestamp = fromTimestamp;
            return this;
        }
        
        public Builder toTimestamp(LocalDateTime toTimestamp) {
            this.toTimestamp = toTimestamp;
            return this;
        }
        
        public Builder includeStackTraces(boolean includeStackTraces) {
            this.includeStackTraces = includeStackTraces;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public ExecutionLogsRequest build() {
            if (executionId == null) {
                throw new IllegalArgumentException("Execution ID is required");
            }
            
            return new ExecutionLogsRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionLogsRequest{executionId=%s, hasTextFilter=%b, hasLevelFilter=%b, " +
                           "limit=%d, hasTimestampFilter=%b, includeStackTraces=%b, correlationId='%s'}", 
                           executionId, hasTextFilter(), hasLevelFilter(), limit, hasTimestampFilter(), 
                           includeStackTraces, correlationId);
    }
}