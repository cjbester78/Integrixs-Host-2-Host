package com.integrixs.core.service;

import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.DeployedFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing flow package operations
 * Handles package-related flow operations following Single Responsibility Principle
 */
@Service
public class FlowPackageService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowPackageService.class);
    
    private final FlowCrudService flowCrudService;
    private final FlowValidationService flowValidationService;
    private final DeployedFlowRepository deployedFlowRepository;
    
    @Autowired
    public FlowPackageService(FlowCrudService flowCrudService,
                             FlowValidationService flowValidationService,
                             DeployedFlowRepository deployedFlowRepository) {
        this.flowCrudService = flowCrudService;
        this.flowValidationService = flowValidationService;
        this.deployedFlowRepository = deployedFlowRepository;
    }
    
    /**
     * Move flow to different package
     */
    public void moveFlowToPackage(UUID flowId, UUID toPackageId, UUID movedBy) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        Objects.requireNonNull(toPackageId, "Target package ID cannot be null");
        Objects.requireNonNull(movedBy, "Moved by user ID cannot be null");
        
        logger.info("Moving flow {} to package {} by user: {}", flowId, toPackageId, movedBy);
        
        // Validate flow exists
        Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(flowId);
        if (!flowOpt.isPresent()) {
            throw new IllegalArgumentException("Flow with ID " + flowId + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        UUID fromPackageId = flow.getPackageId();
        
        // Check if flow is deployed - cannot move deployed flows
        Optional<DeployedFlow> deployedFlow = deployedFlowRepository.findByFlowId(flowId);
        if (deployedFlow.isPresent()) {
            throw new IllegalStateException("Cannot move deployed flow: " + flow.getName() + 
                ". Please undeploy the flow before moving it to a different package.");
        }
        
        // Check for name conflicts in target package
        if (flowValidationService.existsByNameInPackage(flow.getName(), toPackageId)) {
            throw new IllegalArgumentException(
                "Flow with name '" + flow.getName() + "' already exists in target package");
        }
        
        // Update package ID
        flow.setPackageId(toPackageId);
        flow.setUpdatedAt(LocalDateTime.now());
        flow.setUpdatedBy(movedBy);
        
        // Update the flow
        flowCrudService.updateFlow(flowId, flow, movedBy);
        
        logger.info("Successfully moved flow '{}' from package {} to package {}", 
            flow.getName(), fromPackageId, toPackageId);
    }
    
    /**
     * Move flow between packages
     */
    public boolean moveFlowBetweenPackages(UUID flowId, UUID sourcePackageId, UUID targetPackageId, UUID userId) {
        logger.info("Moving flow {} from package {} to package {} by user {}", 
            flowId, sourcePackageId, targetPackageId, userId);
        
        try {
            Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(flowId);
            if (!flowOpt.isPresent()) {
                logger.error("Flow not found: {}", flowId);
                return false;
            }
            
            IntegrationFlow flow = flowOpt.get();
            
            // Verify source package
            if (!Objects.equals(flow.getPackageId(), sourcePackageId)) {
                logger.error("Flow {} is not in source package {}", flowId, sourcePackageId);
                return false;
            }
            
            // Check if flow is deployed - cannot move deployed flows
            Optional<DeployedFlow> deployedFlow = deployedFlowRepository.findByFlowId(flowId);
            if (deployedFlow.isPresent()) {
                logger.error("Cannot move deployed flow: {}", flowId);
                return false;
            }
            
            // Update package ID
            flow.setPackageId(targetPackageId);
            flow.setUpdatedAt(LocalDateTime.now());
            flow.setUpdatedBy(userId);
            
            flowCrudService.updateFlow(flowId, flow, userId);
            
            logger.info("Successfully moved flow {} to package {}", flowId, targetPackageId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to move flow between packages: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get flows by package ID
     */
    public List<IntegrationFlow> getFlowsByPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        return flowCrudService.getFlowsByPackageId(packageId);
    }
    
    /**
     * Get active flows by package
     */
    public List<IntegrationFlow> getActiveFlowsByPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        return flowCrudService.getActiveFlowsByPackage(packageId);
    }
    
    /**
     * Get flows by type and package
     */
    public List<IntegrationFlow> getFlowsByTypeAndPackage(String flowType, UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        return flowCrudService.getFlowsByTypeAndPackage(flowType, packageId);
    }
    
    /**
     * Get scheduled flows by package
     */
    public List<IntegrationFlow> getScheduledFlowsByPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Retrieving scheduled flows for package: {}", packageId);
        
        return getFlowsByPackage(packageId).stream()
            .filter(IntegrationFlow::getScheduleEnabled)
            .toList();
    }
    
    /**
     * Update flow in package context
     */
    public IntegrationFlow updateFlowInPackage(UUID flowId, UUID packageId, IntegrationFlow flow, UUID updatedBy) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(flow, "Flow cannot be null");
        Objects.requireNonNull(updatedBy, "Updated by user ID cannot be null");
        
        logger.info("Updating flow {} in package context: {}", flowId, packageId);
        
        // Validate flow exists in package
        Optional<IntegrationFlow> existingOpt = flowCrudService.getFlowById(flowId);
        if (!existingOpt.isPresent()) {
            throw new IllegalArgumentException("Flow with ID " + flowId + " not found");
        }
        
        IntegrationFlow existing = existingOpt.get();
        if (!Objects.equals(existing.getPackageId(), packageId)) {
            throw new IllegalArgumentException("Flow does not belong to package " + packageId);
        }
        
        // Check for name conflicts within package
        List<IntegrationFlow> packageFlows = getFlowsByPackage(packageId);
        boolean nameConflict = packageFlows.stream()
            .anyMatch(f -> !f.getId().equals(flowId) && 
                     flow.getName().trim().equalsIgnoreCase(f.getName()));
        
        if (nameConflict) {
            throw new IllegalArgumentException(
                "Flow with name '" + flow.getName() + "' already exists in this package");
        }
        
        // Preserve creation audit information and package deployment context
        flow.setId(flowId);
        flow.setCreatedAt(existing.getCreatedAt());
        flow.setCreatedBy(existing.getCreatedBy());
        flow.setDeployedFromPackageId(existing.getDeployedFromPackageId());
        flow.setPackageId(packageId); // Ensure package ID is preserved
        flow.setUpdatedAt(LocalDateTime.now());
        flow.setUpdatedBy(updatedBy);
        flow.incrementVersion();
        
        // Update scheduled run if scheduling enabled
        flow.updateNextScheduledRun();
        
        // Clean any embedded adapter configurations if needed
        if (flow.getFlowDefinition() != null) {
            Map<String, Object> cleanedDefinition = cleanEmbeddedAdapterConfigurations(flow.getFlowDefinition());
            flow.setFlowDefinition(cleanedDefinition);
        }
        
        // Update flow
        IntegrationFlow updatedFlow = flowCrudService.updateFlow(flowId, flow, updatedBy);
        
        logger.info("Successfully updated flow: {} in package: {} to version: {}", 
            flowId, packageId, updatedFlow.getFlowVersion());
        
        return updatedFlow;
    }
    
    /**
     * Create flow in package context
     */
    public IntegrationFlow createFlowInPackage(IntegrationFlow flow, UUID packageId, UUID createdBy) {
        Objects.requireNonNull(flow, "Flow cannot be null");
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(createdBy, "Created by user ID cannot be null");
        
        logger.info("Creating flow in package context: {}", packageId);
        
        // Validate flow name doesn't exist in package
        if (flowValidationService.existsByNameInPackage(flow.getName(), packageId)) {
            throw new IllegalArgumentException(
                "Flow with name '" + flow.getName() + "' already exists in this package");
        }
        
        // Clean embedded adapter configurations if needed
        if (flow.getFlowDefinition() != null) {
            Map<String, Object> cleanedDefinition = cleanEmbeddedAdapterConfigurations(flow.getFlowDefinition());
            flow.setFlowDefinition(cleanedDefinition);
        }
        
        return flowCrudService.createFlow(flow, packageId, createdBy);
    }
    
    // Private helper methods
    
    private Map<String, Object> cleanEmbeddedAdapterConfigurations(Map<String, Object> flowDefinition) {
        // Create a deep copy to avoid modifying the original
        Map<String, Object> cleanedDefinition = new HashMap<>(flowDefinition);
        
        if (cleanedDefinition.containsKey("nodes")) {
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) cleanedDefinition.get("nodes");
            List<Map<String, Object>> cleanedNodes = new ArrayList<>();
            
            for (Map<String, Object> node : nodes) {
                Map<String, Object> cleanedNode = new HashMap<>(node);
                
                // Remove embedded adapter configurations to prevent duplication
                if ("adapter".equals(cleanedNode.get("type"))) {
                    cleanedNode.remove("adapterConfig");
                    cleanedNode.remove("embeddedConfiguration");
                }
                
                cleanedNodes.add(cleanedNode);
            }
            
            cleanedDefinition.put("nodes", cleanedNodes);
        }
        
        return cleanedDefinition;
    }
}