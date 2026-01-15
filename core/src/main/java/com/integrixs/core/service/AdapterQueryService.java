package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for adapter queries, filtering, and search operations
 * Handles complex query operations following Single Responsibility Principle
 */
@Service
public class AdapterQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterQueryService.class);
    
    private final AdapterRepository adapterRepository;
    
    @Autowired
    public AdapterQueryService(AdapterRepository adapterRepository) {
        this.adapterRepository = adapterRepository;
    }
    
    /**
     * Get adapters by type
     */
    public List<Adapter> getAdaptersByType(String type) {
        logger.debug("Retrieving adapters of type: {}", type);
        return adapterRepository.findByType(type);
    }
    
    /**
     * Get adapters by type and direction
     */
    public List<Adapter> getAdaptersByTypeAndDirection(String type, String direction) {
        logger.debug("Retrieving adapters of type: {} and direction: {}", type, direction);
        return adapterRepository.findByTypeAndDirection(type, direction);
    }
    
    /**
     * Get active adapters only
     */
    public List<Adapter> getActiveAdapters() {
        logger.debug("Retrieving active adapters only");
        return adapterRepository.findByActive(true);
    }
    
    /**
     * Get adapters by type and package
     */
    public List<Adapter> getAdaptersByTypeAndPackage(String type, UUID packageId) {
        logger.debug("Retrieving adapters of type: {} in package: {}", type, packageId);
        return adapterRepository.findByTypeAndPackageId(type, packageId);
    }
    
    /**
     * Get adapters by package
     */
    public List<Adapter> getAdaptersByPackage(UUID packageId) {
        logger.debug("Retrieving adapters in package: {}", packageId);
        return adapterRepository.findByPackageId(packageId);
    }
    
    /**
     * Get active adapters by package
     */
    public List<Adapter> getActiveAdaptersByPackage(UUID packageId) {
        logger.debug("Retrieving active adapters in package: {}", packageId);
        return adapterRepository.findByPackageIdAndActive(packageId, true);
    }
    
    /**
     * Get adapters by package ID
     */
    public List<Adapter> getAdaptersByPackageId(UUID packageId) {
        logger.debug("Retrieving adapters by package ID: {}", packageId);
        return adapterRepository.findByPackageId(packageId);
    }
    
    /**
     * Get active adapters by package ID
     */
    public List<Adapter> getActiveAdaptersByPackageId(UUID packageId) {
        logger.debug("Retrieving active adapters by package ID: {}", packageId);
        return adapterRepository.findByPackageIdAndActive(packageId, true);
    }
    
    /**
     * Get adapters by package ID and type
     */
    public List<Adapter> getAdaptersByPackageIdAndType(UUID packageId, String type) {
        logger.debug("Retrieving adapters by package ID: {} and type: {}", packageId, type);
        return adapterRepository.findByPackageIdAndType(packageId, type);
    }
    
    /**
     * Get adapters by type, direction and package
     */
    public List<Adapter> getAdaptersByTypeDirectionAndPackage(String type, String direction, UUID packageId) {
        logger.debug("Retrieving adapters of type: {}, direction: {} in package: {}", type, direction, packageId);
        return adapterRepository.findByTypeAndDirectionAndPackageId(type, direction, packageId);
    }
    
    /**
     * Check if adapter exists by name in package
     */
    public boolean existsByNameInPackage(String name, UUID packageId) {
        logger.debug("Checking if adapter exists with name: {} in package: {}", name, packageId);
        return adapterRepository.existsByNameAndPackageId(name, packageId);
    }
    
    /**
     * Get adapters by multiple criteria (flexible search)
     */
    public List<Adapter> searchAdapters(String type, String direction, Boolean active, UUID packageId) {
        logger.debug("Searching adapters with criteria - type: {}, direction: {}, active: {}, packageId: {}", 
            type, direction, active, packageId);
        
        // If all criteria are null, return all adapters
        if (type == null && direction == null && active == null && packageId == null) {
            return adapterRepository.findAll();
        }
        
        // Build query based on provided criteria
        if (packageId != null) {
            if (type != null && direction != null) {
                List<Adapter> adapters = adapterRepository.findByTypeAndDirectionAndPackageId(type, direction, packageId);
                if (active != null) {
                    return adapters.stream()
                        .filter(adapter -> active.equals(adapter.isActive()))
                        .collect(java.util.stream.Collectors.toList());
                }
                return adapters;
            } else if (type != null) {
                List<Adapter> adapters = adapterRepository.findByTypeAndPackageId(type, packageId);
                if (active != null) {
                    return adapters.stream()
                        .filter(adapter -> active.equals(adapter.isActive()))
                        .collect(java.util.stream.Collectors.toList());
                }
                return adapters;
            } else if (active != null) {
                return adapterRepository.findByPackageIdAndActive(packageId, active);
            } else {
                return adapterRepository.findByPackageId(packageId);
            }
        } else {
            if (type != null && direction != null) {
                List<Adapter> adapters = adapterRepository.findByTypeAndDirection(type, direction);
                if (active != null) {
                    return adapters.stream()
                        .filter(adapter -> active.equals(adapter.isActive()))
                        .collect(java.util.stream.Collectors.toList());
                }
                return adapters;
            } else if (type != null) {
                List<Adapter> adapters = adapterRepository.findByType(type);
                if (active != null) {
                    return adapters.stream()
                        .filter(adapter -> active.equals(adapter.isActive()))
                        .collect(java.util.stream.Collectors.toList());
                }
                return adapters;
            } else if (active != null) {
                return adapterRepository.findByActive(active);
            }
        }
        
        return adapterRepository.findAll();
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
}