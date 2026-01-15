package com.integrixs.backend.dto;

import com.integrixs.shared.model.IntegrationPackage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTOs for Package operations
 * Extracted from monolithic PackageController for better organization
 */
public class PackageRequest {
    
    /**
     * Create package request DTO
     */
    public static class Create {
        private String name;
        private String description;
        private String version;
        private Map<String, Object> configuration;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        
        public Map<String, Object> getConfiguration() { return configuration; }
        public void setConfiguration(Map<String, Object> configuration) { this.configuration = configuration; }
        
    }
    
    /**
     * Update package request DTO
     */
    public static class Update {
        private String name;
        private String description;
        private String version;
        private IntegrationPackage.PackageStatus status;
        private Map<String, Object> configuration;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        
        public IntegrationPackage.PackageStatus getStatus() { return status; }
        public void setStatus(IntegrationPackage.PackageStatus status) { this.status = status; }
        
        public Map<String, Object> getConfiguration() { return configuration; }
        public void setConfiguration(Map<String, Object> configuration) { this.configuration = configuration; }
        
    }
    
    /**
     * Move asset request DTO
     */
    public static class MoveAsset {
        private UUID assetId;
        private String assetType;
        private UUID toPackageId;
        
        // Getters and Setters
        public UUID getAssetId() { return assetId; }
        public void setAssetId(UUID assetId) { this.assetId = assetId; }
        
        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }
        
        public UUID getToPackageId() { return toPackageId; }
        public void setToPackageId(UUID toPackageId) { this.toPackageId = toPackageId; }
    }
}