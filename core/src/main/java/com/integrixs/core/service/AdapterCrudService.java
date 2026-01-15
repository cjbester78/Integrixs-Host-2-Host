package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.shared.model.Adapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for basic adapter CRUD operations
 * Handles create, read, update, delete operations following Single Responsibility Principle
 */
@Service
public class AdapterCrudService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterCrudService.class);
    
    private final AdapterRepository adapterRepository;
    
    @Autowired
    public AdapterCrudService(AdapterRepository adapterRepository) {
        this.adapterRepository = adapterRepository;
    }
    
    /**
     * Get all adapters
     */
    public List<Adapter> getAllAdapters() {
        logger.debug("Retrieving all adapters");
        return adapterRepository.findAll();
    }
    
    /**
     * Get adapter by ID
     */
    public Optional<Adapter> getAdapterById(UUID id) {
        logger.debug("Retrieving adapter by ID: {}", id);
        return adapterRepository.findById(id);
    }
    
    /**
     * Get adapter by name
     */
    public Optional<Adapter> getAdapterByName(String name) {
        logger.debug("Retrieving adapter by name: {}", name);
        return adapterRepository.findByName(name);
    }
    
    /**
     * Check if adapter exists by name
     */
    public boolean existsByName(String name) {
        logger.debug("Checking if adapter exists with name: {}", name);
        return adapterRepository.existsByName(name);
    }
    
    /**
     * Create new adapter
     */
    public UUID createAdapter(Adapter adapter) {
        logger.info("Creating new adapter: {}", adapter.getName());
        
        // Validate required fields
        if (adapter.getName() == null || adapter.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Adapter name is required");
        }
        if (adapter.getAdapterType() == null || adapter.getAdapterType().trim().isEmpty()) {
            throw new IllegalArgumentException("Adapter type is required");
        }
        
        // Check for name uniqueness
        if (existsByName(adapter.getName())) {
            throw new IllegalArgumentException("Adapter with name '" + adapter.getName() + "' already exists");
        }
        
        // Set creation metadata
        adapter.setCreatedAt(LocalDateTime.now());
        adapter.setUpdatedAt(LocalDateTime.now());
        if (adapter.getActive() == null) {
            adapter.setActive(true); // Default to active
        }
        
        UUID adapterId = adapterRepository.save(adapter);
        logger.info("Successfully created adapter: {} with ID: {}", adapter.getName(), adapterId);
        
        return adapterId;
    }
    
    /**
     * Create new adapter with package context
     */
    public UUID createAdapter(Adapter adapter, UUID packageId) {
        logger.info("Creating new adapter: {} in package: {}", adapter.getName(), packageId);
        
        // Set package context
        adapter.setPackageId(packageId);
        
        return createAdapter(adapter);
    }
    
    /**
     * Update existing adapter
     */
    public void updateAdapter(Adapter adapter) {
        logger.info("Updating adapter: {}", adapter.getName());
        
        // Validate required fields
        if (adapter.getId() == null) {
            throw new IllegalArgumentException("Adapter ID is required for updates");
        }
        if (adapter.getName() == null || adapter.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Adapter name is required");
        }
        
        // Check if adapter exists
        Optional<Adapter> existingAdapter = getAdapterById(adapter.getId());
        if (!existingAdapter.isPresent()) {
            throw new IllegalArgumentException("Adapter with ID " + adapter.getId() + " not found");
        }
        
        // Check for name uniqueness (excluding current adapter)
        Optional<Adapter> adapterWithSameName = getAdapterByName(adapter.getName());
        if (adapterWithSameName.isPresent() && !adapterWithSameName.get().getId().equals(adapter.getId())) {
            throw new IllegalArgumentException("Another adapter with name '" + adapter.getName() + "' already exists");
        }
        
        // Preserve creation metadata, update modification metadata
        Adapter existing = existingAdapter.get();
        adapter.setCreatedAt(existing.getCreatedAt());
        adapter.setCreatedBy(existing.getCreatedBy());
        adapter.setUpdatedAt(LocalDateTime.now());
        
        adapterRepository.update(adapter);
        logger.info("Successfully updated adapter: {}", adapter.getName());
    }
    
    /**
     * Delete adapter
     */
    public void deleteAdapter(UUID id) {
        logger.info("Deleting adapter with ID: {}", id);
        
        // Check if adapter exists
        Optional<Adapter> adapter = getAdapterById(id);
        if (!adapter.isPresent()) {
            throw new IllegalArgumentException("Adapter with ID " + id + " not found");
        }
        
        // Check if adapter is in use (you might want to add business rules here)
        // For now, we'll allow deletion
        
        adapterRepository.delete(id);
        logger.info("Successfully deleted adapter with ID: {}", id);
    }
}