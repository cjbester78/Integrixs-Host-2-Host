package com.integrixs.backend.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request DTO for administrative configuration operations.
 * Contains configuration parameters and operation context.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class AdminConfigRequest {
    
    private final String operation;
    private final String configKey;
    private final String configValue;
    private final String category;
    private final String description;
    private final Boolean sensitive;
    private final String reason;
    private final UUID requestedBy;
    private final LocalDateTime requestedAt;
    
    private AdminConfigRequest(Builder builder) {
        this.operation = builder.operation;
        this.configKey = builder.configKey;
        this.configValue = builder.configValue;
        this.category = builder.category;
        this.description = builder.description;
        this.sensitive = builder.sensitive;
        this.reason = builder.reason;
        this.requestedBy = builder.requestedBy;
        this.requestedAt = builder.requestedAt != null ? builder.requestedAt : LocalDateTime.now();
    }
    
    // Getters
    public String getOperation() { return operation; }
    public String getConfigKey() { return configKey; }
    public String getConfigValue() { return configValue; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public Boolean getSensitive() { return sensitive; }
    public String getReason() { return reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    
    /**
     * Check if operation is configuration retrieval.
     */
    public boolean isRetrievalOperation() {
        return "get".equalsIgnoreCase(operation) || "list".equalsIgnoreCase(operation);
    }
    
    /**
     * Check if operation is configuration update.
     */
    public boolean isUpdateOperation() {
        return "update".equalsIgnoreCase(operation) || "set".equalsIgnoreCase(operation);
    }
    
    /**
     * Check if configuration is marked as sensitive.
     */
    public boolean isSensitiveConfiguration() {
        return Boolean.TRUE.equals(sensitive) || 
               (configKey != null && (configKey.toLowerCase().contains("password") || 
                                     configKey.toLowerCase().contains("secret") ||
                                     configKey.toLowerCase().contains("key")));
    }
    
    /**
     * Check if request has category filter.
     */
    public boolean hasCategoryFilter() {
        return category != null && !category.trim().isEmpty();
    }
    
    /**
     * Get safe config value for logging (masks sensitive values).
     */
    public String getSafeConfigValue() {
        if (isSensitiveConfiguration() && configValue != null) {
            return "[MASKED]";
        }
        return configValue;
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create request for configuration retrieval.
     */
    public static AdminConfigRequest getRequest(String configKey, UUID requestedBy) {
        return builder()
            .operation("get")
            .configKey(configKey)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for configuration list by category.
     */
    public static AdminConfigRequest listByCategoryRequest(String category, UUID requestedBy) {
        return builder()
            .operation("list")
            .category(category)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for configuration update.
     */
    public static AdminConfigRequest updateRequest(String configKey, String configValue, String reason, UUID requestedBy) {
        return builder()
            .operation("update")
            .configKey(configKey)
            .configValue(configValue)
            .reason(reason)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for sensitive configuration update.
     */
    public static AdminConfigRequest updateSensitiveRequest(String configKey, String configValue, String reason, UUID requestedBy) {
        return builder()
            .operation("update")
            .configKey(configKey)
            .configValue(configValue)
            .sensitive(true)
            .reason(reason)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for dashboard configuration.
     */
    public static AdminConfigRequest dashboardConfigRequest(UUID requestedBy) {
        return builder()
            .operation("dashboard_config")
            .category("DASHBOARD")
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Builder for AdminConfigRequest.
     */
    public static class Builder {
        private String operation;
        private String configKey;
        private String configValue;
        private String category;
        private String description;
        private Boolean sensitive;
        private String reason;
        private UUID requestedBy;
        private LocalDateTime requestedAt;
        
        private Builder() {}
        
        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }
        
        public Builder configKey(String configKey) {
            this.configKey = configKey;
            return this;
        }
        
        public Builder configValue(String configValue) {
            this.configValue = configValue;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category != null ? category.toUpperCase() : null;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder sensitive(Boolean sensitive) {
            this.sensitive = sensitive;
            return this;
        }
        
        public Builder reason(String reason) {
            this.reason = reason;
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
        
        public AdminConfigRequest build() {
            return new AdminConfigRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("AdminConfigRequest{operation='%s', configKey='%s', " +
                           "configValue='%s', category='%s', sensitive=%s, requestedBy=%s, requestedAt=%s}", 
                           operation, configKey, getSafeConfigValue(), category, sensitive, requestedBy, requestedAt);
    }
}