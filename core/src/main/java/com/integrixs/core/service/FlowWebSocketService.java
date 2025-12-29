package com.integrixs.core.service;

import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for sending real-time flow execution updates via WebSocket
 * Broadcasts flow status changes and step progress to connected clients
 */
@Service
public class FlowWebSocketService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowWebSocketService.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    public FlowWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Send flow execution status update
     */
    public void sendFlowExecutionUpdate(FlowExecution execution) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("type", "FLOW_EXECUTION_UPDATE");
            update.put("executionId", execution.getId());
            update.put("flowId", execution.getFlowId());
            update.put("flowName", execution.getFlowName());
            update.put("status", execution.getExecutionStatus().name());
            update.put("progress", execution.calculateProgress());
            update.put("startedAt", execution.getStartedAt());
            update.put("completedAt", execution.getCompletedAt());
            update.put("durationMs", execution.getDurationMs());
            update.put("triggerType", execution.getTriggerType().name());
            update.put("triggeredBy", execution.getTriggeredBy());
            update.put("correlationId", execution.getCorrelationId());
            update.put("retryAttempt", execution.getRetryAttempt());
            
            if (execution.getErrorMessage() != null) {
                update.put("errorMessage", execution.getErrorMessage());
            }
            
            // Send to all subscribers of flow executions
            messagingTemplate.convertAndSend("/topic/flow-executions", update);
            
            // Send to specific flow subscribers
            messagingTemplate.convertAndSend("/topic/flow/" + execution.getFlowId() + "/executions", update);
            
            // Send to specific execution subscribers
            messagingTemplate.convertAndSend("/topic/execution/" + execution.getId(), update);
            
            logger.debug("Sent flow execution update via WebSocket: {}", execution.getId());
            
        } catch (Exception e) {
            logger.error("Failed to send flow execution update via WebSocket: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send flow step execution update
     */
    public void sendFlowStepUpdate(FlowExecutionStep step) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("type", "FLOW_STEP_UPDATE");
            update.put("stepId", step.getId());
            update.put("executionId", step.getExecutionId());
            update.put("stepName", step.getStepName());
            update.put("stepType", step.getStepType().name());
            update.put("status", step.getStepStatus().name());
            update.put("stepOrder", step.getStepOrder());
            update.put("startedAt", step.getStartedAt());
            update.put("completedAt", step.getCompletedAt());
            update.put("durationMs", step.getDurationMs());
            update.put("progress", step.getProgress());
            
            if (step.getErrorMessage() != null) {
                update.put("errorMessage", step.getErrorMessage());
            }
            
            if (step.getOutputData() != null && !step.getOutputData().isEmpty()) {
                update.put("outputSummary", createOutputSummary(step.getOutputData()));
            }
            
            // Send to specific execution subscribers
            messagingTemplate.convertAndSend("/topic/execution/" + step.getExecutionId() + "/steps", update);
            
            // Send to all flow step subscribers
            messagingTemplate.convertAndSend("/topic/flow-steps", update);
            
            logger.debug("Sent flow step update via WebSocket: {} for execution: {}", 
                        step.getId(), step.getExecutionId());
            
        } catch (Exception e) {
            logger.error("Failed to send flow step update via WebSocket: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send flow validation result
     */
    public void sendFlowValidationResult(UUID flowId, Map<String, Object> validationResult) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("type", "FLOW_VALIDATION_RESULT");
            update.put("flowId", flowId);
            update.put("validation", validationResult);
            update.put("timestamp", System.currentTimeMillis());
            
            // Send to specific flow subscribers
            messagingTemplate.convertAndSend("/topic/flow/" + flowId + "/validation", update);
            
            logger.debug("Sent flow validation result via WebSocket: {}", flowId);
            
        } catch (Exception e) {
            logger.error("Failed to send flow validation result via WebSocket: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send flow definition update notification
     */
    public void sendFlowDefinitionUpdate(UUID flowId, String flowName, String updateType, UUID updatedBy) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("type", "FLOW_DEFINITION_UPDATE");
            update.put("flowId", flowId);
            update.put("flowName", flowName);
            update.put("updateType", updateType); // CREATED, UPDATED, DELETED, ENABLED, DISABLED
            update.put("updatedBy", updatedBy);
            update.put("timestamp", System.currentTimeMillis());
            
            // Send to all flow definition subscribers
            messagingTemplate.convertAndSend("/topic/flow-definitions", update);
            
            // Send to specific flow subscribers
            messagingTemplate.convertAndSend("/topic/flow/" + flowId + "/definition", update);
            
            logger.debug("Sent flow definition update via WebSocket: {} - {}", flowId, updateType);
            
        } catch (Exception e) {
            logger.error("Failed to send flow definition update via WebSocket: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send system health update
     */
    public void sendSystemHealthUpdate(Map<String, Object> healthData) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("type", "SYSTEM_HEALTH_UPDATE");
            update.put("health", healthData);
            update.put("timestamp", System.currentTimeMillis());
            
            // Send to all system health subscribers
            messagingTemplate.convertAndSend("/topic/system-health", update);
            
            logger.debug("Sent system health update via WebSocket");
            
        } catch (Exception e) {
            logger.error("Failed to send system health update via WebSocket: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send message to specific user
     */
    public void sendToUser(UUID userId, String destination, Object message) {
        try {
            messagingTemplate.convertAndSendToUser(
                userId.toString(), 
                destination, 
                message
            );
            
            logger.debug("Sent message to user {} at destination: {}", userId, destination);
            
        } catch (Exception e) {
            logger.error("Failed to send message to user {} at {}: {}", userId, destination, e.getMessage(), e);
        }
    }
    
    /**
     * Create a summary of output data for WebSocket transmission
     */
    private Map<String, Object> createOutputSummary(Map<String, Object> outputData) {
        Map<String, Object> summary = new HashMap<>();
        
        // Include key output metrics
        summary.put("keys", outputData.keySet().size());
        
        if (outputData.containsKey("filesProcessed")) {
            summary.put("filesProcessed", outputData.get("filesProcessed"));
        }
        
        if (outputData.containsKey("bytesProcessed")) {
            summary.put("bytesProcessed", outputData.get("bytesProcessed"));
        }
        
        if (outputData.containsKey("recordsProcessed")) {
            summary.put("recordsProcessed", outputData.get("recordsProcessed"));
        }
        
        if (outputData.containsKey("status")) {
            summary.put("status", outputData.get("status"));
        }
        
        if (outputData.containsKey("message")) {
            summary.put("message", outputData.get("message"));
        }
        
        return summary;
    }
}