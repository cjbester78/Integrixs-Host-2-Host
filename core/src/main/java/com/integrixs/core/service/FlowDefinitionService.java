package com.integrixs.core.service;

import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.FlowUtilityRepository;
import com.integrixs.core.util.FlowExportCrypto;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.DeployedFlow;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing visual flow definitions and orchestration
 * Handles CRUD operations for integration flows and flow validation
 */
@Service
public class FlowDefinitionService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowDefinitionService.class);
    
    private final IntegrationFlowRepository flowRepository;
    private final DeployedFlowRepository deployedFlowRepository;
    private final AdapterRepository adapterRepository;
    private final FlowUtilityRepository utilityRepository;
    private final DeployedFlowSchedulingService deployedFlowSchedulingService;
    private final FlowExportCrypto flowExportCrypto;
    
    @Autowired
    public FlowDefinitionService(IntegrationFlowRepository flowRepository,
                                DeployedFlowRepository deployedFlowRepository,
                                AdapterRepository adapterRepository,
                                FlowUtilityRepository utilityRepository,
                                DeployedFlowSchedulingService deployedFlowSchedulingService,
                                FlowExportCrypto flowExportCrypto) {
        this.flowRepository = flowRepository;
        this.deployedFlowRepository = deployedFlowRepository;
        this.adapterRepository = adapterRepository;
        this.utilityRepository = utilityRepository;
        this.deployedFlowSchedulingService = deployedFlowSchedulingService;
        this.flowExportCrypto = flowExportCrypto;
    }
    
    /**
     * Get all flows
     */
    public List<IntegrationFlow> getAllFlows() {
        logger.debug("Retrieving all integration flows");
        return flowRepository.findAll();
    }
    
    /**
     * Get active flows
     */
    public List<IntegrationFlow> getActiveFlows() {
        logger.debug("Retrieving active integration flows");
        return flowRepository.findByActive(true);
    }
    
    /**
     * Get scheduled flows
     */
    public List<IntegrationFlow> getScheduledFlows() {
        logger.debug("Retrieving scheduled integration flows");
        return flowRepository.findScheduledFlows();
    }
    
    /**
     * Get flow by ID
     */
    public Optional<IntegrationFlow> getFlowById(UUID id) {
        logger.debug("Retrieving integration flow by ID: {}", id);
        return flowRepository.findById(id);
    }
    
    /**
     * Get flow by name
     */
    public Optional<IntegrationFlow> getFlowByName(String name) {
        logger.debug("Retrieving integration flow by name: {}", name);
        return flowRepository.findByName(name);
    }
    
    /**
     * Check if flow exists by name
     */
    public boolean existsByName(String name) {
        return flowRepository.existsByName(name);
    }
    
    /**
     * Create a new integration flow
     */
    public IntegrationFlow createFlow(IntegrationFlow flow, UUID createdBy) {
        logger.info("Creating integration flow: {} by user: {}", flow.getName(), createdBy);
        
        // Validate flow
        validateFlow(flow);
        
        // Check for duplicate names
        if (existsByName(flow.getName())) {
            throw new IllegalArgumentException("Flow with name '" + flow.getName() + "' already exists");
        }
        
        // Set audit information
        LocalDateTime now = LocalDateTime.now();
        flow.setCreatedAt(now);
        flow.setUpdatedAt(now);
        flow.setCreatedBy(createdBy);
        flow.setUpdatedBy(createdBy);
        
        // Initialize flow if needed
        if (flow.getId() == null) {
            flow.setId(UUID.randomUUID());
        }
        
        // Clean any embedded adapter configurations to ensure we use database configs
        if (flow.getFlowDefinition() != null) {
            Map<String, Object> cleanedDefinition = stripEmbeddedAdapterConfigurations(flow.getFlowDefinition());
            flow.setFlowDefinition(cleanedDefinition);
        }
        
        // Save flow
        UUID id = flowRepository.save(flow);
        flow.setId(id);
        
        logger.info("Successfully created integration flow: {} with ID: {}", flow.getName(), id);
        return flow;
    }
    
    /**
     * Update existing integration flow
     */
    public IntegrationFlow updateFlow(UUID id, IntegrationFlow flow, UUID updatedBy) {
        logger.info("Updating integration flow: {} by user: {}", id, updatedBy);
        logger.debug("Flow update details - Name: {}, Description: {}, Active: {}", flow.getName(), flow.getDescription(), flow.getActive());
        logger.debug("Flow definition structure: {}", flow.getFlowDefinition());
        
        // Check if flow exists
        Optional<IntegrationFlow> existingFlow = getFlowById(id);
        if (existingFlow.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + id + " not found");
        }
        
        // Check if flow is currently deployed (prevent editing deployed flows)
        Optional<DeployedFlow> deployedFlow = deployedFlowRepository.findByFlowId(id);
        if (deployedFlow.isPresent()) {
            throw new IllegalArgumentException("Cannot edit a deployed flow. This flow is currently running and must be undeployed first. Use the 'Undeploy' button to stop the flow, then make your changes.");
        }
        
        // Validate flow
        logger.debug("Starting flow validation for flow: {}", id);
        Map<String, Object> validationResult = validateFlow(flow);
        logger.debug("Flow validation result: {}", validationResult);
        
        if (validationResult != null && !(Boolean) validationResult.getOrDefault("valid", false)) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validationResult.getOrDefault("errors", List.of());
            logger.warn("Flow validation failed with errors: {}", errors);
        }
        
        // Check for duplicate names (exclude current flow)
        Optional<IntegrationFlow> duplicateNameFlow = getFlowByName(flow.getName());
        if (duplicateNameFlow.isPresent() && !duplicateNameFlow.get().getId().equals(id)) {
            throw new IllegalArgumentException("Flow with name '" + flow.getName() + "' already exists");
        }
        
        // Preserve creation audit information and increment version
        IntegrationFlow existing = existingFlow.get();
        flow.setId(id);
        flow.setCreatedAt(existing.getCreatedAt());
        flow.setCreatedBy(existing.getCreatedBy());
        flow.setUpdatedAt(LocalDateTime.now());
        flow.setUpdatedBy(updatedBy);
        flow.incrementVersion();
        
        // Update scheduled run if scheduling enabled
        flow.updateNextScheduledRun();
        
        // Clean any embedded adapter configurations to ensure we use database configs
        if (flow.getFlowDefinition() != null) {
            Map<String, Object> cleanedDefinition = stripEmbeddedAdapterConfigurations(flow.getFlowDefinition());
            flow.setFlowDefinition(cleanedDefinition);
        }
        
        // Update flow
        flowRepository.update(flow);
        
        logger.info("Successfully updated integration flow: {} to version: {}", id, flow.getFlowVersion());
        return flow;
    }
    
    /**
     * Set flow active status
     */
    public void setFlowActive(UUID id, boolean active) {
        logger.info("Setting flow {} active status to: {}", id, active);
        
        Optional<IntegrationFlow> flow = getFlowById(id);
        if (flow.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + id + " not found");
        }
        
        flowRepository.setActive(id, active);
        logger.info("Successfully set flow {} active status to: {}", id, active);
    }
    
    /**
     * Delete integration flow
     */
    public boolean deleteFlow(UUID id) {
        logger.info("Deleting integration flow: {}", id);
        
        Optional<IntegrationFlow> flow = getFlowById(id);
        if (flow.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + id + " not found");
        }
        
        // Check if flow is currently deployed (prevent deleting deployed flows)
        Optional<DeployedFlow> deployedFlow = deployedFlowRepository.findByFlowId(id);
        if (deployedFlow.isPresent()) {
            throw new IllegalArgumentException("Cannot delete a deployed flow. This flow is currently running and must be undeployed first. Use the 'Undeploy' button to stop the flow, then delete it.");
        }
        
        // Check if flow has active executions
        // This would require checking flow_executions table for active runs
        // For now, we'll allow deletion but this should be enhanced
        
        boolean deleted = flowRepository.deleteById(id);
        if (deleted) {
            logger.info("Successfully deleted integration flow: {}", id);
        } else {
            logger.warn("Failed to delete integration flow: {}", id);
        }
        
        return deleted;
    }
    
    /**
     * Validate flow definition and structure
     */
    public Map<String, Object> validateFlow(UUID id) {
        logger.info("Validating integration flow: {}", id);
        
        Optional<IntegrationFlow> flowOpt = getFlowById(id);
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + id + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        return validateFlow(flow);
    }
    
    /**
     * Get flow statistics
     */
    public Map<String, Object> getFlowStatistics() {
        logger.debug("Retrieving flow statistics");
        return flowRepository.getFlowStatistics();
    }
    
    /**
     * Get available adapters for flow creation
     */
    public List<Adapter> getAvailableAdapters() {
        logger.debug("Retrieving available adapters for flow creation");
        return adapterRepository.findAllActive();
    }
    
    /**
     * Get available utilities for flow creation
     */
    public List<FlowUtility> getAvailableUtilities() {
        logger.debug("Retrieving available utilities for flow creation");
        return utilityRepository.findAll();
    }
    
    /**
     * Export flow as encrypted JSON with all linked adapters.
     * The exported file is encrypted to prevent unauthorized reading.
     */
    public Map<String, Object> exportFlow(UUID id) {
        logger.info("Exporting integration flow: {}", id);
        
        Optional<IntegrationFlow> flowOpt = getFlowById(id);
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + id + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        Map<String, Object> exportData = new HashMap<>();
        
        // Export flow metadata
        exportData.put("id", flow.getId().toString());  // Add unique flow ID for duplicate detection
        exportData.put("name", flow.getName());
        exportData.put("description", flow.getDescription());
        exportData.put("bankName", flow.getBankName());
        exportData.put("flowDefinition", flow.getFlowDefinition());
        exportData.put("flowType", flow.getFlowType());
        exportData.put("timeoutMinutes", flow.getTimeoutMinutes());
        exportData.put("maxParallelExecutions", flow.getMaxParallelExecutions());
        exportData.put("retryPolicy", flow.getRetryPolicy());
        exportData.put("scheduleEnabled", flow.getScheduleEnabled());
        exportData.put("scheduleCron", flow.getScheduleCron());
        
        // Extract and export all linked adapters
        Set<UUID> adapterIds = extractAdapterIdsFromFlow(flow.getFlowDefinition());
        List<Map<String, Object>> adapters = new ArrayList<>();
        
        for (UUID adapterId : adapterIds) {
            Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
            if (adapterOpt.isPresent()) {
                Adapter adapter = adapterOpt.get();
                Map<String, Object> adapterData = new HashMap<>();
                
                // Export adapter configuration
                adapterData.put("id", adapter.getId().toString());
                adapterData.put("name", adapter.getName());
                adapterData.put("bank", adapter.getBank());
                adapterData.put("description", adapter.getDescription());
                adapterData.put("adapterType", adapter.getAdapterType());
                adapterData.put("direction", adapter.getDirection());
                adapterData.put("configuration", sanitizeAdapterConfiguration(adapter.getConfiguration()));
                adapterData.put("active", adapter.isActive());
                adapterData.put("connectionValidated", adapter.getConnectionValidated());
                adapterData.put("averageExecutionTimeMs", adapter.getAverageExecutionTimeMs());
                adapterData.put("successRatePercent", adapter.getSuccessRatePercent());
                
                adapters.add(adapterData);
                logger.debug("Exported adapter: {} ({})", adapter.getName(), adapterId);
            } else {
                logger.warn("Referenced adapter not found: {}", adapterId);
            }
        }
        
        exportData.put("adapters", adapters);
        logger.info("Exported {} linked adapters with flow", adapters.size());
        
        // Export metadata
        exportData.put("exportedAt", LocalDateTime.now().toString());
        exportData.put("version", flow.getFlowVersion());
        exportData.put("exportFormat", "H2H_FLOW_V2");
        
        // Encrypt the export data for security
        Map<String, Object> encryptedExport = flowExportCrypto.encryptFlowExport(exportData);
        
        logger.info("Successfully exported and encrypted integration flow: {} with {} adapters", id, adapters.size());
        return encryptedExport;
    }
    
    /**
     * Extract all adapter IDs referenced in a flow definition
     */
    private Set<UUID> extractAdapterIdsFromFlow(Map<String, Object> flowDefinition) {
        Set<UUID> adapterIds = new HashSet<>();
        
        if (flowDefinition == null || !flowDefinition.containsKey("nodes")) {
            return adapterIds;
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) flowDefinition.get("nodes");
        
        for (Map<String, Object> node : nodes) {
            String nodeType = (String) node.get("type");
            
            if (nodeType == null) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
            
            if (nodeData == null) {
                continue;
            }
            
            UUID adapterId = null;
            
            // Extract adapter ID based on node type
            switch (nodeType) {
                case "start":
                case "start-process":
                    // START nodes use 'senderAdapter' field
                    Object senderAdapterObj = nodeData.get("senderAdapter");
                    if (senderAdapterObj != null) {
                        adapterId = parseAdapterId(senderAdapterObj.toString());
                    }
                    break;
                    
                case "end":
                case "end-process":
                case "message-end":
                    // END nodes use 'adapterId' field
                    Object endAdapterObj = nodeData.get("adapterId");
                    if (endAdapterObj != null) {
                        adapterId = parseAdapterId(endAdapterObj.toString());
                    }
                    break;
                    
                case "adapter":
                    // Intermediate adapter nodes use 'adapterId' field
                    Object adapterObj = nodeData.get("adapterId");
                    if (adapterObj != null) {
                        adapterId = parseAdapterId(adapterObj.toString());
                    }
                    break;
                    
                default:
                    // Check if any other node type has adapter reference
                    Object anyAdapterObj = nodeData.get("adapterId");
                    if (anyAdapterObj != null) {
                        adapterId = parseAdapterId(anyAdapterObj.toString());
                    }
                    break;
            }
            
            if (adapterId != null) {
                adapterIds.add(adapterId);
                logger.debug("Found adapter reference: {} in node type: {}", adapterId, nodeType);
            }
        }
        
        logger.debug("Extracted {} adapter IDs from flow definition", adapterIds.size());
        return adapterIds;
    }
    
    /**
     * Safely parse adapter ID string to UUID
     */
    private UUID parseAdapterId(String adapterIdStr) {
        try {
            if (adapterIdStr != null && !adapterIdStr.trim().isEmpty()) {
                return UUID.fromString(adapterIdStr.trim());
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid adapter ID format: {}", adapterIdStr);
        }
        return null;
    }
    
    /**
     * Import flow from JSON (supports both encrypted and unencrypted formats)
     */
    public IntegrationFlow importFlow(Map<String, Object> importData, UUID importedBy) {
        logger.info("Importing integration flow by user: {}", importedBy);
        logger.debug("Import data keys: {}", importData.keySet());
        
        try {
            // Check if the import data is encrypted
            Map<String, Object> actualImportData = importData;
            if (flowExportCrypto.isEncryptedFlowExport(importData)) {
                logger.info("Detected encrypted flow export, decrypting...");
                actualImportData = flowExportCrypto.decryptFlowExport(importData);
                logger.info("Flow export decrypted successfully");
            }
            
            // Validate import data
            if (!actualImportData.containsKey("name") || !actualImportData.containsKey("flowDefinition")) {
                throw new IllegalArgumentException("Import data must contain 'name' and 'flowDefinition'");
            }
            
            // Check export format version and handle adapters if present
            Object exportFormatObj = actualImportData.getOrDefault("exportFormat", "H2H_FLOW_V1");
            String exportFormat = exportFormatObj != null ? exportFormatObj.toString() : "H2H_FLOW_V1";
            logger.info("Export format detected: {} (containsAdapters: {})", exportFormat, actualImportData.containsKey("adapters"));
            
            Map<UUID, UUID> adapterIdMapping = new HashMap<>();
            
            if ("H2H_FLOW_V2".equals(exportFormat) && actualImportData.containsKey("adapters")) {
                logger.info("Importing flow with adapters (format version: {})", exportFormat);
                logger.info("Adapters field found in import data");
                Object adaptersObj = actualImportData.get("adapters");
                if (adaptersObj instanceof List<?>) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> adaptersList = (List<Map<String, Object>>) adaptersObj;
                        adapterIdMapping = importAdapters(adaptersList, importedBy);
                    } catch (ClassCastException e) {
                        logger.warn("Invalid adapters format in import data, skipping adapter import: {}", e.getMessage());
                    }
                } else {
                    logger.warn("Adapters field is not a list, skipping adapter import");
                }
            }
        
            // Create new flow from import data
            IntegrationFlow flow = new IntegrationFlow();
            
            Object nameObj = actualImportData.get("name");
            String originalName = nameObj != null ? nameObj.toString() : "Imported Flow";
            
            // Check if this flow has already been imported by its unique ID (if available)
            Object originalIdObj = actualImportData.get("id");
            if (originalIdObj != null) {
                try {
                    UUID originalFlowId = UUID.fromString(originalIdObj.toString());
                    if (flowRepository.existsByOriginalFlowId(originalFlowId)) {
                        throw new IllegalArgumentException("Flow '" + originalName + "' (ID: " + originalFlowId + ") has already been imported. Please use a different flow or delete the existing imported flow first.");
                    }
                    flow.setOriginalFlowId(originalFlowId);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid flow ID format in import data: {}", originalIdObj);
                }
            }
            
            // Use the original name from the export file, not a modified name
            flow.setName(originalName);
            
            Object descObj = actualImportData.getOrDefault("description", "Imported flow");
            String description = descObj != null ? descObj.toString() : "Imported flow";
            flow.setDescription(description);
            
            // Import bank name if provided
            Object bankNameObj = actualImportData.get("bankName");
            if (bankNameObj != null) {
                String bankName = bankNameObj.toString();
                flow.setBankName(bankName);
            }
            
            // Update flow definition with new adapter IDs if needed
            Object flowDefObj = actualImportData.get("flowDefinition");
            if (!(flowDefObj instanceof Map)) {
                throw new IllegalArgumentException("flowDefinition must be a valid JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> flowDefinition = (Map<String, Object>) flowDefObj;
            
            if (!adapterIdMapping.isEmpty()) {
                flowDefinition = updateFlowDefinitionAdapterIds(flowDefinition, adapterIdMapping);
            }
            
            // Clean any embedded adapter configurations to ensure we use database configs
            flowDefinition = stripEmbeddedAdapterConfigurations(flowDefinition);
            
            flow.setFlowDefinition(flowDefinition);
            
            Object flowTypeObj = actualImportData.getOrDefault("flowType", "STANDARD");
            String flowType = flowTypeObj != null ? flowTypeObj.toString() : "STANDARD";
            flow.setFlowType(flowType);
            
            Object timeoutObj = actualImportData.getOrDefault("timeoutMinutes", 60);
            Integer timeoutMinutes = 60;
            if (timeoutObj instanceof Integer) {
                timeoutMinutes = (Integer) timeoutObj;
            } else if (timeoutObj instanceof Number) {
                timeoutMinutes = ((Number) timeoutObj).intValue();
            }
            flow.setTimeoutMinutes(timeoutMinutes);
            
            Object maxParallelObj = actualImportData.getOrDefault("maxParallelExecutions", 1);
            Integer maxParallelExecutions = 1;
            if (maxParallelObj instanceof Integer) {
                maxParallelExecutions = (Integer) maxParallelObj;
            } else if (maxParallelObj instanceof Number) {
                maxParallelExecutions = ((Number) maxParallelObj).intValue();
            }
            flow.setMaxParallelExecutions(maxParallelExecutions);
            
            Object retryPolicyObj = actualImportData.get("retryPolicy");
            if (retryPolicyObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> retryPolicy = (Map<String, Object>) retryPolicyObj;
                flow.setRetryPolicy(retryPolicy);
            }
            
            flow.setScheduleEnabled(false); // Don't import scheduling to prevent conflicts
            flow.setActive(false); // Import as inactive for safety
            
            // Validate imported flow
            validateFlow(flow);
            
            // Create the flow
            IntegrationFlow createdFlow = createFlow(flow, importedBy);
            
            logger.info("Successfully imported integration flow: {} as {} (adapters imported: {})", 
                        originalName, createdFlow.getId(), adapterIdMapping.size());
            return createdFlow;
            
        } catch (Exception e) {
            logger.error("Error importing integration flow: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import flow: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate unique flow name
     */
    private String generateUniqueFlowName(String baseName) {
        String candidateName = baseName;
        int counter = 1;
        
        while (existsByName(candidateName)) {
            candidateName = baseName + " (" + counter + ")";
            counter++;
        }
        
        return candidateName;
    }
    
    /**
     * Import adapters from export data
     */
    private Map<UUID, UUID> importAdapters(List<Map<String, Object>> adaptersData, UUID importedBy) {
        logger.info("Starting to import {} adapters", adaptersData.size());
        Map<UUID, UUID> adapterIdMapping = new HashMap<>();
        
        for (Map<String, Object> adapterData : adaptersData) {
            logger.info("Processing adapter: {}", adapterData.get("name"));
            try {
                UUID originalId = UUID.fromString((String) adapterData.get("id"));
                
                // Create new adapter with imported configuration
                Adapter adapter = new Adapter();
                adapter.setName((String) adapterData.get("name"));
                adapter.setBank((String) adapterData.get("bank"));
                adapter.setDescription((String) adapterData.get("description"));
                adapter.setAdapterType((String) adapterData.get("adapterType"));
                adapter.setDirection((String) adapterData.get("direction"));
                // Set configuration with placeholders for sensitive fields that were removed during export
                Map<String, Object> importedConfig = (Map<String, Object>) adapterData.get("configuration");
                Map<String, Object> configWithPlaceholders = addPlaceholdersForSensitiveFields(importedConfig, adapter.getAdapterType());
                adapter.setConfiguration(configWithPlaceholders);
                // Import as inactive since sensitive configuration fields need to be reconfigured
                adapter.setActive(false);
                adapter.setConnectionValidated(false);
                if (adapterData.get("averageExecutionTimeMs") != null) {
                    adapter.setAverageExecutionTimeMs(((Number) adapterData.get("averageExecutionTimeMs")).longValue());
                }
                if (adapterData.get("successRatePercent") != null) {
                    adapter.setSuccessRatePercent(new java.math.BigDecimal(adapterData.get("successRatePercent").toString()));
                }
                adapter.setCreatedBy(importedBy);
                adapter.setUpdatedBy(importedBy);
                
                // Check if adapter is already imported (prevent duplicates)
                String originalName = adapter.getName();
                if (adapterRepository.existsByName(originalName)) {
                    throw new IllegalArgumentException("Adapter '" + originalName + "' already exists. Please use a different name or delete the existing adapter first.");
                }
                
                // Save the adapter
                UUID newId = adapterRepository.save(adapter);
                adapter.setId(newId);
                
                // Map old ID to new ID
                adapterIdMapping.put(originalId, newId);
                
                logger.info("Imported adapter: {} -> {} (original: {})", 
                           originalName, newId, originalId);
                
            } catch (Exception e) {
                logger.error("Failed to import adapter: {}", adapterData.get("name"), e);
                // Continue with other adapters
            }
        }
        
        return adapterIdMapping;
    }
    
    /**
     * Generate unique adapter name
     */
    private String generateUniqueAdapterName(String baseName) {
        String candidateName = baseName;
        int counter = 1;
        
        while (adapterRepository.existsByName(candidateName)) {
            candidateName = baseName + " (" + counter + ")";
            counter++;
        }
        
        return candidateName;
    }
    
    /**
     * Update flow definition with new adapter IDs
     */
    private Map<String, Object> updateFlowDefinitionAdapterIds(Map<String, Object> flowDefinition, Map<UUID, UUID> adapterIdMapping) {
        Map<String, Object> updatedFlowDefinition = new HashMap<>(flowDefinition);
        
        // Update adapter IDs in nodes
        if (updatedFlowDefinition.containsKey("nodes")) {
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) updatedFlowDefinition.get("nodes");
            for (Map<String, Object> node : nodes) {
                if (node.containsKey("data")) {
                    Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
                    if (nodeData.containsKey("adapterId")) {
                        UUID oldAdapterId = parseAdapterId((String) nodeData.get("adapterId"));
                        if (oldAdapterId != null && adapterIdMapping.containsKey(oldAdapterId)) {
                            nodeData.put("adapterId", adapterIdMapping.get(oldAdapterId).toString());
                            logger.debug("Updated adapter ID in node: {} -> {}", oldAdapterId, adapterIdMapping.get(oldAdapterId));
                        }
                    }
                }
            }
        }
        
        return updatedFlowDefinition;
    }
    
    /**
     * Sanitize adapter configuration by removing sensitive connection details
     * while preserving all other configuration settings
     */
    private Map<String, Object> sanitizeAdapterConfiguration(Map<String, Object> originalConfig) {
        if (originalConfig == null || originalConfig.isEmpty()) {
            return originalConfig;
        }
        
        // Create a copy of the original configuration
        Map<String, Object> sanitizedConfig = new HashMap<>(originalConfig);
        
        // List of sensitive field names to remove (case-insensitive matching)
        String[] sensitiveFields = {
            "host", "hostname", "server", "ip", "ipAddress", "serverAddress",
            "username", "user", "login", "userId", "account",
            "password", "pwd", "pass", "secret", "key", "token",
            "directory", "path", "folder", "remotePath", "localPath", 
            "sourceDirectory", "targetDirectory", "inputDirectory", "outputDirectory",
            "workingDirectory", "baseDirectory", "rootDirectory"
        };
        
        // Remove sensitive fields (case-insensitive)
        for (String sensitiveField : sensitiveFields) {
            sanitizedConfig.entrySet().removeIf(entry -> 
                entry.getKey() != null && entry.getKey().toLowerCase().contains(sensitiveField.toLowerCase())
            );
        }
        
        // Also check for nested configurations (like connectionSettings, sftpConfig, etc.)
        for (Map.Entry<String, Object> entry : sanitizedConfig.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedConfig = (Map<String, Object>) entry.getValue();
                entry.setValue(sanitizeAdapterConfiguration(nestedConfig));
            }
        }
        
        logger.debug("Sanitized adapter configuration: removed sensitive fields, kept {} fields", 
                    sanitizedConfig.size());
        return sanitizedConfig;
    }
    
    /**
     * Strip embedded adapter configurations from flow definition nodes.
     * This ensures flows only store adapter ID references and always use live database configurations.
     * 
     * @param flowDefinition The flow definition map containing nodes and edges
     * @return Cleaned flow definition with embedded configurations removed
     */
    private Map<String, Object> stripEmbeddedAdapterConfigurations(Map<String, Object> flowDefinition) {
        if (flowDefinition == null || flowDefinition.isEmpty()) {
            return Collections.emptyMap();
        }
        
        final FlowDefinitionCleaner cleaner = new FlowDefinitionCleaner();
        return cleaner.cleanFlowDefinition(flowDefinition);
    }
    
    /**
     * Inner class responsible for cleaning flow definitions according to OOP principles:
     * - Single Responsibility: Only handles flow definition cleaning
     * - Immutability: Returns new objects rather than modifying input
     * - Type Safety: Proper type checking and casting
     */
    private static class FlowDefinitionCleaner {
        
        private static final Logger logger = LoggerFactory.getLogger(FlowDefinitionCleaner.class);
        
        // Immutable set of allowed adapter node properties
        private static final Set<String> ALLOWED_ADAPTER_PROPERTIES = Set.of(
            "label", "adapterType", "direction", "adapterId", 
            "showDeleteButton", "availableTypes"
        );
        
        /**
         * Clean flow definition by processing all nodes
         */
        public Map<String, Object> cleanFlowDefinition(Map<String, Object> originalDefinition) {
            final Map<String, Object> cleanedDefinition = createDeepCopy(originalDefinition);
            
            Optional<List<Map<String, Object>>> nodes = extractNodes(cleanedDefinition);
            if (nodes.isPresent()) {
                List<Map<String, Object>> cleanedNodes = nodes.get()
                    .stream()
                    .map(this::processNode)
                    .collect(Collectors.toList());
                    
                cleanedDefinition.put("nodes", cleanedNodes);
                logger.debug("Stripped embedded adapter configurations from {} nodes", cleanedNodes.size());
            }
            
            return cleanedDefinition;
        }
        
        /**
         * Create a deep copy of the flow definition to ensure immutability
         */
        private Map<String, Object> createDeepCopy(Map<String, Object> original) {
            return new HashMap<>(original);
        }
        
        /**
         * Safely extract nodes from flow definition with type checking
         */
        private Optional<List<Map<String, Object>>> extractNodes(Map<String, Object> flowDefinition) {
            Object nodesObj = flowDefinition.get("nodes");
            
            if (!(nodesObj instanceof List)) {
                return Optional.empty();
            }
            
            try {
                @SuppressWarnings("unchecked")
                List<Object> rawNodes = (List<Object>) nodesObj;
                
                List<Map<String, Object>> typedNodes = rawNodes.stream()
                    .filter(node -> node instanceof Map)
                    .map(node -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedNode = (Map<String, Object>) node;
                        return typedNode;
                    })
                    .collect(Collectors.toList());
                    
                return Optional.of(typedNodes);
            } catch (ClassCastException e) {
                logger.warn("Invalid node structure in flow definition", e);
                return Optional.empty();
            }
        }
        
        /**
         * Process individual node - clean if adapter, pass through if not
         */
        private Map<String, Object> processNode(Map<String, Object> node) {
            if (isAdapterNode(node)) {
                return cleanAdapterNode(node);
            }
            return new HashMap<>(node); // Return immutable copy
        }
        
        /**
         * Check if node is an adapter type that needs cleaning
         */
        private boolean isAdapterNode(Map<String, Object> node) {
            return "adapter".equals(node.get("type"));
        }
        
        /**
         * Clean adapter node by removing embedded configurations while preserving references
         */
        private Map<String, Object> cleanAdapterNode(Map<String, Object> adapterNode) {
            Map<String, Object> cleanedNode = new HashMap<>(adapterNode);
            
            Optional<Map<String, Object>> nodeData = extractNodeData(adapterNode);
            if (nodeData.isPresent()) {
                Map<String, Object> cleanedData = filterAllowedProperties(nodeData.get());
                cleanedNode.put("data", cleanedData);
                
                logger.debug("Cleaned adapter node: {}, removed embedded configuration", 
                           cleanedData.get("label"));
            }
            
            return cleanedNode;
        }
        
        /**
         * Safely extract node data with type checking
         */
        private Optional<Map<String, Object>> extractNodeData(Map<String, Object> node) {
            Object dataObj = node.get("data");
            
            if (!(dataObj instanceof Map)) {
                return Optional.empty();
            }
            
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> nodeData = (Map<String, Object>) dataObj;
                return Optional.of(nodeData);
            } catch (ClassCastException e) {
                logger.warn("Invalid node data structure", e);
                return Optional.empty();
            }
        }
        
        /**
         * Filter node properties to only include allowed adapter references
         */
        private Map<String, Object> filterAllowedProperties(Map<String, Object> nodeData) {
            return nodeData.entrySet()
                .stream()
                .filter(entry -> ALLOWED_ADAPTER_PROPERTIES.contains(entry.getKey()))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (existing, replacement) -> existing,
                    HashMap::new
                ));
        }
    }

    /**
     * Add placeholders for sensitive fields that were removed during export
     * This ensures imported adapters have the required configuration structure
     */
    private Map<String, Object> addPlaceholdersForSensitiveFields(Map<String, Object> config, String adapterType) {
        if (config == null) {
            config = new HashMap<>();
        }
        
        Map<String, Object> configWithPlaceholders = new HashMap<>(config);
        
        // Add common placeholders based on adapter type
        if ("SFTP".equalsIgnoreCase(adapterType)) {
            configWithPlaceholders.putIfAbsent("host", "CONFIGURE_HOST");
            configWithPlaceholders.putIfAbsent("username", "CONFIGURE_USERNAME");
            configWithPlaceholders.putIfAbsent("password", "CONFIGURE_PASSWORD");
            configWithPlaceholders.putIfAbsent("remotePath", "CONFIGURE_REMOTE_PATH");
        } else if ("FILE".equalsIgnoreCase(adapterType)) {
            configWithPlaceholders.putIfAbsent("directory", "CONFIGURE_DIRECTORY");
            configWithPlaceholders.putIfAbsent("path", "CONFIGURE_PATH");
        } else if ("EMAIL".equalsIgnoreCase(adapterType)) {
            configWithPlaceholders.putIfAbsent("host", "CONFIGURE_SMTP_HOST");
            configWithPlaceholders.putIfAbsent("username", "CONFIGURE_EMAIL_USERNAME");
            configWithPlaceholders.putIfAbsent("password", "CONFIGURE_EMAIL_PASSWORD");
        }
        
        logger.debug("Added placeholders for {} adapter configuration", adapterType);
        return configWithPlaceholders;
    }
    
    /**
     * Validate flow configuration and structure
     */
    private Map<String, Object> validateFlow(IntegrationFlow flow) {
        Map<String, Object> validation = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic validation
        if (flow.getName() == null || flow.getName().trim().isEmpty()) {
            errors.add("Flow name is required");
        }
        
        if (flow.getFlowDefinition() == null || flow.getFlowDefinition().isEmpty()) {
            errors.add("Flow definition is required");
        }
        
        // Validate flow definition structure
        if (flow.getFlowDefinition() != null) {
            validateFlowDefinitionStructure(flow.getFlowDefinition(), errors, warnings);
        }
        
        // Validate timeout and execution settings
        if (flow.getTimeoutMinutes() != null && flow.getTimeoutMinutes() <= 0) {
            errors.add("Timeout minutes must be greater than 0");
        }
        
        if (flow.getMaxParallelExecutions() != null && flow.getMaxParallelExecutions() <= 0) {
            errors.add("Max parallel executions must be greater than 0");
        }
        
        // Validate scheduling configuration
        if (flow.getScheduleEnabled() != null && flow.getScheduleEnabled()) {
            if (flow.getScheduleCron() == null || flow.getScheduleCron().trim().isEmpty()) {
                errors.add("Schedule cron expression is required when scheduling is enabled");
            } else {
                validateCronExpression(flow.getScheduleCron(), errors);
            }
        }
        
        validation.put("valid", errors.isEmpty());
        validation.put("errors", errors);
        validation.put("warnings", warnings);
        validation.put("errorCount", errors.size());
        validation.put("warningCount", warnings.size());
        
        return validation;
    }
    
    /**
     * Validate flow definition structure (nodes and connections)
     * Supports both React Flow format (nodes/edges) and legacy format (nodes/connections)
     */
    @SuppressWarnings("unchecked")
    private void validateFlowDefinitionStructure(Map<String, Object> flowDefinition, 
                                                List<String> errors, 
                                                List<String> warnings) {
        
        logger.debug("Validating flow definition structure with keys: {}", flowDefinition.keySet());
        
        // Check for required flow structure elements
        if (!flowDefinition.containsKey("nodes")) {
            logger.error("Flow definition missing 'nodes' array");
            errors.add("Flow definition must contain 'nodes' array");
            return;
        }
        
        // Support both React Flow format (edges) and legacy format (connections)
        boolean hasEdges = flowDefinition.containsKey("edges");
        boolean hasConnections = flowDefinition.containsKey("connections");
        
        if (!hasEdges && !hasConnections) {
            errors.add("Flow definition must contain 'edges' or 'connections' array");
            return;
        }
        
        // Validate nodes
        Object nodesObj = flowDefinition.get("nodes");
        if (!(nodesObj instanceof List)) {
            errors.add("Flow definition 'nodes' must be an array");
            return;
        }
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;
        if (nodes.isEmpty()) {
            errors.add("Flow must contain at least one node");
            return;
        }
        
        Set<String> nodeIds = new HashSet<>();
        boolean hasStartNode = false;
        boolean hasEndNode = false;
        
        // Validate each node
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            
            // Check required node fields
            if (!node.containsKey("id") || node.get("id") == null) {
                errors.add("Node " + i + " is missing required 'id' field");
                continue;
            }
            
            String nodeId = node.get("id").toString();
            if (nodeIds.contains(nodeId)) {
                errors.add("Duplicate node ID found: " + nodeId);
            }
            nodeIds.add(nodeId);
            
            if (!node.containsKey("type")) {
                errors.add("Node " + nodeId + " is missing required 'type' field");
            }
            
            String nodeType = node.getOrDefault("type", "").toString();
            if ("start".equalsIgnoreCase(nodeType) || nodeId.startsWith("start-process")) {
                hasStartNode = true;
            } else if ("end".equalsIgnoreCase(nodeType) || nodeId.startsWith("end-process")) {
                hasEndNode = true;
            }
            
            // Validate adapter nodes (contain the actual adapter configurations)
            if ("adapter".equalsIgnoreCase(nodeType)) {
                validateAdapterNode(node, nodeId, errors);
            }
            
            // Validate utility nodes
            if ("utility".equalsIgnoreCase(nodeType)) {
                validateUtilityNode(node, nodeId, errors);
            }
        }
        
        if (!hasStartNode) {
            warnings.add("Flow should have a start node");
        }
        
        if (!hasEndNode) {
            warnings.add("Flow should have an end node");
        }
        
        // Validate connections/edges
        List<Map<String, Object>> connections = new ArrayList<>();
        
        if (hasEdges) {
            // React Flow format with edges
            Object edgesObj = flowDefinition.get("edges");
            if (!(edgesObj instanceof List)) {
                errors.add("Flow definition 'edges' must be an array");
                return;
            }
            connections = (List<Map<String, Object>>) edgesObj;
        } else if (hasConnections) {
            // Legacy format with connections
            Object connectionsObj = flowDefinition.get("connections");
            if (!(connectionsObj instanceof List)) {
                errors.add("Flow definition 'connections' must be an array");
                return;
            }
            connections = (List<Map<String, Object>>) connectionsObj;
        }
        
        validateConnections(connections, nodeIds, errors, warnings, hasEdges);
    }
    
    /**
     * Validate adapter references in START/END nodes and legacy adapter nodes
     * START nodes should have 'senderAdapter' ID, END nodes should have 'adapterId' 
     * Legacy adapter nodes should have 'adapterId' in data
     */
    private void validateAdapterNode(Map<String, Object> node, String nodeId, List<String> errors) {
        String nodeType = (String) node.get("type");
        String adapterIdStr = "";
        
        // Extract adapter ID based on node type
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
        
        if ("start".equals(nodeType) || nodeId.startsWith("start-process")) {
            // START nodes in new structure don't contain adapter IDs - they connect to adapter nodes via edges
            // Skip adapter validation for start nodes
            return;
        } else if ("end".equals(nodeType) || nodeId.startsWith("end-process")) {
            // END nodes in new structure don't contain adapter IDs - they connect to adapter nodes via edges  
            // Skip adapter validation for end nodes
            return;
        } else if ("adapter".equals(nodeType)) {
            // Adapter nodes contain the actual adapter configurations
            if (nodeData != null && nodeData.containsKey("adapterId")) {
                adapterIdStr = nodeData.get("adapterId").toString();
            } else {
                errors.add("Adapter node " + nodeId + " is missing required 'adapterId' field");
                return;
            }
        } else {
            // Not an adapter node, skip validation
            return;
        }
        
        // Validate adapter ID format and existence (only for actual adapter nodes)
        if (!adapterIdStr.isEmpty()) {
            try {
                UUID adapterId = UUID.fromString(adapterIdStr);
                
                // Check if adapter exists and is active
                Optional<Adapter> adapter = adapterRepository.findById(adapterId);
                if (adapter.isEmpty()) {
                    errors.add("Node " + nodeId + " references non-existent adapter: " + adapterId);
                } else if (!adapter.get().isActive()) {
                    errors.add("Node " + nodeId + " references inactive adapter: " + adapterId + " (" + adapter.get().getName() + ")");
                }
                
            } catch (IllegalArgumentException e) {
                errors.add("Node " + nodeId + " has invalid adapter ID format: " + adapterIdStr);
            }
        }
    }
    
    /**
     * Validate utility node configuration
     * Supports both React Flow node format (data.utilityType) and legacy format (utilityId)
     */
    private void validateUtilityNode(Map<String, Object> node, String nodeId, List<String> errors) {
        // Extract utility information from React Flow format or legacy format
        String utilityIdStr = "";
        String utilityType = "";
        
        // Check React Flow format first (data object contains node data)
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
        if (nodeData != null) {
            // React Flow format uses utilityType instead of utilityId
            utilityType = (String) nodeData.getOrDefault("utilityType", "");
            utilityIdStr = (String) nodeData.getOrDefault("utilityId", "");
        } else if (node.containsKey("utilityId")) {
            // Legacy format
            utilityIdStr = node.get("utilityId").toString();
        }
        
        // For React Flow format, we validate utilityType exists
        if (!utilityType.isEmpty()) {
            // Validate utility type is supported
            if (!isValidUtilityType(utilityType)) {
                errors.add("Utility node " + nodeId + " has unsupported utility type: " + utilityType);
            }
            return;
        }
        
        // For legacy format, validate utilityId exists and is valid UUID
        if (utilityIdStr.isEmpty()) {
            errors.add("Utility node " + nodeId + " is missing required 'utilityId' or 'utilityType' field");
            return;
        }
        try {
            UUID utilityId = UUID.fromString(utilityIdStr);
            
            // Check if utility exists and is enabled
            Optional<FlowUtility> utility = utilityRepository.findById(utilityId);
            if (utility.isEmpty()) {
                errors.add("Utility node " + nodeId + " references non-existent utility: " + utilityId);
            } else if (!utility.get().isActive()) {
                errors.add("Utility node " + nodeId + " references inactive utility: " + utilityId);
            }
            
        } catch (IllegalArgumentException e) {
            errors.add("Utility node " + nodeId + " has invalid utility ID format: " + utilityIdStr);
        }
    }
    
    /**
     * Validate flow connections
     * Supports both React Flow edges and legacy connections format
     */
    @SuppressWarnings("unchecked")
    private void validateConnections(List<Map<String, Object>> connections, 
                                   Set<String> validNodeIds, 
                                   List<String> errors, 
                                   List<String> warnings,
                                   boolean isReactFlowFormat) {
        
        Set<String> sourceNodes = new HashSet<>();
        Set<String> targetNodes = new HashSet<>();
        
        for (int i = 0; i < connections.size(); i++) {
            Map<String, Object> connection = connections.get(i);
            
            // Check required connection fields (React Flow uses source/target, legacy uses same)
            if (!connection.containsKey("source")) {
                String fieldName = isReactFlowFormat ? "Edge" : "Connection";
                errors.add(fieldName + " " + i + " is missing required 'source' field");
                continue;
            }
            
            if (!connection.containsKey("target")) {
                String fieldName = isReactFlowFormat ? "Edge" : "Connection";
                errors.add(fieldName + " " + i + " is missing required 'target' field");
                continue;
            }
            
            String source = connection.get("source").toString();
            String target = connection.get("target").toString();
            
            // For React Flow, validate edge ID uniqueness
            if (isReactFlowFormat && connection.containsKey("id")) {
                String edgeId = connection.get("id").toString();
                // Additional edge validation could be added here
            }
            
            // Validate node references
            if (!validNodeIds.contains(source)) {
                errors.add("Connection " + i + " references invalid source node: " + source);
            }
            
            if (!validNodeIds.contains(target)) {
                errors.add("Connection " + i + " references invalid target node: " + target);
            }
            
            // Check for self-connections
            if (source.equals(target)) {
                errors.add("Connection " + i + " cannot connect node to itself: " + source);
            }
            
            sourceNodes.add(source);
            targetNodes.add(target);
        }
        
        // Check for orphaned nodes
        for (String nodeId : validNodeIds) {
            if (!sourceNodes.contains(nodeId) && !targetNodes.contains(nodeId)) {
                warnings.add("Node " + nodeId + " is not connected to any other nodes");
            }
        }
    }
    
    /**
     * Validate utility type is supported
     */
    private boolean isValidUtilityType(String utilityType) {
        // List of supported utility types from React Flow NodeSidebar
        return utilityType != null && (
            "PGP_ENCRYPT".equals(utilityType) ||
            "PGP_DECRYPT".equals(utilityType) ||
            "ZIP_COMPRESS".equals(utilityType) ||
            "ZIP_EXTRACT".equals(utilityType) ||
            "FILE_SPLIT".equals(utilityType) ||
            "FILE_MERGE".equals(utilityType) ||
            "DATA_TRANSFORM".equals(utilityType) ||
            "FILE_VALIDATE".equals(utilityType) ||
            "CUSTOM_SCRIPT".equals(utilityType)
        );
    }
    
    /**
     * Validate cron expression
     */
    private void validateCronExpression(String cronExpression, List<String> errors) {
        try {
            // Basic cron validation - in production, use a proper cron library
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length < 5 || parts.length > 6) {
                errors.add("Cron expression must have 5 or 6 fields");
            }
        } catch (Exception e) {
            errors.add("Invalid cron expression format");
        }
    }

    /**
     * Deploy a flow to the execution registry
     */
    public Map<String, Object> deployFlow(UUID flowId, UUID deployedBy) {
        logger.info("Deploying flow {} by user {}", flowId, deployedBy);
        
        Optional<IntegrationFlow> flowOpt = getFlowById(flowId);
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + flowId + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        
        // Check if already deployed
        Optional<DeployedFlow> existingDeployment = deployedFlowRepository.findByFlowId(flowId);
        if (existingDeployment.isPresent()) {
            throw new IllegalArgumentException("This flow is currently deployed and running. To make changes or redeploy, please undeploy it first using the 'Undeploy' button, then try again.");
        }
        
        // Validate flow can be deployed
        logger.debug("Starting deployment validation for flow: {}", flowId);
        Map<String, Object> validation = validateDeployment(flowId);
        boolean canDeploy = (Boolean) validation.getOrDefault("canDeploy", false);
        logger.debug("Deployment validation result: {}", validation);
        logger.debug("Can deploy: {}", canDeploy);
        
        if (!canDeploy) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) validation.getOrDefault("errors", List.of());
            logger.error("Flow deployment validation failed with errors: {}", errors);
            throw new IllegalArgumentException("Flow cannot be deployed: " + String.join(", ", errors));
        }
        
        try {
            // NEW APPROACH: Extract ALL adapters from flow definition first
            List<UUID> allAdapterIds = extractAllAdapterIds(flow);
            logger.info("Flow {} contains {} adapters that need to be started for deployment", flowId, allAdapterIds.size());
            
            if (allAdapterIds.isEmpty()) {
                throw new IllegalArgumentException("Flow must have at least one adapter to be deployed");
            }
            
            // Get details for all adapters
            Map<UUID, Adapter> flowAdapters = new HashMap<>();
            for (UUID adapterId : allAdapterIds) {
                Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
                if (adapterOpt.isEmpty()) {
                    throw new IllegalArgumentException("Adapter not found: " + adapterId);
                }
                flowAdapters.put(adapterId, adapterOpt.get());
            }
            
            // Determine sender/receiver classification (for deployment record creation)
            Map<String, UUID> legacyAdapterIds = extractAdapterIdsFromFlow(flow);
            UUID primarySenderAdapterId = legacyAdapterIds.get("sender");
            List<UUID> allReceiverAdapterIds = extractAllReceiverAdapterIds(flow);
            
            // If no legacy sender found but we have adapters, use the first adapter node as sender
            if (primarySenderAdapterId == null && !allAdapterIds.isEmpty()) {
                // Find first sender adapter in the flow
                for (UUID adapterId : allAdapterIds) {
                    Adapter adapter = flowAdapters.get(adapterId);
                    // Check if this adapter was configured as a sender in the flow definition
                    if (isAdapterConfiguredAsSender(flow, adapterId)) {
                        primarySenderAdapterId = adapterId;
                        break;
                    }
                }
                // If still no sender found, use the first adapter
                if (primarySenderAdapterId == null) {
                    primarySenderAdapterId = allAdapterIds.get(0);
                    logger.warn("No sender adapter found in flow {}, using first adapter {} as sender", flowId, primarySenderAdapterId);
                }
            }
            
            if (primarySenderAdapterId == null) {
                throw new IllegalArgumentException("Flow must have a sender adapter to be deployed");
            }
            
            Adapter primarySenderAdapter = flowAdapters.get(primarySenderAdapterId);
            
            // STEP 1: Validate ALL adapters are in correct state BEFORE attempting to start them
            logger.info("Step 1: Validating ALL {} adapter states...", allAdapterIds.size());
            for (UUID adapterId : allAdapterIds) {
                Adapter adapter = flowAdapters.get(adapterId);
                
                if (!adapter.isActive()) {
                    throw new RuntimeException("Adapter " + adapter.getName() + " (" + adapter.getId() + ") is inactive and cannot be started");
                }
                if (adapter.getStatus() != Adapter.AdapterStatus.STOPPED) {
                    throw new RuntimeException("Adapter " + adapter.getName() + " (" + adapter.getId() + ") is not in STOPPED state (current: " + adapter.getStatus() + ") and cannot be started for deployment");
                }
                logger.debug("Validated adapter: {} is ready for deployment", adapter.getName());
            }
            logger.info("Step 1 completed: All {} adapters are in valid state for deployment", allAdapterIds.size());
            
            // STEP 2: Start ALL adapters WITHOUT creating deployment records
            logger.info("Step 2: Starting ALL {} adapters...", allAdapterIds.size());
            List<UUID> startedAdapterIds = new ArrayList<>();
            
            try {
                for (UUID adapterId : allAdapterIds) {
                    Adapter adapter = flowAdapters.get(adapterId);
                    logger.info("Starting adapter: {} ({})", adapter.getName(), adapterId);
                    
                    adapterRepository.updateStatus(adapterId, Adapter.AdapterStatus.STARTED);
                    
                    // Verify it actually started
                    Optional<Adapter> updatedAdapter = adapterRepository.findById(adapterId);
                    if (updatedAdapter.isEmpty() || updatedAdapter.get().getStatus() != Adapter.AdapterStatus.STARTED) {
                        throw new RuntimeException("Failed to start adapter " + adapter.getName() + " - status update failed");
                    }
                    
                    startedAdapterIds.add(adapterId);
                    logger.info("Successfully started adapter: {}", adapter.getName());
                }
                logger.info("Step 2 completed: All {} adapters successfully started", allAdapterIds.size());
                
            } catch (Exception adapterStartupError) {
                // ROLLBACK: Stop any adapters that we started
                logger.error("Adapter startup failed, rolling back adapter states: {}", adapterStartupError.getMessage());
                
                for (UUID adapterId : startedAdapterIds) {
                    try {
                        Adapter adapter = flowAdapters.get(adapterId);
                        adapterRepository.updateStatus(adapterId, Adapter.AdapterStatus.STOPPED);
                        logger.info("Rolled back adapter to STOPPED: {} ({})", adapter.getName(), adapterId);
                    } catch (Exception rollbackError) {
                        logger.warn("Failed to rollback adapter {}: {}", adapterId, rollbackError.getMessage());
                    }
                }
                
                throw new RuntimeException("Flow deployment failed - adapters could not be started: " + adapterStartupError.getMessage(), adapterStartupError);
            }
            
            // STEP 3: Only NOW create deployment records since all adapters are successfully started
            logger.info("Step 3: Creating deployment records...");
            List<UUID> deploymentIds = new ArrayList<>();
            List<String> receiverAdapterNames = new ArrayList<>();
            
            // Create deployment record (simplified single deployment for the whole flow)
            DeployedFlow deployedFlow = new DeployedFlow();
            deployedFlow.setFlowId(flowId);
            deployedFlow.setFlowName(flow.getName());
            deployedFlow.setFlowVersion(flow.getFlowVersion());
            deployedFlow.setSenderAdapterId(primarySenderAdapterId);
            deployedFlow.setSenderAdapterName(primarySenderAdapter.getName());
            
            // Set first receiver (if any) for compatibility
            if (!allReceiverAdapterIds.isEmpty()) {
                UUID firstReceiverId = allReceiverAdapterIds.get(0);
                Adapter firstReceiver = flowAdapters.get(firstReceiverId);
                if (firstReceiver != null) {
                    deployedFlow.setReceiverAdapterId(firstReceiverId);
                    deployedFlow.setReceiverAdapterName(firstReceiver.getName());
                    receiverAdapterNames.add(firstReceiver.getName());
                }
            }
            
            deployedFlow.setDeploymentStatus(DeployedFlow.DeploymentStatus.DEPLOYED);
            deployedFlow.setRuntimeStatus(DeployedFlow.RuntimeStatus.ACTIVE);
            deployedFlow.setExecutionEnabled(true);
            deployedFlow.setDeployedBy(deployedBy);
            deployedFlow.setDeployedAt(LocalDateTime.now());
            
            // Set execution configuration
            deployedFlow.setMaxConcurrentExecutions(flow.getMaxParallelExecutions() != null ? flow.getMaxParallelExecutions() : 1);
            deployedFlow.setExecutionTimeoutMinutes(flow.getTimeoutMinutes() != null ? flow.getTimeoutMinutes() : 60);
            deployedFlow.setRetryPolicy(flow.getRetryPolicy());
            
            // Store configuration snapshots
            deployedFlow.setFlowConfiguration(flow.getFlowDefinition());
            deployedFlow.setSenderAdapterConfig(primarySenderAdapter.getConfiguration());
            if (deployedFlow.getReceiverAdapterId() != null) {
                Adapter receiverAdapter = flowAdapters.get(deployedFlow.getReceiverAdapterId());
                deployedFlow.setReceiverAdapterConfig(receiverAdapter.getConfiguration());
            }
            
            // Save to deployment registry
            UUID deploymentId = deployedFlowRepository.deploy(deployedFlow);
            deployedFlow.setId(deploymentId);
            deploymentIds.add(deploymentId);
            logger.info("Created deployment record: {} for flow: {}", deploymentId, flowId);
            
            // Initialize the scheduling service with the deployed flow
            try {
                deployedFlowSchedulingService.onFlowDeployed(deployedFlow);
                logger.info("Successfully initialized scheduling for deployment: {}", deploymentId);
            } catch (Exception schedulingError) {
                logger.warn("Failed to initialize scheduling for deployment {}: {}", deploymentId, schedulingError.getMessage());
                // Don't fail deployment for scheduling errors since adapters are already started
            }
            
            logger.info("Step 3 completed: Created {} deployment records", deploymentIds.size());
            
            // Deployment status is now tracked only in deployed_flows table
            // No need to update integration_flows table
            
            logger.info("Successfully deployed flow {} with ALL {} adapters started: {}", 
                       flowId, allAdapterIds.size(), allAdapterIds.stream().map(id -> flowAdapters.get(id).getName()).toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("deploymentIds", deploymentIds);
            result.put("flowId", flowId);
            result.put("flowName", flow.getName());
            result.put("deploymentStatus", "DEPLOYED");
            result.put("runtimeStatus", "ACTIVE");
            result.put("deployedAt", LocalDateTime.now());
            result.put("deployedBy", deployedBy);
            result.put("senderAdapterId", primarySenderAdapterId);
            result.put("senderAdapterName", primarySenderAdapter.getName());
            result.put("receiverAdapterIds", allReceiverAdapterIds);
            result.put("receiverAdapterNames", receiverAdapterNames);
            result.put("deploymentCount", deploymentIds.size());
            result.put("totalAdaptersStarted", allAdapterIds.size());
            result.put("allAdapterIds", allAdapterIds);
            
            logger.info("Successfully deployed flow {} to deployment registry ({} deployments, {} adapters started: {})", 
                       flowId, deploymentIds.size(), allAdapterIds.size(), deploymentIds);
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to deploy flow {} by user {}: {}", flowId, deployedBy, e.getMessage(), e);
            throw new RuntimeException("Failed to deploy flow: " + e.getMessage(), e);
        }
    }
    
    /**
     * Undeploy a flow from the execution registry
     */
    public Map<String, Object> undeployFlow(UUID flowId, UUID undeployedBy) {
        logger.info("Undeploying flow {} by user {}", flowId, undeployedBy);
        
        Optional<IntegrationFlow> flowOpt = getFlowById(flowId);
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + flowId + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        
        // Find ALL deployment records for this flow (supports multiple receiver adapters)
        List<DeployedFlow> deployedFlows = deployedFlowRepository.findAllByFlowId(flowId);
        if (deployedFlows.isEmpty()) {
            throw new IllegalArgumentException("Flow is not currently deployed");
        }
        
        List<UUID> undeployedIds = new ArrayList<>();
        
        try {
            // NEW APPROACH: Extract ALL adapters from flow definition and stop them all
            List<UUID> allAdapterIds = extractAllAdapterIds(flow);
            logger.info("Undeploying flow {} - stopping ALL {} adapters: {}", flowId, allAdapterIds.size(), allAdapterIds);
            
            // STEP 1: Stop ALL adapters used by this flow
            List<UUID> stoppedAdapterIds = new ArrayList<>();
            for (UUID adapterId : allAdapterIds) {
                try {
                    Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
                    if (adapterOpt.isPresent()) {
                        Adapter adapter = adapterOpt.get();
                        if (adapter.getStatus() == Adapter.AdapterStatus.STARTED) {
                            logger.info("Stopping adapter: {} ({})", adapter.getName(), adapterId);
                            adapterRepository.updateStatus(adapterId, Adapter.AdapterStatus.STOPPED);
                            
                            // Verify it actually stopped
                            Optional<Adapter> updatedAdapter = adapterRepository.findById(adapterId);
                            if (updatedAdapter.isPresent() && updatedAdapter.get().getStatus() == Adapter.AdapterStatus.STOPPED) {
                                stoppedAdapterIds.add(adapterId);
                                logger.info("Successfully stopped adapter: {}", adapter.getName());
                            } else {
                                logger.warn("Failed to stop adapter {}: status update failed", adapter.getName());
                            }
                        } else {
                            logger.debug("Adapter {} is already stopped (status: {})", adapter.getName(), adapter.getStatus());
                        }
                    } else {
                        logger.warn("Adapter {} not found during undeploy", adapterId);
                    }
                } catch (Exception e) {
                    logger.error("Failed to stop adapter {}: {}", adapterId, e.getMessage(), e);
                    // Continue stopping other adapters
                }
            }
            
            logger.info("Stopped {} out of {} adapters for flow {}", stoppedAdapterIds.size(), allAdapterIds.size(), flowId);
            
            // STEP 2: Clean up scheduling and deployment records
            for (DeployedFlow deployedFlow : deployedFlows) {
                try {
                    // Use existing cleanup method for scheduling tasks
                    deployedFlowSchedulingService.onFlowUndeployed(deployedFlow.getId());
                    logger.info("Cleaned up scheduling for deployment: {}", deployedFlow.getId());
                } catch (Exception e) {
                    logger.error("Failed to cleanup scheduling for deployment {}: {}", deployedFlow.getId(), e.getMessage(), e);
                    // Continue with undeployment
                }
                
                // Remove from deployment registry (undeploy)
                deployedFlowRepository.undeploy(deployedFlow.getId(), undeployedBy);
                undeployedIds.add(deployedFlow.getId());
            }
            
            // Deployment status is now tracked only in deployed_flows table
            // No need to update integration_flows table
            
            Map<String, Object> result = new HashMap<>();
            result.put("deploymentIds", undeployedIds);
            result.put("flowId", flowId);
            result.put("flowName", flow.getName());
            result.put("deploymentStatus", "UNDEPLOYED");
            result.put("undeployedAt", LocalDateTime.now());
            result.put("undeployedBy", undeployedBy);
            result.put("undeploymentCount", undeployedIds.size());
            result.put("totalAdaptersStopped", stoppedAdapterIds.size());
            result.put("allAdapterIds", allAdapterIds);
            result.put("stoppedAdapterIds", stoppedAdapterIds);
            
            logger.info("Successfully undeployed flow {} from deployment registry ({} deployments, {} adapters stopped: {})", 
                       flowId, undeployedIds.size(), stoppedAdapterIds.size(), undeployedIds);
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to undeploy flow {} by user {}: {}", flowId, undeployedBy, e.getMessage(), e);
            throw new RuntimeException("Failed to undeploy flow: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get deployment status for a flow
     */
    public Map<String, Object> getDeploymentStatus(UUID flowId) {
        logger.debug("Getting deployment status for flow {}", flowId);
        
        Optional<IntegrationFlow> flowOpt = getFlowById(flowId);
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + flowId + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        Map<String, Object> status = new HashMap<>();
        
        status.put("flowId", flowId);
        status.put("flowName", flow.getName());
        
        // Get deployment details from registry
        Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findByFlowId(flowId);
        boolean isDeployed = deployedFlowOpt.isPresent();
        status.put("deployed", isDeployed);
        if (deployedFlowOpt.isPresent()) {
            DeployedFlow deployedFlow = deployedFlowOpt.get();
            status.put("deploymentId", deployedFlow.getId());
            status.put("deploymentStatus", deployedFlow.getDeploymentStatus().name());
            status.put("runtimeStatus", deployedFlow.getRuntimeStatus().name());
            status.put("executionEnabled", deployedFlow.getExecutionEnabled());
            status.put("deployedAt", deployedFlow.getDeployedAt());
            status.put("deployedBy", deployedFlow.getDeployedBy());
            status.put("senderAdapterId", deployedFlow.getSenderAdapterId());
            status.put("senderAdapterName", deployedFlow.getSenderAdapterName());
            status.put("receiverAdapterId", deployedFlow.getReceiverAdapterId());
            status.put("receiverAdapterName", deployedFlow.getReceiverAdapterName());
            status.put("totalExecutions", deployedFlow.getTotalExecutions());
            status.put("successfulExecutions", deployedFlow.getSuccessfulExecutions());
            status.put("failedExecutions", deployedFlow.getFailedExecutions());
            status.put("lastExecutionAt", deployedFlow.getLastExecutionAt());
            status.put("lastExecutionStatus", deployedFlow.getLastExecutionStatus());
            status.put("averageExecutionTimeMs", deployedFlow.getAverageExecutionTimeMs());
            status.put("healthStatus", deployedFlow.getHealthCheckStatus());
            status.put("lastHealthCheckAt", deployedFlow.getLastHealthCheckAt());
            status.put("consecutiveFailures", deployedFlow.getConsecutiveFailures());
        } else {
            status.put("deploymentId", null);
            status.put("deploymentStatus", "NOT_DEPLOYED");
            status.put("runtimeStatus", "INACTIVE");
            status.put("executionEnabled", false);
        }
        
        return status;
    }
    
    /**
     * Validate flow deployment readiness
     */
    public Map<String, Object> validateDeployment(UUID flowId) {
        logger.debug("Validating deployment readiness for flow {}", flowId);
        
        Optional<IntegrationFlow> flowOpt = getFlowById(flowId);
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + flowId + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        Map<String, Object> validation = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic flow validation
        Map<String, Object> flowValidation = validateFlow(flow);
        boolean flowValid = (Boolean) flowValidation.get("valid");
        
        if (!flowValid) {
            @SuppressWarnings("unchecked")
            List<String> flowErrors = (List<String>) flowValidation.get("errors");
            errors.addAll(flowErrors);
        }
        
        // Check if flow is active
        if (!flow.getActive()) {
            errors.add("Flow must be active before deployment");
        }
        
        // Check adapter availability
        Map<String, UUID> adapterIds = extractAdapterIdsFromFlow(flow);
        
        UUID senderAdapterId = adapterIds.get("sender");
        UUID receiverAdapterId = adapterIds.get("receiver");
        
        if (senderAdapterId == null && receiverAdapterId == null) {
            errors.add("Flow must have at least one adapter (sender or receiver)");
        }
        
        // Strict validation: adapters must exist, be active, and ready for startup
        if (senderAdapterId != null) {
            Optional<Adapter> senderAdapter = adapterRepository.findById(senderAdapterId);
            if (senderAdapter.isEmpty()) {
                errors.add("Sender adapter not found: " + senderAdapterId);
            } else {
                Adapter adapter = senderAdapter.get();
                if (!adapter.isActive()) {
                    errors.add("Sender adapter is inactive and cannot be deployed: " + adapter.getName());
                }
                // For deployment, we require adapters to be in STOPPED state (ready to start)
                // If they're already STARTED, they might be used by another flow
                if (adapter.getStatus() == Adapter.AdapterStatus.STARTED) {
                    errors.add("Sender adapter is already started (possibly used by another flow): " + adapter.getName());
                }
            }
        }
        
        if (receiverAdapterId != null) {
            Optional<Adapter> receiverAdapter = adapterRepository.findById(receiverAdapterId);
            if (receiverAdapter.isEmpty()) {
                errors.add("Receiver adapter not found: " + receiverAdapterId);
            } else {
                Adapter adapter = receiverAdapter.get();
                if (!adapter.isActive()) {
                    errors.add("Receiver adapter is inactive and cannot be deployed: " + adapter.getName());
                }
                // For deployment, we require adapters to be in STOPPED state (ready to start)
                if (adapter.getStatus() == Adapter.AdapterStatus.STARTED) {
                    errors.add("Receiver adapter is already started (possibly used by another flow): " + adapter.getName());
                }
            }
        }
        
        // Check if already deployed
        Optional<DeployedFlow> existingDeployment = deployedFlowRepository.findByFlowId(flowId);
        if (existingDeployment.isPresent()) {
            warnings.add("Flow is already deployed");
        }
        
        boolean canDeploy = errors.isEmpty();
        
        validation.put("canDeploy", canDeploy);
        validation.put("valid", canDeploy);
        validation.put("errors", errors);
        validation.put("warnings", warnings);
        validation.put("errorCount", errors.size());
        validation.put("warningCount", warnings.size());
        validation.put("flowId", flowId);
        validation.put("flowName", flow.getName());
        
        // Check if currently deployed from deployed_flows table
        Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findByFlowId(flowId);
        boolean currentlyDeployed = deployedFlowOpt.isPresent();
        validation.put("currentlyDeployed", currentlyDeployed);
        validation.put("deploymentStatus", currentlyDeployed ? "DEPLOYED" : "NOT_DEPLOYED");
        
        return validation;
    }
    
    /**
     * Extract adapter IDs from flow definition - uses ID references only
     * START node = sender adapter, END node = receiver adapter
     * No embedded configurations - reads live adapter configs during deployment
     */
    @SuppressWarnings("unchecked")
    private Map<String, UUID> extractAdapterIdsFromFlow(IntegrationFlow flow) {
        Map<String, UUID> adapterIds = new HashMap<>();
        adapterIds.put("sender", null);
        adapterIds.put("receiver", null);
        
        if (flow.getFlowDefinition() == null) {
            logger.debug("Flow {} has no flow definition", flow.getId());
            return adapterIds;
        }
        
        Object nodesObj = flow.getFlowDefinition().get("nodes");
        if (!(nodesObj instanceof List)) {
            logger.debug("Flow {} flow definition has no nodes array", flow.getId());
            return adapterIds;
        }
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;
        logger.debug("Extracting adapter IDs from {} nodes in flow {}", nodes.size(), flow.getId());
        
        for (Map<String, Object> node : nodes) {
            String nodeType = (String) node.get("type");
            String nodeId = (String) node.get("id");
            
            // Extract adapters from separate adapter nodes
            if ("adapter".equals(nodeType)) {
                Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
                String direction = null;
                String adapterIdStr = null;
                
                if (nodeData != null) {
                    direction = (String) nodeData.get("direction");
                    adapterIdStr = (String) nodeData.get("adapterId");
                }
                
                if (direction != null && adapterIdStr != null && !adapterIdStr.isEmpty()) {
                    try {
                        UUID adapterId = UUID.fromString(adapterIdStr);
                        if ("SENDER".equalsIgnoreCase(direction)) {
                            adapterIds.put("sender", adapterId);
                            logger.debug("Found sender adapter ID {} in ADAPTER node {} of flow {}", adapterId, nodeId, flow.getId());
                        } else if ("RECEIVER".equalsIgnoreCase(direction)) {
                            adapterIds.put("receiver", adapterId);
                            logger.debug("Found receiver adapter ID {} in ADAPTER node {} of flow {}", adapterId, nodeId, flow.getId());
                        }
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid adapter ID format in adapter node {} of flow {}: {}", nodeId, flow.getId(), adapterIdStr);
                    }
                }
            }
        }
        
        logger.debug("Extracted adapter IDs from flow {}: sender={}, receiver={}", 
                    flow.getId(), adapterIds.get("sender"), adapterIds.get("receiver"));
        return adapterIds;
    }
    
    /**
     * Extract ALL receiver adapter IDs from flow definition 
     * Used to deploy flows with multiple receiver adapters
     */
    @SuppressWarnings("unchecked")
    private List<UUID> extractAllReceiverAdapterIds(IntegrationFlow flow) {
        List<UUID> receiverAdapterIds = new ArrayList<>();
        
        if (flow.getFlowDefinition() == null) {
            logger.debug("Flow {} has no flow definition", flow.getId());
            return receiverAdapterIds;
        }
        
        Object nodesObj = flow.getFlowDefinition().get("nodes");
        if (!(nodesObj instanceof List)) {
            logger.debug("Flow {} flow definition has no nodes array", flow.getId());
            return receiverAdapterIds;
        }
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;
        logger.debug("Extracting ALL receiver adapter IDs from {} nodes in flow {}", nodes.size(), flow.getId());
        
        for (Map<String, Object> node : nodes) {
            String nodeType = (String) node.get("type");
            String nodeId = (String) node.get("id");
            
            // Extract adapters from separate adapter nodes
            if ("adapter".equals(nodeType)) {
                Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
                String direction = null;
                String adapterIdStr = null;
                
                if (nodeData != null) {
                    direction = (String) nodeData.get("direction");
                    adapterIdStr = (String) nodeData.get("adapterId");
                }
                
                if ("RECEIVER".equalsIgnoreCase(direction) && adapterIdStr != null && !adapterIdStr.isEmpty()) {
                    try {
                        UUID adapterId = UUID.fromString(adapterIdStr);
                        receiverAdapterIds.add(adapterId);
                        logger.debug("Found receiver adapter ID {} in ADAPTER node {} of flow {}", adapterId, nodeId, flow.getId());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid receiver adapter ID format in adapter node {} of flow {}: {}", nodeId, flow.getId(), adapterIdStr);
                    }
                }
            }
        }
        
        logger.debug("Extracted ALL receiver adapter IDs from flow {}: {}", flow.getId(), receiverAdapterIds);
        return receiverAdapterIds;
    }
    
    /**
     * Extract ALL adapter IDs from flow definition (both senders and receivers)
     * This method finds every adapter referenced in the flow and returns their UUIDs
     */
    @SuppressWarnings("unchecked")
    private List<UUID> extractAllAdapterIds(IntegrationFlow flow) {
        List<UUID> allAdapterIds = new ArrayList<>();
        
        if (flow.getFlowDefinition() == null) {
            logger.debug("Flow {} has no flow definition", flow.getId());
            return allAdapterIds;
        }
        
        Object nodesObj = flow.getFlowDefinition().get("nodes");
        if (!(nodesObj instanceof List)) {
            logger.debug("Flow {} flow definition has no nodes array", flow.getId());
            return allAdapterIds;
        }
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;
        logger.debug("Extracting ALL adapter IDs from {} nodes in flow {}", nodes.size(), flow.getId());
        
        for (Map<String, Object> node : nodes) {
            String nodeType = (String) node.get("type");
            String nodeId = (String) node.get("id");
            
            // Extract adapters from adapter nodes
            if ("adapter".equals(nodeType)) {
                Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
                String adapterIdStr = null;
                
                if (nodeData != null) {
                    adapterIdStr = (String) nodeData.get("adapterId");
                }
                
                if (adapterIdStr != null && !adapterIdStr.isEmpty()) {
                    try {
                        UUID adapterId = UUID.fromString(adapterIdStr);
                        if (!allAdapterIds.contains(adapterId)) {  // Avoid duplicates
                            allAdapterIds.add(adapterId);
                            logger.debug("Found adapter ID {} in node {} of flow {}", adapterId, nodeId, flow.getId());
                        }
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid adapter ID format in node {} of flow {}: {}", nodeId, flow.getId(), adapterIdStr);
                    }
                }
            }
        }
        
        logger.debug("Extracted ALL adapter IDs from flow {}: {}", flow.getId(), allAdapterIds);
        return allAdapterIds;
    }
    
    /**
     * Check if an adapter is configured as a sender in the flow definition
     */
    @SuppressWarnings("unchecked")
    private boolean isAdapterConfiguredAsSender(IntegrationFlow flow, UUID adapterId) {
        if (flow.getFlowDefinition() == null) {
            return false;
        }
        
        Object nodesObj = flow.getFlowDefinition().get("nodes");
        if (!(nodesObj instanceof List)) {
            return false;
        }
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;
        
        for (Map<String, Object> node : nodes) {
            String nodeType = (String) node.get("type");
            
            if ("adapter".equals(nodeType)) {
                Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
                if (nodeData != null) {
                    String nodeAdapterIdStr = (String) nodeData.get("adapterId");
                    String direction = (String) nodeData.get("direction");
                    
                    if (nodeAdapterIdStr != null && adapterId.toString().equals(nodeAdapterIdStr) && 
                        "SENDER".equalsIgnoreCase(direction)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
}