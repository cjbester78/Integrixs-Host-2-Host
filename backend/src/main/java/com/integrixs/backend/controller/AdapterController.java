package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.core.service.AdapterManagementService;
import com.integrixs.core.service.DeployedFlowSchedulingService;
import com.integrixs.core.service.FlowMonitoringService;
import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.DeployedFlow;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/adapters")
public class AdapterController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterController.class);
    
    private final AdapterManagementService adapterManagementService;
    private final DeployedFlowSchedulingService deployedFlowSchedulingService;
    private final DeployedFlowRepository deployedFlowRepository;
    private final FlowMonitoringService monitoringService;
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public AdapterController(AdapterManagementService adapterManagementService,
                           DeployedFlowSchedulingService deployedFlowSchedulingService,
                           DeployedFlowRepository deployedFlowRepository,
                           FlowMonitoringService monitoringService,
                           JdbcTemplate jdbcTemplate) {
        this.adapterManagementService = adapterManagementService;
        this.deployedFlowSchedulingService = deployedFlowSchedulingService;
        this.deployedFlowRepository = deployedFlowRepository;
        this.monitoringService = monitoringService;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Adapter>>> getAllAdapters(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting adapters (type: {}, direction: {}, enabled: {})", 
                   currentUser, type, direction, enabled);
        
        try {
            List<Adapter> adapters;
            
            if (enabled != null && enabled) {
                adapters = adapterManagementService.getActiveAdapters();
            } else if (type != null && direction != null) {
                adapters = adapterManagementService.getAdaptersByTypeAndDirection(type.toUpperCase(), direction.toUpperCase());
            } else if (type != null) {
                adapters = adapterManagementService.getAdaptersByType(type.toUpperCase());
            } else {
                adapters = adapterManagementService.getAllAdapters();
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
    public ResponseEntity<ApiResponse<Adapter>> createAdapter(@RequestBody Adapter adapterData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} creating adapter: {}", currentUser, adapterData.getName());
        
        try {
            UUID createdBy = UUID.fromString(currentUser);
            Adapter createdAdapter = adapterManagementService.createAdapter(adapterData, createdBy);
            
            logger.info("Successfully created adapter for user {}", currentUser);
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
            @RequestBody Adapter adapterData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} updating adapter: {}", currentUser, id);
        
        try {
            UUID adapterId = UUID.fromString(id);
            UUID updatedBy = UUID.fromString(currentUser);
            Adapter updatedAdapter = adapterManagementService.updateAdapter(adapterId, adapterData, updatedBy);
            
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
            
            // Get adapter logs from system log repository
            // This is a placeholder implementation - could be enhanced to filter by specific adapter
            List<Map<String, Object>> logs = new ArrayList<>();
            
            // For now, return empty list but structure is in place for future enhancement
            if (adapterIdUuid != null) {
                logger.debug("Adapter logs requested for adapter: {}", adapterIdUuid);
                // Future implementation: fetch logs specific to this adapter
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