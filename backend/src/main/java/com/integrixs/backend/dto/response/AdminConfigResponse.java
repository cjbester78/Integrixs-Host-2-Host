package com.integrixs.backend.dto.response;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable response DTO for administrative configuration operations.
 * Contains configuration data and operation results.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class AdminConfigResponse {
    
    private final String operation;
    private final String status;
    private final String configKey;
    private final String configValue;
    private final String category;
    private final String description;
    private final Boolean sensitive;
    private final Map<String, Object> configurationData;
    private final String message;
    private final UUID modifiedBy;
    private final LocalDateTime lastModified;
    private final LocalDateTime timestamp;
    
    private AdminConfigResponse(Builder builder) {
        this.operation = builder.operation;
        this.status = builder.status;
        this.configKey = builder.configKey;
        this.configValue = builder.configValue;
        this.category = builder.category;
        this.description = builder.description;
        this.sensitive = builder.sensitive;
        this.configurationData = builder.configurationData != null ? 
            Collections.unmodifiableMap(builder.configurationData) : Collections.emptyMap();
        this.message = builder.message;
        this.modifiedBy = builder.modifiedBy;
        this.lastModified = builder.lastModified;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
    }
    
    // Getters
    public String getOperation() { return operation; }
    public String getStatus() { return status; }
    public String getConfigKey() { return configKey; }
    public String getConfigValue() { return configValue; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public Boolean getSensitive() { return sensitive; }
    public Map<String, Object> getConfigurationData() { return configurationData; }
    public String getMessage() { return message; }
    public UUID getModifiedBy() { return modifiedBy; }
    public LocalDateTime getLastModified() { return lastModified; }
    public LocalDateTime getTimestamp() { return timestamp; }
    
    /**
     * Check if operation was successful.
     */
    public boolean isOperationSuccessful() {
        return "SUCCESS".equalsIgnoreCase(status) || "UPDATED".equalsIgnoreCase(status);
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
     * Check if response contains configuration data.
     */
    public boolean hasConfigurationData() {
        return !configurationData.isEmpty();
    }
    
    /**
     * Check if configuration was recently modified (within last hour).
     */
    public boolean isRecentlyModified() {
        return lastModified != null && lastModified.isAfter(LocalDateTime.now().minusHours(1));
    }
    
    /**
     * Get safe config value for display (masks sensitive values).
     */
    public String getSafeConfigValue() {
        if (isSensitiveConfiguration() && configValue != null) {
            return "[MASKED]";
        }
        return configValue;
    }
    
    /**
     * Get configuration summary.
     */
    public String getConfigurationSummary() {
        if (category != null && configKey != null) {
            return String.format("%s.%s", category, configKey);
        }
        return configKey != null ? configKey : "Configuration";
    }
    
    /**
     * Get configuration count from data.
     */
    public int getConfigurationCount() {
        return configurationData.size();
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create successful configuration retrieval response.
     */
    public static AdminConfigResponse getResponse(String configKey, String configValue, String category, Boolean sensitive) {
        return builder()
            .operation("get")
            .status("SUCCESS")
            .configKey(configKey)
            .configValue(configValue)
            .category(category)
            .sensitive(sensitive)
            .build();
    }
    
    /**
     * Create configuration list response.
     */
    public static AdminConfigResponse listResponse(String category, Map<String, Object> configurations) {
        return builder()
            .operation("list")
            .status("SUCCESS")
            .category(category)
            .configurationData(configurations)
            .message("Configuration list retrieved successfully")
            .build();
    }
    
    /**
     * Create successful configuration update response.
     */
    public static AdminConfigResponse updateResponse(String configKey, String category, UUID modifiedBy) {
        return builder()
            .operation("update")
            .status("UPDATED")
            .configKey(configKey)
            .category(category)
            .modifiedBy(modifiedBy)
            .lastModified(LocalDateTime.now())
            .message("Configuration updated successfully")
            .build();
    }
    
    /**
     * Create configuration validation response.
     */
    public static AdminConfigResponse validationResponse(String configKey, boolean isValid, String message) {
        String status = isValid ? "VALID" : "INVALID";
        return builder()
            .operation("validate")
            .status(status)
            .configKey(configKey)
            .message(message)
            .build();
    }
    
    /**
     * Create dashboard configuration response.
     */
    public static AdminConfigResponse dashboardConfigResponse(Map<String, Object> dashboardConfig) {
        return builder()
            .operation("dashboard_config")
            .status("SUCCESS")
            .category("DASHBOARD")
            .configurationData(dashboardConfig)
            .message("Dashboard configuration retrieved")
            .build();
    }
    
    /**
     * Create configuration backup response.
     */
    public static AdminConfigResponse backupResponse(String category, int configCount) {
        return builder()
            .operation("backup")
            .status("SUCCESS")
            .category(category)
            .message(String.format("Configuration backup created (%d items)", configCount))
            .build();
    }
    
    /**
     * Create configuration restore response.
     */
    public static AdminConfigResponse restoreResponse(String category, int restoredCount) {
        return builder()
            .operation("restore")
            .status("SUCCESS")
            .category(category)
            .message(String.format("Configuration restored (%d items)", restoredCount))
            .build();
    }
    
    /**
     * Builder for AdminConfigResponse.
     */
    public static class Builder {
        private String operation;
        private String status;
        private String configKey;
        private String configValue;
        private String category;
        private String description;
        private Boolean sensitive;
        private Map<String, Object> configurationData;
        private String message;
        private UUID modifiedBy;
        private LocalDateTime lastModified;
        private LocalDateTime timestamp;
        
        private Builder() {}
        
        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
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
        
        public Builder configurationData(Map<String, Object> configurationData) {
            this.configurationData = configurationData;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder modifiedBy(UUID modifiedBy) {
            this.modifiedBy = modifiedBy;
            return this;
        }
        
        public Builder lastModified(LocalDateTime lastModified) {
            this.lastModified = lastModified;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public AdminConfigResponse build() {
            return new AdminConfigResponse(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("AdminConfigResponse{operation='%s', status='%s', " +
                           "configKey='%s', category='%s', sensitive=%s, timestamp=%s}", 
                           operation, status, configKey, category, sensitive, timestamp);
    }
}