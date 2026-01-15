package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.core.service.AdapterManagementService;
import com.integrixs.core.service.DeployedFlowSchedulingService;
import com.integrixs.core.service.FlowMonitoringService;
import com.integrixs.core.service.PackageMetadataService;
import com.integrixs.core.service.TransactionLogService;
import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.DeployedFlow;
import com.integrixs.shared.model.IntegrationPackage;
import com.integrixs.shared.model.TransactionLog;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.*;

@RestController
@RequestMapping("/api/adapters")
public class AdapterController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterController.class);
    
    private final AdapterManagementService adapterManagementService;
    private final DeployedFlowSchedulingService deployedFlowSchedulingService;
    private final DeployedFlowRepository deployedFlowRepository;
    private final FlowMonitoringService monitoringService;
    private final PackageMetadataService packageMetadataService;
    private final TransactionLogService transactionLogService;
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public AdapterController(AdapterManagementService adapterManagementService,
                           DeployedFlowSchedulingService deployedFlowSchedulingService,
                           DeployedFlowRepository deployedFlowRepository,
                           FlowMonitoringService monitoringService,
                           PackageMetadataService packageMetadataService,
                           TransactionLogService transactionLogService,
                           JdbcTemplate jdbcTemplate) {
        this.adapterManagementService = Objects.requireNonNull(adapterManagementService, "Adapter management service cannot be null");
        this.deployedFlowSchedulingService = Objects.requireNonNull(deployedFlowSchedulingService, "Deployed flow scheduling service cannot be null");
        this.deployedFlowRepository = Objects.requireNonNull(deployedFlowRepository, "Deployed flow repository cannot be null");
        this.monitoringService = Objects.requireNonNull(monitoringService, "Monitoring service cannot be null");
        this.packageMetadataService = Objects.requireNonNull(packageMetadataService, "Package metadata service cannot be null");
        this.transactionLogService = Objects.requireNonNull(transactionLogService, "Transaction log service cannot be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Adapter>>> getAllAdapters(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "packageId", required = false) String packageId) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting adapters (type: {}, direction: {}, enabled: {}, packageId: {})", 
                   currentUser, type, direction, enabled, packageId);
        
        try {
            List<Adapter> adapters;
            UUID packageUuid = null;
            
            // Parse and validate package ID if provided
            if (packageId != null && !packageId.trim().isEmpty()) {
                try {
                    packageUuid = UUID.fromString(packageId.trim());
                    // Validate package exists
                    packageMetadataService.findPackageById(packageUuid);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid package ID format: " + packageId));
                } catch (IllegalStateException e) {
                    return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Package not found: " + packageId));
                }
            }
            
            // Apply filtering logic with package context
            if (packageUuid != null) {
                // Package-scoped queries
                if (enabled != null && enabled) {
                    adapters = adapterManagementService.getActiveAdaptersByPackage(packageUuid);
                } else if (type != null && direction != null) {
                    adapters = adapterManagementService.getAdaptersByTypeDirectionAndPackage(
                        type.toUpperCase(), direction.toUpperCase(), packageUuid);
                } else if (type != null) {
                    adapters = adapterManagementService.getAdaptersByTypeAndPackage(
                        type.toUpperCase(), packageUuid);
                } else {
                    adapters = adapterManagementService.getAdaptersByPackage(packageUuid);
                }
            } else {
                // Global queries (backward compatibility)
                if (enabled != null && enabled) {
                    adapters = adapterManagementService.getActiveAdapters();
                } else if (type != null && direction != null) {
                    adapters = adapterManagementService.getAdaptersByTypeAndDirection(
                        type.toUpperCase(), direction.toUpperCase());
                } else if (type != null) {
                    adapters = adapterManagementService.getAdaptersByType(type.toUpperCase());
                } else {
                    adapters = adapterManagementService.getAllAdapters();
                }
            }
            
            // Enhance adapters with runtime status from deployed flows
            List<Adapter> enhancedAdapters = new ArrayList<>();
            for (Adapter adapter : adapters) {
                Adapter enhancedAdapter = enhanceAdapterWithRuntimeStatus(adapter);
                enhancedAdapters.add(enhancedAdapter);
            }
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapters retrieved successfully", 
                enhancedAdapters
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid parameter from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error("Invalid parameter: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving adapters for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve adapters"));
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Adapter>> getAdapterById(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting adapter: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            Optional<Adapter> adapter = adapterManagementService.getAdapterById(adapterId);
            
            if (adapter.isEmpty()) {
                return ResponseEntity.status(404)
                    .body(ApiResponse.error("Adapter not found"));
            }
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter retrieved successfully", 
                adapter.get()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid adapter ID format: {}", id);
            return ResponseEntity.status(400)
                .body(ApiResponse.error("Invalid adapter ID format"));
        } catch (Exception e) {
            logger.error("Error retrieving adapter {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve adapter"));
        }
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Adapter>> createAdapter(
            @Valid @RequestBody CreateAdapterRequest request) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} creating adapter: {} in package: {}", 
                   currentUser, request.getAdapter().getName(), request.getPackageId());
        
        try {
            UUID createdBy = UUID.fromString(currentUser);
            
            // Validate package exists if provided
            UUID packageId = null;
            if (request.getPackageId() != null) {
                packageId = validateAndGetPackageId(request.getPackageId());
            }
            
            Adapter createdAdapter;
            if (packageId != null) {
                // Use package-aware creation
                createdAdapter = adapterManagementService.createAdapter(
                    request.getAdapter(), packageId, createdBy);
            } else {
                // Use legacy creation (backward compatibility)
                createdAdapter = adapterManagementService.createAdapter(
                    request.getAdapter(), createdBy);
            }
            
            logger.info("Successfully created adapter {} in package {} for user {}", 
                       createdAdapter.getId(), packageId, currentUser);
            return ResponseEntity.status(201)
                .body(new ApiResponse<>(
                    true, 
                    "Adapter created successfully", 
                    createdAdapter
                ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid adapter data from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating adapter for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to create adapter"));
        }
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Adapter>> updateAdapter(
            @PathVariable String id,
            @Valid @RequestBody CreateAdapterRequest request) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} updating adapter: {}", currentUser, id);

        try {
            UUID adapterId = UUID.fromString(id);
            UUID updatedBy = UUID.fromString(currentUser);
            Adapter updatedAdapter = adapterManagementService.updateAdapter(adapterId, request.getAdapter(), updatedBy);
            
            logger.info("Successfully updated adapter {} for user {}", id, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter updated successfully", 
                updatedAdapter
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for adapter {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating adapter {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to update adapter"));
        }
    }
    
    @PutMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> setAdapterEnabled(@PathVariable String id, @RequestParam(value = "enabled") boolean enabled) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} setting adapter {} enabled status to: {}", currentUser, id, enabled);
        
        try {
            UUID adapterId = UUID.fromString(id);
            adapterManagementService.setAdapterActive(adapterId, enabled);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter enabled status updated successfully", 
                null
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for adapter {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating adapter enabled status {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to update adapter enabled status"));
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteAdapter(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} deleting adapter: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            boolean deleted = adapterManagementService.deleteAdapter(adapterId);
            
            if (deleted) {
                logger.info("Successfully deleted adapter {} for user {}", id, currentUser);
                return ResponseEntity.ok(new ApiResponse<>(
                    true, 
                    "Adapter deleted successfully", 
                    null
                ));
            } else {
                return ResponseEntity.status(404)
                    .body(ApiResponse.error("Adapter not found"));
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for adapter {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting adapter {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to delete adapter"));
        }
    }
    
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testAdapter(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} testing adapter connection: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            Map<String, Object> testResult = adapterManagementService.testAdapterConnection(adapterId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter connection test completed", 
                testResult
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for adapter {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error testing adapter {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to test adapter connection", null));
        }
    }
    
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdapterStatistics() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting adapter statistics", currentUser);
        
        try {
            Map<String, Object> statistics = adapterManagementService.getAdapterStatistics();
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter statistics retrieved successfully", 
                statistics
            ));
        } catch (Exception e) {
            logger.error("Error retrieving adapter statistics for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve adapter statistics", null));
        }
    }
    
    /**
     * Start an adapter for monitoring and execution
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> startAdapter(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} starting adapter: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            adapterManagementService.startAdapter(adapterId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter started successfully", 
                null
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for adapter {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error starting adapter {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to start adapter", null));
        }
    }
    
    /**
     * Stop an adapter
     */
    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> stopAdapter(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} stopping adapter: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            adapterManagementService.stopAdapter(adapterId);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter stopped successfully", 
                null
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for adapter {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error stopping adapter {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to stop adapter", null));
        }
    }
    
    /**
     * Get adapter runtime status
     */
    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdapterStatus(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting adapter status: {}", currentUserId, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            
            // Get adapter information
            Optional<Adapter> adapterOpt = adapterManagementService.getAdapterById(adapterId);
            if (adapterOpt.isEmpty()) {
                return ResponseEntity.status(404)
                    .body(ApiResponse.error("Adapter not found"));
            }
            
            Adapter adapter = adapterOpt.get();
            Map<String, Object> status = getAdapterRuntimeStatus(adapter);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter status retrieved successfully", 
                status
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for adapter {}: {}", currentUserId, id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving adapter status {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse<>(false, "Failed to retrieve adapter status", null));
        }
    }
    
    /**
     * Get adapter logs for monitoring
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAdapterLogs(
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "adapterId", required = false) String adapterId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {
        
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting adapter logs (filter: {}, level: {}, adapterId: {})", 
                   currentUserId, filter, level, adapterId);
        
        try {
            UUID adapterIdUuid = null;
            if (adapterId != null && !adapterId.isEmpty()) {
                adapterIdUuid = UUID.fromString(adapterId);
            }
            
            // Get adapter logs from transaction log service
            List<Map<String, Object>> logs = new ArrayList<>();
            
            try {
                if (adapterIdUuid != null) {
                    logger.debug("Fetching logs for adapter: {}", adapterIdUuid);
                    
                    // Get transaction logs specific to this adapter
                    List<TransactionLog> adapterLogs = transactionLogService.getAdapterTransactionLogs(adapterIdUuid, 100);
                    
                    // Convert to API format
                    logs = adapterLogs.stream()
                        .map(this::convertLogToApiFormat)
                        .collect(java.util.stream.Collectors.toList());
                        
                } else {
                    // No specific adapter requested, get recent adapter-related logs
                    List<TransactionLog> recentLogs = transactionLogService.getTransactionLogsByCategory("ADAPTER", 50);
                    
                    // Convert to API format
                    logs = recentLogs.stream()
                        .map(this::convertLogToApiFormat)
                        .collect(java.util.stream.Collectors.toList());
                }
                
            } catch (Exception e) {
                logger.error("Error fetching adapter logs for adapter: {}", adapterId, e);
                // Return empty list on error but log it
            }
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter logs retrieved successfully", 
                logs
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {}: {}", currentUserId, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving adapter logs for user {}: {}", currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse<>(false, "Failed to retrieve adapter logs", null));
        }
    }
    
    private Map<String, Object> convertLogToApiFormat(TransactionLog log) {
        Map<String, Object> apiLog = new HashMap<>();
        
        apiLog.put("id", log.getId());
        apiLog.put("timestamp", log.getTimestamp());
        apiLog.put("category", log.getCategory());
        apiLog.put("subcategory", log.getComponent());
        apiLog.put("message", log.getMessage());
        apiLog.put("details", log.getDetails());
        apiLog.put("username", log.getUsername());
        apiLog.put("adapterId", log.getAdapterId());
        
        // Determine log level from category
        String level = "INFO";
        if (log.getCategory() != null) {
            if (log.getCategory().contains("ERROR") || log.getCategory().contains("FAILED")) {
                level = "ERROR";
            } else if (log.getCategory().contains("WARN")) {
                level = "WARN";
            } else if (log.getCategory().contains("SUCCESS")) {
                level = "INFO";
            }
        }
        apiLog.put("level", level);
        
        return apiLog;
    }
    
    /**
     * Enhance adapter with runtime status from deployed flows
     */
    private Adapter enhanceAdapterWithRuntimeStatus(Adapter adapter) {
        try {
            // Create a copy of the adapter to avoid modifying the original
            Adapter enhancedAdapter = new Adapter();
            enhancedAdapter.setId(adapter.getId());
            enhancedAdapter.setName(adapter.getName());
            enhancedAdapter.setAdapterType(adapter.getAdapterType());
            enhancedAdapter.setDirection(adapter.getDirection());
            enhancedAdapter.setActive(adapter.isActive());
            enhancedAdapter.setStatus(adapter.getStatus());
            enhancedAdapter.setConfiguration(adapter.getConfiguration());
            enhancedAdapter.setCreatedAt(adapter.getCreatedAt());
            enhancedAdapter.setUpdatedAt(adapter.getUpdatedAt());
            enhancedAdapter.setCreatedBy(adapter.getCreatedBy());
            enhancedAdapter.setUpdatedBy(adapter.getUpdatedBy());
            
            // Add runtime status from deployed flows
            Map<String, Object> runtimeStatus = getAdapterRuntimeStatus(adapter);
            enhancedAdapter.getConfiguration().putAll(runtimeStatus);
            
            return enhancedAdapter;
        } catch (Exception e) {
            logger.warn("Failed to enhance adapter {} with runtime status: {}", adapter.getId(), e.getMessage());
            return adapter; // Return original if enhancement fails
        }
    }
    
    /**
     * Get adapter runtime status based on deployed flows
     */
    private Map<String, Object> getAdapterRuntimeStatus(Adapter adapter) {
        Map<String, Object> status = new HashMap<>();
        status.put("adapterId", adapter.getId());
        status.put("adapterName", adapter.getName());
        
        try {
            // Use adapter's actual status from database as primary status
            String runtimeStatus = adapter.getStatus() != null ? adapter.getStatus().name() : "STOPPED";
            boolean isRunning = adapter.getStatus() != null && adapter.getStatus().canProcess();
            
            // Get deployed flows that use this adapter to find executions by flow name
            List<DeployedFlow> deployedFlows = deployedFlowRepository.findExecutableFlows();
            List<String> flowNames = new ArrayList<>();
            int deploymentCount = 0;
            
            for (DeployedFlow flow : deployedFlows) {
                if (adapter.getId().equals(flow.getSenderAdapterId()) || 
                    adapter.getId().equals(flow.getReceiverAdapterId())) {
                    deploymentCount++;
                    flowNames.add(flow.getFlowName());
                }
            }
            
            String lastExecution = null;
            int executionCount = 0;
            int errorCount = 0;
            String lastError = null;
            
            // Find executions by flow names (since execution context isn't populated)
            if (!flowNames.isEmpty()) {
                try {
                    String flowNamesParam = String.join("','", flowNames);
                    String sql = "SELECT started_at, execution_status, error_message FROM flow_executions " +
                                "WHERE flow_name IN ('" + flowNamesParam + "') " +
                                "ORDER BY started_at DESC";
                    
                    List<Map<String, Object>> execResults = jdbcTemplate.queryForList(sql);
                    
                    executionCount = execResults.size();
                    
                    if (!execResults.isEmpty()) {
                        // Get most recent execution time
                        Object startedAtObj = execResults.get(0).get("started_at");
                        if (startedAtObj != null) {
                            lastExecution = startedAtObj.toString();
                        }
                        
                        // Count errors and get last error
                        for (Map<String, Object> exec : execResults) {
                            String execStatus = (String) exec.get("execution_status");
                            String errorMsg = (String) exec.get("error_message");
                            
                            if ("FAILED".equals(execStatus) || errorMsg != null) {
                                errorCount++;
                                if (lastError == null) {
                                    lastError = errorMsg != null ? errorMsg : "Execution failed";
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error querying execution history for adapter {}: {}", adapter.getId(), e.getMessage());
                }
            }
            
            // Only override status if adapter is not active
            if (!adapter.isActive()) {
                runtimeStatus = "STOPPED";
                isRunning = false;
            }
            
            // If adapter has errors, mark as error status (execution-based, not adapter status)
            if (errorCount > 0 && lastError != null) {
                runtimeStatus = "ERROR";
            }
            
            status.put("status", runtimeStatus);
            status.put("isRunning", isRunning);
            status.put("deploymentCount", deploymentCount);
            status.put("lastExecution", lastExecution);
            status.put("executionCount", executionCount);
            status.put("errorCount", errorCount);
            status.put("lastError", lastError);
            status.put("active", adapter.isActive());
            
            logger.debug("Adapter {} runtime status: {} (deployments: {}, executions: {}, errors: {})", 
                        adapter.getId(), runtimeStatus, deploymentCount, executionCount, errorCount);
            
        } catch (Exception e) {
            logger.error("Error determining runtime status for adapter {}: {}", adapter.getId(), e.getMessage(), e);
            status.put("status", "UNKNOWN");
            status.put("isRunning", false);
            status.put("error", e.getMessage());
        }
        
        return status;
    }
    
    // Package-aware endpoint additions following OOP principles
    
    /**
     * Get adapters by package with enhanced filtering.
     * 
     * @param packageId Package UUID
     * @param type Optional adapter type filter
     * @param direction Optional direction filter
     * @param enabled Optional enabled status filter
     * @return List of adapters in the specified package
     */
    @GetMapping("/package/{packageId}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Adapter>>> getAdaptersByPackage(
            @PathVariable String packageId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting adapters for package {} (type: {}, direction: {}, enabled: {})", 
                   currentUser, packageId, type, direction, enabled);
        
        try {
            UUID packageUuid = validateAndGetPackageId(packageId);
            List<Adapter> adapters;
            
            if (enabled != null && enabled) {
                adapters = adapterManagementService.getActiveAdaptersByPackage(packageUuid);
            } else if (type != null && direction != null) {
                adapters = adapterManagementService.getAdaptersByTypeDirectionAndPackage(
                    type.toUpperCase(), direction.toUpperCase(), packageUuid);
            } else if (type != null) {
                adapters = adapterManagementService.getAdaptersByTypeAndPackage(
                    type.toUpperCase(), packageUuid);
            } else {
                adapters = adapterManagementService.getAdaptersByPackage(packageUuid);
            }
            
            // Enhance adapters with runtime status
            List<Adapter> enhancedAdapters = adapters.stream()
                .map(this::enhanceAdapterWithRuntimeStatus)
                .toList();
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Package adapters retrieved successfully", 
                enhancedAdapters
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving package adapters for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve package adapters"));
        }
    }
    
    /**
     * Get package adapter statistics.
     * 
     * @param packageId Package UUID
     * @return Package adapter statistics
     */
    @GetMapping("/package/{packageId}/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPackageAdapterStatistics(
            @PathVariable String packageId) {
        
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting adapter statistics for package {}", currentUser, packageId);
        
        try {
            UUID packageUuid = validateAndGetPackageId(packageId);
            Map<String, Object> statistics = adapterManagementService.getAdapterStatisticsByPackage(packageUuid);
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Package adapter statistics retrieved successfully", 
                statistics
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving package adapter statistics for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve package adapter statistics"));
        }
    }
    
    /**
     * Move adapter to a different package.
     * 
     * @param adapterId Adapter UUID
     * @param request Move adapter request containing target package ID
     * @return Success response
     */
    @PutMapping("/{adapterId}/package")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> moveAdapterToPackage(
            @PathVariable String adapterId,
            @Valid @RequestBody MoveAdapterRequest request) {
        
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} moving adapter {} to package {}", 
                   currentUser, adapterId, request.getTargetPackageId());
        
        try {
            UUID adapterUuid = UUID.fromString(adapterId);
            UUID targetPackageId = validateAndGetPackageId(request.getTargetPackageId());
            UUID movedBy = UUID.fromString(currentUser);
            
            // Get current adapter to find source package
            Adapter adapter = adapterManagementService.getAdapterById(adapterUuid)
                .orElseThrow(() -> new IllegalArgumentException("Adapter not found: " + adapterId));
            
            if (adapter.getPackageId() == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Adapter is not currently associated with any package"));
            }
            
            boolean moved = adapterManagementService.moveAdapterBetweenPackages(
                adapterUuid, adapter.getPackageId(), targetPackageId, movedBy);
            
            if (moved) {
                logger.info("Successfully moved adapter {} to package {} by user {}", 
                           adapterId, targetPackageId, currentUser);
                return ResponseEntity.ok(new ApiResponse<>(
                    true, 
                    "Adapter moved to package successfully", 
                    null
                ));
            } else {
                return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to move adapter to package"));
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error moving adapter {} for user {}: {}", adapterId, currentUser, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to move adapter to package"));
        }
    }
    
    // Private helper methods following OOP encapsulation principles
    
    /**
     * Validate and parse package ID.
     * 
     * @param packageId Package ID string
     * @return Validated UUID
     * @throws IllegalArgumentException if package ID is invalid or package not found
     */
    private UUID validateAndGetPackageId(String packageId) {
        if (packageId == null || packageId.trim().isEmpty()) {
            throw new IllegalArgumentException("Package ID cannot be null or empty");
        }
        
        UUID packageUuid;
        try {
            packageUuid = UUID.fromString(packageId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid package ID format: " + packageId);
        }
        
        // Validate package exists
        try {
            packageMetadataService.findPackageById(packageUuid);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Package not found: " + packageId);
        }
        
        return packageUuid;
    }
    
    // Request/Response DTOs following encapsulation principles
    
    /**
     * Request DTO for creating adapters with package context.
     */
    public static class CreateAdapterRequest {
        private Adapter adapter;
        private String packageId;
        
        public CreateAdapterRequest() {}
        
        public CreateAdapterRequest(Adapter adapter, String packageId) {
            this.adapter = adapter;
            this.packageId = packageId;
        }
        
        public Adapter getAdapter() { return adapter; }
        public void setAdapter(Adapter adapter) { this.adapter = adapter; }
        
        public String getPackageId() { return packageId; }
        public void setPackageId(String packageId) { this.packageId = packageId; }
    }
    
    /**
     * Request DTO for moving adapters between packages.
     */
    public static class MoveAdapterRequest {
        private String targetPackageId;
        private String reason;
        
        public MoveAdapterRequest() {}
        
        public MoveAdapterRequest(String targetPackageId, String reason) {
            this.targetPackageId = targetPackageId;
            this.reason = reason;
        }
        
        public String getTargetPackageId() { return targetPackageId; }
        public void setTargetPackageId(String targetPackageId) { this.targetPackageId = targetPackageId; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    /**
     * Get adapter execution history - last 5 executions with clickable IDs
     */
    @GetMapping("/{id}/executions")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdapterExecutions(
            @PathVariable String id,
            @RequestParam(value = "limit", required = false, defaultValue = "5") int limit) {
        
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting adapter execution history: {} (limit: {})", currentUserId, id, limit);
        
        try {
            UUID adapterId = UUID.fromString(id);
            
            // Get adapter execution history with enterprise-style clickable summaries
            Map<String, Object> executionHistory = monitoringService.getAdapterExecutionHistory(adapterId, limit);
            
            logger.info("Retrieved {} execution records for adapter {} (total: {})", 
                       ((List<?>) executionHistory.get("executions")).size(), 
                       adapterId,
                       executionHistory.get("totalExecutions"));
            
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "Adapter execution history retrieved successfully", 
                executionHistory
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for adapter {}: {}", currentUserId, id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving adapter execution history {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new ApiResponse<>(false, "Failed to retrieve adapter execution history", null));
        }
    }
}