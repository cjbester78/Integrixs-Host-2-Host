package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/end-to-end-flows")
public class EndToEndFlowController {
    
    private static final Logger logger = LoggerFactory.getLogger(EndToEndFlowController.class);
    
    // In-memory store for demo purposes - in production this would be a database
    private final Map<String, Map<String, Object>> endToEndFlows = new HashMap<>();
    private final Map<String, Map<String, Object>> flowConfigs = new HashMap<>();
    private Map<String, Object> defaultConfigTemplate = new HashMap<>();
    
    public EndToEndFlowController() {
        // Initialize empty - no sample data
        initializeDefaultConfigurations();
    }
    
    private void initializeSampleFlows() {
        // Sample end-to-end flow 1
        Map<String, Object> flow1 = new HashMap<>();
        flow1.put("id", "e2e-flow-1");
        flow1.put("name", "FNB Payment Processing");
        flow1.put("description", "Process payment files from FNB SFTP and send confirmations");
        flow1.put("senderAdapter", "adapter-fnb-sender");
        flow1.put("processingFlow", "flow-validate");
        flow1.put("receiverAdapter", "adapter-email-notification");
        flow1.put("enabled", true);
        flow1.put("status", "ACTIVE");
        flow1.put("lastExecution", "2024-12-01T14:30:00");
        flow1.put("successCount", 145);
        flow1.put("errorCount", 2);
        flow1.put("createdAt", LocalDateTime.now().minusDays(5).toString());
        flow1.put("updatedAt", LocalDateTime.now().minusHours(2).toString());
        endToEndFlows.put("e2e-flow-1", flow1);
        
        // Sample end-to-end flow 2
        Map<String, Object> flow2 = new HashMap<>();
        flow2.put("id", "e2e-flow-2");
        flow2.put("name", "Stanbic File Archive");
        flow2.put("description", "Archive processed files from Stanbic to backup location");
        flow2.put("senderAdapter", "adapter-stanbic-sender");
        flow2.put("processingFlow", "flow-compress");
        flow2.put("receiverAdapter", "adapter-file-archive");
        flow2.put("enabled", true);
        flow2.put("status", "ACTIVE");
        flow2.put("lastExecution", "2024-12-01T13:45:00");
        flow2.put("successCount", 89);
        flow2.put("errorCount", 0);
        flow2.put("createdAt", LocalDateTime.now().minusDays(3).toString());
        flow2.put("updatedAt", LocalDateTime.now().minusHours(1).toString());
        endToEndFlows.put("e2e-flow-2", flow2);
        
        // Initialize default configurations
        initializeDefaultConfigurations();
    }
    
    private void initializeDefaultConfigurations() {
        Map<String, Object> defaultConfig = new HashMap<>();
        Map<String, Object> scheduling = new HashMap<>();
        scheduling.put("enabled", true);
        scheduling.put("cronExpression", "0 */15 * * * *");
        scheduling.put("timezone", "UTC");
        
        Map<String, Object> retry = new HashMap<>();
        retry.put("enabled", true);
        retry.put("maxAttempts", 3);
        retry.put("backoffStrategy", "exponential");
        retry.put("delaySeconds", 60);
        
        Map<String, Object> monitoring = new HashMap<>();
        monitoring.put("alertOnFailure", true);
        monitoring.put("alertOnSuccess", false);
        Map<String, Object> thresholds = new HashMap<>();
        thresholds.put("errorRatePercent", 10);
        thresholds.put("processingTimeMs", 300000);
        monitoring.put("thresholds", thresholds);
        
        defaultConfig.put("scheduling", scheduling);
        defaultConfig.put("retry", retry);
        defaultConfig.put("monitoring", monitoring);
        
        // Store as template for new flows
        this.defaultConfigTemplate = defaultConfig;
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllFlows() {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting end-to-end flows", currentUser);
        
        try {
            List<Map<String, Object>> flows = new ArrayList<>(endToEndFlows.values());
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "End-to-end flows retrieved successfully", 
                flows
            ));
        } catch (Exception e) {
            logger.error("Error retrieving end-to-end flows for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve end-to-end flows", null));
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR') or hasAuthority('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFlowById(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} requesting end-to-end flow: {}", currentUser, id);
        
