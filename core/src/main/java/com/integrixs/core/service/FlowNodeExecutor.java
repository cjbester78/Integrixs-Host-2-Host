package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.shared.model.FlowExecutionStep;
import com.integrixs.shared.model.Adapter;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.core.logging.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for executing different types of flow nodes
 * Handles node-specific execution logic following Single Responsibility Principle
 */
@Service
public class FlowNodeExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowNodeExecutor.class);
    private final EnhancedLogger enhancedLogger = EnhancedLogger.getLogger(FlowNodeExecutor.class);
    
    private final AdapterRepository adapterRepository;
    private final AdapterExecutionService adapterExecutionService;
    private final UtilityExecutionService utilityExecutionService;
    
    @Autowired
    public FlowNodeExecutor(AdapterRepository adapterRepository,
                           AdapterExecutionService adapterExecutionService,
                           UtilityExecutionService utilityExecutionService) {
        this.adapterRepository = adapterRepository;
        this.adapterExecutionService = adapterExecutionService;
        this.utilityExecutionService = utilityExecutionService;
    }
    
    /**
     * Execute a node based on its type
     */
    public Map<String, Object> executeNode(FlowExecutionStep step, Map<String, Object> node, 
                                         Map<String, Object> context) {
        String nodeType = (String) node.get("type");
        
        logger.debug("Executing {} node: {} for step: {}", 
            nodeType, node.get("id"), step.getId());
        
        switch (nodeType.toLowerCase()) {
            case "start":
                return executeStartNode(step, node, context);
            case "end":
                return executeEndNode(step, node, context);
            case "messageend":
                return executeMessageEndNode(step, node, context);
            case "adapter":
                return executeAdapterNode(step, node, context);
            case "utility":
                return executeUtilityNode(step, node, context);
            case "condition":
            case "decision":
                return executeDecisionNode(step, node, context);
            case "parallel":
            case "parallelsplit":
                return executeParallelSplitNode(step, node, context);
            default:
                logger.warn("Unknown node type: {} for node: {}", nodeType, node.get("id"));
                return executeCustomNode(step, node, context);
        }
    }
    
    /**
     * Execute start node
     */
    private Map<String, Object> executeStartNode(FlowExecutionStep step, Map<String, Object> node, 
                                               Map<String, Object> context) {
        logger.debug("Executing start node: {}", node.get("id"));
        
        // Enhanced logging for start node
        enhancedLogger.flowExecutionStep("START", (String) node.get("id"), "Flow execution started");
        
        Map<String, Object> result = new HashMap<>();
        result.put("startTime", System.currentTimeMillis());
        result.put("nodeId", node.get("id"));
        result.put("status", "STARTED");
        
        // Copy initial payload to result
        if (context.containsKey("payload")) {
            result.put("payload", context.get("payload"));
        }
        
        logger.info("Flow execution started at node: {}", node.get("id"));
        return result;
    }
    
    /**
     * Execute end node
     */
    private Map<String, Object> executeEndNode(FlowExecutionStep step, Map<String, Object> node, 
                                             Map<String, Object> context) {
        logger.debug("Executing end node: {}", node.get("id"));
        
        // Enhanced logging for end node
        enhancedLogger.flowExecutionStep("END", (String) node.get("id"), "Flow execution completed");
        
        Map<String, Object> result = new HashMap<>();
        result.put("endTime", System.currentTimeMillis());
        result.put("nodeId", node.get("id"));
        result.put("status", "COMPLETED");
        
        // Calculate execution duration if start time is available
        if (context.containsKey("startTime")) {
            long startTime = ((Number) context.get("startTime")).longValue();
            result.put("executionDuration", System.currentTimeMillis() - startTime);
        }
        
        // Preserve final payload
        result.put("finalPayload", context);
        
        logger.info("Flow execution completed at end node: {}", node.get("id"));
        return result;
    }
    
    /**
     * Execute message end node (for message-based flows)
     */
    private Map<String, Object> executeMessageEndNode(FlowExecutionStep step, Map<String, Object> node, 
                                                     Map<String, Object> context) {
        logger.debug("Executing message end node: {}", node.get("id"));
        
        Map<String, Object> result = new HashMap<>();
        result.put("endTime", System.currentTimeMillis());
        result.put("nodeId", node.get("id"));
        result.put("status", "MESSAGE_COMPLETED");
        
        // Extract message configuration
        Map<String, Object> messageConfig = (Map<String, Object>) node.get("messageConfig");
        if (messageConfig != null) {
            String messageTemplate = (String) messageConfig.get("template");
            Map<String, Object> messageData = (Map<String, Object>) messageConfig.get("data");
            
            // Process message template with context data
            String processedMessage = processMessageTemplate(messageTemplate, context);
            result.put("message", processedMessage);
            result.put("messageData", messageData);
        }
        
        // Enhanced logging for message end
        enhancedLogger.flowExecutionStep("MESSAGE_END", (String) node.get("id"), "Message flow completed");
        
        logger.info("Message flow execution completed at node: {}", node.get("id"));
        return result;
    }
    
    /**
     * Execute adapter node
     */
    private Map<String, Object> executeAdapterNode(FlowExecutionStep step, Map<String, Object> node, 
                                                  Map<String, Object> context) {
        String nodeId = (String) node.get("id");
        String adapterId = (String) node.get("adapterId");
        
        logger.debug("Executing adapter node: {} with adapter: {}", nodeId, adapterId);
        
        if (adapterId == null || adapterId.isEmpty()) {
            throw new RuntimeException("Adapter ID not specified for adapter node: " + nodeId);
        }
        
        try {
            UUID adapterUuid = UUID.fromString(adapterId);
            Optional<Adapter> adapterOpt = adapterRepository.findById(adapterUuid);
            
            if (!adapterOpt.isPresent()) {
                throw new RuntimeException("Adapter not found: " + adapterId);
            }
            
            Adapter adapter = adapterOpt.get();
            
            // Enhanced logging for adapter execution
            enhancedLogger.flowExecutionStep("ADAPTER", nodeId, 
                "Executing " + adapter.getAdapterType() + " adapter: " + adapter.getName());
            
            // Execute adapter through adapter execution service
            Map<String, Object> adapterContext = new HashMap<>(context);
            adapterContext.put("nodeId", nodeId);
            adapterContext.put("stepId", step.getId());
            
            Map<String, Object> adapterResult = adapterExecutionService.executeAdapter(
                adapter, adapterContext, step);
            
            logger.info("Adapter execution completed for node: {} adapter: {}", 
                nodeId, adapter.getName());
            
            return adapterResult;
            
        } catch (Exception e) {
            logger.error("Adapter node execution failed for node: {} - {}", nodeId, e.getMessage(), e);
            throw new RuntimeException("Adapter execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute utility node
     */
    private Map<String, Object> executeUtilityNode(FlowExecutionStep step, Map<String, Object> node, 
                                                  Map<String, Object> context) {
        String nodeId = (String) node.get("id");
        String utilityType = (String) node.get("utilityType");
        Map<String, Object> utilityConfig = (Map<String, Object>) node.get("configuration");
        
        logger.debug("Executing utility node: {} with utility type: {}", nodeId, utilityType);
        
        if (utilityType == null || utilityType.isEmpty()) {
            throw new RuntimeException("Utility type not specified for utility node: " + nodeId);
        }
        
        try {
            // Enhanced logging for utility execution
            enhancedLogger.flowExecutionStep("UTILITY", nodeId, 
                "Executing " + utilityType + " utility");
            
            // Execute utility through utility execution service
            Map<String, Object> utilityContext = new HashMap<>(context);
            utilityContext.put("nodeId", nodeId);
            utilityContext.put("stepId", step.getId());
            utilityContext.put("configuration", utilityConfig);
            
            Map<String, Object> utilityResult = utilityExecutionService.executeUtility(
                utilityType, utilityConfig, utilityContext, step);
            
            logger.info("Utility execution completed for node: {} utility: {}", 
                nodeId, utilityType);
            
            return utilityResult;
            
        } catch (Exception e) {
            logger.error("Utility node execution failed for node: {} - {}", nodeId, e.getMessage(), e);
            throw new RuntimeException("Utility execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute decision/condition node
     */
    private Map<String, Object> executeDecisionNode(FlowExecutionStep step, Map<String, Object> node, 
                                                   Map<String, Object> context) {
        String nodeId = (String) node.get("id");
        Map<String, Object> conditions = (Map<String, Object>) node.get("conditions");
        
        logger.debug("Executing decision node: {}", nodeId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeId);
        
        if (conditions == null || conditions.isEmpty()) {
            logger.warn("No conditions defined for decision node: {}", nodeId);
            result.put("decision", "default");
            result.put("path", "default");
            return result;
        }
        
        // Enhanced logging for decision execution
        enhancedLogger.flowExecutionStep("DECISION", nodeId, "Evaluating conditions");
        
        // Evaluate conditions
        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            String conditionName = condition.getKey();
            Map<String, Object> conditionConfig = (Map<String, Object>) condition.getValue();
            
            if (evaluateCondition(conditionConfig, context)) {
                result.put("decision", conditionName);
                result.put("path", conditionName);
                result.put("conditionMet", true);
                
                logger.info("Decision node {} evaluated to: {}", nodeId, conditionName);
                return result;
            }
        }
        
        // No condition met, use default path
        result.put("decision", "default");
        result.put("path", "default");
        result.put("conditionMet", false);
        
        logger.info("Decision node {} evaluated to default path", nodeId);
        return result;
    }
    
    /**
     * Execute parallel split node
     */
    private Map<String, Object> executeParallelSplitNode(FlowExecutionStep step, Map<String, Object> node, 
                                                        Map<String, Object> context) {
        String nodeId = (String) node.get("id");
        List<String> parallelPaths = (List<String>) node.get("parallelPaths");
        
        logger.debug("Executing parallel split node: {} with {} paths", 
            nodeId, parallelPaths != null ? parallelPaths.size() : 0);
        
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeId);
        result.put("parallelPaths", parallelPaths != null ? parallelPaths : new ArrayList<>());
        
        // Enhanced logging for parallel execution
        enhancedLogger.flowExecutionStep("PARALLEL", nodeId, "Splitting execution into parallel paths");
        
        if (parallelPaths != null && !parallelPaths.isEmpty()) {
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            
            for (String path : parallelPaths) {
                // Create separate context for each parallel path
                Map<String, Object> pathContext = new HashMap<>(context);
                pathContext.put("parallelPath", path);
                
                // For now, we'll prepare the context for parallel execution
                // The actual parallel execution would be handled by the step executor
                result.put("parallelPath_" + path + "_context", pathContext);
            }
        }
        
        logger.info("Parallel split node {} prepared {} paths for execution", 
            nodeId, parallelPaths != null ? parallelPaths.size() : 0);
        
        return result;
    }
    
    /**
     * Execute custom node type
     */
    private Map<String, Object> executeCustomNode(FlowExecutionStep step, Map<String, Object> node, 
                                                 Map<String, Object> context) {
        String nodeId = (String) node.get("id");
        String nodeType = (String) node.get("type");
        
        logger.debug("Executing custom node: {} type: {}", nodeId, nodeType);
        
        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeId);
        result.put("nodeType", nodeType);
        result.put("status", "COMPLETED");
        result.put("message", "Custom node executed with default behavior");
        
        logger.info("Custom node {} (type: {}) executed with default behavior", nodeId, nodeType);
        return result;
    }
    
    // Helper methods
    
    /**
     * Process message template with context data
     */
    private String processMessageTemplate(String template, Map<String, Object> context) {
        if (template == null) {
            return "";
        }
        
        String processed = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (processed.contains(placeholder)) {
                processed = processed.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        
        return processed;
    }
    
    /**
     * Evaluate a condition against context
     */
    private boolean evaluateCondition(Map<String, Object> conditionConfig, Map<String, Object> context) {
        if (conditionConfig == null) {
            return false;
        }
        
        String field = (String) conditionConfig.get("field");
        String operator = (String) conditionConfig.get("operator");
        Object value = conditionConfig.get("value");
        
        if (field == null || operator == null) {
            return false;
        }
        
        Object contextValue = context.get(field);
        
        switch (operator.toLowerCase()) {
            case "equals":
                return Objects.equals(contextValue, value);
            case "not_equals":
                return !Objects.equals(contextValue, value);
            case "contains":
                return contextValue != null && contextValue.toString().contains(value.toString());
            case "greater_than":
                return compareNumbers(contextValue, value) > 0;
            case "less_than":
                return compareNumbers(contextValue, value) < 0;
            case "exists":
                return contextValue != null;
            case "not_exists":
                return contextValue == null;
            default:
                logger.warn("Unknown condition operator: {}", operator);
                return false;
        }
    }
    
    /**
     * Compare two numbers
     */
    private int compareNumbers(Object a, Object b) {
        if (a == null || b == null) {
            return 0;
        }
        
        try {
            double aVal = Double.parseDouble(a.toString());
            double bVal = Double.parseDouble(b.toString());
            return Double.compare(aVal, bVal);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}