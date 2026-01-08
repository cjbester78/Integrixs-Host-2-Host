package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * FlowUtility entity representing available utilities for flow processing
 * Defines reusable processing components like PGP, ZIP, transformation utilities
 */
public class FlowUtility {
    
    private UUID id;
    private String name;
    private UtilityType utilityType;
    private String description;
    
    // Configuration schema and defaults
    private Map<String, Object> configurationSchema; // JSON schema for this utility type
    private Map<String, Object> defaultConfiguration; // Default configuration values
    
    // Processing capabilities
    private Boolean supportsParallel;
    private Integer maxFileSizeMb; // 0 = unlimited
    private List<String> supportedFormats; // Array of file extensions/types
    
    // Status
    private Boolean active;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    
    // Enums
    public enum UtilityType {
        PGP_ENCRYPT("PGP Encryption", "Encrypt files using PGP encryption"),
        PGP_DECRYPT("PGP Decryption", "Decrypt PGP encrypted files"),
        ZIP_COMPRESS("ZIP Compression", "Create ZIP archives from files"),
        ZIP_EXTRACT("ZIP Extraction", "Extract files from ZIP archives"),
        FILE_SPLIT("File Splitter", "Split large files into smaller chunks"),
        FILE_MERGE("File Merger", "Merge multiple files into one"),
        DATA_TRANSFORM("Data Transformation", "Transform data between formats"),
        FILE_VALIDATE("File Validation", "Validate file formats and integrity"),
        CUSTOM_SCRIPT("Custom Script", "Execute custom processing scripts");
        
        private final String displayName;
        private final String description;
        
        UtilityType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isEncryption() {
            return this == PGP_ENCRYPT || this == PGP_DECRYPT;
        }
        
        public boolean isCompression() {
            return this == ZIP_COMPRESS || this == ZIP_EXTRACT;
        }
        
        public boolean isFileOperation() {
            return this == FILE_SPLIT || this == FILE_MERGE || this == FILE_VALIDATE;
        }
        
        public boolean isTransformation() {
            return this == DATA_TRANSFORM;
        }
        
        public boolean isCustom() {
            return this == CUSTOM_SCRIPT;
        }
    }
    
    // Constructors
    public FlowUtility() {
        this.supportsParallel = false;
        this.maxFileSizeMb = 0; // unlimited
        this.active = true;
        this.createdAt = LocalDateTime.now();
        // updatedAt and updatedBy should be NULL on creation - only set on actual updates
        this.updatedAt = null;
        this.updatedBy = null;
    }
    
    public FlowUtility(String name, UtilityType utilityType, String description, 
                      Map<String, Object> configurationSchema, Map<String, Object> defaultConfiguration, 
                      UUID createdBy) {
        this();
        this.name = name;
        this.utilityType = utilityType;
        this.description = description;
        this.configurationSchema = configurationSchema;
        this.defaultConfiguration = defaultConfiguration;
        this.createdBy = createdBy;
    }
    
    /**
     * Mark entity as updated by specified user. Should be called for all business logic updates.
     * This properly maintains the audit trail for UPDATE operations.
     */
    public void markAsUpdated(UUID updatedBy) {
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = Objects.requireNonNull(updatedBy, "Updated by cannot be null");
    }
    
    // Business logic methods
    public boolean supportsFileType(String fileExtension) {
        if (supportedFormats == null || supportedFormats.isEmpty()) {
            return true; // No restrictions
        }
        
        String extension = fileExtension.toLowerCase();
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        
        return supportedFormats.contains(extension) || supportedFormats.contains("*");
    }
    
    public boolean canProcessFileSize(long fileSizeBytes) {
        if (maxFileSizeMb == null || maxFileSizeMb == 0) {
            return true; // No size limit
        }
        
        long maxSizeBytes = maxFileSizeMb * 1024L * 1024L;
        return fileSizeBytes <= maxSizeBytes;
    }
    
    public boolean isReadyForUse() {
        return active != null && active && 
               configurationSchema != null && 
               !configurationSchema.isEmpty();
    }
    
    public String getFormattedMaxFileSize() {
        if (maxFileSizeMb == null || maxFileSizeMb == 0) {
            return "Unlimited";
        }
        
        if (maxFileSizeMb < 1024) {
            return maxFileSizeMb + " MB";
        } else {
            double gb = maxFileSizeMb / 1024.0;
            return String.format("%.1f GB", gb);
        }
    }
    
