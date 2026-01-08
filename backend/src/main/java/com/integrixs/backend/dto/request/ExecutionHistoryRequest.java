package com.integrixs.backend.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request object for execution history queries.
 * Contains pagination, filtering, and date range parameters.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class ExecutionHistoryRequest {
    
    private final int page;
    private final int size;
    private final UUID flowId;
    private final String status;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final String sortBy;
    private final String sortDirection;
    
    private ExecutionHistoryRequest(Builder builder) {
        this.page = builder.page;
        this.size = builder.size;
        this.flowId = builder.flowId;
        this.status = builder.status;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.sortBy = builder.sortBy;
        this.sortDirection = builder.sortDirection;
    }
    
    // Getters
    public int getPage() { return page; }
    public int getSize() { return size; }
    public UUID getFlowId() { return flowId; }
    public String getStatus() { return status; }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public String getSortBy() { return sortBy; }
    public String getSortDirection() { return sortDirection; }
    
    /**
     * Check if request has flow filter.
     */
    public boolean hasFlowFilter() {
        return flowId != null;
    }
    
    /**
     * Check if request has status filter.
     */
    public boolean hasStatusFilter() {
        return status != null && !status.trim().isEmpty();
    }
    
    /**
     * Check if request has date range filter.
     */
    public boolean hasDateRangeFilter() {
        return startDate != null || endDate != null;
    }
    
    /**
     * Check if request has custom sorting.
     */
    public boolean hasCustomSorting() {
        return sortBy != null && !sortBy.trim().isEmpty();
    }
    
    /**
     * Get page offset for database queries.
     */
    public int getOffset() {
        return page * size;
    }
    
    /**
     * Create builder instance for constructing ExecutionHistoryRequest.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ExecutionHistoryRequest following builder pattern.
     */
    public static class Builder {
        private int page = 0;
        private int size = 20;
        private UUID flowId;
        private String status;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String sortBy = "startedAt";
        private String sortDirection = "DESC";
        
        private Builder() {}
        
        public Builder page(int page) {
            this.page = Math.max(0, page);
            return this;
        }
        
        public Builder size(int size) {
            this.size = Math.min(Math.max(1, size), 1000);
            return this;
        }
        
        public Builder flowId(UUID flowId) {
            this.flowId = flowId;
            return this;
        }
        
        public Builder flowId(String flowId) {
            this.flowId = flowId != null && !flowId.trim().isEmpty() ? UUID.fromString(flowId) : null;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status != null && !status.trim().isEmpty() ? status.trim().toUpperCase() : null;
            return this;
        }
        
        public Builder startDate(LocalDateTime startDate) {
            this.startDate = startDate;
            return this;
        }
        
        public Builder endDate(LocalDateTime endDate) {
            this.endDate = endDate;
            return this;
        }
        
        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy != null && !sortBy.trim().isEmpty() ? sortBy.trim() : "startedAt";
            return this;
        }
        
        public Builder sortDirection(String sortDirection) {
            this.sortDirection = sortDirection != null && !sortDirection.trim().isEmpty() ? 
                sortDirection.trim().toUpperCase() : "DESC";
            return this;
        }
        
        public ExecutionHistoryRequest build() {
            return new ExecutionHistoryRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionHistoryRequest{page=%d, size=%d, flowId=%s, status='%s', " +
                           "hasDateRange=%b, sortBy='%s', sortDirection='%s'}", 
                           page, size, flowId, status, hasDateRangeFilter(), sortBy, sortDirection);
    }
}