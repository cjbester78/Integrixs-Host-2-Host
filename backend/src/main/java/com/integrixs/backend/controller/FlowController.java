package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.core.service.FlowDefinitionService;
import com.integrixs.core.service.FlowExecutionService;
import com.integrixs.core.service.FlowMonitoringService;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/flows")
public class FlowController {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowController.class);
    
    private final FlowDefinitionService flowDefinitionService;
    private final FlowExecutionService flowExecutionService;
    private final FlowMonitoringService flowMonitoringService;
    
    @Autowired
    public FlowController(FlowDefinitionService flowDefinitionService,
                         FlowExecutionService flowExecutionService,
                         FlowMonitoringService flowMonitoringService) {
        this.flowDefinitionService = flowDefinitionService;
        this.flowExecutionService = flowExecutionService;
        this.flowMonitoringService = flowMonitoringService;
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllFlows() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting integration flows", currentUser);
        
        try {
            List<IntegrationFlow> flows = flowDefinitionService.getAllFlows();
            
            // Enrich flows with deployment status
            List<Map<String, Object>> enrichedFlows = new ArrayList<>();
            for (IntegrationFlow flow : flows) {
                Map<String, Object> flowData = new HashMap<>();
                
                // Copy all IntegrationFlow properties
                flowData.put("id", flow.getId());
                flowData.put("name", flow.getName());
                flowData.put("description", flow.getDescription());
                flowData.put("bankName", flow.getBankName());
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
                    flowData.put("deployed", deploymentStatus.get("deployed"));
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
    public ResponseEntity<ApiResponse<IntegrationFlow>> createFlow(@RequestBody IntegrationFlow flowData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} creating integration flow: {}", currentUser, flowData.getName());
        
        try {
            UUID createdBy = UUID.fromString(currentUser);
            IntegrationFlow createdFlow = flowDefinitionService.createFlow(flowData, createdBy);
            
            logger.info("Successfully created integration flow for user {}", currentUser);
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
            @RequestBody IntegrationFlow flowData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
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
    
    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportFlow(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} exporting flow: {}", currentUser, id);
        
        try {
            UUID flowId = UUID.fromString(id);
            Map<String, Object> exportData = flowDefinitionService.exportFlow(flowId);
            
            return ResponseEntity.ok(ApiResponse.success(
                "Flow exported successfully", 
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
    
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<IntegrationFlow>> importFlow(@RequestBody Map<String, Object> importData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} importing flow", currentUser);
        
        try {
            UUID importedBy = UUID.fromString(currentUser);
            IntegrationFlow importedFlow = flowDefinitionService.importFlow(importData, importedBy);
            
            return ResponseEntity.status(201)
                .body(ApiResponse.success(
                    "Flow imported successfully", 
                    importedFlow
                ));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid import data from user {}: {}", currentUser, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error importing flow for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to import flow"));
        }
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
}