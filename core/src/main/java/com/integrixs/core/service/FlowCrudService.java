package com.integrixs.core.service;

import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.shared.model.IntegrationFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for basic CRUD operations on Integration Flows
 * Handles Create, Read, Update, Delete operations following Single Responsibility Principle
 */
@Service
public class FlowCrudService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowCrudService.class);
    
    private final IntegrationFlowRepository flowRepository;
    
    @Autowired
    public FlowCrudService(IntegrationFlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }
    
    /**
     * Get all flows
     */
    public List<IntegrationFlow> getAllFlows() {
        logger.debug("Retrieving all flows");
        return flowRepository.findAll();
    }
    
    /**
     * Get active flows
     */
    public List<IntegrationFlow> getActiveFlows() {
        logger.debug("Retrieving active flows");
        return flowRepository.findByActive(true);
    }
    
    /**
     * Get scheduled flows
     */
    public List<IntegrationFlow> getScheduledFlows() {
        logger.debug("Retrieving scheduled flows");
        return flowRepository.findScheduledFlows();
    }
    
    /**
     * Get flow by ID
     */
    public Optional<IntegrationFlow> getFlowById(UUID id) {
        Objects.requireNonNull(id, "Flow ID cannot be null");
        logger.debug("Retrieving flow by ID: {}", id);
        return flowRepository.findById(id);
    }
    
    /**
     * Get flow by name
     */
    public Optional<IntegrationFlow> getFlowByName(String name) {
        Objects.requireNonNull(name, "Flow name cannot be null");
        logger.debug("Retrieving flow by name: {}", name);
        return flowRepository.findByName(name);
    }
    
    /**
     * Check if flow exists by name
     */
    public boolean existsByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return flowRepository.findByName(name).isPresent();
    }
    
    /**
     * Create a new flow
     */
    public IntegrationFlow createFlow(IntegrationFlow flow, UUID packageId, UUID createdBy) {
        Objects.requireNonNull(flow, "Flow cannot be null");
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(createdBy, "Created by user ID cannot be null");
        
        logger.info("Creating flow: {} in package: {}", flow.getName(), packageId);
        
        // Set package and audit info
        flow.setPackageId(packageId);
        flow.setCreatedAt(LocalDateTime.now());
        flow.setCreatedBy(createdBy);
        flow.setUpdatedAt(LocalDateTime.now());
        flow.setUpdatedBy(createdBy);
        
        // Set initial version
        flow.setFlowVersion(1);
        
        UUID flowId = flowRepository.save(flow);
        flow.setId(flowId);
        return flow;
    }
    
    /**
     * Update an existing flow
     */
    public IntegrationFlow updateFlow(UUID id, IntegrationFlow flow, UUID updatedBy) {
        Objects.requireNonNull(id, "Flow ID cannot be null");
        Objects.requireNonNull(flow, "Flow cannot be null");
        Objects.requireNonNull(updatedBy, "Updated by user ID cannot be null");
        
        logger.info("Updating flow: {}", id);
        
        // Verify flow exists
        Optional<IntegrationFlow> existingOpt = getFlowById(id);
        if (!existingOpt.isPresent()) {
            throw new IllegalArgumentException("Flow with ID " + id + " not found");
        }
        
        IntegrationFlow existing = existingOpt.get();
        
        // Preserve audit information
        flow.setId(id);
        flow.setCreatedAt(existing.getCreatedAt());
        flow.setCreatedBy(existing.getCreatedBy());
        flow.setPackageId(existing.getPackageId());
        flow.setUpdatedAt(LocalDateTime.now());
        flow.setUpdatedBy(updatedBy);
        flow.incrementVersion();
        
        flowRepository.update(flow);
        return flow;
    }
    
    /**
     * Set flow active status
     */
    public void setFlowActive(UUID id, boolean active) {
        Objects.requireNonNull(id, "Flow ID cannot be null");
        logger.info("Setting flow {} active status to: {}", id, active);
        
        Optional<IntegrationFlow> flowOpt = getFlowById(id);
        if (!flowOpt.isPresent()) {
            throw new IllegalArgumentException("Flow with ID " + id + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        flow.setActive(active);
        flow.setUpdatedAt(LocalDateTime.now());
        
        flowRepository.update(flow);
    }
    
    /**
     * Delete flow by ID
     */
    public boolean deleteFlow(UUID id) {
        Objects.requireNonNull(id, "Flow ID cannot be null");
        logger.info("Deleting flow: {}", id);
        
        if (!getFlowById(id).isPresent()) {
            logger.warn("Flow with ID {} not found for deletion", id);
            return false;
        }
        
        try {
            boolean deleted = flowRepository.deleteById(id);
            if (deleted) {
                logger.info("Successfully deleted flow: {}", id);
            }
            return deleted;
        } catch (Exception e) {
            logger.error("Failed to delete flow: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get flows by package ID
     */
    public List<IntegrationFlow> getFlowsByPackageId(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Retrieving flows for package: {}", packageId);
        return flowRepository.findByPackageId(packageId);
    }
    
    /**
     * Get active flows by package
     */
    public List<IntegrationFlow> getActiveFlowsByPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Retrieving active flows for package: {}", packageId);
        return flowRepository.findActiveByPackageId(packageId);
    }
    
    /**
     * Get flows by type
     */
    public List<IntegrationFlow> getFlowsByType(String flowType) {
        logger.debug("Retrieving flows of type: {}", flowType);
        // Use existing repository methods to filter by flow type
        return getAllFlows().stream()
            .filter(flow -> flowType.equals(flow.getFlowType()))
            .toList();
    }
    
    /**
     * Get flows by type and package
     */
    public List<IntegrationFlow> getFlowsByTypeAndPackage(String flowType, UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Retrieving flows of type: {} for package: {}", flowType, packageId);
        return getFlowsByPackageId(packageId).stream()
            .filter(flow -> flowType.equals(flow.getFlowType()))
            .toList();
    }
}