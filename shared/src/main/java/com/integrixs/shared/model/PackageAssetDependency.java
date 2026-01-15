package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * PackageAssetDependency entity for tracking relationships between package assets.
 * Manages dependencies between adapters and flows within packages to ensure
 * proper validation and deployment ordering.
 */
public class PackageAssetDependency {
    
    // Enum for asset types
    public enum AssetType {
        ADAPTER("Adapter asset"),
        FLOW("Flow asset");
        
        private final String description;
        
        AssetType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Enum for dependency types
    public enum DependencyType {
        REQUIRED("Asset cannot function without this dependency"),
        OPTIONAL("Asset can function without this dependency but prefers it"),
        WEAK("Loose dependency for informational purposes");
        
        private final String description;
        
        DependencyType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isBlocking() {
            return this == REQUIRED;
        }
    }
    
    private UUID id;
    private UUID packageId;
    
    // Asset identification
    private AssetType assetType;
    private UUID assetId;
    
    // Dependency identification
    private AssetType dependsOnAssetType;
    private UUID dependsOnAssetId;
    
    // Dependency metadata
    private DependencyType dependencyType;
    private String dependencyDescription;
    
    // Audit columns
    private LocalDateTime createdAt;
    private UUID createdBy;
    
    // Constructors
    public PackageAssetDependency() {
        this.dependencyType = DependencyType.REQUIRED;
        this.createdAt = LocalDateTime.now();
    }
    
    public PackageAssetDependency(UUID packageId, AssetType assetType, UUID assetId,
                                  AssetType dependsOnAssetType, UUID dependsOnAssetId, 
                                  UUID createdBy) {
        this();
        this.packageId = Objects.requireNonNull(packageId, "Package ID cannot be null");
        this.assetType = Objects.requireNonNull(assetType, "Asset type cannot be null");
        this.assetId = Objects.requireNonNull(assetId, "Asset ID cannot be null");
        this.dependsOnAssetType = Objects.requireNonNull(dependsOnAssetType, "Depends on asset type cannot be null");
        this.dependsOnAssetId = Objects.requireNonNull(dependsOnAssetId, "Depends on asset ID cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "Created by cannot be null");
        
        // Validate no self-dependency
        if (assetType == dependsOnAssetType && assetId.equals(dependsOnAssetId)) {
            throw new IllegalArgumentException("Asset cannot depend on itself");
        }
    }
    
    public PackageAssetDependency(UUID packageId, AssetType assetType, UUID assetId,
                                  AssetType dependsOnAssetType, UUID dependsOnAssetId,
                                  DependencyType dependencyType, String description, UUID createdBy) {
        this(packageId, assetType, assetId, dependsOnAssetType, dependsOnAssetId, createdBy);
        this.dependencyType = dependencyType != null ? dependencyType : DependencyType.REQUIRED;
        this.dependencyDescription = description;
    }
    
    // Business logic methods
    public boolean isRequiredDependency() {
        return dependencyType == DependencyType.REQUIRED;
    }
    
    public boolean isOptionalDependency() {
        return dependencyType == DependencyType.OPTIONAL;
    }
    
    public boolean isWeakDependency() {
        return dependencyType == DependencyType.WEAK;
    }
    
    public boolean isAdapterDependency() {
        return dependsOnAssetType == AssetType.ADAPTER;
    }
    
    public boolean isFlowDependency() {
        return dependsOnAssetType == AssetType.FLOW;
    }
    
    public boolean isWithinSamePackage(UUID otherPackageId) {
        return packageId != null && packageId.equals(otherPackageId);
    }
    
    public String getDependencyLabel() {
        return String.format("%s %s depends on %s %s (%s)",
            assetType.name(), 
            assetId,
            dependsOnAssetType.name(),
            dependsOnAssetId,
            dependencyType.name());
    }
    
    /**
     * Create a flow-to-adapter dependency
     */
    public static PackageAssetDependency flowDependsOnAdapter(UUID packageId, UUID flowId, UUID adapterId, UUID createdBy) {
        return new PackageAssetDependency(packageId, AssetType.FLOW, flowId, AssetType.ADAPTER, adapterId, createdBy);
    }
    
    /**
     * Create an adapter-to-adapter dependency
     */
    public static PackageAssetDependency adapterDependsOnAdapter(UUID packageId, UUID assetAdapterId, UUID dependsOnAdapterId, UUID createdBy) {
        return new PackageAssetDependency(packageId, AssetType.ADAPTER, assetAdapterId, AssetType.ADAPTER, dependsOnAdapterId, createdBy);
    }
    
    /**
     * Create a flow-to-flow dependency
     */
    public static PackageAssetDependency flowDependsOnFlow(UUID packageId, UUID assetFlowId, UUID dependsOnFlowId, UUID createdBy) {
        return new PackageAssetDependency(packageId, AssetType.FLOW, assetFlowId, AssetType.FLOW, dependsOnFlowId, createdBy);
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getPackageId() {
        return packageId;
    }
    
    public void setPackageId(UUID packageId) {
        this.packageId = packageId;
    }
    
    public AssetType getAssetType() {
        return assetType;
    }
    
    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }
    
    public UUID getAssetId() {
        return assetId;
    }
    
    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }
    
    public AssetType getDependsOnAssetType() {
        return dependsOnAssetType;
    }
    
    public void setDependsOnAssetType(AssetType dependsOnAssetType) {
        this.dependsOnAssetType = dependsOnAssetType;
    }
    
    public UUID getDependsOnAssetId() {
        return dependsOnAssetId;
    }
    
    public void setDependsOnAssetId(UUID dependsOnAssetId) {
        this.dependsOnAssetId = dependsOnAssetId;
    }
    
    public DependencyType getDependencyType() {
        return dependencyType;
    }
    
    public void setDependencyType(DependencyType dependencyType) {
        this.dependencyType = dependencyType;
    }
    
    public String getDependencyDescription() {
        return dependencyDescription;
    }
    
    public void setDependencyDescription(String dependencyDescription) {
        this.dependencyDescription = dependencyDescription;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
    
    @Override
    public String toString() {
        return "PackageAssetDependency{" +
                "id=" + id +
                ", packageId=" + packageId +
                ", assetType=" + assetType +
                ", assetId=" + assetId +
                ", dependsOnAssetType=" + dependsOnAssetType +
                ", dependsOnAssetId=" + dependsOnAssetId +
                ", dependencyType=" + dependencyType +
                ", dependencyDescription='" + dependencyDescription + '\'' +
                ", createdAt=" + createdAt +
                ", createdBy=" + createdBy +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackageAssetDependency that = (PackageAssetDependency) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}