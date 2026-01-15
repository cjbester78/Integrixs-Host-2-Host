package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.FlowUtilityRepository;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for validating Integration Flows
 * Handles all validation logic following Single Responsibility Principle
 */
@Service
public class FlowValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowValidationService.class);
    
    private final AdapterRepository adapterRepository;
    private final FlowUtilityRepository utilityRepository;
    private final FlowCrudService flowCrudService;
    
    @Autowired
    public FlowValidationService(AdapterRepository adapterRepository, 
                                FlowUtilityRepository utilityRepository,
                                FlowCrudService flowCrudService) {
        this.adapterRepository = adapterRepository;
        this.utilityRepository = utilityRepository;
        this.flowCrudService = flowCrudService;
    }
    
    /**
     * Validate flow by ID
     */
    public Map<String, Object> validateFlow(UUID flowId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        logger.info("Validating flow: {}", flowId);
        
        Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(flowId);
        if (!flowOpt.isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("isValid", false);
            result.put("errors", List.of("Flow not found"));
            result.put("warnings", List.of());
            return result;
        }
        
        return validateFlow(flowOpt.get());
    }
    
    /**
     * Validate flow object
     */
    public Map<String, Object> validateFlow(IntegrationFlow flow) {
        Objects.requireNonNull(flow, "Flow cannot be null");
        logger.debug("Validating flow definition for: {}", flow.getName());
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic validation
        validateBasicFlowStructure(flow, errors, warnings);
        
        // Definition validation
        if (flow.getFlowDefinition() != null) {
            validateFlowDefinition(flow.getFlowDefinition(), errors, warnings);
        }
        
        // Package context validation
        if (flow.getPackageId() != null) {
            validatePackageContext(flow, errors, warnings);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("isValid", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("validatedAt", new Date());
        
        if (errors.isEmpty()) {
            logger.info("Flow validation passed for: {}", flow.getName());
        } else {
            logger.warn("Flow validation failed for: {} with errors: {}", flow.getName(), errors);
        }
        
        return result;
    }
    
    /**
     * Validate flow within package context
     */
    public Map<String, Object> validateFlowInPackageContext(UUID flowId, UUID packageId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        logger.info("Validating flow {} in package context: {}", flowId, packageId);
        
        Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(flowId);
        if (!flowOpt.isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("isValid", false);
            result.put("errors", List.of("Flow not found"));
            result.put("warnings", List.of());
            return result;
        }
        
        IntegrationFlow flow = flowOpt.get();
        
        // Validate flow belongs to package
        if (!Objects.equals(flow.getPackageId(), packageId)) {
            Map<String, Object> result = new HashMap<>();
            result.put("isValid", false);
            result.put("errors", List.of("Flow does not belong to package " + packageId));
            result.put("warnings", List.of());
            return result;
        }
        
        Map<String, Object> validation = validateFlow(flow);
        List<String> errors = (List<String>) validation.get("errors");
        
        // Additional package-specific validations
        validateAdaptersInSamePackage(flow, packageId, errors);
        
        validation.put("packageId", packageId);
        validation.put("packageContextValid", errors.stream().noneMatch(e -> e.contains("different package")));
        
        return validation;
    }
    
    /**
     * Check if flow name exists within a specific package
     */
    public boolean existsByNameInPackage(String name, UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        List<IntegrationFlow> packageFlows = flowCrudService.getFlowsByPackageId(packageId);
        return packageFlows.stream()
            .anyMatch(flow -> name.trim().equalsIgnoreCase(flow.getName()));
    }
    
    /**
     * Get available adapters for flow building
     */
    public List<Adapter> getAvailableAdapters() {
        logger.debug("Retrieving available adapters for flow building");
        return adapterRepository.findAllActive();
    }
    
    /**
     * Get available adapters for specific package
     */
    public List<Adapter> getAvailableAdaptersForPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Retrieving available adapters for package: {}", packageId);
        return adapterRepository.findActiveByPackageId(packageId);
    }
    
    /**
     * Get available utilities for flow building
     */
    public List<FlowUtility> getAvailableUtilities() {
        logger.debug("Retrieving available flow utilities");
        return utilityRepository.findAllActive();
    }
    
    // Private helper methods
    
    private void validateBasicFlowStructure(IntegrationFlow flow, List<String> errors, List<String> warnings) {
        if (flow.getName() == null || flow.getName().trim().isEmpty()) {
            errors.add("Flow name is required");
        }
        
        if (flow.getFlowType() == null || flow.getFlowType().trim().isEmpty()) {
            warnings.add("Flow type not specified");
        }
        
        if (flow.getDescription() == null || flow.getDescription().trim().isEmpty()) {
            warnings.add("Flow description is recommended");
        }
    }
    
    private void validateFlowDefinition(Map<String, Object> definition, List<String> errors, List<String> warnings) {
        if (definition.isEmpty()) {
            warnings.add("Flow definition is empty");
            return;
        }
        
        // Validate nodes exist
        if (!definition.containsKey("nodes") || !(definition.get("nodes") instanceof List)) {
            errors.add("Flow must contain nodes");
            return;
        }
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        if (nodes.isEmpty()) {
            errors.add("Flow must contain at least one node");
            return;
        }
        
        // Validate start and end nodes
        boolean hasStart = nodes.stream().anyMatch(node -> "start".equals(node.get("type")));
        boolean hasEnd = nodes.stream().anyMatch(node -> "end".equals(node.get("type")) || "messageEnd".equals(node.get("type")));
        
        if (!hasStart) {
            errors.add("Flow must have a start node");
        }
        
        if (!hasEnd) {
            errors.add("Flow must have an end node");
        }
        
        // Validate node structure
        for (Map<String, Object> node : nodes) {
            validateNode(node, errors, warnings);
        }
    }
    
    private void validateNode(Map<String, Object> node, List<String> errors, List<String> warnings) {
        if (!node.containsKey("id")) {
            errors.add("Node missing required 'id' field");
        }
        
        if (!node.containsKey("type")) {
            errors.add("Node missing required 'type' field");
        }
        
        String nodeType = (String) node.get("type");
        if ("adapter".equals(nodeType)) {
            validateAdapterNode(node, errors, warnings);
        } else if ("utility".equals(nodeType)) {
            validateUtilityNode(node, errors, warnings);
        }
    }
    
    private void validateAdapterNode(Map<String, Object> node, List<String> errors, List<String> warnings) {
        // adapterId can be at top level or inside data object (React Flow structure)
        Object adapterIdObj = node.get("adapterId");
        if (adapterIdObj == null && node.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data != null) {
                adapterIdObj = data.get("adapterId");
            }
        }

        if (adapterIdObj == null || adapterIdObj.toString().trim().isEmpty()) {
            errors.add("Adapter node missing adapterId");
            return;
        }

        try {
            UUID adapterId = UUID.fromString(adapterIdObj.toString());
            Optional<Adapter> adapter = adapterRepository.findById(adapterId);
            if (!adapter.isPresent()) {
                errors.add("Referenced adapter not found: " + adapterId);
            } else if (!adapter.get().isActive()) {
                warnings.add("Referenced adapter is disabled: " + adapter.get().getName());
            }
        } catch (IllegalArgumentException e) {
            errors.add("Invalid adapter ID format: " + adapterIdObj);
        }
    }
    
    private void validateUtilityNode(Map<String, Object> node, List<String> errors, List<String> warnings) {
        // utilityId can be at top level or inside data object (React Flow structure)
        Object utilityIdObj = node.get("utilityId");
        if (utilityIdObj == null && node.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data != null) {
                utilityIdObj = data.get("utilityId");
            }
        }

        if (utilityIdObj == null || utilityIdObj.toString().trim().isEmpty()) {
            errors.add("Utility node missing utilityId");
            return;
        }

        try {
            UUID utilityId = UUID.fromString(utilityIdObj.toString());
            Optional<FlowUtility> utility = utilityRepository.findById(utilityId);
            if (!utility.isPresent()) {
                errors.add("Referenced utility not found: " + utilityId);
            } else if (!utility.get().isActive()) {
                warnings.add("Referenced utility is disabled: " + utility.get().getName());
            }
        } catch (IllegalArgumentException e) {
            errors.add("Invalid utility ID format: " + utilityIdObj);
        }
    }
    
    private void validatePackageContext(IntegrationFlow flow, List<String> errors, List<String> warnings) {
        UUID packageId = flow.getPackageId();
        
        // Check for name conflicts within package
        if (existsByNameInPackage(flow.getName(), packageId)) {
            List<IntegrationFlow> packageFlows = flowCrudService.getFlowsByPackageId(packageId);
            boolean nameConflict = packageFlows.stream()
                .anyMatch(f -> !f.getId().equals(flow.getId()) && 
                         flow.getName().trim().equalsIgnoreCase(f.getName()));
            
            if (nameConflict) {
                errors.add("Flow with name '" + flow.getName() + "' already exists in this package");
            }
        }
    }
    
    private void validateAdaptersInSamePackage(IntegrationFlow flow, UUID packageId, List<String> errors) {
        if (flow.getFlowDefinition() == null) {
            return;
        }

        Map<String, Object> definition = flow.getFlowDefinition();
        if (!definition.containsKey("nodes")) {
            return;
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        for (Map<String, Object> node : nodes) {
            if ("adapter".equals(node.get("type"))) {
                // adapterId can be at top level or inside data object (React Flow structure)
                Object adapterIdObj = node.get("adapterId");
                if (adapterIdObj == null && node.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>) node.get("data");
                    if (data != null) {
                        adapterIdObj = data.get("adapterId");
                    }
                }

                if (adapterIdObj != null && !adapterIdObj.toString().trim().isEmpty()) {
                    try {
                        UUID adapterId = UUID.fromString(adapterIdObj.toString());
                        Optional<Adapter> adapter = adapterRepository.findById(adapterId);
                        if (adapter.isPresent() && !Objects.equals(adapter.get().getPackageId(), packageId)) {
                            errors.add("Adapter " + adapter.get().getName() + " belongs to different package");
                        }
                    } catch (IllegalArgumentException e) {
                        // Already validated in basic validation
                    }
                }
            }
        }
    }

    /**
     * Validate if a flow can be activated.
     * Checks all linked adapters are active and any other conditions that might block activation.
     *
     * @param flowId Flow to validate for activation
     * @return Validation result with isValid flag and list of errors if any
     */
    public Map<String, Object> validateFlowActivation(UUID flowId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        logger.info("Validating flow activation: {}", flowId);

        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();

        // Get the flow
        Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(flowId);
        if (!flowOpt.isPresent()) {
            errors.add("Flow not found");
            result.put("isValid", false);
            result.put("errors", errors);
            return result;
        }

        IntegrationFlow flow = flowOpt.get();

        // Extract adapter IDs from flow definition
        Set<UUID> adapterIds = extractAdapterIdsFromFlowDefinition(flow.getFlowDefinition());

        if (adapterIds.isEmpty()) {
            errors.add("Flow does not contain any adapter nodes. Please add at least one sender or receiver adapter to the flow in the visual flow designer.");
            result.put("isValid", false);
            result.put("errors", errors);
            return result;
        }

        // Validate each adapter
        List<String> inactiveAdapters = new ArrayList<>();
        List<String> missingAdapters = new ArrayList<>();

        for (UUID adapterId : adapterIds) {
            Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);

            if (!adapterOpt.isPresent()) {
                missingAdapters.add("Adapter with ID " + adapterId + " not found");
            } else {
                Adapter adapter = adapterOpt.get();
                if (!adapter.getActive()) {
                    inactiveAdapters.add(adapter.getName() + " (" + adapter.getAdapterType() + " - " + adapter.getDirection() + ")");
                }
            }
        }

        // Build error messages
        if (!missingAdapters.isEmpty()) {
            errors.add("Missing adapters: " + String.join(", ", missingAdapters));
        }

        if (!inactiveAdapters.isEmpty()) {
            errors.add("The following adapters must be activated before activating this flow: " + String.join(", ", inactiveAdapters));
        }

        // Check if flow definition is valid
        if (flow.getFlowDefinition() == null || flow.getFlowDefinition().isEmpty()) {
            errors.add("Flow definition is empty");
        }

        // All validations passed
        result.put("isValid", errors.isEmpty());
        result.put("errors", errors);

        if (errors.isEmpty()) {
            logger.info("Flow {} validation for activation passed", flowId);
        } else {
            logger.warn("Flow {} validation for activation failed: {}", flowId, errors);
        }

        return result;
    }

    /**
     * Extract adapter IDs from flow definition
     */
    private Set<UUID> extractAdapterIdsFromFlowDefinition(Map<String, Object> flowDefinition) {
        Set<UUID> adapterIds = new HashSet<>();

        if (flowDefinition == null || !flowDefinition.containsKey("nodes")) {
            return adapterIds;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) flowDefinition.get("nodes");

        for (Map<String, Object> node : nodes) {
            // Get node type from node level, not data level
            String nodeType = (String) node.get("type");

            Object nodeData = node.get("data");
            if (nodeData == null) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) nodeData;

            UUID adapterId = null;

            // Different node types store adapter ID in different fields
            switch (nodeType != null ? nodeType : "") {
                case "adapter":
                    // ADAPTER nodes use 'adapterId' field
                    Object adapterIdObj = data.get("adapterId");
                    if (adapterIdObj != null) {
                        adapterId = parseAdapterId(adapterIdObj.toString());
                    }
                    break;

                case "start":
                case "start-process":
                    // START nodes use 'senderAdapter' field
                    Object senderAdapterObj = data.get("senderAdapter");
                    if (senderAdapterObj != null) {
                        adapterId = parseAdapterId(senderAdapterObj.toString());
                    }
                    break;

                case "end":
                case "end-process":
                case "message-end":
                    // END nodes use 'receiverAdapter' field
                    Object receiverAdapterObj = data.get("receiverAdapter");
                    if (receiverAdapterObj != null) {
                        adapterId = parseAdapterId(receiverAdapterObj.toString());
                    }
                    break;
            }

            if (adapterId != null) {
                adapterIds.add(adapterId);
                logger.debug("Found adapter ID {} in node type {}", adapterId, nodeType);
            }
        }

        logger.debug("Extracted {} adapter IDs from flow definition", adapterIds.size());
        return adapterIds;
    }

    private UUID parseAdapterId(String adapterIdStr) {
        try {
            return UUID.fromString(adapterIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid adapter ID format: {}", adapterIdStr);
            return null;
        }
    }
}