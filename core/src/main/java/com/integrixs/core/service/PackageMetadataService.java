package com.integrixs.core.service;

import com.integrixs.shared.model.IntegrationPackage;
import com.integrixs.core.repository.IntegrationPackageRepository;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.core.repository.PackageAssetDependencyRepository;
import com.integrixs.shared.util.AuditUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing IntegrationPackage metadata and lifecycle operations.
 * Follows SOLID principles with encapsulated business logic and validation.
 * 
 * @author Claude Code
 * @since Package Management V1.0
 */
@Service
@Transactional
public class PackageMetadataService {
    
    private final IntegrationPackageRepository packageRepository;
    private final AdapterRepository adapterRepository;
    private final IntegrationFlowRepository flowRepository;
    private final PackageAssetDependencyRepository dependencyRepository;
    
    @Autowired
    public PackageMetadataService(
            IntegrationPackageRepository packageRepository,
            AdapterRepository adapterRepository,
            IntegrationFlowRepository flowRepository,
            PackageAssetDependencyRepository dependencyRepository) {
        this.packageRepository = Objects.requireNonNull(packageRepository, "Package repository cannot be null");
        this.adapterRepository = Objects.requireNonNull(adapterRepository, "Adapter repository cannot be null");
        this.flowRepository = Objects.requireNonNull(flowRepository, "Flow repository cannot be null");
        this.dependencyRepository = Objects.requireNonNull(dependencyRepository, "Dependency repository cannot be null");
    }
    
    /**
     * Create a new integration package with validation.
     * 
     * @param packageRequest Package creation request
     * @return Created package with generated ID
     * @throws IllegalArgumentException if validation fails
     */
    public IntegrationPackage createPackage(PackageCreationRequest packageRequest) {
        validatePackageCreationRequest(packageRequest);
        
        IntegrationPackage newPackage = buildPackageFromRequest(packageRequest);
        
        UUID packageId = packageRepository.save(newPackage);
        
        return findPackageById(packageId);
    }
    
    /**
     * Update existing package metadata.
     * 
     * @param packageId Package UUID to update
     * @param updateRequest Package update request
     * @return Updated package
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if package not found
     */
    public IntegrationPackage updatePackage(UUID packageId, PackageUpdateRequest updateRequest) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        validatePackageUpdateRequest(updateRequest);
        
        IntegrationPackage existingPackage = findPackageById(packageId);
        
        applyUpdateToPackage(existingPackage, updateRequest);
        packageRepository.update(existingPackage);
        
