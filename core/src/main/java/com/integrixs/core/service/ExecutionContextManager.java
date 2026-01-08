package com.integrixs.core.service;

import com.integrixs.core.logging.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

/**
 * Service for managing flow execution context state.
 * Handles context creation, restoration, and isolation for parallel execution.
 * Follows OOP principles with immutable context snapshots and proper encapsulation.
 */
@Service
public class ExecutionContextManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionContextManager.class);
    
    /**
     * Immutable execution context snapshot
     */
    public static class ExecutionContextSnapshot {
        private final Map<String, Object> contextData;
        private final String correlationId;
        private final String messageId;
        private final String executionId;
        private final String flowId;
        private final String flowName;
        private final LocalDateTime snapshotTime;
        
        public ExecutionContextSnapshot(Map<String, Object> contextData, String correlationId,
                                      String messageId, String executionId, String flowId, 
                                      String flowName, LocalDateTime snapshotTime) {
            this.contextData = new HashMap<>(contextData);
            this.correlationId = correlationId;
            this.messageId = messageId;
            this.executionId = executionId;
            this.flowId = flowId;
            this.flowName = flowName;
            this.snapshotTime = snapshotTime;
        }
        
        public Map<String, Object> getContextData() { 
            return new HashMap<>(contextData); 
        }
        
        public String getCorrelationId() { return correlationId; }
        public String getMessageId() { return messageId; }
        public String getExecutionId() { return executionId; }
        public String getFlowId() { return flowId; }
        public String getFlowName() { return flowName; }
        public LocalDateTime getSnapshotTime() { return snapshotTime; }
    }
    
    /**
     * Create initial execution context from execution and flow data
     */
    public Map<String, Object> createExecutionContext(Map<String, Object> executionPayload, 
                                                     Map<String, Object> executionContext) {
        logger.debug("Creating initial execution context");
        
        Map<String, Object> context = new HashMap<>();
        
        // Add payload data
        if (executionPayload != null) {
            context.putAll(executionPayload);
        }
        
        // Add execution context data
        if (executionContext != null) {
            context.putAll(executionContext);
        }
        
        return context;
    }
    
    /**
     * Restore correlation context for async execution
     */
    public void restoreCorrelationContext(Map<String, Object> executionContext) {
        if (executionContext == null) {
            logger.warn("No execution context provided for correlation context restoration");
            return;
        }
        
        String correlationId = (String) executionContext.get("correlationId");
        String messageId = (String) executionContext.get("messageId");
        String executionId = (String) executionContext.get("executionId");
        String flowId = (String) executionContext.get("flowId");
        String flowName = (String) executionContext.get("flowName");
        
        // Restore correlation context
        if (correlationId != null) {
            CorrelationContext.setCorrelationId(correlationId);
        } else {
            CorrelationContext.setCorrelationId(CorrelationContext.generateCorrelationId());
        }
        
        if (messageId != null) {
            CorrelationContext.setMessageId(messageId);
        } else {
            String newMessageId = CorrelationContext.generateMessageId();
            CorrelationContext.setMessageId(newMessageId);
        }
        
        if (executionId != null) {
            CorrelationContext.setExecutionId(executionId);
        }
        
        if (flowId != null) {
            CorrelationContext.setFlowId(flowId);
        }
        
        if (flowName != null) {
            CorrelationContext.setFlowName(flowName);
        }
        
        logger.debug("Restored correlation context: correlationId={}, messageId={}", 
                    correlationId, messageId);
    }
    
    /**
     * Create context snapshot for monitoring and parallel execution
     */
    public ExecutionContextSnapshot createContextSnapshot(Map<String, Object> context) {
        return new ExecutionContextSnapshot(
            sanitizeContextForSnapshot(context),
            CorrelationContext.getCorrelationId(),
            CorrelationContext.getMessageId(),
            CorrelationContext.getExecutionId(),
            CorrelationContext.getFlowId(),
            CorrelationContext.getFlowName(),
            LocalDateTime.now()
        );
    }
    
    /**
     * Create isolated context copy for parallel execution
     */
    public Map<String, Object> createIsolatedContext(Map<String, Object> sourceContext) {
        return new HashMap<>(sourceContext);
    }
    
    /**
     * Update context with step results
     */
    public void updateContextWithStepResults(Map<String, Object> context, 
                                           Map<String, Object> stepResults) {
        if (stepResults != null) {
            context.putAll(stepResults);
        }
    }
    
    /**
     * Extract trigger data from context
     */
    public Optional<Map<String, Object>> extractTriggerData(Map<String, Object> context) {
        Object triggerData = context.get("triggerData");
        if (triggerData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> triggerMap = (Map<String, Object>) triggerData;
            return Optional.of(triggerMap);
        }
        return Optional.empty();
    }
    
    /**
     * Extract files from context for processing
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractFilesToProcess(Map<String, Object> context) {
        List<Map<String, Object>> files = new ArrayList<>();
        
        // Check multiple possible file keys
        String[] fileKeys = {"filesToProcess", "senderFiles", "foundFiles", "senderProcessedFiles"};
        
        for (String key : fileKeys) {
            Object fileData = context.get(key);
            if (fileData instanceof List) {
                List<Map<String, Object>> fileList = (List<Map<String, Object>>) fileData;
                files.addAll(fileList);
            }
        }
        
        return files;
    }
    
    /**
     * Add files to context for downstream processing
     */
    public void addFilesToContext(Map<String, Object> context, String key, 
                                 List<Map<String, Object>> files) {
        context.put(key, new ArrayList<>(files));
    }
    
    /**
     * Get deployment context information
     */
    public Optional<UUID> getDeploymentId(Map<String, Object> context) {
        Object deploymentId = context.get("deploymentId");
        if (deploymentId instanceof UUID) {
            return Optional.of((UUID) deploymentId);
        }
        return Optional.empty();
    }
    
    /**
     * Sanitize context for snapshot to avoid memory issues
     */
    private Map<String, Object> sanitizeContextForSnapshot(Map<String, Object> context) {
        Map<String, Object> sanitized = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Handle file lists specially to avoid storing large binary data
            if (key.contains("files") || key.contains("Files")) {
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> files = (List<Map<String, Object>>) value;
                    List<Map<String, Object>> fileMetadata = new ArrayList<>();
                    
                    for (Map<String, Object> file : files) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("fileName", file.getOrDefault("fileName", "unknown"));
                        metadata.put("filePath", file.getOrDefault("filePath", "unknown"));
                        metadata.put("fileSize", file.getOrDefault("fileSize", 0));
                        metadata.put("lastModified", file.getOrDefault("lastModified", "unknown"));
                        fileMetadata.add(metadata);
                    }
                    sanitized.put(key, fileMetadata);
                } else {
                    sanitized.put(key, value);
                }
            } else {
                // Include other context data as-is
                sanitized.put(key, value);
            }
        }
        
        return sanitized;
    }
    
    /**
     * Validate context has required data
     */
    public boolean validateContextForExecution(Map<String, Object> context) {
        // Basic validation - can be extended as needed
        return context != null;
    }
    
    /**
     * Log context state for debugging
     */
    public void logContextState(Map<String, Object> context, String phase) {
        if (logger.isDebugEnabled()) {
            int fileCount = extractFilesToProcess(context).size();
            logger.debug("Execution context at {}: {} keys, {} files", 
                        phase, context.size(), fileCount);
        }
    }
}