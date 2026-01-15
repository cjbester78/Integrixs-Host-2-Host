package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * IntegrationPackage entity representing logical containers for organizing integration assets.
 * Packages provide organizational structure, access control boundaries, and deployment management
 * for adapters, flows, and their dependencies.
 */
public class IntegrationPackage {
    
    // Enum for package status
    public enum PackageStatus {
        ACTIVE("Package is active and can be used");

        private final String description;

        PackageStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean isActive() {
            return this == ACTIVE;
        }

        public boolean canModify() {
            return this == ACTIVE;
        }
    }
    
    
    private UUID id;
    private String name;
    private String description;
    private String version;
    
    // Package metadata
    private PackageStatus status;
    
    // Package configuration
    private Map<String, Object> configuration;
    
    
    // Audit columns
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    
    // Constructors
    public IntegrationPackage() {
        this.version = "1.0.0";
        this.status = PackageStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        // updatedAt and updatedBy should be NULL on creation - only set on actual updates
        this.updatedAt = null;
        this.updatedBy = null;
    }
    
    public IntegrationPackage(String name, String description, UUID createdBy) {
        this();
        this.name = validateName(name);
        this.description = description;
        this.createdBy = Objects.requireNonNull(createdBy, "Created by cannot be null");
        // Do not set updatedBy on creation - only set on actual updates
    }
    
    
    /**
     * Validate package name.
     */
    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Package name cannot be null or empty");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Package name cannot exceed 255 characters");
        }
        return name.trim();
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
    public boolean isActive() {
        return status != null && status.isActive();
    }
    
    public boolean canModify() {
        return status != null && status.canModify();
    }
    
    public boolean canDelete() {
        // Packages are always active, can only delete if explicitly allowed
        return true;
    }
    
    public String getDisplayName() {
        return String.format("%s (v%s)", name, version);
    }
    
    public String getStatusIcon() {
        return "✅"; // Active
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
        this.name = validateName(name);
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    
    public PackageStatus getStatus() {
        return status;
    }
    
    public void setStatus(PackageStatus status) {
        this.status = status;
    }
    
    public Map<String, Object> getConfiguration() {
        return configuration;
    }
    
    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
    
    
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
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
        return "IntegrationPackage{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy=" + createdBy +
                ", updatedBy=" + updatedBy +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntegrationPackage that = (IntegrationPackage) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}