        return findPackageById(packageId);
    }
    
    /**
     * Retrieve package by ID.
     * 
     * @param packageId Package UUID
     * @return Package if found
     * @throws IllegalStateException if package not found
     */
    @Transactional(readOnly = true)
    public IntegrationPackage findPackageById(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        return packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageId));
    }
    
    /**
     * Retrieve package by name.
     * 
     * @param packageName Package name
     * @return Package if found
     * @throws IllegalStateException if package not found
     */
    @Transactional(readOnly = true)
    public IntegrationPackage findPackageByName(String packageName) {
        validatePackageName(packageName);
        
        return packageRepository.findByName(packageName)
                .orElseThrow(() -> new IllegalStateException("Package not found: " + packageName));
    }
    
    /**
     * Find all active packages.
     * 
     * @return List of active packages ordered by creation date
     */
    @Transactional(readOnly = true)
    public List<IntegrationPackage> findAllActivePackages() {
        return packageRepository.findAllActive();
    }
    
    /**
     * Find packages by status.
     * 
     * @param status Package status
     * @return List of packages with specified status
     */
    @Transactional(readOnly = true)
    public List<IntegrationPackage> findPackagesByStatus(IntegrationPackage.PackageStatus status) {
        Objects.requireNonNull(status, "Package status cannot be null");
        
        return packageRepository.findByStatus(status);
    }
    
    /**
     * Find packages created by specific user.
     * 
     * @param userId User UUID
     * @return List of packages created by user
     */
    @Transactional(readOnly = true)
    public List<IntegrationPackage> findPackagesByCreatedBy(UUID userId) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        
        return packageRepository.findByCreatedBy(userId);
    }
    
    /**
     * Get package summary with asset counts.
     * 
     * @param packageId Package UUID
     * @return Package summary with asset statistics
     */
    @Transactional(readOnly = true)
    public PackageSummary getPackageSummary(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        IntegrationPackage pkg = findPackageById(packageId);
        
        long adapterCount = adapterRepository.countByPackageId(packageId);
        long activeAdapterCount = adapterRepository.findActiveByPackageId(packageId).size();
        long flowCount = flowRepository.countByPackageId(packageId);
        long activeFlowCount = flowRepository.findActiveByPackageId(packageId).size();
        long dependencyCount = dependencyRepository.findByPackageId(packageId).size();
        
        return new PackageSummary(pkg, adapterCount, activeAdapterCount, flowCount, activeFlowCount, dependencyCount);
    }
    
    /**
     * Check if package name is available.
     * 
     * @param packageName Package name to check
     * @return true if name is available, false if already exists
     */
    @Transactional(readOnly = true)
    public boolean isPackageNameAvailable(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        
        return !packageRepository.existsByName(packageName.trim());
    }
    
    /**
     * Delete package (hard delete - soft delete functionality removed).
     * 
     * @param packageId Package UUID to delete
     * @return true if package was deleted
     * @throws IllegalStateException if package has active assets
     */
    public boolean deletePackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        validatePackageCanBeDeleted(packageId);
        
        // Clean up dependencies first
        dependencyRepository.deleteAllForPackage(packageId);
        
        return packageRepository.deletePackage(packageId);
    }
    
    // Archive and permanent delete functionality merged into main deletePackage method
    
    /**
     * Get package statistics for dashboard.
     * 
     * @return Map containing package statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPackageStatistics() {
        return packageRepository.getPackageStatistics();
    }
    
    // Private helper methods following OOP encapsulation principles
    
    /**
     * Validate package creation request.
     */
    private void validatePackageCreationRequest(PackageCreationRequest request) {
        Objects.requireNonNull(request, "Package creation request cannot be null");
        
        validatePackageName(request.getName());
        
        if (!isPackageNameAvailable(request.getName())) {
            throw new IllegalArgumentException("Package name already exists: " + request.getName());
        }
        
        // Package type removed - all packages are integration packages
        
        if (request.getVersion() == null || request.getVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("Package version cannot be null or empty");
        }
    }
    
    /**
     * Validate package update request.
     */
    private void validatePackageUpdateRequest(PackageUpdateRequest request) {
        Objects.requireNonNull(request, "Package update request cannot be null");
        
        if (request.getName() != null) {
            validatePackageName(request.getName());
        }
        
        if (request.getVersion() != null && request.getVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("Package version cannot be empty");
        }
    }
    
    /**
     * Validate package name.
     */
    private void validatePackageName(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("Package name cannot be null or empty");
        }
        
        if (packageName.trim().length() > 255) {
            throw new IllegalArgumentException("Package name cannot exceed 255 characters");
        }
        
        // Check for invalid characters
        if (!packageName.matches("^[a-zA-Z0-9\\s\\-_\\.]+$")) {
            throw new IllegalArgumentException("Package name contains invalid characters");
        }
    }
    
    /**
     * Build package from creation request.
     */
    private IntegrationPackage buildPackageFromRequest(PackageCreationRequest request) {
        IntegrationPackage pkg = new IntegrationPackage();
        pkg.setName(request.getName().trim());
        pkg.setDescription(request.getDescription());
        pkg.setVersion(request.getVersion().trim());
        // Package type removed
        // Status is automatically set to ACTIVE by constructor
        pkg.setConfiguration(request.getConfiguration() != null ? new HashMap<>(request.getConfiguration()) : new HashMap<>());
        // Tags removed from package system

        return pkg;
    }
    
    /**
     * Apply updates to existing package.
     */
    private void applyUpdateToPackage(IntegrationPackage pkg, PackageUpdateRequest request) {
        if (request.getName() != null) {
            // Check name availability excluding current package
            if (packageRepository.existsByNameAndNotId(request.getName().trim(), pkg.getId())) {
                throw new IllegalArgumentException("Package name already exists: " + request.getName());
            }
            pkg.setName(request.getName().trim());
        }
        
        if (request.getDescription() != null) {
            pkg.setDescription(request.getDescription());
        }
        
        if (request.getVersion() != null) {
            pkg.setVersion(request.getVersion().trim());
        }
        
        // Package type removed
        
        if (request.getStatus() != null) {
            pkg.setStatus(request.getStatus());
        }
        
        if (request.getConfiguration() != null) {
            pkg.setConfiguration(new HashMap<>(request.getConfiguration()));
        }
        
        if (request.getTags() != null) {
            // Tags removed from package system
        }
    }
    
    // Archive validation removed - using direct hard delete
    
    /**
     * Validate package can be permanently deleted.
     */
    private void validatePackageCanBeDeleted(UUID packageId) {
        long adapterCount = adapterRepository.countByPackageId(packageId);
        long flowCount = flowRepository.countByPackageId(packageId);
        
        if (adapterCount > 0 || flowCount > 0) {
            throw new IllegalStateException(
                String.format("Cannot permanently delete package - it contains %d adapters and %d flows. " +
                             "Remove all assets before deletion.",
                             adapterCount, flowCount));
        }
    }
    
    /**
     * Get current user ID for audit purposes.
     */
    private UUID getCurrentUserId() {
        try {
            return UUID.fromString(AuditUtils.getCurrentUserId());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unable to determine valid user ID for audit trail");
        }
    }
    
    // Inner classes for request/response DTOs following encapsulation principles
    
    /**
     * Request DTO for package creation.
     */
    public static class PackageCreationRequest {
        private String name;
        private String description;
        private String version;
        // Package type removed
        private Map<String, Object> configuration;
        private List<String> tags;
        
        // Constructors
        public PackageCreationRequest() {}
        
        public PackageCreationRequest(String name, String description, String version, 
                                    String ignored_packageType) {
            this.name = name;
            this.description = description;
            this.version = version;
            // Package type removed
            this.configuration = new HashMap<>();
            this.tags = new ArrayList<>();
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        // Package type methods removed
        // Package type methods removed
        
        public Map<String, Object> getConfiguration() { return configuration; }
        public void setConfiguration(Map<String, Object> configuration) { this.configuration = configuration; }
        
        public List<String> getTags() { return tags; }
        // Tags methods removed
    }
    
    /**
     * Request DTO for package updates.
     */
    public static class PackageUpdateRequest {
        private String name;
        private String description;
        private String version;
        // Package type removed
        private IntegrationPackage.PackageStatus status;
        private Map<String, Object> configuration;
        private List<String> tags;
        
        // Constructors
        public PackageUpdateRequest() {}
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        // Package type methods removed
        // Package type methods removed
        
        public IntegrationPackage.PackageStatus getStatus() { return status; }
        public void setStatus(IntegrationPackage.PackageStatus status) { this.status = status; }
        
        public Map<String, Object> getConfiguration() { return configuration; }
        public void setConfiguration(Map<String, Object> configuration) { this.configuration = configuration; }
        
        public List<String> getTags() { return tags; }
        // Tags methods removed
    }
    
    /**
     * Response DTO for package summary with asset counts.
     */
    public static class PackageSummary {
        private final IntegrationPackage packageInfo;
        private final long adapterCount;
        private final long activeAdapterCount;
        private final long flowCount;
        private final long activeFlowCount;
        private final long dependencyCount;
        
        public PackageSummary(IntegrationPackage packageInfo, long adapterCount, long activeAdapterCount,
                            long flowCount, long activeFlowCount, long dependencyCount) {
            this.packageInfo = Objects.requireNonNull(packageInfo, "Package info cannot be null");
            this.adapterCount = adapterCount;
            this.activeAdapterCount = activeAdapterCount;
            this.flowCount = flowCount;
            this.activeFlowCount = activeFlowCount;
            this.dependencyCount = dependencyCount;
        }
        
        // Getters (read-only)
        public IntegrationPackage getPackageInfo() { return packageInfo; }
        public long getAdapterCount() { return adapterCount; }
        public long getActiveAdapterCount() { return activeAdapterCount; }
        public long getFlowCount() { return flowCount; }
        public long getActiveFlowCount() { return activeFlowCount; }
        public long getDependencyCount() { return dependencyCount; }
        
        public long getTotalAssetCount() { return adapterCount + flowCount; }
        public long getTotalActiveAssetCount() { return activeAdapterCount + activeFlowCount; }
        public boolean hasAssets() { return getTotalAssetCount() > 0; }
        public boolean hasActiveAssets() { return getTotalActiveAssetCount() > 0; }
    }
}