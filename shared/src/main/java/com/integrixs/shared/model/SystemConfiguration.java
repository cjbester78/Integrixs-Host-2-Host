package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Model for system configuration settings stored in database
 * Allows for dynamic configuration of application behavior
 */
public class SystemConfiguration {

    public enum ConfigType {
        STRING("String value"),
        INTEGER("Integer number"),
        BOOLEAN("Boolean true/false"),
        JSON("JSON object/array");

        private final String description;

        ConfigType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum ConfigCategory {
        DASHBOARD("Dashboard settings"),
        SECURITY("Security and authentication"),
        NOTIFICATIONS("Email and notification settings"),
        FILE_PROCESSING("File processing and transfer settings"),
        SYSTEM("General system settings"),
        LOGGING("Logging and audit settings"),
        ADAPTER("Adapter configuration settings"),
        GENERAL("General application settings");

        private final String description;

        ConfigCategory(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private UUID id;
    private String configKey;
    private String configValue;
    private ConfigType configType;
    private String description;
    private ConfigCategory category;
    private boolean encrypted;
    private boolean readonly;
    private String defaultValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;

    // Constructors
    public SystemConfiguration() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        // updatedAt and updatedBy should be NULL on creation - only set on actual updates
        this.updatedAt = null;
        this.updatedBy = null;
    }

    public SystemConfiguration(String configKey, String configValue, ConfigType configType, 
                             String description, ConfigCategory category) {
        this();
        this.configKey = configKey;
        this.configValue = configValue;
        this.configType = configType;
        this.description = description;
        this.category = category;
    }

    /**
     * Mark entity as updated by specified user. Should be called for all business logic updates.
     * This properly maintains the audit trail for UPDATE operations.
     */
    public void markAsUpdated(UUID updatedBy) {
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = Objects.requireNonNull(updatedBy, "Updated by cannot be null");
    }

    // Utility methods for type conversion
    public String getStringValue() {
        return configValue;
    }

    public Integer getIntegerValue() {
        if (configType != ConfigType.INTEGER) {
            return null;
        }
        try {
            return Integer.parseInt(configValue);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Configuration value '" + configKey + 
                "' is not a valid integer: " + configValue);
        }
    }

    public Boolean getBooleanValue() {
        if (configType != ConfigType.BOOLEAN) {
            return null;
        }
        return Boolean.parseBoolean(configValue);
    }

    public Long getLongValue() {
        if (configType != ConfigType.INTEGER) {
            return null;
        }
        try {
            return Long.parseLong(configValue);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Configuration value '" + configKey + 
                "' is not a valid long: " + configValue);
        }
    }

    public Double getDoubleValue() {
        if (configType != ConfigType.INTEGER) {
            return null;
        }
        try {
            return Double.parseDouble(configValue);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Configuration value '" + configKey + 
                "' is not a valid double: " + configValue);
        }
    }

    public void setValueFromObject(Object value) {
        if (value == null) {
            this.configValue = null;
            return;
        }
        
        switch (configType) {
            case BOOLEAN:
                this.configValue = String.valueOf(value);
                break;
            case INTEGER:
                if (value instanceof Number) {
                    this.configValue = String.valueOf(((Number) value).intValue());
                } else {
                    this.configValue = String.valueOf(value);
                }
                break;
            case STRING:
            case JSON:
            default:
                this.configValue = String.valueOf(value);
                break;
        }
    }

    // Validation methods
    public boolean isValidValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        try {
            switch (configType) {
                case INTEGER:
                    Integer.parseInt(value);
                    return true;
                case BOOLEAN:
                    String lowerValue = value.toLowerCase();
                    return "true".equals(lowerValue) || "false".equals(lowerValue);
                case STRING:
                case JSON:
                    return true;
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String getValidationError(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Value cannot be empty";
        }

        switch (configType) {
            case INTEGER:
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return "Value must be a valid integer";
                }
                break;
            case BOOLEAN:
                String lowerValue = value.toLowerCase();
                if (!"true".equals(lowerValue) && !"false".equals(lowerValue)) {
                    return "Value must be 'true' or 'false'";
                }
                break;
        }
        return null;
    }

    // Static utility methods for common configurations
    public static SystemConfiguration dashboardRefreshInterval(String key, int milliseconds, String description) {
        return new SystemConfiguration(
            key, 
            String.valueOf(milliseconds), 
            ConfigType.INTEGER, 
            description, 
            ConfigCategory.DASHBOARD
        );
    }

    public static SystemConfiguration securitySetting(String key, String value, ConfigType type, String description) {
        return new SystemConfiguration(key, value, type, description, ConfigCategory.SECURITY);
    }

    public static SystemConfiguration fileProcessingSetting(String key, String value, ConfigType type, String description) {
        return new SystemConfiguration(key, value, type, description, ConfigCategory.FILE_PROCESSING);
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { 
        this.configValue = configValue;
    }

    public ConfigType getConfigType() { return configType; }
    public void setConfigType(ConfigType configType) { this.configType = configType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ConfigCategory getCategory() { return category; }
    public void setCategory(ConfigCategory category) { this.category = category; }

    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }

    public boolean isReadonly() { return readonly; }
    public void setReadonly(boolean readonly) { this.readonly = readonly; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    /**
     * Sets the creation timestamp. Should only be used during INSERT operations.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /**
     * Sets the update timestamp. Should only be used during UPDATE operations by persistence layer.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    /**
     * Sets the user who created this entity. Should only be used during INSERT operations.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { 
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return String.format("SystemConfiguration{key='%s', value='%s', type=%s, category=%s}", 
                configKey, configValue, configType, category);
    }
}