package com.integrixs.backend.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request DTO for interface listing operations.
 * Contains filtering, pagination, and sorting parameters.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class InterfaceListRequest {
    
    private final String type;
    private final String direction;
    private final Boolean enabled;
    private final String sortBy;
    private final String sortOrder;
    private final UUID requestedBy;
    private final LocalDateTime requestedAt;
    
    private InterfaceListRequest(Builder builder) {
        this.type = builder.type;
        this.direction = builder.direction;
        this.enabled = builder.enabled;
        this.sortBy = builder.sortBy;
        this.sortOrder = builder.sortOrder;
        this.requestedBy = builder.requestedBy;
        this.requestedAt = builder.requestedAt != null ? builder.requestedAt : LocalDateTime.now();
    }
    
    // Getters
    public String getType() { return type; }
    public String getDirection() { return direction; }
    public Boolean getEnabled() { return enabled; }
    public String getSortBy() { return sortBy; }
    public String getSortOrder() { return sortOrder; }
    public UUID getRequestedBy() { return requestedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    
    /**
     * Check if any filters are applied.
     */
    public boolean hasFilters() {
        return type != null || direction != null || enabled != null;
    }
    
    /**
     * Check if sorting is specified.
     */
    public boolean hasSorting() {
        return sortBy != null && !sortBy.trim().isEmpty();
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create request for all interfaces without filters.
     */
    public static InterfaceListRequest all(UUID requestedBy) {
        return builder()
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request filtered by type.
     */
    public static InterfaceListRequest byType(String type, UUID requestedBy) {
        return builder()
            .type(type)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request filtered by enabled status.
     */
    public static InterfaceListRequest byEnabledStatus(boolean enabled, UUID requestedBy) {
        return builder()
            .enabled(enabled)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Builder for InterfaceListRequest.
     */
    public static class Builder {
        private String type;
        private String direction;
        private Boolean enabled;
        private String sortBy;
        private String sortOrder;
        private UUID requestedBy;
        private LocalDateTime requestedAt;
        
        private Builder() {}
        
        public Builder type(String type) {
            this.type = type != null && !type.trim().isEmpty() ? type.toUpperCase() : null;
            return this;
        }
        
        public Builder direction(String direction) {
            this.direction = direction != null && !direction.trim().isEmpty() ? direction.toUpperCase() : null;
            return this;
        }
        
        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy;
            return this;
        }
        
        public Builder sortOrder(String sortOrder) {
            this.sortOrder = sortOrder != null ? sortOrder.toUpperCase() : "ASC";
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
        
        public InterfaceListRequest build() {
            return new InterfaceListRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("InterfaceListRequest{type='%s', direction='%s', enabled=%s, " +
                           "sortBy='%s', requestedBy=%s, requestedAt=%s}", 
                           type, direction, enabled, sortBy, requestedBy, requestedAt);
    }
}