        try {
            Map<String, Object> flow = endToEndFlows.get(id);
            if (flow != null) {
                return ResponseEntity.ok(new ApiResponse<>(
                    true, 
                    "End-to-end flow retrieved successfully", 
                    flow
                ));
            } else {
                return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "End-to-end flow not found", null));
            }
        } catch (Exception e) {
            logger.error("Error retrieving end-to-end flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to retrieve end-to-end flow", null));
        }
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createFlow(@RequestBody Map<String, Object> flowData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} creating end-to-end flow: {}", currentUser, flowData.get("name"));
        
        try {
            String flowId = UUID.randomUUID().toString();
            Map<String, Object> createdFlow = new HashMap<>(flowData);
            createdFlow.put("id", flowId);
            createdFlow.put("status", "ACTIVE");
            createdFlow.put("successCount", 0);
            createdFlow.put("errorCount", 0);
            createdFlow.put("createdAt", LocalDateTime.now().toString());
            createdFlow.put("updatedAt", LocalDateTime.now().toString());
            createdFlow.put("createdBy", currentUser);
            createdFlow.put("updatedBy", currentUser);
            
            endToEndFlows.put(flowId, createdFlow);
            
            // Initialize default configuration for new flow
            flowConfigs.put(flowId, new HashMap<>(defaultConfigTemplate));
            
            logger.info("Successfully created end-to-end flow {} for user {}", flowId, currentUser);
            return ResponseEntity.status(201)
                .body(new ApiResponse<>(
                    true, 
                    "End-to-end flow created successfully", 
                    createdFlow
                ));
        } catch (Exception e) {
            logger.error("Error creating end-to-end flow for user {}: {}", currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to create end-to-end flow", null));
        }
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateFlow(
            @PathVariable String id, 
            @RequestBody Map<String, Object> flowData) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} updating end-to-end flow: {}", currentUser, id);
        
        try {
            Map<String, Object> existingFlow = endToEndFlows.get(id);
            if (existingFlow == null) {
                return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "End-to-end flow not found", null));
            }
            
            Map<String, Object> updatedFlow = new HashMap<>(existingFlow);
            updatedFlow.putAll(flowData);
            updatedFlow.put("id", id);
            updatedFlow.put("updatedAt", LocalDateTime.now().toString());
            updatedFlow.put("updatedBy", currentUser);
            
            endToEndFlows.put(id, updatedFlow);
            
            logger.info("Successfully updated end-to-end flow {} for user {}", id, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "End-to-end flow updated successfully", 
                updatedFlow
            ));
        } catch (Exception e) {
            logger.error("Error updating end-to-end flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to update end-to-end flow", null));
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> deleteFlow(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} deleting end-to-end flow: {}", currentUser, id);
        
        try {
            if (endToEndFlows.remove(id) != null) {
                flowConfigs.remove(id);
                logger.info("Successfully deleted end-to-end flow {} for user {}", id, currentUser);
                return ResponseEntity.ok(new ApiResponse<>(
                    true, 
                    "End-to-end flow deleted successfully", 
                    null
                ));
            } else {
                return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "End-to-end flow not found", null));
            }
        } catch (Exception e) {
            logger.error("Error deleting end-to-end flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to delete end-to-end flow", null));
        }
    }
    
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeFlow(@PathVariable String id) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} executing end-to-end flow: {}", currentUser, id);
        
        try {
            Map<String, Object> flow = endToEndFlows.get(id);
            if (flow == null) {
                return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "End-to-end flow not found", null));
            }
            
            // Update execution statistics
            flow.put("lastExecution", LocalDateTime.now().toString());
            int successCount = (Integer) flow.getOrDefault("successCount", 0);
            flow.put("successCount", successCount + 1);
            
            Map<String, Object> executionResult = new HashMap<>();
            executionResult.put("executionId", UUID.randomUUID().toString());
            executionResult.put("flowId", id);
            executionResult.put("flowName", flow.get("name"));
            executionResult.put("status", "SUCCESS");
            executionResult.put("startedAt", LocalDateTime.now().toString());
            executionResult.put("triggeredBy", currentUser);
            executionResult.put("message", "Flow executed successfully");
            
            logger.info("Successfully executed end-to-end flow {} for user {}", id, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "End-to-end flow execution completed successfully", 
                executionResult
            ));
        } catch (Exception e) {
            logger.error("Error executing end-to-end flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to execute end-to-end flow", null));
        }
    }
    
    @PutMapping("/{id}/config")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> configureFlow(
            @PathVariable String id, 
            @RequestBody Map<String, Object> config) {
        String currentUser = SecurityContextHelper.getCurrentUserIdAsString();
        logger.info("User {} configuring end-to-end flow: {}", currentUser, id);
        
        try {
            if (!endToEndFlows.containsKey(id)) {
                return ResponseEntity.status(404)
                    .body(new ApiResponse<>(false, "End-to-end flow not found", null));
            }
            
            flowConfigs.put(id, new HashMap<>(config));
            
            Map<String, Object> result = new HashMap<>();
            result.put("flowId", id);
            result.put("configUpdatedAt", LocalDateTime.now().toString());
            result.put("configUpdatedBy", currentUser);
            
            logger.info("Successfully configured end-to-end flow {} for user {}", id, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(
                true, 
                "End-to-end flow configuration updated successfully", 
                result
            ));
        } catch (Exception e) {
            logger.error("Error configuring end-to-end flow {} for user {}: {}", id, currentUser, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(false, "Failed to configure end-to-end flow", null));
        }
    }
}