    public String getFormattedSupportedFormats() {
        if (supportedFormats == null || supportedFormats.isEmpty()) {
            return "All formats";
        }
        
        if (supportedFormats.size() <= 3) {
            return String.join(", ", supportedFormats);
        } else {
            return String.join(", ", supportedFormats.subList(0, 3)) + " and " + (supportedFormats.size() - 3) + " more";
        }
    }
    
    public String getTypeIcon() {
        switch (utilityType) {
            case PGP_ENCRYPT:
            case PGP_DECRYPT: return "🔐";
            case ZIP_COMPRESS:
            case ZIP_EXTRACT: return "📦";
            case FILE_SPLIT:
            case FILE_MERGE: return "✂️";
            case DATA_TRANSFORM: return "🔄";
            case FILE_VALIDATE: return "✅";
            case CUSTOM_SCRIPT: return "🔧";
            default: return "⚙️";
        }
    }
    
    public String getStatusIcon() {
        if (!isReadyForUse()) {
            return "❌"; // Not configured properly
        } else if (active) {
            return "✅"; // Ready and enabled
        } else {
            return "⏸️"; // Disabled
        }
    }
    
    public Map<String, Object> mergeWithDefaults(Map<String, Object> userConfiguration) {
        Map<String, Object> merged = defaultConfiguration != null ? 
            new java.util.HashMap<>(defaultConfiguration) : 
            new java.util.HashMap<>();
        
        if (userConfiguration != null) {
            merged.putAll(userConfiguration);
        }
        
        return merged;
    }
    
    public boolean validateConfiguration(Map<String, Object> configuration) {
        // Basic validation - in a real implementation this would validate against the JSON schema
        if (configurationSchema == null) {
            return true; // No schema to validate against
        }
        
        // Check required fields
        Map<String, Object> properties = (Map<String, Object>) configurationSchema.get("properties");
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String fieldName = entry.getKey();
                Map<String, Object> fieldSchema = (Map<String, Object>) entry.getValue();
                
                if (fieldSchema.containsKey("required") && (Boolean) fieldSchema.get("required")) {
                    if (configuration == null || !configuration.containsKey(fieldName)) {
                        return false; // Required field missing
                    }
                }
            }
        }
        
        return true;
    }
    
    public void updateFormatSupport(List<String> formats) {
        this.supportedFormats = formats;
    }
    
    public void updateConfiguration(Map<String, Object> newSchema, Map<String, Object> newDefaults) {
        this.configurationSchema = newSchema;
        this.defaultConfiguration = newDefaults;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public UtilityType getUtilityType() {
        return utilityType;
    }
    
    public void setUtilityType(UtilityType utilityType) {
        this.utilityType = utilityType;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, Object> getConfigurationSchema() {
        return configurationSchema;
    }
    
    public void setConfigurationSchema(Map<String, Object> configurationSchema) {
        this.configurationSchema = configurationSchema;
    }
    
    public Map<String, Object> getDefaultConfiguration() {
        return defaultConfiguration;
    }
    
    public void setDefaultConfiguration(Map<String, Object> defaultConfiguration) {
        this.defaultConfiguration = defaultConfiguration;
    }
    
    public Boolean getSupportsParallel() {
        return supportsParallel;
    }
    
    public void setSupportsParallel(Boolean supportsParallel) {
        this.supportsParallel = supportsParallel;
    }
    
    public boolean isSupportsParallel() {
        return supportsParallel != null && supportsParallel;
    }
    
    public Integer getMaxFileSizeMb() {
        return maxFileSizeMb;
    }
    
    public void setMaxFileSizeMb(Integer maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }
    
    public List<String> getSupportedFormats() {
        return supportedFormats;
    }
    
    public void setSupportedFormats(List<String> supportedFormats) {
        this.supportedFormats = supportedFormats;
    }
    
    public Boolean getActive() {
        return active;
    }
    
    public void setActive(Boolean active) {
        this.active = active;
    }
    
    public boolean isActive() {
        return active != null && active;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Sets the creation timestamp. Should only be used during INSERT operations.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Sets the update timestamp. Should only be used during UPDATE operations by persistence layer.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
    /**
     * Sets the user who created this entity. Should only be used during INSERT operations.
     * Protected visibility to prevent misuse in business logic.
     */
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
    
    public UUID getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "FlowUtility{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", utilityType=" + utilityType +
                ", supportsParallel=" + supportsParallel +
                ", maxFileSizeMb=" + maxFileSizeMb +
                ", active=" + active +
                ", supportedFormats=" + getFormattedSupportedFormats() +
                ", createdAt=" + createdAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowUtility that = (FlowUtility) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}