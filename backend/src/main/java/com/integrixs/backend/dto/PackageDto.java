package com.integrixs.backend.dto;

import com.integrixs.shared.model.IntegrationPackage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Objects for Package-related API responses
 * Extracted from monolithic PackageController for better organization
 */
public class PackageDto {
    
    /**
     * Main package DTO for API responses
     */
    public static class Package {
        private UUID id;
        private String name;
        private String description;
        private String version;
        private IntegrationPackage.PackageStatus status;
        private Map<String, Object> configuration;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private UUID createdBy;
        private UUID updatedBy;

        // Asset counts for frontend statistics
        private int adapterCount;
        private int activeAdapterCount;
        private int flowCount;
        private int activeFlowCount;
        private int totalAssetCount;
        private int totalActiveAssetCount;

        // Getters and Setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

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


        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

        public UUID getCreatedBy() { return createdBy; }
        public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

        public UUID getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

        // Asset count getters and setters
        public int getAdapterCount() { return adapterCount; }
        public void setAdapterCount(int adapterCount) { this.adapterCount = adapterCount; }

        public int getActiveAdapterCount() { return activeAdapterCount; }
        public void setActiveAdapterCount(int activeAdapterCount) { this.activeAdapterCount = activeAdapterCount; }

        public int getFlowCount() { return flowCount; }
        public void setFlowCount(int flowCount) { this.flowCount = flowCount; }

        public int getActiveFlowCount() { return activeFlowCount; }
        public void setActiveFlowCount(int activeFlowCount) { this.activeFlowCount = activeFlowCount; }

        public int getTotalAssetCount() { return totalAssetCount; }
        public void setTotalAssetCount(int totalAssetCount) { this.totalAssetCount = totalAssetCount; }

        public int getTotalActiveAssetCount() { return totalActiveAssetCount; }
        public void setTotalActiveAssetCount(int totalActiveAssetCount) { this.totalActiveAssetCount = totalActiveAssetCount; }
    }
    
    /**
     * Package container DTO
     */
    public static class Container {
        private Package packageInfo;
        private List<Adapter> adapters;
        private List<Flow> flows;
        private int totalAssetCount;
        private boolean hasAssets;
        
        // Getters and Setters
        public Package getPackageInfo() { return packageInfo; }
        public void setPackageInfo(Package packageInfo) { this.packageInfo = packageInfo; }
        
        public List<Adapter> getAdapters() { return adapters; }
        public void setAdapters(List<Adapter> adapters) { this.adapters = adapters; }
        
        public List<Flow> getFlows() { return flows; }
        public void setFlows(List<Flow> flows) { this.flows = flows; }
        
        public int getTotalAssetCount() { return totalAssetCount; }
        public void setTotalAssetCount(int totalAssetCount) { this.totalAssetCount = totalAssetCount; }
        
        public boolean isHasAssets() { return hasAssets; }
        public void setHasAssets(boolean hasAssets) { this.hasAssets = hasAssets; }
    }
    
    /**
     * Package summary DTO
     */
    public static class Summary {
        private UUID packageId;
        private String packageName;
        private long totalAdapters;
        private long activeAdapters;
        private long totalFlows;
        private long activeFlows;
        private long scheduledFlows;
        private long deployedFlows;
        private long dependencies;
        private double adapterSuccessRate;
        private double flowSuccessRate;
        
        // Getters
        public UUID getPackageId() { return packageId; }
        public void setPackageId(UUID packageId) { this.packageId = packageId; }
        
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        
        public long getTotalAdapters() { return totalAdapters; }
        public void setTotalAdapters(long totalAdapters) { this.totalAdapters = totalAdapters; }
        
        public long getActiveAdapters() { return activeAdapters; }
        public void setActiveAdapters(long activeAdapters) { this.activeAdapters = activeAdapters; }
        
        public long getTotalFlows() { return totalFlows; }
        public void setTotalFlows(long totalFlows) { this.totalFlows = totalFlows; }
        
        public long getActiveFlows() { return activeFlows; }
        public void setActiveFlows(long activeFlows) { this.activeFlows = activeFlows; }
        
        public long getScheduledFlows() { return scheduledFlows; }
        public void setScheduledFlows(long scheduledFlows) { this.scheduledFlows = scheduledFlows; }
        
