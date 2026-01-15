package com.integrixs.core.service;

import com.integrixs.core.config.FlowExportConfiguration;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.core.repository.IntegrationPackageRepository;
import com.integrixs.core.util.FlowExportCrypto;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.IntegrationPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for flow import and export operations following OOP principles.
 * Single Responsibility: Only handles flow import/export functionality
 * Separation from CRUD operations for better maintainability and testability
 */
@Service
public class FlowImportExportService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowImportExportService.class);
    
    private final IntegrationFlowRepository flowRepository;
    private final AdapterRepository adapterRepository;
    private final IntegrationPackageRepository packageRepository;
    private final FlowExportCrypto flowExportCrypto;
    private final FlowConfigurationCleaningService cleaningService;
    private final FlowExportConfiguration exportConfig;
    
    @Autowired
    public FlowImportExportService(IntegrationFlowRepository flowRepository,
                                 AdapterRepository adapterRepository,
                                 IntegrationPackageRepository packageRepository,
                                 FlowExportCrypto flowExportCrypto,
                                 FlowConfigurationCleaningService cleaningService,
                                 FlowExportConfiguration exportConfig) {
        this.flowRepository = flowRepository;
        this.adapterRepository = adapterRepository;
        this.packageRepository = packageRepository;
        this.flowExportCrypto = flowExportCrypto;
        this.cleaningService = cleaningService;
        this.exportConfig = exportConfig;
    }
    
    /**
     * Export flow with all associated adapters
     */
    public FlowExportResult exportFlow(UUID flowId) {
        logger.info("Exporting integration flow: {}", flowId);
        
        Optional<IntegrationFlow> flowOpt = flowRepository.findById(flowId);
        if (flowOpt.isEmpty()) {
            return FlowExportResult.failure("Flow with ID " + flowId + " not found");
        }
        
        try {
            IntegrationFlow flow = flowOpt.get();
            Map<String, Object> exportData = createExportData(flow);
            
            // Apply encryption if enabled
            Map<String, Object> finalData = exportConfig.isEncryptionEnabled() ? 
                flowExportCrypto.encryptFlowExport(exportData) : exportData;
            
            logger.info("Flow exported successfully: {}", flow.getName());
            return FlowExportResult.success(finalData, flow.getName());
            
        } catch (Exception e) {
            logger.error("Failed to export flow {}: {}", flowId, e.getMessage(), e);
            return FlowExportResult.failure("Export failed: " + e.getMessage());
        }
    }
    
    /**
     * Import flow from export data with package-aware logic (V3+)
     */
    public FlowImportResult importFlow(Map<String, Object> importData, UUID importedBy) {
        logger.info("Importing integration flow by user: {}", importedBy);
        
        try {
            // Decrypt if necessary
            Map<String, Object> actualImportData = decryptIfNecessary(importData);
            
            // Validate import data
            FlowImportValidation validation = validateImportData(actualImportData);
            if (!validation.isValid()) {
                return FlowImportResult.failure(validation.getErrorMessage());
            }
            
            // Detect format version and handle accordingly
            String exportFormat = getExportFormat(actualImportData);
            
            if (exportConfig.isPackageAwareFormat(exportFormat)) {
                // NEW V3+: Package-aware import
                return importFlowWithPackageAwareness(actualImportData, importedBy, exportFormat);
            } else {
                // LEGACY V1/V2: Backward compatibility
                return importFlowLegacy(actualImportData, importedBy);
            }
            
        } catch (Exception e) {
            logger.error("Failed to import flow: {}", e.getMessage(), e);
            return FlowImportResult.failure("Import failed: " + e.getMessage());
        }
    }
    
    /**
     * Import flow with package awareness (V3+ format)
     */
    private FlowImportResult importFlowWithPackageAwareness(Map<String, Object> importData, UUID importedBy, String exportFormat) {
        logger.info("Importing flow with package awareness (format: {})", exportFormat);

        try {
            // 1. Handle package creation/detection
            UUID targetPackageId = handlePackageImport(importData, importedBy);

            // 2. Import adapters with package association
            Map<UUID, UUID> adapterIdMapping = importAdaptersWithPackage(importData, targetPackageId, importedBy);

            // 3. Check if this flow was previously imported (by originalFlowId)
            String originalIdStr = getStringValue(importData, "id");
            UUID originalFlowId = originalIdStr != null ? UUID.fromString(originalIdStr) : null;
            Optional<IntegrationFlow> existingFlow = Optional.empty();

            if (originalFlowId != null) {
                existingFlow = flowRepository.findByOriginalFlowId(originalFlowId);
            }

            IntegrationFlow flow;
            if (existingFlow.isPresent()) {
                // UPDATE existing imported flow
                logger.info("Re-importing flow: updating existing flow {} (originalFlowId={})",
                           existingFlow.get().getName(), originalFlowId);
                flow = updateFlowFromImportData(existingFlow.get(), importData, adapterIdMapping, importedBy);
                flow.setPackageId(targetPackageId);
                flowRepository.update(flow);
            } else {
                // CREATE new flow
                flow = createFlowFromImportData(importData, adapterIdMapping, importedBy);
                flow.setPackageId(targetPackageId);
                UUID savedFlowId = flowRepository.save(flow);
                flow.setId(savedFlowId);
            }

            logger.info("Flow imported successfully with package awareness: {} (ID: {}, Package: {})",
                       flow.getName(), flow.getId(), targetPackageId);
            return FlowImportResult.success(flow);

        } catch (Exception e) {
            logger.error("Failed to import flow with package awareness: {}", e.getMessage(), e);
            return FlowImportResult.failure("Package-aware import failed: " + e.getMessage());
        }
    }
    
    /**
     * Legacy import for V1/V2 formats (backward compatibility)
     */
    private FlowImportResult importFlowLegacy(Map<String, Object> importData, UUID importedBy) {
        logger.info("Importing flow using legacy format");

        try {
            // Import adapters if present and enabled (legacy behavior)
            Map<UUID, UUID> adapterIdMapping = importAdaptersIfPresent(importData, importedBy);

            // Check if this flow was previously imported (by originalFlowId)
            String originalIdStr = getStringValue(importData, "id");
            UUID originalFlowId = originalIdStr != null ? UUID.fromString(originalIdStr) : null;
            Optional<IntegrationFlow> existingFlow = Optional.empty();

            if (originalFlowId != null) {
                existingFlow = flowRepository.findByOriginalFlowId(originalFlowId);
            }

            IntegrationFlow flow;
            if (existingFlow.isPresent()) {
                // UPDATE existing imported flow
                logger.info("Re-importing flow: updating existing flow {} (originalFlowId={})",
                           existingFlow.get().getName(), originalFlowId);
                flow = updateFlowFromImportData(existingFlow.get(), importData, adapterIdMapping, importedBy);
                flowRepository.update(flow);
            } else {
                // CREATE new flow
                flow = createFlowFromImportData(importData, adapterIdMapping, importedBy);
                UUID savedFlowId = flowRepository.save(flow);
                flow.setId(savedFlowId);
            }

            logger.info("Flow imported successfully (legacy): {} (ID: {})", flow.getName(), flow.getId());
            return FlowImportResult.success(flow);

        } catch (Exception e) {
            logger.error("Failed to import flow (legacy): {}", e.getMessage(), e);
            return FlowImportResult.failure("Legacy import failed: " + e.getMessage());
        }
    }
    
    // Immutable result classes following OOP principles
    
    public static class FlowExportResult {
        private final boolean successful;
        private final Map<String, Object> exportData;
        private final String flowName;
        private final String errorMessage;
        
        private FlowExportResult(boolean successful, Map<String, Object> exportData, String flowName, String errorMessage) {
            this.successful = successful;
            this.exportData = exportData;
            this.flowName = flowName;
            this.errorMessage = errorMessage;
        }
        
        public static FlowExportResult success(Map<String, Object> exportData, String flowName) {
            return new FlowExportResult(true, exportData, flowName, null);
        }
        
        public static FlowExportResult failure(String errorMessage) {
            return new FlowExportResult(false, null, null, errorMessage);
        }
        
        public boolean isSuccessful() { return successful; }
        public Optional<Map<String, Object>> getExportData() { return Optional.ofNullable(exportData); }
        public Optional<String> getFlowName() { return Optional.ofNullable(flowName); }
        public Optional<String> getErrorMessage() { return Optional.ofNullable(errorMessage); }
    }
    
    public static class FlowImportResult {
        private final boolean successful;
        private final IntegrationFlow importedFlow;
        private final String errorMessage;
        
        private FlowImportResult(boolean successful, IntegrationFlow importedFlow, String errorMessage) {
            this.successful = successful;
            this.importedFlow = importedFlow;
            this.errorMessage = errorMessage;
        }
        
        public static FlowImportResult success(IntegrationFlow flow) {
            return new FlowImportResult(true, flow, null);
        }
        
        public static FlowImportResult failure(String errorMessage) {
            return new FlowImportResult(false, null, errorMessage);
        }
        
        public boolean isSuccessful() { return successful; }
        public Optional<IntegrationFlow> getImportedFlow() { return Optional.ofNullable(importedFlow); }
        public Optional<String> getErrorMessage() { return Optional.ofNullable(errorMessage); }
    }
    
    private static class FlowImportValidation {
        private final boolean valid;
        private final String errorMessage;
        
        private FlowImportValidation(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static FlowImportValidation valid() {
            return new FlowImportValidation(true, null);
        }
        
        public static FlowImportValidation invalid(String errorMessage) {
            return new FlowImportValidation(false, errorMessage);
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    // Private helper methods following OOP principles
    
    private Map<String, Object> createExportData(IntegrationFlow flow) {
        Map<String, Object> exportData = new HashMap<>();
        
        // Export flow metadata
        exportData.put("exportFormat", exportConfig.getFormat());
        exportData.put("exportedAt", LocalDateTime.now().toString());
        exportData.put("id", flow.getId().toString());
        exportData.put("name", flow.getName());
        exportData.put("description", flow.getDescription());
        exportData.put("flowDefinition", flow.getFlowDefinition());
        exportData.put("flowType", flow.getFlowType());
        exportData.put("timeoutMinutes", flow.getTimeoutMinutes());
        exportData.put("maxParallelExecutions", flow.getMaxParallelExecutions());
        exportData.put("retryPolicy", flow.getRetryPolicy());
        exportData.put("scheduleEnabled", flow.getScheduleEnabled());
        exportData.put("scheduleCron", flow.getScheduleCron());
        
        // NEW V3+: Export package information if enabled and supported
        if (exportConfig.isIncludePackageInfo() && exportConfig.supportsPackageInfo() && flow.getPackageId() != null) {
            Map<String, Object> packageInfo = createPackageInfo(flow.getPackageId());
            if (packageInfo != null) {
                exportData.put("packageInfo", packageInfo);
            }
        }
        
        // Export associated adapters if enabled
        if (exportConfig.isIncludeAdapters()) {
            Set<UUID> adapterIds = extractAdapterIdsFromFlow(flow.getFlowDefinition());
            List<Map<String, Object>> adapters = exportAdapters(adapterIds);
            exportData.put("adapters", adapters);
        }
        
        // Export metrics if enabled
        if (exportConfig.isIncludeMetrics()) {
            exportData.put("createdAt", flow.getCreatedAt());
            exportData.put("updatedAt", flow.getUpdatedAt());
        }
        
        logger.debug("Created export data for flow: {} with format: {}", flow.getName(), exportConfig.getFormat());
        return exportData;
    }
    
    private List<Map<String, Object>> exportAdapters(Set<UUID> adapterIds) {
        return adapterIds.stream()
            .map(adapterRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(this::createAdapterExportData)
            .collect(Collectors.toList());
    }
    
    private Map<String, Object> createAdapterExportData(Adapter adapter) {
        Map<String, Object> adapterData = new HashMap<>();
        adapterData.put("id", adapter.getId().toString());
        adapterData.put("name", adapter.getName());
        adapterData.put("description", adapter.getDescription());
        adapterData.put("adapterType", adapter.getAdapterType());
        adapterData.put("direction", adapter.getDirection());
        adapterData.put("configuration", cleaningService.sanitizeAdapterConfiguration(adapter.getConfiguration()));
        adapterData.put("active", adapter.isActive());
        adapterData.put("connectionValidated", adapter.getConnectionValidated());
        
        logger.debug("Exported adapter: {} ({})", adapter.getName(), adapter.getId());
        return adapterData;
    }
    
    private Map<String, Object> decryptIfNecessary(Map<String, Object> importData) {
        if (flowExportCrypto.isEncryptedFlowExport(importData)) {
            logger.info("Detected encrypted flow export, decrypting...");
            Map<String, Object> decrypted = flowExportCrypto.decryptFlowExport(importData);
            logger.info("Flow export decrypted successfully");
            return decrypted;
        }
        return importData;
    }
    
    private FlowImportValidation validateImportData(Map<String, Object> importData) {
        if (!importData.containsKey("name")) {
            return FlowImportValidation.invalid("Import data must contain 'name'");
        }
        
        if (!importData.containsKey("flowDefinition")) {
            return FlowImportValidation.invalid("Import data must contain 'flowDefinition'");
        }
        
        // Validate format if present
        Object formatObj = importData.get("exportFormat");
        if (formatObj != null && !exportConfig.isValidFormat(formatObj.toString())) {
            logger.warn("Import format {} may not be supported", formatObj);
        }
        
        return FlowImportValidation.valid();
    }
    
    private Map<UUID, UUID> importAdaptersIfPresent(Map<String, Object> importData, UUID importedBy) {
        if (!exportConfig.isIncludeAdapters() || !importData.containsKey("adapters")) {
            logger.debug("No adapters to import");
            return new HashMap<>();
        }
        
        Object adaptersObj = importData.get("adapters");
        if (!(adaptersObj instanceof List)) {
            logger.warn("Adapters field is not a list, skipping adapter import");
            return new HashMap<>();
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> adaptersList = (List<Map<String, Object>>) adaptersObj;
        
        return importAdaptersList(adaptersList, importedBy);
    }
    
    private IntegrationFlow createFlowFromImportData(Map<String, Object> importData,
                                                   Map<UUID, UUID> adapterIdMapping, UUID importedBy) {
        IntegrationFlow flow = new IntegrationFlow();

        // Set basic properties
        String originalName = importData.get("name").toString();
        flow.setName(generateUniqueName(originalName));
        flow.setDescription(getStringValue(importData, "description"));
        flow.setFlowType(getStringValue(importData, "flowType"));

        // Set flow definition with cleaned configurations
        @SuppressWarnings("unchecked")
        Map<String, Object> flowDefinition = (Map<String, Object>) importData.get("flowDefinition");
        Map<String, Object> cleanedDefinition = cleaningService.stripEmbeddedConfigurations(flowDefinition);
        Map<String, Object> updatedDefinition = updateAdapterReferences(cleanedDefinition, adapterIdMapping);
        flow.setFlowDefinition(updatedDefinition);

        // Set execution settings
        flow.setTimeoutMinutes(getIntValue(importData, "timeoutMinutes", 60));
        flow.setMaxParallelExecutions(getIntValue(importData, "maxParallelExecutions", 1));
        flow.setRetryPolicy(getMapValue(importData, "retryPolicy"));
        flow.setScheduleEnabled(getBooleanValue(importData, "scheduleEnabled", false));
        flow.setScheduleCron(getStringValue(importData, "scheduleCron"));

        // CRITICAL: Track original flow ID to distinguish imported vs locally created flows
        // If originalFlowId is NULL, the flow was created locally, not imported
        // If originalFlowId has a value, the flow was imported from another system/environment
        String originalIdStr = getStringValue(importData, "id");
        if (originalIdStr != null) {
            try {
                UUID originalId = UUID.fromString(originalIdStr);
                flow.setOriginalFlowId(originalId);
                logger.debug("Set originalFlowId={} for imported flow: {}", originalId, flow.getName());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid original flow ID in import data: {}", originalIdStr);
                // Continue without setting originalFlowId - flow will be treated as locally created
            }
        }

        // Set audit fields
        flow.setCreatedAt(LocalDateTime.now());
        flow.setCreatedBy(importedBy);
        flow.setActive(false); // Imported flows are inactive by default

        return flow;
    }

    /**
     * Update existing flow with imported data (for re-import scenarios)
     */
    private IntegrationFlow updateFlowFromImportData(IntegrationFlow existingFlow,
                                                    Map<String, Object> importData,
                                                    Map<UUID, UUID> adapterIdMapping,
                                                    UUID updatedBy) {
        // Update basic properties
        String originalName = importData.get("name").toString();
        // Keep existing name if no conflict, otherwise generate unique
        if (!existingFlow.getName().equals(originalName) && flowRepository.existsByName(originalName)) {
            existingFlow.setName(generateUniqueName(originalName));
        } else if (!existingFlow.getName().equals(originalName)) {
            existingFlow.setName(originalName);
        }

        existingFlow.setDescription(getStringValue(importData, "description"));
        existingFlow.setFlowType(getStringValue(importData, "flowType"));

        // Update flow definition with cleaned configurations
        @SuppressWarnings("unchecked")
        Map<String, Object> flowDefinition = (Map<String, Object>) importData.get("flowDefinition");
        Map<String, Object> cleanedDefinition = cleaningService.stripEmbeddedConfigurations(flowDefinition);
        Map<String, Object> updatedDefinition = updateAdapterReferences(cleanedDefinition, adapterIdMapping);
        existingFlow.setFlowDefinition(updatedDefinition);

        // Update execution settings
        existingFlow.setTimeoutMinutes(getIntValue(importData, "timeoutMinutes", 60));
        existingFlow.setMaxParallelExecutions(getIntValue(importData, "maxParallelExecutions", 1));
        existingFlow.setRetryPolicy(getMapValue(importData, "retryPolicy"));
        existingFlow.setScheduleEnabled(getBooleanValue(importData, "scheduleEnabled", false));
        existingFlow.setScheduleCron(getStringValue(importData, "scheduleCron"));

        // Increment version number for re-import
        existingFlow.incrementVersion();

        // Update audit fields
        existingFlow.markAsUpdated(updatedBy);

        logger.info("Updated existing flow from import: {} (ID: {}, version: {})",
                   existingFlow.getName(), existingFlow.getId(), existingFlow.getFlowVersion());

        return existingFlow;
    }

    // Utility methods for safe value extraction
    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    private int getIntValue(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    private boolean getBooleanValue(Map<String, Object> data, String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
    
    /**
     * Extract adapter IDs from flow definition - moved from FlowDefinitionService
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
                case "adapter":
                    // ADAPTER nodes use 'adapterId' field
                    Object adapterIdObj = nodeData.get("adapterId");
                    if (adapterIdObj != null) {
                        adapterId = parseAdapterId(adapterIdObj.toString());
                    }
                    break;
                    
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
                    // END nodes use 'receiverAdapter' field
                    Object receiverAdapterObj = nodeData.get("receiverAdapter");
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

    private Map<UUID, UUID> importAdaptersList(List<Map<String, Object>> adaptersList, UUID importedBy) {
        logger.info("Starting to import {} adapters", adaptersList.size());
        Map<UUID, UUID> adapterIdMapping = new HashMap<>();

        for (Map<String, Object> adapterData : adaptersList) {
            logger.info("Processing adapter: {}", adapterData.get("name"));
            try {
                UUID originalId = UUID.fromString((String) adapterData.get("id"));

                // Create new adapter with imported configuration
                Adapter adapter = new Adapter();
                adapter.setName((String) adapterData.get("name"));
                adapter.setDescription((String) adapterData.get("description"));
                adapter.setAdapterType((String) adapterData.get("adapterType"));
                adapter.setDirection((String) adapterData.get("direction"));

                // Set configuration with placeholders for sensitive fields that were removed during export
                @SuppressWarnings("unchecked")
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

                // CRITICAL: Track original adapter ID to distinguish imported vs locally created adapters
                adapter.setOriginalAdapterId(originalId);

                // Check if this adapter was previously imported (by originalAdapterId)
                Optional<Adapter> existingAdapter = adapterRepository.findByOriginalAdapterId(originalId);

                UUID newId;
                if (existingAdapter.isPresent()) {
                    // UPDATE existing imported adapter
                    Adapter existing = existingAdapter.get();
                    logger.info("Re-importing adapter: updating existing adapter {} (originalAdapterId={})",
                               existing.getName(), originalId);

                    // Update adapter properties
                    existing.setName(adapter.getName());
                    existing.setDescription(adapter.getDescription());
                    existing.setConfiguration(adapter.getConfiguration());
                    existing.setAverageExecutionTimeMs(adapter.getAverageExecutionTimeMs());
                    existing.setSuccessRatePercent(adapter.getSuccessRatePercent());
                    existing.setUpdatedBy(importedBy);
                    existing.setUpdatedAt(LocalDateTime.now());
                    // Keep existing active status - don't override

                    adapterRepository.update(existing);
                    newId = existing.getId();
                    logger.info("Updated existing adapter: {} (ID: {}, originalAdapterId: {})",
                               existing.getName(), newId, originalId);
                } else {
                    // Check if adapter name conflicts with existing
                    String originalName = adapter.getName();
                    if (adapterRepository.existsByName(originalName)) {
                        // Generate unique name if conflict
                        String uniqueName = generateUniqueAdapterName(originalName);
                        adapter.setName(uniqueName);
                        logger.info("Renamed adapter to avoid conflict: {} -> {}", originalName, uniqueName);
                    }

                    // Set originalAdapterId for tracking
                    adapter.setOriginalAdapterId(originalId);

                    // CREATE new adapter
                    newId = adapterRepository.save(adapter);
                    adapter.setId(newId);

                    logger.info("Imported NEW adapter: {} -> {} (originalAdapterId: {})",
                               adapter.getName(), newId, originalId);
                }

                // Map old ID to new ID
                adapterIdMapping.put(originalId, newId);

            } catch (Exception e) {
                logger.error("Failed to import adapter: {}", adapterData.get("name"), e);
                // Continue with other adapters
            }
        }

        return adapterIdMapping;
    }
    
    private String generateUniqueName(String originalName) {
        // Check for existing names and generate unique one
        String baseName = originalName;
        int counter = 1;
        String candidateName = baseName;
        
        while (flowRepository.existsByName(candidateName)) {
            candidateName = baseName + "_imported_" + counter++;
        }
        
        return candidateName;
    }
    
    private String generateUniqueAdapterName(String baseName) {
        String candidateName = baseName;
        int counter = 1;
        
        while (adapterRepository.existsByName(candidateName)) {
            candidateName = baseName + " (" + counter + ")";
            counter++;
        }
        
        return candidateName;
    }
    
    private Map<String, Object> updateAdapterReferences(Map<String, Object> flowDefinition, Map<UUID, UUID> adapterIdMapping) {
        if (adapterIdMapping.isEmpty()) {
            return flowDefinition;
        }
        
        Map<String, Object> updatedFlowDefinition = new HashMap<>(flowDefinition);
        
        // Update adapter IDs in nodes
        if (updatedFlowDefinition.containsKey("nodes")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) updatedFlowDefinition.get("nodes");
            
            for (Map<String, Object> node : nodes) {
                if (node.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
                    
                    // Update adapterId field
                    if (nodeData.containsKey("adapterId")) {
                        UUID oldAdapterId = parseAdapterId((String) nodeData.get("adapterId"));
                        if (oldAdapterId != null && adapterIdMapping.containsKey(oldAdapterId)) {
                            nodeData.put("adapterId", adapterIdMapping.get(oldAdapterId).toString());
                            logger.debug("Updated adapter ID in node: {} -> {}", oldAdapterId, adapterIdMapping.get(oldAdapterId));
                        }
                    }
                    
                    // Update senderAdapter field for start nodes
                    if (nodeData.containsKey("senderAdapter")) {
                        UUID oldAdapterId = parseAdapterId((String) nodeData.get("senderAdapter"));
                        if (oldAdapterId != null && adapterIdMapping.containsKey(oldAdapterId)) {
                            nodeData.put("senderAdapter", adapterIdMapping.get(oldAdapterId).toString());
                            logger.debug("Updated senderAdapter ID in node: {} -> {}", oldAdapterId, adapterIdMapping.get(oldAdapterId));
                        }
                    }
                    
                    // Update receiverAdapter field for end nodes
                    if (nodeData.containsKey("receiverAdapter")) {
                        UUID oldAdapterId = parseAdapterId((String) nodeData.get("receiverAdapter"));
                        if (oldAdapterId != null && adapterIdMapping.containsKey(oldAdapterId)) {
                            nodeData.put("receiverAdapter", adapterIdMapping.get(oldAdapterId).toString());
                            logger.debug("Updated receiverAdapter ID in node: {} -> {}", oldAdapterId, adapterIdMapping.get(oldAdapterId));
                        }
                    }
                }
            }
        }
        
        return updatedFlowDefinition;
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
        
        return configWithPlaceholders;
    }
    
    // NEW V3+ Package-aware helper methods
    
    /**
     * Create package information for export (V3+)
     */
    private Map<String, Object> createPackageInfo(UUID packageId) {
        try {
            Optional<IntegrationPackage> packageOpt = packageRepository.findById(packageId);
            if (packageOpt.isEmpty()) {
                logger.warn("Package not found for export: {}", packageId);
                return null;
            }
            
            IntegrationPackage pkg = packageOpt.get();
            Map<String, Object> packageInfo = new HashMap<>();
            packageInfo.put("packageId", pkg.getId().toString());
            packageInfo.put("packageName", pkg.getName());
            packageInfo.put("packageDescription", pkg.getDescription());
            // Package type removed
            packageInfo.put("packageVersion", pkg.getVersion());
            // Tags removed
            
            logger.debug("Created package info for export: {} ({})", pkg.getName(), packageId);
            return packageInfo;
            
        } catch (Exception e) {
            logger.error("Failed to create package info for export: {}", packageId, e);
            return null;
        }
    }
    
    /**
     * Get export format from import data
     */
    private String getExportFormat(Map<String, Object> importData) {
        Object formatObj = importData.get("exportFormat");
        return formatObj != null ? formatObj.toString() : "H2H_FLOW_V1"; // Default to V1 for legacy
    }
    
    /**
     * Handle package import - create new or use existing (V3+)
     */
    private UUID handlePackageImport(Map<String, Object> importData, UUID importedBy) {
        
        if (!importData.containsKey("packageInfo")) {
            logger.warn("No package info found in V3+ import, creating default package");
            return createDefaultPackage(importData, importedBy);
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> packageInfo = (Map<String, Object>) importData.get("packageInfo");
        
        String packageName = (String) packageInfo.get("packageName");
        if (packageName == null || packageName.trim().isEmpty()) {
            logger.warn("Invalid package name in import data, creating default package");
            return createDefaultPackage(importData, importedBy);
        }
        
        // Check if package already exists
        Optional<IntegrationPackage> existingPackage = packageRepository.findByName(packageName);
        
        if (existingPackage.isPresent()) {
            // EXISTING PACKAGE: Use it
            UUID existingPackageId = existingPackage.get().getId();
            logger.info("Using existing package: {} ({})", packageName, existingPackageId);
            return existingPackageId;
        } else {
            // NEW PACKAGE: Create it
            IntegrationPackage newPackage = createPackageFromImportData(packageInfo, importedBy);
            UUID newPackageId = packageRepository.save(newPackage);
            newPackage.setId(newPackageId);
            
            logger.info("Created new package: {} ({})", newPackage.getName(), newPackageId);
            return newPackageId;
        }
    }
    
    /**
     * Create package from import data
     */
    private IntegrationPackage createPackageFromImportData(Map<String, Object> packageInfo, UUID importedBy) {
        IntegrationPackage pkg = new IntegrationPackage();
        
        // Generate unique name if conflict
        String originalName = (String) packageInfo.get("packageName");
        String uniqueName = generateUniquePackageName(originalName);
        
        pkg.setName(uniqueName);
        pkg.setDescription(getStringValue(packageInfo, "packageDescription"));
        
        // Set package type from string
        String packageTypeStr = getStringValue(packageInfo, "packageType");
        // Package type removed - all packages are integration packages
        
        pkg.setVersion(getStringValue(packageInfo, "packageVersion"));
        
        // Tags removed - no more tagging system
        
        // Set audit fields (status is automatically ACTIVE from constructor)
        pkg.setCreatedBy(importedBy);
        pkg.setUpdatedBy(importedBy);

        return pkg;
    }
    
    /**
     * Create default package for flows without package info
     */
    private UUID createDefaultPackage(Map<String, Object> importData, UUID importedBy) {
        IntegrationPackage defaultPkg = new IntegrationPackage();
        
        String flowName = getStringValue(importData, "name");
        String defaultPackageName = (flowName != null ? flowName + " Package" : "Imported Package");
        String uniqueName = generateUniquePackageName(defaultPackageName);
        
        defaultPkg.setName(uniqueName);
        defaultPkg.setDescription("Auto-created package during flow import");
        // Package type removed - all packages are integration packages
        defaultPkg.setVersion("1.0.0");
        defaultPkg.setCreatedBy(importedBy);
        defaultPkg.setUpdatedBy(importedBy);
        // Status is automatically ACTIVE from constructor

        UUID packageId = packageRepository.save(defaultPkg);
        defaultPkg.setId(packageId);
        
        logger.info("Created default package: {} ({})", uniqueName, packageId);
        return packageId;
    }
    
    /**
     * Import adapters with package association (V3+)
     */
    private Map<UUID, UUID> importAdaptersWithPackage(Map<String, Object> importData, UUID packageId, UUID importedBy) {
        if (!exportConfig.isIncludeAdapters() || !importData.containsKey("adapters")) {
            logger.debug("No adapters to import");
            return new HashMap<>();
        }
        
        Object adaptersObj = importData.get("adapters");
        if (!(adaptersObj instanceof List)) {
            logger.warn("Adapters field is not a list, skipping adapter import");
            return new HashMap<>();
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> adaptersList = (List<Map<String, Object>>) adaptersObj;
        
        return importAdaptersListWithPackage(adaptersList, packageId, importedBy);
    }
    
    /**
     * Import adapters list with package association
     */
    private Map<UUID, UUID> importAdaptersListWithPackage(List<Map<String, Object>> adaptersList, UUID packageId, UUID importedBy) {
        logger.info("Starting to import {} adapters with package association ({})", adaptersList.size(), packageId);
        Map<UUID, UUID> adapterIdMapping = new HashMap<>();
        
        for (Map<String, Object> adapterData : adaptersList) {
            logger.info("Processing adapter: {}", adapterData.get("name"));
            try {
                UUID originalId = UUID.fromString((String) adapterData.get("id"));
                
                // Create new adapter with imported configuration
                Adapter adapter = new Adapter();
                adapter.setName((String) adapterData.get("name"));
                adapter.setDescription((String) adapterData.get("description"));
                adapter.setAdapterType((String) adapterData.get("adapterType"));
                adapter.setDirection((String) adapterData.get("direction"));
                adapter.setPackageId(packageId); // NEW: Associate with package
                
                // Set configuration with placeholders for sensitive fields that were removed during export
                @SuppressWarnings("unchecked")
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

                // CRITICAL: Track original adapter ID to distinguish imported vs locally created adapters
                adapter.setOriginalAdapterId(originalId);

                // Check if this adapter was previously imported (by originalAdapterId)
                Optional<Adapter> existingAdapter = adapterRepository.findByOriginalAdapterId(originalId);

                UUID newId;
                if (existingAdapter.isPresent()) {
                    // UPDATE existing imported adapter
                    Adapter existing = existingAdapter.get();
                    logger.info("Re-importing adapter: updating existing adapter {} (originalAdapterId={})",
                               existing.getName(), originalId);

                    // Update adapter properties
                    existing.setName(adapter.getName());
                    existing.setDescription(adapter.getDescription());
                    existing.setConfiguration(adapter.getConfiguration());
                    existing.setAverageExecutionTimeMs(adapter.getAverageExecutionTimeMs());
                    existing.setSuccessRatePercent(adapter.getSuccessRatePercent());
                    existing.setPackageId(packageId); // Update package association
                    existing.setUpdatedBy(importedBy);
                    existing.setUpdatedAt(LocalDateTime.now());
                    // Keep existing active status - don't override

                    adapterRepository.update(existing);
                    newId = existing.getId();
                    logger.info("Updated existing adapter with package: {} (ID: {}, originalAdapterId: {}, package: {})",
                               existing.getName(), newId, originalId, packageId);
                } else {
                    // Check if adapter name conflicts with existing
                    String originalName = adapter.getName();
                    if (adapterRepository.existsByName(originalName)) {
                        // Generate unique name if conflict
                        String uniqueName = generateUniqueAdapterName(originalName);
                        adapter.setName(uniqueName);
                        logger.info("Renamed adapter to avoid conflict: {} -> {}", originalName, uniqueName);
                    }

                    // Set originalAdapterId for tracking
                    adapter.setOriginalAdapterId(originalId);

                    // CREATE new adapter
                    newId = adapterRepository.save(adapter);
                    adapter.setId(newId);

                    logger.info("Imported NEW adapter with package: {} -> {} (originalAdapterId: {}, package: {})",
                               adapter.getName(), newId, originalId, packageId);
                }

                // Map old ID to new ID
                adapterIdMapping.put(originalId, newId);
                           
            } catch (Exception e) {
                logger.error("Failed to import adapter: {}", adapterData.get("name"), e);
                // Continue with other adapters
            }
        }
        
        return adapterIdMapping;
    }
    
    /**
     * Generate unique package name to avoid conflicts
     */
    private String generateUniquePackageName(String baseName) {
        String candidateName = baseName;
        int counter = 1;
        
        while (packageRepository.findByName(candidateName).isPresent()) {
            candidateName = baseName + " (" + counter + ")";
            counter++;
        }
        
        return candidateName;
    }
}