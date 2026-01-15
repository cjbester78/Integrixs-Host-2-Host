package com.integrixs.core.service;

import com.integrixs.core.repository.FlowExecutionStepRepository;
import com.integrixs.core.service.execution.StepExecutionCommand;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for coordinating individual step execution using the command pattern.
 * Handles step creation, execution delegation, and result processing.
 * Follows OOP principles with proper separation of concerns and dependency injection.
 */
@Service
public class StepExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(StepExecutionService.class);
    
    private final FlowExecutionStepRepository stepRepository;
    private final ExecutionContextManager contextManager;
    private final ExecutionResultAggregator resultAggregator;
    private final FlowWebSocketService webSocketService;
    
    // Command registry - automatically injected by Spring
    private final Map<String, StepExecutionCommand> commandRegistry = new HashMap<>();
    
    @Autowired
    public StepExecutionService(FlowExecutionStepRepository stepRepository,
                              ExecutionContextManager contextManager,
                              ExecutionResultAggregator resultAggregator,
                              FlowWebSocketService webSocketService,
                              List<StepExecutionCommand> commands) {
        this.stepRepository = stepRepository;
        this.contextManager = contextManager;
        this.resultAggregator = resultAggregator;
        this.webSocketService = webSocketService;
        
        // Register all command implementations
        registerCommands(commands);
    }
    
    /**
     * Execute a single node step in the flow
     */
    public Map<String, Object> executeStep(FlowExecution execution, Map<String, Object> node, 
                                          Map<String, Object> executionContext, int stepOrder) {
        
        String nodeId = node.get("id").toString();
        String nodeType = node.getOrDefault("type", "").toString();
        String nodeName = node.getOrDefault("name", nodeId).toString();
        
        logger.debug("Executing node: {} (type: {}) for execution: {}", nodeId, nodeType, execution.getId());
        
        // Business-friendly logging for node execution
        logNodeExecution(nodeType, nodeName);
        
        // Create execution step
        FlowExecutionStep step = createExecutionStep(execution, nodeId, nodeName, nodeType, 
                                                    stepOrder, node);
        
        try {
            // Start step
            step.start();
            stepRepository.updateStatus(step.getId(), FlowExecutionStep.StepStatus.RUNNING);
            
            // Send WebSocket update for step start
            webSocketService.sendFlowStepUpdate(step);
            
            // Execute step using appropriate command
            Map<String, Object> stepResult = executeStepWithCommand(step, node, executionContext);
            
            // Process result and complete step
            completeStepExecution(step, stepResult);
            
            // Update execution context with step results
            contextManager.updateContextWithStepResults(executionContext, stepResult);
            
            return stepResult;
            
        } catch (Exception e) {
            logger.error("Step execution failed for node {}: {}", nodeId, e.getMessage(), e);
            
            failStepExecution(step, e);
            
            throw new RuntimeException("Step execution failed for node " + nodeId + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Create execution step record
     */
    private FlowExecutionStep createExecutionStep(FlowExecution execution, String nodeId, String nodeName, 
                                                 String nodeType, int stepOrder, Map<String, Object> node) {
        
        FlowExecutionStep step = new FlowExecutionStep(execution.getId(), nodeId, nodeName, 
                                                      getStepType(nodeType), stepOrder);
        
        step.setStepConfiguration((Map<String, Object>) node.getOrDefault("config", new HashMap<>()));
        step.setInputData(new HashMap<>(execution.getPayload()));
        step.setCorrelationId(execution.getCorrelationId() != null ? execution.getCorrelationId() : UUID.randomUUID());
        
        // Save step
        UUID stepId = stepRepository.save(step);
        step.setId(stepId);
        
        return step;
    }
    
    /**
     * Execute step using appropriate command pattern
     */
    private Map<String, Object> executeStepWithCommand(FlowExecutionStep step, Map<String, Object> node,
                                                      Map<String, Object> executionContext) {
        
        String nodeType = node.getOrDefault("type", "").toString().toLowerCase();
        
        // Find appropriate command
        StepExecutionCommand command = findCommandForNode(nodeType, node);
        if (command == null) {
            throw new RuntimeException("No command found for node type: " + nodeType);
        }
        
        // Execute using command pattern
        return command.execute(step, node, executionContext);
    }
    
    /**
     * Complete step execution with result processing
     */
    private void completeStepExecution(FlowExecutionStep step, Map<String, Object> stepResult) {
        // Complete step with business-friendly logging
        logger.info("COMPLETE: Step completed successfully {} completed", step.getStepName());
        
        // Extract and update step metrics
        ExecutionResultAggregator.StepExecutionResult result = resultAggregator.extractStepMetrics(stepResult);
        resultAggregator.updateStepWithMetrics(step, result);
        
        step.complete();
        stepRepository.update(step);
        
        // Send WebSocket update for step completion
        webSocketService.sendFlowStepUpdate(step);
    }
    
    /**
     * Handle step execution failure
     */
    private void failStepExecution(FlowExecutionStep step, Exception e) {
        step.fail(e.getMessage());
        stepRepository.update(step);
        
        // Send WebSocket update for step failure
        webSocketService.sendFlowStepUpdate(step);
    }
    
    /**
     * Register command implementations
     */
    private void registerCommands(List<StepExecutionCommand> commands) {
        for (StepExecutionCommand command : commands) {
            commandRegistry.put(command.getStepType(), command);
            logger.debug("Registered step execution command: {} -> {}", 
                        command.getStepType(), command.getClass().getSimpleName());
        }
    }
    
    /**
     * Find appropriate command for node
     */
    private StepExecutionCommand findCommandForNode(String nodeType, Map<String, Object> node) {
        // First try direct lookup by node type
        StepExecutionCommand command = commandRegistry.get(nodeType);
        if (command != null && command.canHandle(node)) {
            return command;
        }
        
        // Handle special cases for start/end nodes
        if ("start-process".equals(nodeType)) {
            command = commandRegistry.get("start");
            if (command != null && command.canHandle(node)) {
                return command;
            }
        }
        
        if ("end-process".equals(nodeType)) {
            command = commandRegistry.get("end");
            if (command != null && command.canHandle(node)) {
                return command;
            }
        }
        
        // Try all commands to see if any can handle this node
        for (StepExecutionCommand cmd : commandRegistry.values()) {
            if (cmd.canHandle(node)) {
                return cmd;
            }
        }
        
        return null;
    }
    
    /**
     * Business-friendly logging for node execution
     */
    private void logNodeExecution(String nodeType, String nodeName) {
        switch (nodeType.toLowerCase()) {
            case "start":
            case "start-process":
                logger.info("Processing sender adapter {}", nodeName);
                break;
            case "end":
            case "end-process":
                logger.info("File has been received by the receiver adapter {}", nodeName);
                break;
            case "utility":
                logger.info("Branch 1 message sent to utility {}", nodeName);
                break;
            case "decision":
                logger.info("COMPLETE: Step completed successfully Decision: {}", nodeName);
                break;
            case "parallelsplit":
                logger.info("PARALLEL: Splitting execution into parallel paths");
                break;
            case "messageend":
                logger.info("Branch 1 message sent to {}", nodeName);
                break;
            default:
                logger.info("COMPLETE: Step completed successfully {}", nodeName);
                break;
        }
    }
    
    /**
     * Convert node type to step type
     */
    private FlowExecutionStep.StepType getStepType(String nodeType) {
        switch (nodeType.toLowerCase()) {
            case "start":
            case "start-process":
                return FlowExecutionStep.StepType.ADAPTER_SENDER;
            case "end":
            case "end-process":
            case "messageend":
                return FlowExecutionStep.StepType.ADAPTER_RECEIVER;
            case "adapter":
                return FlowExecutionStep.StepType.ADAPTER_SENDER;
            case "utility":
                return FlowExecutionStep.StepType.UTILITY;
            case "decision":
                return FlowExecutionStep.StepType.DECISION;
            case "parallelsplit":
                return FlowExecutionStep.StepType.UTILITY;
            default:
                return FlowExecutionStep.StepType.UTILITY;
        }
    }
}