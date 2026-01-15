package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for adapter package-related operations
 * Handles package-specific adapter operations following Single Responsibility Principle
 */
@Service
public class AdapterPackageService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterPackageService.class);
    
    private final AdapterRepository adapterRepository;
    
    @Autowired
    public AdapterPackageService(AdapterRepository adapterRepository) {
        this.adapterRepository = adapterRepository;
    }
    
    /**
     * Get adapters by package ID
     */
    public List<Adapter> getAdaptersByPackage(UUID packageId) {
        logger.debug("Retrieving adapters in package: {}", packageId);
        return adapterRepository.findByPackageId(packageId);
    }
    
    /**
     * Get active adapters by package ID
     */
    public List<Adapter> getActiveAdaptersByPackage(UUID packageId) {
        logger.debug("Retrieving active adapters in package: {}", packageId);
        return adapterRepository.findByPackageIdAndActive(packageId, true);
    }
    
    /**
     * Get adapters by package ID and type
     */
    public List<Adapter> getAdaptersByPackageAndType(UUID packageId, String adapterType) {
        logger.debug("Retrieving adapters in package: {} of type: {}", packageId, adapterType);
        return adapterRepository.findByTypeAndPackageId(adapterType, packageId);
    }
    
    /**
     * Get adapters by package, type and direction
     */
    public List<Adapter> getAdaptersByPackageTypeAndDirection(UUID packageId, String adapterType, String direction) {
        logger.debug("Retrieving adapters in package: {} of type: {} and direction: {}", packageId, adapterType, direction);
        return adapterRepository.findByTypeAndDirectionAndPackageId(adapterType, direction, packageId);
    }
    
    /**
     * Count adapters in package
     */
    public long countAdaptersInPackage(UUID packageId) {
        logger.debug("Counting adapters in package: {}", packageId);
        return adapterRepository.countByPackageId(packageId);
    }
    
    /**
     * Count active adapters in package
     */
    public long countActiveAdaptersInPackage(UUID packageId) {
        logger.debug("Counting active adapters in package: {}", packageId);
        return adapterRepository.countByPackageIdAndActive(packageId, true);
    }
    
    /**
     * Update adapter's package association
     */
    public void updateAdapterPackageAssociation(UUID adapterId, UUID newPackageId, UUID updatedBy) {
        logger.info("Updating adapter {} package association to: {}", adapterId, newPackageId);
        
        // Verify adapter exists
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        UUID currentPackageId = adapter.getPackageId();
        
        // Check if adapter is already in the target package
        if (newPackageId.equals(currentPackageId)) {
            logger.info("Adapter {} is already in package {}", adapter.getName(), newPackageId);
            return;
        }
        
        // Update package association
        adapterRepository.updatePackageAssociation(adapterId, newPackageId, updatedBy);
        
        logger.info("Successfully updated adapter {} package association from {} to {}", 
            adapter.getName(), currentPackageId, newPackageId);
    }
    
    /**
     * Move adapter to different package
     */
    public void moveAdapterToPackage(UUID adapterId, UUID targetPackageId, UUID updatedBy) {
        logger.info("Moving adapter {} to package: {}", adapterId, targetPackageId);
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (!adapterOpt.isPresent()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        
        // Check for name conflicts in target package
        if (adapterRepository.existsByNameAndPackageId(adapter.getName(), targetPackageId)) {
            throw new IllegalArgumentException("An adapter with name '" + adapter.getName() + 
                "' already exists in target package");
        }
        
        // Update package association
        updateAdapterPackageAssociation(adapterId, targetPackageId, updatedBy);
        
        logger.info("Successfully moved adapter {} to package {}", adapter.getName(), targetPackageId);
    }
    
    /**
     * Copy adapter to different package
     */
    public UUID copyAdapterToPackage(UUID adapterId, UUID targetPackageId, String newAdapterName, UUID createdBy) {
        logger.info("Copying adapter {} to package: {} with name: {}", adapterId, targetPackageId, newAdapterName);
        
        Optional<Adapter> sourceAdapterOpt = adapterRepository.findById(adapterId);
        if (!sourceAdapterOpt.isPresent()) {
            throw new IllegalArgumentException("Source adapter not found: " + adapterId);
        }
        
        Adapter sourceAdapter = sourceAdapterOpt.get();
        
        // Check for name conflicts in target package
        if (adapterRepository.existsByNameAndPackageId(newAdapterName, targetPackageId)) {
            throw new IllegalArgumentException("An adapter with name '" + newAdapterName + 
                "' already exists in target package");
        }
        
        // Create new adapter based on source
        Adapter newAdapter = new Adapter();
        newAdapter.setName(newAdapterName);
        newAdapter.setDescription(sourceAdapter.getDescription() + " (Copy)");
        newAdapter.setAdapterType(sourceAdapter.getAdapterType());
        newAdapter.setDirection(sourceAdapter.getDirection());
        newAdapter.setConfiguration(sourceAdapter.getConfiguration());
        newAdapter.setActive(false); // Start as inactive
        newAdapter.setPackageId(targetPackageId);
        newAdapter.setDeployedFromPackageId(sourceAdapter.getPackageId()); // Track original package
        
        // Save the new adapter
        UUID newAdapterId = adapterRepository.save(newAdapter);
        
        logger.info("Successfully copied adapter {} to package {} as {} with ID: {}", 
            sourceAdapter.getName(), targetPackageId, newAdapterName, newAdapterId);
        
        return newAdapterId;
    }
    
    /**
     * Check if adapter name exists in package
     */
    public boolean isAdapterNameTakenInPackage(String adapterName, UUID packageId) {
        logger.debug("Checking if adapter name {} exists in package: {}", adapterName, packageId);
        return adapterRepository.existsByNameAndPackageId(adapterName, packageId);
    }
    
    /**
     * Get package statistics for adapters
     */
    public PackageAdapterStats getPackageAdapterStatistics(UUID packageId) {
        logger.debug("Retrieving adapter statistics for package: {}", packageId);
        
        long totalAdapters = countAdaptersInPackage(packageId);
        long activeAdapters = countActiveAdaptersInPackage(packageId);
        
        List<Adapter> allAdapters = getAdaptersByPackage(packageId);
        
        // Count by type
        long sftpAdapters = allAdapters.stream().filter(a -> "SFTP".equals(a.getAdapterType())).count();
        long fileAdapters = allAdapters.stream().filter(a -> "FILE".equals(a.getAdapterType())).count();
        long emailAdapters = allAdapters.stream().filter(a -> "EMAIL".equals(a.getAdapterType())).count();
        
        // Count by direction
        long senderAdapters = allAdapters.stream().filter(a -> "SENDER".equals(a.getDirection())).count();
        long receiverAdapters = allAdapters.stream().filter(a -> "RECEIVER".equals(a.getDirection())).count();
        
        return new PackageAdapterStats(
            totalAdapters,
            activeAdapters,
            sftpAdapters,
            fileAdapters,
            emailAdapters,
            senderAdapters,
            receiverAdapters
        );
    }
    
    /**
     * Data transfer object for package adapter statistics
     */
    public static class PackageAdapterStats {
        public final long totalAdapters;
        public final long activeAdapters;
        public final long sftpAdapters;
        public final long fileAdapters;
        public final long emailAdapters;
        public final long senderAdapters;
        public final long receiverAdapters;
        
        public PackageAdapterStats(long totalAdapters, long activeAdapters, 
                                 long sftpAdapters, long fileAdapters, long emailAdapters,
                                 long senderAdapters, long receiverAdapters) {
            this.totalAdapters = totalAdapters;
            this.activeAdapters = activeAdapters;
            this.sftpAdapters = sftpAdapters;
            this.fileAdapters = fileAdapters;
            this.emailAdapters = emailAdapters;
            this.senderAdapters = senderAdapters;
            this.receiverAdapters = receiverAdapters;
        }
    }
}