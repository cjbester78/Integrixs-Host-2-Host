package com.integrixs.core.service;

import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Facade service for managing adapter configurations and operations
 * Delegates to specialized services following the facade pattern and Single Responsibility Principle
 * 
 * This service has been refactored from a monolithic 1,220-line class into focused services:
 * - AdapterCrudService: Basic CRUD operations
 * - AdapterQueryService: Complex queries and filtering 
 * - AdapterValidationService: Configuration validation and testing
 * - AdapterLifecycleService: State management and lifecycle operations
 * - AdapterPackageService: Package-related operations
 * - AdapterAnalyticsService: Statistics and reporting
 */
@Service
public class AdapterManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterManagementService.class);
    
    private final AdapterCrudService adapterCrudService;
    private final AdapterQueryService adapterQueryService;
    private final AdapterValidationService adapterValidationService;
    private final AdapterLifecycleService adapterLifecycleService;
    private final AdapterPackageService adapterPackageService;
    private final AdapterAnalyticsService adapterAnalyticsService;
    
    @Autowired
    public AdapterManagementService(
            AdapterCrudService adapterCrudService,
            AdapterQueryService adapterQueryService,
            AdapterValidationService adapterValidationService,
            AdapterLifecycleService adapterLifecycleService,
            AdapterPackageService adapterPackageService,
            AdapterAnalyticsService adapterAnalyticsService) {
        this.adapterCrudService = adapterCrudService;
        this.adapterQueryService = adapterQueryService;
        this.adapterValidationService = adapterValidationService;
        this.adapterLifecycleService = adapterLifecycleService;
        this.adapterPackageService = adapterPackageService;
        this.adapterAnalyticsService = adapterAnalyticsService;
    }
    
    // ===== CRUD Operations (delegated to AdapterCrudService) =====
    
    /**
     * Get all adapters
     */
    public List<Adapter> getAllAdapters() {
        return adapterCrudService.getAllAdapters();
    }
    
    /**
     * Get adapter by ID
     */
    public Optional<Adapter> getAdapterById(UUID id) {
        return adapterCrudService.getAdapterById(id);
    }
    
    /**
     * Get adapter by name
     */
    public Optional<Adapter> getAdapterByName(String name) {
        return adapterCrudService.getAdapterByName(name);
    }
    
    /**
     * Check if adapter exists by name
     */
    public boolean existsByName(String name) {
        return adapterCrudService.existsByName(name);
    }
    
    /**
     * Create new adapter with package context
     */
    public Adapter createAdapter(Adapter adapter, UUID packageId, UUID createdBy) {
        logger.info("Creating adapter: {} in package: {}", adapter.getName(), packageId);
        
        // Validate configuration first
        adapterValidationService.validateAdapterConfiguration(adapter);
        
        // Create the adapter
        UUID adapterId = adapterCrudService.createAdapter(adapter, packageId);
        
        // Return the created adapter
        return adapterCrudService.getAdapterById(adapterId).orElse(adapter);
    }
    
    /**
     * Create new adapter (legacy method for backward compatibility)
     */
    @Deprecated
    public Adapter createAdapter(Adapter adapter, UUID createdBy) {
        logger.info("Creating adapter: {} (legacy method)", adapter.getName());
        
        // Validate configuration first
        adapterValidationService.validateAdapterConfiguration(adapter);
        
        // Create the adapter without package context
        UUID adapterId = adapterCrudService.createAdapter(adapter);
        
        // Return the created adapter
        return adapterCrudService.getAdapterById(adapterId).orElse(adapter);
    }
    
    /**
     * Update existing adapter
     */
    public Adapter updateAdapter(UUID id, Adapter adapter, UUID updatedBy) {
        logger.info("Updating adapter: {}", id);

        // Get existing adapter to preserve package_id and other fields
        Optional<Adapter> existingOpt = adapterCrudService.getAdapterById(id);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Adapter not found: " + id);
        }
        Adapter existing = existingOpt.get();

        // Set the adapter ID for update
        adapter.setId(id);

        // Preserve package_id from existing adapter if not set in request
        if (adapter.getPackageId() == null) {
            adapter.setPackageId(existing.getPackageId());
        }

        // Validate configuration
        adapterValidationService.validateAdapterConfiguration(adapter);

        // Update the adapter
        adapterCrudService.updateAdapter(adapter);

        // Return the updated adapter
        return adapterCrudService.getAdapterById(id).orElse(adapter);
    }
    
    /**
     * Update adapter with package context
     */
    public Adapter updateAdapterWithPackageContext(UUID id, Adapter adapter, UUID packageId, UUID updatedBy) {
        logger.info("Updating adapter: {} in package: {}", id, packageId);
        
        // Set package context
        adapter.setId(id);
        adapter.setPackageId(packageId);
        
        // Validate configuration
        adapterValidationService.validateAdapterConfiguration(adapter);
        
        // Update the adapter
        adapterCrudService.updateAdapter(adapter);
        
        // Return the updated adapter
        return adapterCrudService.getAdapterById(id).orElse(adapter);
    }
    
    /**
     * Delete adapter
     */
    public boolean deleteAdapter(UUID id) {
        logger.info("Deleting adapter: {}", id);
        
        try {
            adapterCrudService.deleteAdapter(id);
            return true;
        } catch (Exception e) {
            logger.error("Failed to delete adapter: {}", id, e);
            return false;
        }
    }
    
    // ===== Query Operations (delegated to AdapterQueryService) =====
    
    /**
     * Get adapters by type
     */
    public List<Adapter> getAdaptersByType(String type) {
        return adapterQueryService.getAdaptersByType(type);
    }
    
    /**
     * Get adapters by type and direction
     */
    public List<Adapter> getAdaptersByTypeAndDirection(String type, String direction) {
        return adapterQueryService.getAdaptersByTypeAndDirection(type, direction);
    }
    
    /**
     * Get active adapters only
     */
    public List<Adapter> getActiveAdapters() {
        return adapterQueryService.getActiveAdapters();
    }
    
    /**
     * Get adapters by package ID
     */
    public List<Adapter> getAdaptersByPackageId(UUID packageId) {
        return adapterQueryService.getAdaptersByPackage(packageId);
    }
    
    /**
     * Get active adapters by package ID
     */
    public List<Adapter> getActiveAdaptersByPackageId(UUID packageId) {
        return adapterQueryService.getActiveAdaptersByPackage(packageId);
    }
    
    /**
     * Get adapters by package ID and type
     */
    public List<Adapter> getAdaptersByPackageIdAndType(UUID packageId, String adapterType) {
        return adapterQueryService.getAdaptersByPackageIdAndType(packageId, adapterType);
    }
    
    /**
     * Get adapters by type and package (alternative method signature)
     */
    public List<Adapter> getAdaptersByTypeAndPackage(String type, UUID packageId) {
        return adapterQueryService.getAdaptersByTypeAndPackage(type, packageId);
    }
    
    /**
     * Get adapters by package (alternative method signature)
     */
    public List<Adapter> getAdaptersByPackage(UUID packageId) {
        return adapterQueryService.getAdaptersByPackage(packageId);
    }
    
    /**
     * Get active adapters by package (alternative method signature)
     */
    public List<Adapter> getActiveAdaptersByPackage(UUID packageId) {
        return adapterQueryService.getActiveAdaptersByPackage(packageId);
    }
    
    /**
     * Get adapters by type, direction and package
     */
    public List<Adapter> getAdaptersByTypeDirectionAndPackage(String type, String direction, UUID packageId) {
        return adapterQueryService.getAdaptersByTypeDirectionAndPackage(type, direction, packageId);
    }
    
    /**
     * Check if adapter exists by name in package
     */
    public boolean existsByNameInPackage(String name, UUID packageId) {
        return adapterQueryService.existsByNameInPackage(name, packageId);
    }
    
    // ===== Validation Operations (delegated to AdapterValidationService) =====
    
    /**
     * Test adapter connection
     */
    public Map<String, Object> testAdapterConnection(UUID id) {
        logger.info("Testing connection for adapter: {}", id);
        
        Optional<Adapter> adapterOpt = adapterCrudService.getAdapterById(id);
        if (!adapterOpt.isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("testSuccess", false);
            result.put("error", "Adapter not found: " + id);
            return result;
        }
        
        return adapterValidationService.testAdapterConnection(adapterOpt.get());
    }
    
    // ===== Lifecycle Operations (delegated to AdapterLifecycleService) =====
    
    /**
     * Set adapter active/inactive status
     */
    public void setAdapterActive(UUID id, boolean active) {
        adapterLifecycleService.setAdapterActive(id, active);
    }
    
    /**
     * Start an adapter
     */
    public void startAdapter(UUID id) {
        adapterLifecycleService.startAdapter(id);
    }
    
    /**
     * Stop an adapter
     */
    public void stopAdapter(UUID id) {
        adapterLifecycleService.stopAdapter(id);
    }
    
    /**
     * Execute adapter operations (simplified version)
     */
    public Map<String, Object> executeAdapter(UUID id) {
        logger.info("Executing adapter: {}", id);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Check if adapter exists and is active
            Optional<Adapter> adapterOpt = adapterCrudService.getAdapterById(id);
            if (!adapterOpt.isPresent()) {
                result.put("success", false);
                result.put("error", "Adapter not found: " + id);
                return result;
            }
            
            Adapter adapter = adapterOpt.get();
            if (!adapter.getActive()) {
                result.put("success", false);
                result.put("error", "Adapter is not active: " + adapter.getName());
                return result;
            }
            
            // Start the adapter if not running
            if (!adapterLifecycleService.isAdapterRunning(id)) {
                adapterLifecycleService.startAdapter(id);
            }
            
            result.put("success", true);
            result.put("message", "Adapter executed successfully");
            result.put("adapterName", adapter.getName());
            result.put("adapterType", adapter.getAdapterType());
            
        } catch (Exception e) {
            logger.error("Failed to execute adapter: {}", id, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    // ===== Package Operations (delegated to AdapterPackageService) =====
    
    /**
     * Count adapters in package
     */
    public long countAdaptersInPackage(UUID packageId) {
        return adapterPackageService.countAdaptersInPackage(packageId);
    }
    
    /**
     * Move adapter to different package
     */
    public void moveAdapterToPackage(UUID adapterId, UUID toPackageId, UUID movedBy) {
        adapterPackageService.moveAdapterToPackage(adapterId, toPackageId, movedBy);
    }
    
    /**
     * Move adapter between packages with validation
     */
    public boolean moveAdapterBetweenPackages(UUID adapterId, UUID sourcePackageId, UUID targetPackageId, UUID userId) {
        logger.info("Moving adapter {} from package {} to package {}", adapterId, sourcePackageId, targetPackageId);
        
        try {
            // Verify adapter is in source package
            Optional<Adapter> adapterOpt = adapterCrudService.getAdapterById(adapterId);
            if (!adapterOpt.isPresent()) {
                logger.error("Adapter not found: {}", adapterId);
                return false;
            }
            
            Adapter adapter = adapterOpt.get();
            if (!sourcePackageId.equals(adapter.getPackageId())) {
                logger.error("Adapter {} is not in source package {}", adapterId, sourcePackageId);
                return false;
            }
            
            // Move the adapter
            adapterPackageService.moveAdapterToPackage(adapterId, targetPackageId, userId);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to move adapter between packages", e);
            return false;
        }
    }
    
    /**
     * Get package adapter summary
     */
    public PackageAdapterSummary getPackageAdapterSummary(UUID packageId) {
        logger.debug("Generating package adapter summary for: {}", packageId);
        
        AdapterPackageService.PackageAdapterStats stats = adapterPackageService.getPackageAdapterStatistics(packageId);
        
        return new PackageAdapterSummary(
            packageId,
            stats.totalAdapters,
            stats.activeAdapters,
            stats.sftpAdapters + stats.fileAdapters + stats.emailAdapters, // Assuming these are validated count
            createAdaptersByTypeMap(stats),
            createAdaptersByDirectionMap(stats)
        );
    }
    
    // ===== Analytics Operations (delegated to AdapterAnalyticsService) =====
    
    /**
     * Get adapter statistics
     */
    public Map<String, Object> getAdapterStatistics() {
        return adapterAnalyticsService.getAdapterStatistics();
    }
    
    /**
     * Get adapter statistics by package
     */
    public Map<String, Object> getAdapterStatisticsByPackage(UUID packageId) {
        logger.debug("Retrieving adapter statistics for package: {}", packageId);
        
        AdapterPackageService.PackageAdapterStats stats = adapterPackageService.getPackageAdapterStatistics(packageId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("packageId", packageId);
        result.put("totalAdapters", stats.totalAdapters);
        result.put("activeAdapters", stats.activeAdapters);
        result.put("inactiveAdapters", stats.totalAdapters - stats.activeAdapters);
        result.put("sftpAdapters", stats.sftpAdapters);
        result.put("fileAdapters", stats.fileAdapters);
        result.put("emailAdapters", stats.emailAdapters);
        result.put("senderAdapters", stats.senderAdapters);
        result.put("receiverAdapters", stats.receiverAdapters);
        
        return result;
    }
    
    // ===== Helper Methods for backward compatibility =====
    
    private Map<String, Long> createAdaptersByTypeMap(AdapterPackageService.PackageAdapterStats stats) {
        Map<String, Long> typeMap = new HashMap<>();
        typeMap.put("SFTP", stats.sftpAdapters);
        typeMap.put("FILE", stats.fileAdapters);
        typeMap.put("EMAIL", stats.emailAdapters);
        return typeMap;
    }
    
    private Map<String, Long> createAdaptersByDirectionMap(AdapterPackageService.PackageAdapterStats stats) {
        Map<String, Long> directionMap = new HashMap<>();
        directionMap.put("SENDER", stats.senderAdapters);
        directionMap.put("RECEIVER", stats.receiverAdapters);
        return directionMap;
    }
    
    // ===== Inner Classes for backward compatibility =====
    
    /**
     * Package adapter summary data structure (preserved for backward compatibility)
     */
    public static class PackageAdapterSummary {
        private final UUID packageId;
        private final long totalAdapters;
        private final long activeAdapters;
        private final long validatedAdapters;
        private final Map<String, Long> adaptersByType;
        private final Map<String, Long> adaptersByDirection;
        
        public PackageAdapterSummary(UUID packageId, long totalAdapters, long activeAdapters, 
                                   long validatedAdapters, Map<String, Long> adaptersByType,
                                   Map<String, Long> adaptersByDirection) {
            this.packageId = packageId;
            this.totalAdapters = totalAdapters;
            this.activeAdapters = activeAdapters;
            this.validatedAdapters = validatedAdapters;
            this.adaptersByType = adaptersByType;
            this.adaptersByDirection = adaptersByDirection;
        }
        
        public UUID getPackageId() { return packageId; }
        public long getTotalAdapters() { return totalAdapters; }
        public long getActiveAdapters() { return activeAdapters; }
        public long getValidatedAdapters() { return validatedAdapters; }
        public long getInactiveAdapters() { return totalAdapters - activeAdapters; }
        public long getUnvalidatedAdapters() { return totalAdapters - validatedAdapters; }
        public Map<String, Long> getAdaptersByType() { return adaptersByType; }
        public Map<String, Long> getAdaptersByDirection() { return adaptersByDirection; }
        public boolean hasAdapters() { return totalAdapters > 0; }
        public boolean allAdaptersActive() { return totalAdapters > 0 && activeAdapters == totalAdapters; }
        public boolean allAdaptersValidated() { return totalAdapters > 0 && validatedAdapters == totalAdapters; }
        public double getActivePercentage() { 
            return totalAdapters > 0 ? (double) activeAdapters / totalAdapters * 100.0 : 0.0;
        }
        public double getValidatedPercentage() { 
            return totalAdapters > 0 ? (double) validatedAdapters / totalAdapters * 100.0 : 0.0;
        }
    }
}