        public long getDeployedFlows() { return deployedFlows; }
        public void setDeployedFlows(long deployedFlows) { this.deployedFlows = deployedFlows; }
        
        public long getDependencies() { return dependencies; }
        public void setDependencies(long dependencies) { this.dependencies = dependencies; }
        
        public double getAdapterSuccessRate() { return adapterSuccessRate; }
        public void setAdapterSuccessRate(double adapterSuccessRate) { this.adapterSuccessRate = adapterSuccessRate; }
        
        public double getFlowSuccessRate() { return flowSuccessRate; }
        public void setFlowSuccessRate(double flowSuccessRate) { this.flowSuccessRate = flowSuccessRate; }
        
        public long getTotalAssets() { return totalAdapters + totalFlows; }
        public long getActiveAssets() { return activeAdapters + activeFlows; }
    }
    
    /**
     * Adapter DTO
     */
    public static class Adapter {
        private UUID id;
        private String name;
        private String description;
        private String adapterType;
        private String direction;
        private Boolean active;
        private Boolean connectionValidated;
        private UUID packageId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        // Getters and Setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getAdapterType() { return adapterType; }
        public void setAdapterType(String adapterType) { this.adapterType = adapterType; }
        
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        
        public Boolean getConnectionValidated() { return connectionValidated; }
        public void setConnectionValidated(Boolean connectionValidated) { this.connectionValidated = connectionValidated; }
        
        public UUID getPackageId() { return packageId; }
        public void setPackageId(UUID packageId) { this.packageId = packageId; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
    
    /**
     * Flow DTO
     */
    public static class Flow {
        private UUID id;
        private String name;
        private String description;
        private String flowType;
        private Boolean active;
        private Boolean scheduleEnabled;
        private Boolean deployed;
        private UUID packageId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        // Getters and Setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getFlowType() { return flowType; }
        public void setFlowType(String flowType) { this.flowType = flowType; }
        
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        
        public Boolean getScheduleEnabled() { return scheduleEnabled; }
        public void setScheduleEnabled(Boolean scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }

        public Boolean getDeployed() { return deployed; }
        public void setDeployed(Boolean deployed) { this.deployed = deployed; }

        public UUID getPackageId() { return packageId; }
        public void setPackageId(UUID packageId) { this.packageId = packageId; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
    
    /**
     * Package asset DTO
     */
    public static class Asset {
        private String assetType;
        private UUID assetId;
        private String name;
        private UUID packageId;
        private Boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        // Getters and Setters
        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }
        
        public UUID getAssetId() { return assetId; }
        public void setAssetId(UUID assetId) { this.assetId = assetId; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public UUID getPackageId() { return packageId; }
        public void setPackageId(UUID packageId) { this.packageId = packageId; }
        
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
    
    /**
     * Package deployment readiness DTO
     */
    public static class DeploymentReadiness {
        private UUID packageId;
        private String packageName;
        private boolean ready;
        private List<String> issues;
        private Map<String, Object> readinessMetrics;
        
        // Getters and Setters
        public UUID getPackageId() { return packageId; }
        public void setPackageId(UUID packageId) { this.packageId = packageId; }
        
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
        
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
        
        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }
        
        public Map<String, Object> getReadinessMetrics() { return readinessMetrics; }
        public void setReadinessMetrics(Map<String, Object> readinessMetrics) { this.readinessMetrics = readinessMetrics; }
    }
    
    /**
     * Package dependency validation DTO
     */
    public static class DependencyValidation {
        private UUID packageId;
        private boolean valid;
        private boolean hasCircularDependencies;
        private int circularDependencyCount;
        
        // Getters and Setters
        public UUID getPackageId() { return packageId; }
        public void setPackageId(UUID packageId) { this.packageId = packageId; }
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public boolean isHasCircularDependencies() { return hasCircularDependencies; }
        public void setHasCircularDependencies(boolean hasCircularDependencies) { this.hasCircularDependencies = hasCircularDependencies; }
        
        public int getCircularDependencyCount() { return circularDependencyCount; }
        public void setCircularDependencyCount(int circularDependencyCount) { this.circularDependencyCount = circularDependencyCount; }
    }
}