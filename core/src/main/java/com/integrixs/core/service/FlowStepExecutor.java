package com.integrixs.core.service;

import com.integrixs.core.repository.FlowExecutionStepRepository;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.FlowExecutionStep;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.core.logging.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for executing individual flow steps
 * Handles step-by-step processing logic following Single Responsibility Principle
 */
@Service
public class FlowStepExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowStepExecutor.class);
    private final EnhancedLogger enhancedLogger = EnhancedLogger.getLogger(FlowStepExecutor.class);
    
    private final FlowExecutionStepRepository stepRepository;
    private final FlowNodeExecutor nodeExecutor;
    
    @Autowired
    public FlowStepExecutor(FlowExecutionStepRepository stepRepository,
                           FlowNodeExecutor nodeExecutor) {
        this.stepRepository = stepRepository;
        this.nodeExecutor = nodeExecutor;
    }
    
    /**
     * Execute all steps in a flow
     */
    public void executeFlowSteps(FlowExecution execution, IntegrationFlow flow) {
        Objects.requireNonNull(execution, "Execution cannot be null");
        Objects.requireNonNull(flow, "Flow cannot be null");
        
        logger.info("Starting step execution for flow: {} execution: {}", 
            flow.getName(), execution.getId());
        
        try {
            Map<String, Object> flowDefinition = flow.getFlowDefinition();
            if (flowDefinition == null || !flowDefinition.containsKey("nodes")) {
                throw new RuntimeException("Flow definition is missing or invalid");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) flowDefinition.get("nodes");
            
            // Find start node
            Map<String, Object> startNode = findNodeByType(nodes, "start");
            if (startNode == null) {
                throw new RuntimeException("Start node not found in flow definition");
            }
            
            // Begin execution with start node
            Map<String, Object> context = new HashMap<>(execution.getPayload());
            context.put("executionId", execution.getId());
            context.put("flowId", execution.getFlowId());
            context.put("triggeredBy", execution.getTriggeredBy());
            
            executeNode(execution, startNode, context, nodes);
            
            logger.info("Completed step execution for flow: {} execution: {}", 
                flow.getName(), execution.getId());
                
        } catch (Exception e) {
            logger.error("Failed to execute steps for flow: {} execution: {} - {}", 
                flow.getName(), execution.getId(), e.getMessage(), e);
            throw new RuntimeException("Flow step execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute a single node in the flow
     */
    public void executeNode(FlowExecution execution, Map<String, Object> node, 
                           Map<String, Object> context, List<Map<String, Object>> allNodes) {
        
        String nodeId = (String) node.get("id");
        String nodeType = (String) node.get("type");
        
        logger.debug("Executing node: {} type: {} for execution: {}", 
            nodeId, nodeType, execution.getId());
        
        // Create step record
        FlowExecutionStep step = new FlowExecutionStep();
        step.setExecutionId(execution.getId());
        step.setStepType(getStepType(nodeType));
        step.setStepId(nodeId);
        step.setStepName((String) node.getOrDefault("name", nodeType + "_" + nodeId));
        step.setStepOrder(getNextStepOrder(execution.getId()));
        step.setStepStatus(FlowExecutionStep.StepStatus.RUNNING);
        step.setStartedAt(LocalDateTime.now());
        step.setInputData(new HashMap<>(context));
        step.setCorrelationId(execution.getCorrelationId() != null ? execution.getCorrelationId() : UUID.randomUUID());

        UUID stepId = stepRepository.save(step);
        step.setId(stepId);
        
        try {
            // Execute the node using NodeExecutor
            Map<String, Object> result = nodeExecutor.executeNode(step, node, context);
            
            // Update step with results
            step.setStepStatus(FlowExecutionStep.StepStatus.COMPLETED);
            step.setCompletedAt(LocalDateTime.now());
            step.setOutputData(result);
            step.setDurationMs(java.time.Duration.between(step.getStartedAt(), step.getCompletedAt()).toMillis());
            stepRepository.update(step);
            
            // Update context with results
            if (result != null) {
                context.putAll(result);
            }
            
            // Continue to next nodes if this isn't an end node
            if (!"end".equals(nodeType) && !"messageEnd".equals(nodeType)) {
                executeNextNodes(execution, nodeId, context, allNodes);
            }
            
        } catch (Exception e) {
            logger.error("Node execution failed for node: {} execution: {} - {}", 
                nodeId, execution.getId(), e.getMessage(), e);
            
            // Update step with error
            step.setStepStatus(FlowExecutionStep.StepStatus.FAILED);
            step.setCompletedAt(LocalDateTime.now());
            step.setErrorMessage(e.getMessage());
            step.setDurationMs(java.time.Duration.between(step.getStartedAt(), step.getCompletedAt()).toMillis());
            stepRepository.update(step);
            
            throw new RuntimeException("Node execution failed: " + e.getMessage(), e);
        } finally {
            // Step tracking handled by FlowExecutionStep entity
        }
    }
    
    /**
     * Execute next nodes in the flow based on connections
     */
    public void executeNextNodes(FlowExecution execution, String currentNodeId, 
                                Map<String, Object> context, List<Map<String, Object>> allNodes) {
        
        // Look for edges/connections from current node
        Map<String, Object> flowDefinition = (Map<String, Object>) context.get("flowDefinition");
        if (flowDefinition != null && flowDefinition.containsKey("edges")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edges = (List<Map<String, Object>>) flowDefinition.get("edges");
            
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                if (currentNodeId.equals(source)) {
                    String target = (String) edge.get("target");
                    Map<String, Object> nextNode = findNodeById(allNodes, target);
                    if (nextNode != null) {
                        executeNode(execution, nextNode, context, allNodes);
                    }
                }
            }
        } else {
            // Fallback: look for nodes that reference this node as parent
            for (Map<String, Object> node : allNodes) {
                Object parentId = node.get("parentId");
                if (currentNodeId.equals(parentId)) {
                    executeNode(execution, node, context, allNodes);
                }
            }
        }
    }
    
    /**
     * Get step type from node type
     */
    private FlowExecutionStep.StepType getStepType(String nodeType) {
        switch (nodeType.toLowerCase()) {
            case "start": 
            case "adapter": 
            case "sender": return FlowExecutionStep.StepType.ADAPTER_SENDER;
            case "end": 
            case "messageend": 
            case "receiver": return FlowExecutionStep.StepType.ADAPTER_RECEIVER;
            case "utility": return FlowExecutionStep.StepType.UTILITY;
            case "condition": 
            case "decision": return FlowExecutionStep.StepType.DECISION;
            case "parallel": 
            case "parallelsplit": return FlowExecutionStep.StepType.SPLIT;
            case "wait": return FlowExecutionStep.StepType.WAIT;
            case "notification": return FlowExecutionStep.StepType.NOTIFICATION;
            default: return FlowExecutionStep.StepType.UTILITY; // Default to utility for unknown types
        }
    }
    
    /**
     * Find node by type
     */
    private Map<String, Object> findNodeByType(List<Map<String, Object>> nodes, String type) {
        return nodes.stream()
            .filter(node -> type.equals(node.get("type")))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Find node by ID
     */
    private Map<String, Object> findNodeById(List<Map<String, Object>> nodes, String nodeId) {
        return nodes.stream()
            .filter(node -> nodeId.equals(node.get("id")))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get next step order for execution
     */
    private int getNextStepOrder(UUID executionId) {
        List<FlowExecutionStep> existingSteps = stepRepository.findByExecutionId(executionId);
        return existingSteps.size() + 1;
    }
}