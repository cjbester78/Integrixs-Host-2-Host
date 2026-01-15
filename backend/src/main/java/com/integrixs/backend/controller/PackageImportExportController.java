package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.core.repository.IntegrationPackageRepository;
import com.integrixs.core.util.PackageExportCrypto;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.IntegrationPackage;
import com.integrixs.shared.util.AuditUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Package Import/Export Controller with Encryption
 * Handles encrypted package export and import operations
 */
@RestController
@RequestMapping("/api/packages")
public class PackageImportExportController {

    private static final Logger logger = LoggerFactory.getLogger(PackageImportExportController.class);

    private final IntegrationPackageRepository packageRepository;
    private final AdapterRepository adapterRepository;
    private final IntegrationFlowRepository flowRepository;
    private final PackageExportCrypto packageExportCrypto;

    @Autowired
    public PackageImportExportController(
            IntegrationPackageRepository packageRepository,
            AdapterRepository adapterRepository,
            IntegrationFlowRepository flowRepository,
            PackageExportCrypto packageExportCrypto) {
        this.packageRepository = packageRepository;
        this.adapterRepository = adapterRepository;
        this.flowRepository = flowRepository;
        this.packageExportCrypto = packageExportCrypto;
    }

    /**
     * Export package with selected flows (encrypted)
     * Returns encrypted package data that should be saved with .h2hpkg extension
     * POST /api/packages/{id}/export
     */
    @PostMapping("/{id}/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportPackage(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {

        try {
            logger.info("Exporting package: {}", id);

            // Get the package
            IntegrationPackage pkg = packageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + id));

