package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.core.service.FlowDefinitionService;
import com.integrixs.core.service.FlowExecutionService;
import com.integrixs.core.service.FlowMonitoringService;
import com.integrixs.core.service.PackageMetadataService;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.IntegrationPackage;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.*;

@RestController
@RequestMapping("/api/flows")
public class FlowController {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowController.class);
    
    private final FlowDefinitionService flowDefinitionService;
    private final FlowExecutionService flowExecutionService;
    private final FlowMonitoringService flowMonitoringService;
    private final PackageMetadataService packageMetadataService;
    
    @Autowired
    public FlowController(FlowDefinitionService flowDefinitionService,
                         FlowExecutionService flowExecutionService,
                         FlowMonitoringService flowMonitoringService,
                         PackageMetadataService packageMetadataService) {
        this.flowDefinitionService = Objects.requireNonNull(flowDefinitionService, "Flow definition service cannot be null");
        this.flowExecutionService = Objects.requireNonNull(flowExecutionService, "Flow execution service cannot be null");
        this.flowMonitoringService = Objects.requireNonNull(flowMonitoringService, "Flow monitoring service cannot be null");
        this.packageMetadataService = Objects.requireNonNull(packageMetadataService, "Package metadata service cannot be null");
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllFlows(
            @RequestParam(value = "packageId", required = false) String packageId,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "flowType", required = false) String flowType) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting integration flows (packageId: {}, active: {}, flowType: {})", 
                   currentUser, packageId, active, flowType);
        
        try {
            List<IntegrationFlow> flows;
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
                if (active != null && active) {
                    flows = flowDefinitionService.getActiveFlowsByPackage(packageUuid);
                } else if (flowType != null) {
                    flows = flowDefinitionService.getFlowsByTypeAndPackage(flowType.toUpperCase(), packageUuid);
                } else {
                    flows = flowDefinitionService.getFlowsByPackage(packageUuid);
                }
            } else {
                // Global queries (backward compatibility)
                if (active != null && active) {
                    flows = flowDefinitionService.getActiveFlows();
                } else if (flowType != null) {
                    flows = flowDefinitionService.getFlowsByType(flowType.toUpperCase());
                } else {
                    flows = flowDefinitionService.getAllFlows();
                }
            }
            
            // Enrich flows with deployment status
            List<Map<String, Object>> enrichedFlows = new ArrayList<>();
            for (IntegrationFlow flow : flows) {
                Map<String, Object> flowData = new HashMap<>();
                
                // Copy all IntegrationFlow properties
                flowData.put("id", flow.getId());
                flowData.put("name", flow.getName());
                flowData.put("description", flow.getDescription());
                flowData.put("flowDefinition", flow.getFlowDefinition());
                flowData.put("flowVersion", flow.getFlowVersion());
                flowData.put("flowType", flow.getFlowType());
                flowData.put("maxParallelExecutions", flow.getMaxParallelExecutions());
                flowData.put("timeoutMinutes", flow.getTimeoutMinutes());
                flowData.put("retryPolicy", flow.getRetryPolicy());
                flowData.put("totalExecutions", flow.getTotalExecutions());
                flowData.put("successfulExecutions", flow.getSuccessfulExecutions());
                flowData.put("failedExecutions", flow.getFailedExecutions());
                flowData.put("averageExecutionTimeMs", flow.getAverageExecutionTimeMs());
                flowData.put("scheduleEnabled", flow.getScheduleEnabled());
                flowData.put("scheduleCron", flow.getScheduleCron());
                flowData.put("nextScheduledRun", flow.getNextScheduledRun());
                flowData.put("active", flow.getActive());
                flowData.put("originalFlowId", flow.getOriginalFlowId());
                flowData.put("createdAt", flow.getCreatedAt());
                flowData.put("updatedAt", flow.getUpdatedAt());
                flowData.put("createdBy", flow.getCreatedBy());
                flowData.put("updatedBy", flow.getUpdatedBy());
                
                // Add computed properties
                flowData.put("displayName", flow.getDisplayName());
                flowData.put("formattedAverageExecutionTime", flow.getFormattedAverageExecutionTime());
                flowData.put("overdue", flow.isOverdue());
                flowData.put("scheduled", flow.isScheduled());
                flowData.put("failureRate", flow.getFailureRate());
                flowData.put("successRate", flow.getSuccessRate());
                flowData.put("readyForExecution", flow.isReadyForExecution());
                flowData.put("statusIcon", flow.getStatusIcon());
                
                // Add deployment status from deployed_flows table
                try {
                    Map<String, Object> deploymentStatus = flowDefinitionService.getDeploymentStatus(flow.getId());
                    flowData.put("deployed", deploymentStatus.get("isDeployed"));
                    flowData.put("deploymentStatus", deploymentStatus.get("deploymentStatus"));
                    flowData.put("runtimeStatus", deploymentStatus.get("runtimeStatus"));
                    flowData.put("deployedAt", deploymentStatus.get("deployedAt"));
                    flowData.put("deployedBy", deploymentStatus.get("deployedBy"));
                } catch (Exception e) {
                    logger.warn("Could not get deployment status for flow {}: {}", flow.getId(), e.getMessage());
                    // Default values when deployment status cannot be retrieved
                    flowData.put("deployed", false);
                    flowData.put("deploymentStatus", "NOT_DEPLOYED");
                    flowData.put("runtimeStatus", "INACTIVE");
                    flowData.put("deployedAt", null);
                    flowData.put("deployedBy", null);
                }
                
                enrichedFlows.add(flowData);
            }
            
            return ResponseEntity.ok(ApiResponse.success(
                "Integration flows retrieved successfully", 
                enrichedFlows
            ));
        } catch (Exception e) {
            logger.error("Error retrieving integration flows for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve integration flows"));
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<IntegrationFlow>> getFlowById(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting integration flow: {}", currentUser, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            Optional<IntegrationFlow> flow = flowDefinitionService.getFlowById(flowId);
            
            if (flow.isEmpty()) {
                return ResponseEntity.status(404)
                    .body(ApiResponse.error("Flow not found"));
            }
            
            return ResponseEntity.ok(ApiResponse.success(
                "Integration flow retrieved successfully", 
                flow.get()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid flow ID format: {}", id);
            return ResponseEntity.status(400)
                .body(ApiResponse.error("Invalid flow ID format"));
        } catch (Exception e) {
            logger.error("Error retrieving integration flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve integration flow"));
        }
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<IntegrationFlow>> createFlow(
            @Valid @RequestBody CreateFlowRequest request) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} creating integration flow: {} in package: {}", 
                   currentUser, request.getFlow().getName(), request.getPackageId());
        
        try {
            UUID createdBy = UUID.fromString(currentUser);
            
            // Validate package exists if provided
            UUID packageId = null;
            if (request.getPackageId() != null) {
                packageId = validateAndGetPackageId(request.getPackageId());
            }
            
            IntegrationFlow createdFlow;
            if (packageId != null) {
                // Use package-aware creation
                createdFlow = flowDefinitionService.createFlow(
                    request.getFlow(), packageId, createdBy);
            } else {
                // Use legacy creation (backward compatibility)
                createdFlow = flowDefinitionService.createFlow(
                    request.getFlow(), createdBy);
            }
            
            logger.info("Successfully created integration flow {} in package {} for user {}", 
                       createdFlow.getId(), packageId, currentUser);
            return ResponseEntity.status(201)
                .body(ApiResponse.success(
                    "Integration flow created successfully", 
                    createdFlow
                ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid flow data from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating integration flow for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to create integration flow"));
        }
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<IntegrationFlow>> updateFlow(
            @PathVariable String id,
            @Valid @RequestBody CreateFlowRequest request) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        IntegrationFlow flowData = request.getFlow();
        logger.info("User {} updating integration flow: {}", currentUser, id);
        logger.debug("Flow update request - ID: {}, Name: {}, Active: {}", id, flowData.getName(), flowData.getActive());
        logger.debug("Flow definition keys: {}", flowData.getFlowDefinition() != null ? flowData.getFlowDefinition().keySet() : "null");

        try {
            UUID flowId = UUID.fromString(id);
            UUID updatedBy = UUID.fromString(currentUser);
            IntegrationFlow updatedFlow = flowDefinitionService.updateFlow(flowId, flowData, updatedBy);
            
            logger.info("Successfully updated integration flow {} for user {}", id, currentUser);
            logger.debug("Updated flow - Name: {}, Active: {}", updatedFlow.getName(), updatedFlow.getActive());
            return ResponseEntity.ok(ApiResponse.success(
                "Integration flow updated successfully", 
                updatedFlow
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating integration flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to update integration flow"));
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteFlow(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} deleting integration flow: {}", currentUser, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            boolean deleted = flowDefinitionService.deleteFlow(flowId);
            
            if (deleted) {
                logger.info("Successfully deleted integration flow {} for user {}", id, currentUser);
                return ResponseEntity.ok(ApiResponse.success(
                    "Integration flow deleted successfully"
                ));
            } else {
                return ResponseEntity.status(404)
                    .body(ApiResponse.error("Flow not found"));
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid flow ID format: {}", id);
            return ResponseEntity.status(400)
                .body(ApiResponse.error("Invalid flow ID format"));
        } catch (Exception e) {
            logger.error("Error deleting integration flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to delete integration flow"));
        }
    }
    
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeFlow(
            @PathVariable String id, 
            @RequestBody(required = false) Map<String, Object> payload) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} executing integration flow: {}", currentUser, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            UUID triggeredBy = UUID.fromString(currentUser);
            
            com.integrixs.shared.model.FlowExecution execution = flowExecutionService.executeFlow(flowId, payload, triggeredBy);
            
            Map<String, Object> executionResult = new HashMap<>();
            executionResult.put("executionId", execution.getId());
            executionResult.put("flowId", execution.getFlowId());
            executionResult.put("flowName", execution.getFlowName());
            executionResult.put("status", execution.getExecutionStatus().name());
            executionResult.put("triggerType", execution.getTriggerType().name());
            executionResult.put("startedAt", execution.getStartedAt());
            executionResult.put("triggeredBy", execution.getTriggeredBy());
            executionResult.put("correlationId", execution.getCorrelationId());
            
            logger.info("Successfully started flow execution {} for user {}", execution.getId(), currentUser);
            return ResponseEntity.ok(ApiResponse.success(
                "Integration flow execution started successfully", 
                executionResult
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error executing integration flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to execute integration flow"));
        }
    }
    
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateFlow(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} validating integration flow: {}", currentUser, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            Map<String, Object> validation = flowDefinitionService.validateFlow(flowId);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Flow validation completed", 
                validation
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error validating integration flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to validate integration flow"));
        }
    }
    
    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> setFlowActive(@PathVariable String id, @RequestParam boolean active) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} setting flow {} active status to: {}", currentUser, id, active);

        try {
            UUID flowId = UUID.fromString(id);

            // CRITICAL: Validate flow can be activated before setting active=true
            if (active) {
                Map<String, Object> validationResult = flowDefinitionService.validateFlowActivation(flowId);
                Boolean isValid = (Boolean) validationResult.get("isValid");

                if (!Boolean.TRUE.equals(isValid)) {
                    @SuppressWarnings("unchecked")
                    List<String> errors = (List<String>) validationResult.get("errors");
                    String errorMessage = "Cannot activate flow:\n" + String.join("\n", errors);

                    logger.warn("Flow {} activation blocked by validation: {}", flowId, errors);
                    return ResponseEntity.status(400)
                        .body(ApiResponse.error(errorMessage));
                }
            }

            flowDefinitionService.setFlowActive(flowId, active);

            return ResponseEntity.ok(ApiResponse.success(
                "Flow active status updated successfully"
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating flow active status {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to update flow active status"));
        }
    }
    
    @GetMapping("/{id}/executions")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<com.integrixs.shared.model.FlowExecution>>> getFlowExecutions(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting executions for flow: {}", currentUser, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            List<com.integrixs.shared.model.FlowExecution> executions = flowExecutionService.getExecutionsByFlowId(flowId);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Flow executions retrieved successfully", 
                executions
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid flow ID format: {}", id);
            return ResponseEntity.status(400)
                .body(ApiResponse.error("Invalid flow ID format"));
        } catch (Exception e) {
            logger.error("Error retrieving executions for flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve flow executions"));
        }
    }
    
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFlowStatistics() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting flow statistics", currentUser);
        
        try {
            Map<String, Object> statistics = flowDefinitionService.getFlowStatistics();
            
            return ResponseEntity.ok(ApiResponse.success(
                "Flow statistics retrieved successfully", 
                statistics
            ));
        } catch (Exception e) {
            logger.error("Error retrieving flow statistics for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve flow statistics"));
        }
    }
    
    /**
     * Export flow with encryption
     * Returns encrypted flow data that should be saved with .h2hflow extension
     * GET /api/flows/{id}/export
     */
    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportFlow(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} exporting flow: {}", currentUser, id);

        try {
            UUID flowId = UUID.fromString(id);
            Map<String, Object> exportData = flowDefinitionService.exportFlow(flowId);

            return ResponseEntity.ok(ApiResponse.success(
                "Flow exported successfully (save as .h2hflow)",
                exportData
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUser, id, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error exporting flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to export flow"));
        }
    }
    
    /**
     * Import encrypted flow
     * Accepts encrypted flow data from .h2hflow files
     * POST /api/flows/import
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<IntegrationFlow>> importFlow(@RequestBody Map<String, Object> importData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} importing flow", currentUser);

        try {
            UUID importedBy = UUID.fromString(currentUser);

            // CRITICAL: Extract actual flow data if wrapped in ApiResponse structure
            // The frontend may send the entire export response which includes wrapper fields
            Map<String, Object> actualFlowData = extractFlowData(importData);

            IntegrationFlow importedFlow = flowDefinitionService.importFlow(actualFlowData, importedBy);

            // CRITICAL: Check if import actually succeeded
            if (importedFlow == null) {
                logger.error("Flow import returned null for user {}", currentUser);
                return ResponseEntity.status(500)
                    .body(ApiResponse.error("Flow import failed - no flow was created. Check server logs for details."));
            }

            return ResponseEntity.status(201)
                .body(ApiResponse.success(
                    "Flow imported successfully",
                    importedFlow
                ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid import data from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            // Import validation or processing errors - show detailed message to user
            logger.error("Error importing flow for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            // Unexpected errors - show generic message
            logger.error("Unexpected error importing flow for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to import flow: " + e.getMessage()));
        }
    }

    /**
     * Extract actual flow data from import request.
     * Handles both direct flow export data and wrapped ApiResponse structures.
     */
    private Map<String, Object> extractFlowData(Map<String, Object> importData) {
        // Check if this is wrapped in an ApiResponse structure (has 'data' field with wrapper fields)
        if (importData.containsKey("data") &&
            importData.containsKey("success") &&
            importData.containsKey("message")) {

            logger.debug("Detected wrapped ApiResponse structure, extracting 'data' field");
            Object dataObj = importData.get("data");

            if (dataObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> extractedData = (Map<String, Object>) dataObj;
                return extractedData;
            }
        }

        // Otherwise assume it's already the flow export data
        return importData;
    }
    
    /**
     * Deploy a flow to make it executable
     */
    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deployFlow(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} deploying integration flow: {}", currentUserId, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            Map<String, Object> deploymentResult = flowDefinitionService.deployFlow(flowId, currentUserId);
            
            logger.info("Successfully deployed integration flow {} by user {}", id, currentUserId);
            return ResponseEntity.ok(ApiResponse.success(
                "Integration flow deployed successfully", 
                deploymentResult
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUserId, id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deploying integration flow {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to deploy integration flow"));
        }
    }
    
    /**
     * Undeploy a flow to stop execution
     */
    @PostMapping("/{id}/undeploy")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> undeployFlow(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} undeploying integration flow: {}", currentUserId, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            Map<String, Object> undeploymentResult = flowDefinitionService.undeployFlow(flowId, currentUserId);
            
            logger.info("Successfully undeployed integration flow {} by user {}", id, currentUserId);
            return ResponseEntity.ok(ApiResponse.success(
                "Integration flow undeployed successfully", 
                undeploymentResult
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUserId, id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error undeploying integration flow {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to undeploy integration flow"));
        }
    }
    
    /**
     * Get deployment status and history for a flow
     */
    @GetMapping("/{id}/deployment")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFlowDeploymentStatus(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} requesting deployment status for flow: {}", currentUserId, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            Map<String, Object> deploymentStatus = flowDefinitionService.getDeploymentStatus(flowId);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Deployment status retrieved successfully", 
                deploymentStatus
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUserId, id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving deployment status for flow {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve deployment status"));
        }
    }
    
    /**
     * Validate flow deployment readiness
     */
    @PostMapping("/{id}/validate-deployment")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateFlowDeployment(@PathVariable String id) {
        UUID currentUserId = SecurityContextHelper.getCurrentUserId();
        logger.info("User {} validating deployment readiness for flow: {}", currentUserId, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            Map<String, Object> validationResult = flowDefinitionService.validateDeployment(flowId);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Deployment validation completed", 
                validationResult
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {} for flow {}: {}", currentUserId, id, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error validating deployment for flow {} for user {}: {}", id, currentUserId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to validate deployment readiness"));
        }
    }
    
    // Package-aware endpoint additions following OOP principles
    
    /**
     * Get flows by package with enhanced filtering.
     * 
     * @param packageId Package UUID
     * @param active Optional active status filter
     * @param flowType Optional flow type filter
     * @return List of flows in the specified package
     */
    @GetMapping("/package/{packageId}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFlowsByPackage(
            @PathVariable String packageId,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "flowType", required = false) String flowType) {
        
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting flows for package {} (active: {}, flowType: {})", 
                   currentUser, packageId, active, flowType);
        
        try {
            UUID packageUuid = validateAndGetPackageId(packageId);
            List<IntegrationFlow> flows;
            
            if (active != null && active) {
                flows = flowDefinitionService.getActiveFlowsByPackage(packageUuid);
            } else if (flowType != null) {
                flows = flowDefinitionService.getFlowsByTypeAndPackage(flowType.toUpperCase(), packageUuid);
            } else {
                flows = flowDefinitionService.getFlowsByPackage(packageUuid);
            }
            
            // Enrich flows with deployment status (same logic as getAllFlows)
            List<Map<String, Object>> enrichedFlows = enrichFlowsWithDeploymentStatus(flows);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Package flows retrieved successfully", 
                enrichedFlows
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving package flows for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve package flows"));
        }
    }
    
    /**
     * Get package flow statistics.
     * 
     * @param packageId Package UUID
     * @return Package flow statistics
     */
    @GetMapping("/package/{packageId}/statistics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPackageFlowStatistics(
            @PathVariable String packageId) {
        
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting flow statistics for package {}", currentUser, packageId);
        
        try {
            UUID packageUuid = validateAndGetPackageId(packageId);
            Map<String, Object> statistics = flowDefinitionService.getFlowStatisticsByPackage(packageUuid);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Package flow statistics retrieved successfully", 
                statistics
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving package flow statistics for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve package flow statistics"));
        }
    }
    
    /**
     * Move flow to a different package.
     * 
     * @param flowId Flow UUID
     * @param request Move flow request containing target package ID
     * @return Success response
     */
    @PutMapping("/{flowId}/package")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> moveFlowToPackage(
            @PathVariable String flowId,
            @Valid @RequestBody MoveFlowRequest request) {
        
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} moving flow {} to package {}", 
                   currentUser, flowId, request.getTargetPackageId());
        
        try {
            UUID flowUuid = UUID.fromString(flowId);
            UUID targetPackageId = validateAndGetPackageId(request.getTargetPackageId());
            UUID movedBy = UUID.fromString(currentUser);
            
            // Get current flow to find source package
            IntegrationFlow flow = flowDefinitionService.getFlowById(flowUuid)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + flowId));
            
            if (flow.getPackageId() == null) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Flow is not currently associated with any package"));
            }
            
            boolean moved = flowDefinitionService.moveFlowBetweenPackages(
                flowUuid, flow.getPackageId(), targetPackageId, movedBy);
            
            if (moved) {
                logger.info("Successfully moved flow {} to package {} by user {}", 
                           flowId, targetPackageId, currentUser);
                return ResponseEntity.ok(ApiResponse.success(
                    "Flow moved to package successfully"
                ));
            } else {
                return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to move flow to package"));
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error moving flow {} for user {}: {}", flowId, currentUser, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to move flow to package"));
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
    
    /**
     * Enrich flows with deployment status information.
     * Extracted method following DRY principle.
     * 
     * @param flows List of flows to enrich
     * @return List of enriched flow data maps
     */
    private List<Map<String, Object>> enrichFlowsWithDeploymentStatus(List<IntegrationFlow> flows) {
        List<Map<String, Object>> enrichedFlows = new ArrayList<>();
        
        for (IntegrationFlow flow : flows) {
            Map<String, Object> flowData = new HashMap<>();
            
            // Copy all IntegrationFlow properties
            flowData.put("id", flow.getId());
            flowData.put("name", flow.getName());
            flowData.put("description", flow.getDescription());
            flowData.put("flowDefinition", flow.getFlowDefinition());
            flowData.put("flowVersion", flow.getFlowVersion());
            flowData.put("flowType", flow.getFlowType());
            flowData.put("maxParallelExecutions", flow.getMaxParallelExecutions());
            flowData.put("timeoutMinutes", flow.getTimeoutMinutes());
            flowData.put("retryPolicy", flow.getRetryPolicy());
            flowData.put("totalExecutions", flow.getTotalExecutions());
            flowData.put("successfulExecutions", flow.getSuccessfulExecutions());
            flowData.put("failedExecutions", flow.getFailedExecutions());
            flowData.put("averageExecutionTimeMs", flow.getAverageExecutionTimeMs());
            flowData.put("scheduleEnabled", flow.getScheduleEnabled());
            flowData.put("scheduleCron", flow.getScheduleCron());
            flowData.put("nextScheduledRun", flow.getNextScheduledRun());
            flowData.put("active", flow.getActive());
            flowData.put("originalFlowId", flow.getOriginalFlowId());
            flowData.put("createdAt", flow.getCreatedAt());
            flowData.put("updatedAt", flow.getUpdatedAt());
            flowData.put("createdBy", flow.getCreatedBy());
            flowData.put("updatedBy", flow.getUpdatedBy());
            flowData.put("packageId", flow.getPackageId());
            
            // Add computed properties
            flowData.put("displayName", flow.getDisplayName());
            flowData.put("formattedAverageExecutionTime", flow.getFormattedAverageExecutionTime());
            flowData.put("overdue", flow.isOverdue());
            flowData.put("scheduled", flow.isScheduled());
            flowData.put("failureRate", flow.getFailureRate());
            flowData.put("successRate", flow.getSuccessRate());
            flowData.put("readyForExecution", flow.isReadyForExecution());
            flowData.put("statusIcon", flow.getStatusIcon());
            
            // Add deployment status from deployed_flows table
            try {
                Map<String, Object> deploymentStatus = flowDefinitionService.getDeploymentStatus(flow.getId());
                flowData.put("deployed", deploymentStatus.get("isDeployed"));
                flowData.put("deploymentStatus", deploymentStatus.get("deploymentStatus"));
                flowData.put("runtimeStatus", deploymentStatus.get("runtimeStatus"));
                flowData.put("deployedAt", deploymentStatus.get("deployedAt"));
                flowData.put("deployedBy", deploymentStatus.get("deployedBy"));
            } catch (Exception e) {
                logger.warn("Could not get deployment status for flow {}: {}", flow.getId(), e.getMessage());
                // Default values when deployment status cannot be retrieved
                flowData.put("deployed", false);
                flowData.put("deploymentStatus", "NOT_DEPLOYED");
                flowData.put("runtimeStatus", "INACTIVE");
                flowData.put("deployedAt", null);
                flowData.put("deployedBy", null);
            }
            
            enrichedFlows.add(flowData);
        }
        
        return enrichedFlows;
    }
    
    // Request/Response DTOs following encapsulation principles
    
    /**
     * Request DTO for creating flows with package context.
     */
    public static class CreateFlowRequest {
        private IntegrationFlow flow;
        private String packageId;
        
        public CreateFlowRequest() {}
        
        public CreateFlowRequest(IntegrationFlow flow, String packageId) {
            this.flow = flow;
            this.packageId = packageId;
        }
        
        public IntegrationFlow getFlow() { return flow; }
        public void setFlow(IntegrationFlow flow) { this.flow = flow; }
        
        public String getPackageId() { return packageId; }
        public void setPackageId(String packageId) { this.packageId = packageId; }
    }
    
    /**
     * Request DTO for moving flows between packages.
     */
    public static class MoveFlowRequest {
        private String targetPackageId;
        private String reason;
        
        public MoveFlowRequest() {}
        
        public MoveFlowRequest(String targetPackageId, String reason) {
            this.targetPackageId = targetPackageId;
            this.reason = reason;
        }
        
        public String getTargetPackageId() { return targetPackageId; }
        public void setTargetPackageId(String targetPackageId) { this.targetPackageId = targetPackageId; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
}