            // Extract selected flow IDs from request
            @SuppressWarnings("unchecked")
            List<String> selectedFlowIds = (List<String>) request.get("selectedFlowIds");
            if (selectedFlowIds == null || selectedFlowIds.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("No flows selected for export"));
            }

            // Convert flow ID strings to UUIDs
            Set<UUID> flowIdSet = new HashSet<>();
            for (String flowIdStr : selectedFlowIds) {
                try {
                    flowIdSet.add(UUID.fromString(flowIdStr));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid flow ID format: {}", flowIdStr);
                }
            }

            // Get selected flows
            List<IntegrationFlow> selectedFlows = new ArrayList<>();
            for (UUID flowId : flowIdSet) {
                flowRepository.findById(flowId).ifPresent(selectedFlows::add);
            }

            if (selectedFlows.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("No valid flows found for export"));
            }

            // Extract adapter IDs used by selected flows
            Set<UUID> usedAdapterIds = extractAdapterIdsFromFlows(selectedFlows);

            // Get adapters used by selected flows
            List<Adapter> relevantAdapters = new ArrayList<>();
            for (UUID adapterId : usedAdapterIds) {
                adapterRepository.findById(adapterId).ifPresent(relevantAdapters::add);
            }

            // Create export data structure
            Map<String, Object> exportData = new HashMap<>();
            exportData.put("exportedAt", LocalDateTime.now().toString());
            exportData.put("exportedBy", AuditUtils.getCurrentUserId());
            exportData.put("application", "Integrixs Host-2-Host");
            exportData.put("version", "1.0");

            // Package info
            Map<String, Object> packageInfo = new HashMap<>();
            packageInfo.put("id", pkg.getId().toString());
            packageInfo.put("name", pkg.getName());
            packageInfo.put("description", pkg.getDescription());
            packageInfo.put("version", pkg.getVersion());
            packageInfo.put("status", pkg.getStatus().name());
            exportData.put("package", packageInfo);

            // Flows
            List<Map<String, Object>> flowsData = new ArrayList<>();
            for (IntegrationFlow flow : selectedFlows) {
                Map<String, Object> flowData = new HashMap<>();
                flowData.put("id", flow.getId().toString());
                flowData.put("name", flow.getName());
                flowData.put("description", flow.getDescription());
                flowData.put("flowType", flow.getFlowType());
                flowData.put("flowDefinition", flow.getFlowDefinition());
                flowData.put("active", flow.getActive());
                flowData.put("scheduleEnabled", flow.getScheduleEnabled());
                flowData.put("scheduleCron", flow.getScheduleCron());
                flowsData.add(flowData);
            }
            exportData.put("flows", flowsData);

            // Adapters
            List<Map<String, Object>> adaptersData = new ArrayList<>();
            for (Adapter adapter : relevantAdapters) {
                Map<String, Object> adapterData = new HashMap<>();
                adapterData.put("id", adapter.getId().toString());
                adapterData.put("name", adapter.getName());
                adapterData.put("description", adapter.getDescription());
                adapterData.put("adapterType", adapter.getAdapterType());
                adapterData.put("direction", adapter.getDirection());
                adapterData.put("configuration", adapter.getConfiguration());
                adapterData.put("active", adapter.getActive());
                adaptersData.add(adapterData);
            }
            exportData.put("adapters", adaptersData);

            // Metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("flowCount", selectedFlows.size());
            metadata.put("adapterCount", relevantAdapters.size());
            metadata.put("selectedFlowIds", selectedFlowIds);
            exportData.put("metadata", metadata);

            // Encrypt the export data
            Map<String, Object> encryptedData = packageExportCrypto.encryptPackageExport(exportData);

            logger.info("Package exported successfully: {} ({} flows, {} adapters)",
                pkg.getName(), selectedFlows.size(), relevantAdapters.size());

            return ResponseEntity.ok(ApiResponse.success("Package exported successfully", encryptedData));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for package export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to export package: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to export package: " + e.getMessage()));
        }
    }

    /**
     * Import encrypted package
     * Accepts encrypted package data from .h2hpkg files
     * POST /api/packages/import
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importPackage(
            @RequestBody Map<String, Object> encryptedData) {

        try {
            logger.info("Importing encrypted package");

            // Decrypt the package data
            Map<String, Object> packageData;
            if (packageExportCrypto.isEncryptedPackageExport(encryptedData)) {
                packageData = packageExportCrypto.decryptPackageExport(encryptedData);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid encrypted package format"));
            }

            // Extract package info
            @SuppressWarnings("unchecked")
            Map<String, Object> packageInfo = (Map<String, Object>) packageData.get("package");
            if (packageInfo == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Package information missing from import data"));
            }

            String packageName = (String) packageInfo.get("name");
            String packageDescription = (String) packageInfo.get("description");
            String packageVersion = (String) packageInfo.get("version");

            // Find or create package
            IntegrationPackage pkg = packageRepository.findByName(packageName).orElse(null);
            UUID packageId;

            if (pkg != null) {
                // UPDATE existing package
                logger.info("Updating existing package: {}", packageName);
                pkg.setDescription(packageDescription);
                pkg.setVersion(packageVersion);
                packageRepository.update(pkg);
                packageId = pkg.getId();
            } else {
                // CREATE new package (status automatically set to ACTIVE by constructor)
                logger.info("Creating new package: {}", packageName);
                pkg = new IntegrationPackage();
                pkg.setName(packageName);
                pkg.setDescription(packageDescription);
                pkg.setVersion(packageVersion);
                pkg.setConfiguration(new HashMap<>());
                packageId = packageRepository.save(pkg);
                pkg.setId(packageId);
            }

            // Import adapters
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> adaptersData = (List<Map<String, Object>>) packageData.get("adapters");
            Map<String, UUID> oldToNewAdapterIds = new HashMap<>();

            if (adaptersData != null) {
                for (Map<String, Object> adapterData : adaptersData) {
                    String oldAdapterId = (String) adapterData.get("id");
                    String adapterName = (String) adapterData.get("name");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> configuration = (Map<String, Object>) adapterData.get("configuration");

                    // Find existing adapter in this package by name
                    Adapter adapter = adapterRepository.findByPackageIdAndName(packageId, adapterName).orElse(null);
                    UUID adapterId;

                    if (adapter != null) {
                        // UPDATE existing adapter
                        logger.info("Updating existing adapter: {}", adapterName);
                        adapter.setDescription((String) adapterData.get("description"));
                        adapter.setAdapterType((String) adapterData.get("adapterType"));
                        adapter.setDirection((String) adapterData.get("direction"));
                        adapter.setConfiguration(configuration);
                        adapter.setActive(false);
                        adapter.setConnectionValidated(false);
                        adapterRepository.update(adapter);
                        adapterId = adapter.getId();
                    } else {
                        // CREATE new adapter
                        logger.info("Creating new adapter: {}", adapterName);
                        adapter = new Adapter();
                        adapter.setName(adapterName);
                        adapter.setDescription((String) adapterData.get("description"));
                        adapter.setAdapterType((String) adapterData.get("adapterType"));
                        adapter.setDirection((String) adapterData.get("direction"));
                        adapter.setConfiguration(configuration);
                        adapter.setActive(false);
                        adapter.setConnectionValidated(false);
                        adapter.setPackageId(packageId);
                        adapterId = adapterRepository.save(adapter);
                    }

                    oldToNewAdapterIds.put(oldAdapterId, adapterId);
                }
            }

            // Import flows
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> flowsData = (List<Map<String, Object>>) packageData.get("flows");
            int importedFlowCount = 0;

            if (flowsData != null) {
                for (Map<String, Object> flowData : flowsData) {
                    String flowName = (String) flowData.get("name");
                    String originalFlowIdStr = (String) flowData.get("id");
                    UUID originalFlowId = null;
                    if (originalFlowIdStr != null && !originalFlowIdStr.trim().isEmpty()) {
                        try {
                            originalFlowId = UUID.fromString(originalFlowIdStr);
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid original flow ID format: {}", originalFlowIdStr);
                        }
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> flowDefinition = (Map<String, Object>) flowData.get("flowDefinition");

                    // CRITICAL: Update adapter IDs in flow definition to use new adapter IDs
                    Map<String, Object> updatedFlowDefinition = updateAdapterReferences(flowDefinition, oldToNewAdapterIds);

                    // Find existing flow in this package by name
                    IntegrationFlow flow = flowRepository.findByPackageIdAndName(packageId, flowName).orElse(null);

                    if (flow != null) {
                        // UPDATE existing flow using importUpdate
                        logger.info("Updating existing flow: {}", flowName);
                        flow.setDescription((String) flowData.get("description"));
                        flow.setFlowType((String) flowData.get("flowType"));
                        flow.setFlowDefinition(updatedFlowDefinition);
                        flow.setActive(false);
                        flow.setScheduleEnabled(false);
                        flowRepository.importUpdate(flow, originalFlowId);
                    } else {
                        // CREATE new flow using importSave
                        logger.info("Creating new flow: {}", flowName);
                        flow = new IntegrationFlow();
                        flow.setName(flowName);
                        flow.setDescription((String) flowData.get("description"));
                        flow.setFlowType((String) flowData.get("flowType"));
                        flow.setFlowDefinition(updatedFlowDefinition);
                        flow.setActive(false);
                        flow.setScheduleEnabled(false);
                        flow.setPackageId(packageId);
                        flowRepository.importSave(flow, originalFlowId);
                    }

                    importedFlowCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("packageId", packageId.toString());
            result.put("packageName", packageName);
            result.put("adaptersImported", oldToNewAdapterIds.size());
            result.put("flowsImported", importedFlowCount);

            logger.info("Package imported successfully: {} ({} adapters, {} flows)",
                packageName, oldToNewAdapterIds.size(), importedFlowCount);

            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Package imported successfully", result));

        } catch (Exception e) {
            logger.error("Failed to import package: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to import package: " + e.getMessage()));
        }
    }

    /**
     * Extract adapter IDs from flow definitions
     * Uses the same logic as FlowImportExportService to ensure consistency
     */
    private Set<UUID> extractAdapterIdsFromFlows(List<IntegrationFlow> flows) {
        Set<UUID> adapterIds = new HashSet<>();

        for (IntegrationFlow flow : flows) {
            Map<String, Object> flowDef = flow.getFlowDefinition();
            if (flowDef != null && !flowDef.isEmpty()) {
                try {
                    // Extract adapter IDs from flow definition
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> nodes = (List<Map<String, Object>>) flowDef.get("nodes");
                    if (nodes != null) {
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

                            // Extract adapter ID based on node type (matching FlowImportExportService logic)
                            Set<UUID> nodeAdapterIds = new HashSet<>();

                            switch (nodeType) {
                                case "adapter":
                                    // ADAPTER nodes use 'adapterId' field
                                    Object adapterIdObj = nodeData.get("adapterId");
                                    if (adapterIdObj != null) {
                                        UUID id = parseAdapterId(adapterIdObj.toString());
                                        if (id != null) nodeAdapterIds.add(id);
                                    }
                                    break;

                                case "start":
                                case "start-process":
                                    // START nodes use 'senderAdapter' field
                                    Object senderAdapterObj = nodeData.get("senderAdapter");
                                    if (senderAdapterObj != null) {
                                        UUID id = parseAdapterId(senderAdapterObj.toString());
                                        if (id != null) nodeAdapterIds.add(id);
                                    }
                                    break;

                                case "end":
                                case "end-process":
                                case "message-end":
                                    // END nodes use 'receiverAdapter' field
                                    Object receiverAdapterObj = nodeData.get("receiverAdapter");
                                    if (receiverAdapterObj != null) {
                                        UUID id = parseAdapterId(receiverAdapterObj.toString());
                                        if (id != null) nodeAdapterIds.add(id);
                                    }
                                    break;
                            }

                            // Fallback: Check all fields in nodeData for any adapter ID patterns
                            for (Map.Entry<String, Object> entry : nodeData.entrySet()) {
                                String key = entry.getKey();
                                Object value = entry.getValue();

                                // Check for common adapter field names
                                if (value != null &&
                                    (key.toLowerCase().contains("adapter") ||
                                     key.equals("senderId") ||
                                     key.equals("receiverId"))) {
                                    UUID id = parseAdapterId(value.toString());
                                    if (id != null) {
                                        nodeAdapterIds.add(id);
                                        logger.debug("Found adapter ID {} in field '{}' of node type '{}'", id, key, nodeType);
                                    }
                                }
                            }

                            if (!nodeAdapterIds.isEmpty()) {
                                adapterIds.addAll(nodeAdapterIds);
                                logger.debug("Found {} adapter ID(s) in node type '{}': {}",
                                    nodeAdapterIds.size(), nodeType, nodeAdapterIds);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to extract adapter IDs from flow definition: {}", e.getMessage());
                }
            }
        }

        logger.info("Extracted {} adapter IDs from {} flows: {}", adapterIds.size(), flows.size(), adapterIds);
        return adapterIds;
    }

    /**
     * Parse adapter ID string to UUID
     */
    private UUID parseAdapterId(String adapterIdStr) {
        try {
            return UUID.fromString(adapterIdStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid adapter ID format: {}", adapterIdStr);
            return null;
        }
    }

    /**
     * Update adapter references in flow definition with new adapter IDs
     * This ensures imported flows reference the newly created adapters
     */
    private Map<String, Object> updateAdapterReferences(Map<String, Object> flowDefinition, Map<String, UUID> adapterIdMapping) {
        if (flowDefinition == null || adapterIdMapping.isEmpty()) {
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
                        String oldAdapterIdStr = (String) nodeData.get("adapterId");
                        UUID oldAdapterId = parseAdapterId(oldAdapterIdStr);
                        if (oldAdapterId != null && adapterIdMapping.containsKey(oldAdapterIdStr)) {
                            UUID newAdapterId = adapterIdMapping.get(oldAdapterIdStr);
                            nodeData.put("adapterId", newAdapterId.toString());
                            logger.debug("Updated adapterId in node: {} -> {}", oldAdapterId, newAdapterId);
                        }
                    }

                    // Update senderAdapter field for start nodes
                    if (nodeData.containsKey("senderAdapter")) {
                        String oldAdapterIdStr = (String) nodeData.get("senderAdapter");
                        UUID oldAdapterId = parseAdapterId(oldAdapterIdStr);
                        if (oldAdapterId != null && adapterIdMapping.containsKey(oldAdapterIdStr)) {
                            UUID newAdapterId = adapterIdMapping.get(oldAdapterIdStr);
                            nodeData.put("senderAdapter", newAdapterId.toString());
                            logger.debug("Updated senderAdapter in node: {} -> {}", oldAdapterId, newAdapterId);
                        }
                    }

                    // Update receiverAdapter field for end nodes
                    if (nodeData.containsKey("receiverAdapter")) {
                        String oldAdapterIdStr = (String) nodeData.get("receiverAdapter");
                        UUID oldAdapterId = parseAdapterId(oldAdapterIdStr);
                        if (oldAdapterId != null && adapterIdMapping.containsKey(oldAdapterIdStr)) {
                            UUID newAdapterId = adapterIdMapping.get(oldAdapterIdStr);
                            nodeData.put("receiverAdapter", newAdapterId.toString());
                            logger.debug("Updated receiverAdapter in node: {} -> {}", oldAdapterId, newAdapterId);
                        }
                    }
                }
            }
        }

        return updatedFlowDefinition;
    }
}
