package com.integrixs.core.service;

import com.integrixs.core.repository.FlowExecutionRepository;
import com.integrixs.core.repository.FlowExecutionStepRepository;
import com.integrixs.core.repository.IntegrationFlowRepository;
import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.SystemConfigurationRepository;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.FlowExecutionStep;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.DeployedFlow;
import com.integrixs.shared.model.Adapter;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.core.logging.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for executing integration flows and managing flow orchestration
 * Handles flow execution lifecycle, step-by-step processing, and retry logic
 */
@Service
public class FlowExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionService.class);
    private final EnhancedLogger enhancedLogger = EnhancedLogger.getLogger(FlowExecutionService.class);
    private static final Marker FLOW_EXECUTION_MARKER = MarkerFactory.getMarker("FLOW_EXECUTION");
    
    private final FlowExecutionRepository executionRepository;
    private final FlowExecutionStepRepository stepRepository;
    private final IntegrationFlowRepository flowRepository;
    private final DeployedFlowRepository deployedFlowRepository;
    private final AdapterRepository adapterRepository;
    private final AdapterManagementService adapterManagementService;
    private final AdapterExecutionService adapterExecutionService;
    private final UtilityExecutionService utilityExecutionService;
    private final FlowWebSocketService webSocketService;
    private final SystemConfigurationRepository configRepository;
    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(10);
    
    @Autowired
    public FlowExecutionService(FlowExecutionRepository executionRepository,
                               FlowExecutionStepRepository stepRepository,
                               IntegrationFlowRepository flowRepository,
                               DeployedFlowRepository deployedFlowRepository,
                               AdapterRepository adapterRepository,
                               AdapterManagementService adapterManagementService,
                               AdapterExecutionService adapterExecutionService,
                               UtilityExecutionService utilityExecutionService,
                               FlowWebSocketService webSocketService,
                               SystemConfigurationRepository configRepository) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.flowRepository = flowRepository;
        this.deployedFlowRepository = deployedFlowRepository;
        this.adapterRepository = adapterRepository;
        this.adapterManagementService = adapterManagementService;
        this.adapterExecutionService = adapterExecutionService;
        this.utilityExecutionService = utilityExecutionService;
        this.webSocketService = webSocketService;
        this.configRepository = configRepository;
    }
    
    /**
     * Get all executions
     */
    public List<FlowExecution> getAllExecutions() {
        logger.debug("Retrieving all flow executions");
        return executionRepository.findAll();
    }
    
    /**
     * Get recent executions with limit for dashboard
     */
    public List<FlowExecution> getRecentExecutions(int limit) {
        logger.debug("Retrieving {} recent flow executions", limit);
        return executionRepository.findRecentExecutions(limit);
    }
    
    /**
     * Get executions by flow ID
     */
    public List<FlowExecution> getExecutionsByFlowId(UUID flowId) {
        logger.debug("Retrieving executions for flow: {}", flowId);
        return executionRepository.findByFlowId(flowId);
    }
    
    /**
     * Get executions by status
     */
    public List<FlowExecution> getExecutionsByStatus(FlowExecution.ExecutionStatus status) {
        logger.debug("Retrieving executions with status: {}", status);
        return executionRepository.findByStatus(status);
    }
    
    /**
     * Get running executions
     */
    public List<FlowExecution> getRunningExecutions() {
        logger.debug("Retrieving running executions");
        return executionRepository.findRunningExecutions();
    }
    
    /**
     * Get failed executions eligible for retry
     */
    public List<FlowExecution> getFailedExecutionsForRetry() {
        logger.debug("Retrieving failed executions eligible for retry");
        return executionRepository.findFailedExecutionsForRetry();
    }
    
    /**
     * Get execution by ID
     */
    public Optional<FlowExecution> getExecutionById(UUID id) {
        logger.debug("Retrieving execution by ID: {}", id);
        return executionRepository.findById(id);
    }
    
    /**
     * Get execution steps by execution ID
     */
    public List<FlowExecutionStep> getExecutionSteps(UUID executionId) {
        logger.debug("Retrieving steps for execution: {}", executionId);
        return stepRepository.findByExecutionId(executionId);
    }
    
    /**
     * Execute flow manually
     */
    public FlowExecution executeFlow(UUID flowId, Map<String, Object> payload, UUID triggeredBy) {
        return executeFlow(flowId, payload, triggeredBy, FlowExecution.TriggerType.MANUAL);
    }
    
    public FlowExecution executeFlow(UUID flowId, Map<String, Object> payload, UUID triggeredBy, FlowExecution.TriggerType triggerType) {
        // Set up correlation context for enterprise logging
        CorrelationContext.setCorrelationId(CorrelationContext.generateCorrelationId());
        CorrelationContext.setOperationId("FLOW_EXEC_" + flowId.toString().substring(0, 8).toUpperCase());
        
        // Generate enterprise message ID and set in correlation context for MDC
        String messageId = CorrelationContext.generateMessageId();
        CorrelationContext.setMessageId(messageId);
        
        // Set flow context for logging integration
        CorrelationContext.setFlowId(flowId.toString());
        
        // Enterprise-style logging - flow entry
        enhancedLogger.adapterMessageEntry("FLOW", triggeredBy != null ? triggeredBy.toString() : "system");
        enhancedLogger.moduleProcessing("h2h/flow-execution/" + flowId.toString().substring(0, 8));
        
        logger.info(FLOW_EXECUTION_MARKER, "{} execution requested for flow: {} by user: {}", 
                   triggerType.name().toLowerCase(), flowId, triggeredBy);
        
        // Check if flow can execute using deployment registry
        Map<String, Object> executionCheck = deployedFlowRepository.checkFlowExecution(flowId);
        boolean canExecute = (Boolean) executionCheck.get("canExecute");
        String blockingReason = (String) executionCheck.get("blockingReason");
        
        if (!canExecute) {
            logger.warn("Flow execution blocked for flow {}: {}", flowId, blockingReason);
            throw new IllegalArgumentException("Flow execution blocked: " + blockingReason);
        }
        
        // Get flow definition (still needed for execution)
        Optional<IntegrationFlow> flowOpt = flowRepository.findById(flowId);
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Flow with ID " + flowId + " not found");
        }
        
        IntegrationFlow flow = flowOpt.get();
        if (!flow.isReadyForExecution()) {
            throw new IllegalArgumentException("Flow " + flowId + " is not ready for execution");
        }
        
        // Get deployment information
        UUID deploymentId = (UUID) executionCheck.get("deploymentId");
        Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findById(deploymentId);
        if (deployedFlowOpt.isEmpty()) {
            throw new IllegalArgumentException("Deployment record not found for flow: " + flowId);
        }
        
        DeployedFlow deployedFlow = deployedFlowOpt.get();
        
        // Create execution record with deployment context
        FlowExecution execution = new FlowExecution(flowId, flow.getName(), 
                                                   triggerType, triggeredBy);
        execution.setPayload(payload != null ? payload : new HashMap<>());
        execution.setPriority(5); // Normal priority for manual executions
        
        // Set execution context in CorrelationContext for logging integration
        CorrelationContext.setExecutionId(execution.getId().toString());
        CorrelationContext.setFlowName(flow.getName());
        
        // Add deployment context to execution
        Map<String, Object> executionContext = new HashMap<>();
        executionContext.put("deploymentId", deploymentId);
        executionContext.put("deploymentEnvironment", deployedFlow.getDeploymentEnvironment());
        executionContext.put("senderAdapterId", deployedFlow.getSenderAdapterId());
        executionContext.put("receiverAdapterId", deployedFlow.getReceiverAdapterId());
        executionContext.put("maxConcurrentExecutions", deployedFlow.getMaxConcurrentExecutions());
        executionContext.put("executionTimeoutMinutes", deployedFlow.getExecutionTimeoutMinutes());
        
        // Add correlation context for async execution restoration
        executionContext.put("correlationId", CorrelationContext.getCorrelationId());
        executionContext.put("messageId", messageId);
        executionContext.put("executionId", execution.getId().toString());
        executionContext.put("flowId", flowId.toString());
        executionContext.put("flowName", flow.getName());
        
        // Add trigger data from payload to execution context
        if (payload != null && payload.containsKey("triggerData")) {
            executionContext.put("triggerData", payload.get("triggerData"));
        }
        
        execution.setExecutionContext(executionContext);
        
        // Set timeout based on deployment configuration
        if (deployedFlow.getExecutionTimeoutMinutes() != null) {
            execution.setTimeoutAt(LocalDateTime.now().plusMinutes(deployedFlow.getExecutionTimeoutMinutes()));
        }
        
        // Save execution
        UUID executionId = executionRepository.save(execution);
        execution.setId(executionId);
        
        // Add message ID to execution context for tracing
        if (execution.getExecutionContext() == null) {
            execution.setExecutionContext(new HashMap<>());
        }
        execution.getExecutionContext().put("messageId", messageId);
        execution.getExecutionContext().put("correlationId", CorrelationContext.getCorrelationId());
        
        // Business-friendly logging - flow execution start
        logger.info(FLOW_EXECUTION_MARKER, "Starting async execution for flow {}", flow.getName());
        logger.info(FLOW_EXECUTION_MARKER, "Application attempting to send message asynchronously using connection FlowExecution-{}", execution.getId().toString().substring(0, 8));
        logger.info(FLOW_EXECUTION_MARKER, "Trying to put the message into the processing queue");
        
        // Execute flow asynchronously with deployment context
        executeFlowAsync(execution, flow);
        
        // Business-friendly logging - message queued successfully  
        logger.info(FLOW_EXECUTION_MARKER, "The message was successfully retrieved from the processing queue");
        
        // Send WebSocket notification
        webSocketService.sendFlowExecutionUpdate(execution);
        
        logger.info("Flow execution started: {} for flow: {} (deployment: {})", executionId, flowId, deploymentId);
        return execution;
    }
    
    /**
     * Execute flow asynchronously
     */
    @Async
    public CompletableFuture<FlowExecution> executeFlowAsync(FlowExecution execution, IntegrationFlow flow) {
        // Restore correlation context for async execution including message ID
        String correlationId = null;
        String messageId = null;
        String executionId = null;
        String flowId = null;
        String flowName = null;
        
        if (execution.getExecutionContext() != null) {
            correlationId = (String) execution.getExecutionContext().get("correlationId");
            if (correlationId != null) {
                CorrelationContext.setCorrelationId(correlationId);
            }
            messageId = (String) execution.getExecutionContext().get("messageId");
            if (messageId != null) {
                CorrelationContext.setMessageId(messageId);
            }
            executionId = (String) execution.getExecutionContext().get("executionId");
            if (executionId != null) {
                CorrelationContext.setExecutionId(executionId);
            }
            flowId = (String) execution.getExecutionContext().get("flowId");
            if (flowId != null) {
                CorrelationContext.setFlowId(flowId);
            }
            flowName = (String) execution.getExecutionContext().get("flowName");
            if (flowName != null) {
                CorrelationContext.setFlowName(flowName);
            }
        }
        if (correlationId == null) {
            CorrelationContext.setCorrelationId(CorrelationContext.generateCorrelationId());
        }
        if (messageId == null) {
            messageId = CorrelationContext.generateMessageId();
            CorrelationContext.setMessageId(messageId);
        }
        CorrelationContext.setOperationId("FLOW_ASYNC_" + execution.getId().toString().substring(0, 8).toUpperCase());
        
        // Ensure execution context is set (fallback if not in context)
        if (executionId == null) {
            CorrelationContext.setExecutionId(execution.getId().toString());
        }
        if (flowId == null) {
            CorrelationContext.setFlowId(execution.getFlowId().toString());
        }
        if (flowName == null) {
            CorrelationContext.setFlowName(execution.getFlowName());
        }
        
        logger.info(FLOW_EXECUTION_MARKER, "Starting async execution: {} for flow: {}", execution.getId(), flow.getId());
        logger.info(FLOW_EXECUTION_MARKER, "The message was successfully retrieved from the processing queue");
        
        try {
            // Start execution
            execution.start();
            executionRepository.updateStatus(execution.getId(), FlowExecution.ExecutionStatus.RUNNING);
            logger.info(FLOW_EXECUTION_MARKER, "Execution status changed to RUNNING");
            
            // Send WebSocket update for execution start
            webSocketService.sendFlowExecutionUpdate(execution);
            
            // Set timeout if configured
            if (flow.getTimeoutMinutes() != null && flow.getTimeoutMinutes() > 0) {
                execution.setTimeoutAt(LocalDateTime.now().plusMinutes(flow.getTimeoutMinutes()));
                executionRepository.update(execution);
            }
            
            // Execute flow steps with business-friendly logging
            logger.info(FLOW_EXECUTION_MARKER, "Executing flow steps for {}", flow.getName());
            executeFlowSteps(execution, flow);
            
            // Collect file metrics from execution steps before completing
            updateExecutionFileMetrics(execution);
            
            // Complete execution with business-friendly logging
            logger.info(FLOW_EXECUTION_MARKER, "Flow execution completed successfully");
            
            execution.complete();
            executionRepository.updateCompletion(execution.getId(), 
                                               FlowExecution.ExecutionStatus.COMPLETED,
                                               execution.getCompletedAt(),
                                               execution.getDurationMs(),
                                               null);
            
            // Update flow statistics
            flow.recordExecution(execution.getDurationMs(), true);
            flowRepository.update(flow);
            
            // Update deployment statistics
            updateDeploymentStatistics(execution, true);
            
            // Send WebSocket update for execution completion
            webSocketService.sendFlowExecutionUpdate(execution);
            
            logger.info(FLOW_EXECUTION_MARKER, "Flow execution completed successfully: {}", execution.getId());
            
        } catch (Exception e) {
            logger.error("Flow execution failed: {}: {}", execution.getId(), e.getMessage(), e);
            
            // Enterprise logging for failure
            enhancedLogger.messageStatus("FAILED");
            enhancedLogger.flowExecutionStep("ERROR", "Flow Execution Error", "Flow failed: " + e.getMessage());
            
            // Mark execution as failed
            execution.fail(e.getMessage());
            executionRepository.updateCompletion(execution.getId(), 
                                               FlowExecution.ExecutionStatus.FAILED,
                                               execution.getCompletedAt(),
                                               execution.getDurationMs(),
                                               e.getMessage());
            
            // Update flow statistics
            flow.recordExecution(execution.getDurationMs(), false);
            flowRepository.update(flow);
            
            // Update deployment statistics
            updateDeploymentStatistics(execution, false);
            
            // Send WebSocket update for execution failure
            webSocketService.sendFlowExecutionUpdate(execution);
        }
        
        return CompletableFuture.completedFuture(execution);
    }
    
    /**
     * Retry failed execution - updates the existing execution instead of creating a new one
     */
    public FlowExecution retryExecution(UUID executionId, UUID triggeredBy) {
        logger.info("Retry requested for execution: {} by user: {}", executionId, triggeredBy);
        
        Optional<FlowExecution> originalOpt = getExecutionById(executionId);
        if (originalOpt.isEmpty()) {
            throw new IllegalArgumentException("Execution with ID " + executionId + " not found");
        }
        
        FlowExecution execution = originalOpt.get();
        if (!execution.canRetry()) {
            throw new IllegalArgumentException("Execution " + executionId + " cannot be retried");
        }
        
        // Get the flow
        Optional<IntegrationFlow> flowOpt = flowRepository.findById(execution.getFlowId());
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Original flow not found for execution " + executionId);
        }
        
        // Update the existing execution for retry
        execution.setTriggerType(FlowExecution.TriggerType.RETRY);
        execution.setTriggeredBy(triggeredBy);
        execution.setRetryAttempt(execution.getRetryAttempt() + 1);
        execution.setErrorMessage(null); // Clear previous error message
        execution.setCompletedAt(null); // Reset completion time
        execution.setStartedAt(LocalDateTime.now()); // Set new start time for retry
        execution.setExecutionStatus(FlowExecution.ExecutionStatus.PENDING); // Reset status
        
        // Update the execution record to reflect retry state
        executionRepository.update(execution);
        
        // Clear any existing execution steps for the retry
        int deletedSteps = stepRepository.deleteByExecutionId(executionId);
        if (deletedSteps > 0) {
            logger.info("Cleared {} existing steps for retry of execution: {}", deletedSteps, executionId);
        }
        
        // Execute retry asynchronously using the same execution record
        executeFlowAsync(execution, flowOpt.get());
        
        logger.info("Retry execution started for execution: {} (correlation: {})", executionId, execution.getCorrelationId());
        return execution;
    }
    
    /**
     * Cancel running execution
     */
    public void cancelExecution(UUID executionId, UUID cancelledBy) {
        logger.info("Cancel requested for execution: {} by user: {}", executionId, cancelledBy);
        
        Optional<FlowExecution> executionOpt = getExecutionById(executionId);
        if (executionOpt.isEmpty()) {
            throw new IllegalArgumentException("Execution with ID " + executionId + " not found");
        }
        
        FlowExecution execution = executionOpt.get();
        if (!execution.isRunning()) {
            throw new IllegalArgumentException("Execution " + executionId + " is not running");
        }
        
        // Cancel execution
        execution.cancel();
        executionRepository.updateCompletion(execution.getId(), 
                                           FlowExecution.ExecutionStatus.CANCELLED,
                                           execution.getCompletedAt(),
                                           execution.getDurationMs(),
                                           "Cancelled by user " + cancelledBy);
        
        // Cancel all running steps
        List<FlowExecutionStep> runningSteps = stepRepository.findByExecutionIdAndStatus(
            executionId, FlowExecutionStep.StepStatus.RUNNING);
        
        for (FlowExecutionStep step : runningSteps) {
            step.cancel();
            stepRepository.update(step);
        }
        
        logger.info("Execution cancelled: {}", executionId);
    }
    
    /**
     * Get execution statistics
     */
    public Map<String, Object> getExecutionStatistics() {
        logger.debug("Retrieving execution statistics");
        return executionRepository.getExecutionStatistics();
    }
    
    /**
     * Execute flow steps based on flow definition
     */
    @SuppressWarnings("unchecked")
    private void executeFlowSteps(FlowExecution execution, IntegrationFlow flow) {
        logger.info("Executing steps for execution: {}", execution.getId());
        
        Map<String, Object> flowDefinition = flow.getFlowDefinition();
        if (flowDefinition == null || !flowDefinition.containsKey("nodes")) {
            throw new RuntimeException("Invalid flow definition - no nodes found");
        }
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) flowDefinition.get("nodes");
        
        // Support both React Flow format (edges) and legacy format (connections)
        List<Map<String, Object>> connections = new ArrayList<>();
        if (flowDefinition.containsKey("edges")) {
            // React Flow format
            connections = (List<Map<String, Object>>) flowDefinition.get("edges");
        } else {
            // Legacy format
            connections = (List<Map<String, Object>>) flowDefinition.getOrDefault("connections", new ArrayList<>());
        }
        
        // Find start node (support both "start" and "start-process" types)
        Map<String, Object> startNode = findNodeByType(nodes, "start");
        if (startNode == null) {
            startNode = findNodeByType(nodes, "start-process");
        }
        if (startNode == null) {
            throw new RuntimeException("Flow definition missing start node (start or start-process)");
        }
        
        // Execute flow starting from start node
        Map<String, Object> executionContext = new HashMap<>(execution.getPayload());
        
        // Add execution context for accessing deployment snapshot configurations
        if (execution.getExecutionContext() != null) {
            executionContext.putAll(execution.getExecutionContext());
            logger.debug("Added deployment context to execution: deploymentId = {}", 
                        execution.getExecutionContext().get("deploymentId"));
        }
        
        executeNode(execution, startNode, nodes, connections, executionContext, 1);
        
        logger.info("Flow step execution completed for execution: {}", execution.getId());
    }
    
    /**
     * Execute a single node in the flow
     */
    @SuppressWarnings("unchecked")
    private void executeNode(FlowExecution execution, Map<String, Object> node, 
                           List<Map<String, Object>> allNodes, List<Map<String, Object>> connections,
                           Map<String, Object> executionContext, int stepOrder) {
        
        String nodeId = node.get("id").toString();
        String nodeType = node.getOrDefault("type", "").toString();
        String nodeName = node.getOrDefault("name", nodeId).toString();
        
        logger.debug("Executing node: {} (type: {}) for execution: {}", nodeId, nodeType, execution.getId());
        
        // Business-friendly logging for node execution
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
        
        // Create execution step
        FlowExecutionStep step = new FlowExecutionStep(execution.getId(), nodeId, nodeName, 
                                                      getStepType(nodeType), stepOrder);
        step.setStepConfiguration((Map<String, Object>) node.getOrDefault("config", new HashMap<>()));
        step.setInputData(new HashMap<>(executionContext));
        step.setCorrelationId(execution.getCorrelationId());
        
        // Save step
        UUID stepId = stepRepository.save(step);
        step.setId(stepId);
        
        try {
            // Start step
            step.start();
            stepRepository.updateStatus(step.getId(), FlowExecutionStep.StepStatus.RUNNING);
            
            // Send WebSocket update for step start
            webSocketService.sendFlowStepUpdate(step);
            
            // Execute step based on type
            Map<String, Object> stepResult = new HashMap<>();
            
            switch (nodeType.toLowerCase()) {
                case "start":
                case "start-process":
                    stepResult = executeStartNode(step, node, executionContext);
                    break;
                case "end":
                case "end-process":
                    stepResult = executeEndNode(step, node, executionContext);
                    break;
                case "adapter":
                    stepResult = executeAdapterNode(step, node, executionContext);
                    break;
                case "utility":
                    stepResult = executeUtilityNode(step, node, executionContext);
                    break;
                case "decision":
                    stepResult = executeDecisionNode(step, node, executionContext);
                    break;
                case "parallelsplit":
                    stepResult = executeParallelSplitNode(step, node, executionContext);
                    break;
                case "messageend":
                    stepResult = executeMessageEndNode(step, node, executionContext);
                    break;
                default:
                    throw new RuntimeException("Unsupported node type: " + nodeType);
            }
            
            // Complete step with business-friendly logging
            logger.info("COMPLETE: Step completed successfully {} completed", nodeName);
            step.setOutputData(stepResult);
            
            // Extract file metrics from step result
            extractStepFileMetrics(step, stepResult);
            
            step.complete();
            stepRepository.update(step);
            
            // Send WebSocket update for step completion
            webSocketService.sendFlowStepUpdate(step);
            
            // Update execution context with step results
            executionContext.putAll(stepResult);
            
            // Find and execute next nodes (continue execution regardless of node type)
            executeNextNodes(execution, nodeId, allNodes, connections, executionContext, stepOrder + 1);
            
        } catch (Exception e) {
            logger.error("Step execution failed for node {}: {}", nodeId, e.getMessage(), e);
            
            // Enterprise logging for step failure
            enhancedLogger.flowExecutionStep("ERROR", "Step execution failed", nodeName + " failed: " + e.getMessage());
            
            step.fail(e.getMessage());
            stepRepository.update(step);
            
            // Send WebSocket update for step failure
            webSocketService.sendFlowStepUpdate(step);
            
            throw new RuntimeException("Step execution failed for node " + nodeId + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Find and execute next nodes in the flow
     */
    private void executeNextNodes(FlowExecution execution, String currentNodeId,
                                 List<Map<String, Object>> allNodes, List<Map<String, Object>> connections,
                                 Map<String, Object> executionContext, int stepOrder) {
        
        // Find outgoing connections from current node
        List<String> nextNodeIds = new ArrayList<>();
        for (Map<String, Object> connection : connections) {
            if (currentNodeId.equals(connection.get("source"))) {
                nextNodeIds.add(connection.get("target").toString());
            }
        }
        
        // Execute next nodes (parallel execution for multiple branches)
        if (nextNodeIds.size() <= 1) {
            // Sequential execution for single node
            for (String nextNodeId : nextNodeIds) {
                Map<String, Object> nextNode = findNodeById(allNodes, nextNodeId);
                if (nextNode != null) {
                    executeNode(execution, nextNode, allNodes, connections, executionContext, stepOrder);
                }
            }
        } else {
            // Parallel execution for multiple branches
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (String nextNodeId : nextNodeIds) {
                Map<String, Object> nextNode = findNodeById(allNodes, nextNodeId);
                if (nextNode != null) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            // Create isolated context copy for each parallel branch
                            Map<String, Object> branchContext = new HashMap<>(executionContext);
                            executeNode(execution, nextNode, allNodes, connections, branchContext, stepOrder);
                        } catch (Exception e) {
                            logger.error("Parallel node execution failed for {}: {}", nextNodeId, e.getMessage(), e);
                            throw new RuntimeException("Parallel execution failed for node " + nextNodeId, e);
                        }
                    }, parallelExecutor);
                    
                    futures.add(future);
                }
            }
            
            // Wait for all parallel branches to complete
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(getParallelExecutionTimeoutSeconds(), TimeUnit.SECONDS);
                
                logger.debug("Parallel execution completed for {} branches", nextNodeIds.size());
                
            } catch (Exception e) {
                logger.error("Parallel execution failed: {}", e.getMessage(), e);
                throw new RuntimeException("Parallel execution timeout or failure", e);
            }
        }
    }
    
    /**
     * Execute start node - In new structure, START nodes are flow control only
     * Sender adapter execution happens separately and data is passed via triggerData
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeStartNode(FlowExecutionStep step, Map<String, Object> node, Map<String, Object> context) {
        logger.debug("Executing start node for step: {} (Flow control - data passed from sender adapter)", step.getId());
        
        // In new structure, start nodes don't execute adapters - they receive data from separate adapter execution
        // The triggerData should contain the data from the sender adapter execution
        Object triggerData = context.get("triggerData");
        if (triggerData == null) {
            // No trigger data - this could be manual execution or flow testing
            // Initialize with empty data for flow control
            triggerData = new HashMap<String, Object>();
            logger.debug("No trigger data provided to START node - initializing with empty data for flow control");
        }
        
        // Process trigger data from sender adapter execution
        Map<String, Object> result = new HashMap<>();
        
        if (triggerData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> triggerMap = (Map<String, Object>) triggerData;
            
            // Extract files that were processed by the sender adapter
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foundFiles = (List<Map<String, Object>>) triggerMap.getOrDefault("foundFiles", new ArrayList<>());
            
            if (!foundFiles.isEmpty()) {
                // Add files to execution context for downstream processing
                context.put("filesToProcess", foundFiles);
                context.put("senderProcessedFiles", foundFiles);
                result.put("hasData", true);
                result.put("foundFiles", foundFiles);
                
                logger.info("START node received {} files from sender adapter, added to execution context", foundFiles.size());
            } else {
                result.put("hasData", false);
                result.put("foundFiles", new ArrayList<>());
                logger.debug("START node received trigger data but no files to process");
            }
            
            // Pass through any additional trigger data
            for (Map.Entry<String, Object> entry : triggerMap.entrySet()) {
                if (!"foundFiles".equals(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            result.put("hasData", false);
            result.put("foundFiles", new ArrayList<>());
            logger.warn("START node received non-map trigger data: {}", triggerData.getClass().getSimpleName());
        }
        
        // Add start node metadata
        result.put("nodeType", "start");
        result.put("executionMode", "flow_control");
        result.put("timestamp", LocalDateTime.now().toString());
        
        return result;
    }
    
    /**
     * Execute end node - In new structure, END nodes are flow control only  
     * Receiver adapter execution happens in separate adapter nodes connected via edges
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeEndNode(FlowExecutionStep step, Map<String, Object> node, Map<String, Object> context) {
        logger.debug("Executing end node for step: {} (Flow control - data passed to receiver adapters)", step.getId());
        
        // In new structure, end nodes don't execute adapters - they pass data to separate adapter nodes
        // Extract any files from the execution context that need to be passed to receiver adapters
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filesToProcess = (List<Map<String, Object>>) context.getOrDefault("filesToProcess", new ArrayList<>());
        
        Map<String, Object> result = new HashMap<>();
        
        if (!filesToProcess.isEmpty()) {
            logger.info("END node: Received {} files from execution context to pass to receiver adapters", filesToProcess.size());
            
            // Pass files to the context for receiver adapter nodes connected via edges
            context.put("receiverFiles", filesToProcess);
            result.put("hasData", true);
            result.put("filesToProcess", filesToProcess);
            
            // Log files being passed
            for (int i = 0; i < filesToProcess.size(); i++) {
                Map<String, Object> file = filesToProcess.get(i);
                logger.debug("END node: File {} - {}", i + 1, file.getOrDefault("name", "unknown"));
            }
        } else {
            logger.debug("END node: No files to pass to receiver adapters");
            result.put("hasData", false);
            result.put("filesToProcess", new ArrayList<>());
        }
        
        // Add end node metadata
        result.put("nodeType", "end");
        result.put("executionMode", "flow_control");
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("status", "completed");
        
        return result;
    }
    
    /**
     * Execute adapter node - now only used for intermediate adapter nodes (not start/end)
     * Supports both React Flow node format (data.adapterId) and legacy format (adapterId)
     * Note: START/END nodes now execute adapters directly through executeStartNode/executeEndNode
     */
    private Map<String, Object> executeAdapterNode(FlowExecutionStep step, Map<String, Object> node, Map<String, Object> context) {
        logger.debug("Executing intermediate adapter node for step: {}", step.getId());
        
        // Extract adapter ID from React Flow format (data.adapterId) or legacy format (adapterId)
        String adapterIdStr = "";
        
        // Check React Flow format first (data object contains node data)
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
        if (nodeData != null && nodeData.containsKey("adapterId")) {
            adapterIdStr = nodeData.get("adapterId").toString();
        } else if (node.containsKey("adapterId")) {
            // Legacy format
            adapterIdStr = node.get("adapterId").toString();
        }
        
        if (adapterIdStr.isEmpty()) {
            throw new RuntimeException("Intermediate adapter node missing adapterId in data or node configuration");
        }
        
        try {
            UUID adapterId = UUID.fromString(adapterIdStr);
            
            // Get adapter configuration
            Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
            if (adapterOpt.isEmpty()) {
                throw new RuntimeException("Intermediate adapter not found: " + adapterId);
            }
            
            Adapter adapter = adapterOpt.get();
            if (!adapter.isActive()) {
                throw new RuntimeException("Intermediate adapter is inactive: " + adapterId);
            }
            
            logger.info("Executing intermediate adapter: {} ({})", adapter.getName(), adapterId);
            
            // Execute real adapter operation using AdapterExecutionService
            Map<String, Object> adapterResult = adapterExecutionService.executeAdapter(adapterId, context, step);
            
            // Add intermediate adapter metadata
            adapterResult.put("adapterType", "intermediate");
            adapterResult.put("adapterId", adapterId.toString());
            
            return adapterResult;
            
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid adapter ID format: " + adapterIdStr, e);
        }
    }
    
    /**
     * Execute utility node
     * Supports both React Flow node format (data.utilityType) and legacy format (utilityType)
     */
    private Map<String, Object> executeUtilityNode(FlowExecutionStep step, Map<String, Object> node, Map<String, Object> context) {
        logger.debug("Executing utility node for step: {}", step.getId());
        
        // Extract utility type from React Flow format (data.utilityType) or legacy format (utilityType)
        String utilityType = "";
        Map<String, Object> utilityConfiguration = new HashMap<>();
        
        // Check React Flow format first (data object contains node data)
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
        if (nodeData != null) {
            utilityType = (String) nodeData.getOrDefault("utilityType", "");
            utilityConfiguration = (Map<String, Object>) nodeData.getOrDefault("configuration", new HashMap<>());
        } else {
            // Legacy format
            utilityType = node.getOrDefault("utilityType", "").toString();
            utilityConfiguration = (Map<String, Object>) node.getOrDefault("configuration", new HashMap<>());
        }
        
        if (utilityType.isEmpty()) {
            throw new RuntimeException("Utility node missing utilityType in data or node configuration");
        }
        
        // Execute real utility operation using UtilityExecutionService
        Map<String, Object> utilityResult = utilityExecutionService.executeUtility(utilityType, utilityConfiguration, context, step);
        
        return utilityResult;
    }
    
    /**
     * Execute decision node
     * Supports both React Flow node format (data.conditionType) and legacy format (decisionConfig)
     */
    private Map<String, Object> executeDecisionNode(FlowExecutionStep step, Map<String, Object> node, Map<String, Object> context) {
        logger.debug("Executing decision node for step: {}", step.getId());
        
        Map<String, Object> decisionResult = new HashMap<>();
        
        try {
            // Extract decision configuration from React Flow format or legacy format
            String conditionType = "ALWAYS_TRUE";
            String conditionExpression = "true";
            
            // Check React Flow format first (data object contains node data)
            @SuppressWarnings("unchecked")
            Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
            if (nodeData != null) {
                conditionType = (String) nodeData.getOrDefault("conditionType", "ALWAYS_TRUE");
                conditionExpression = (String) nodeData.getOrDefault("condition", "true");
            } else {
                // Legacy format
                @SuppressWarnings("unchecked")
                Map<String, Object> decisionConfig = (Map<String, Object>) node.getOrDefault("decisionConfig", new HashMap<>());
                conditionType = (String) decisionConfig.getOrDefault("conditionType", "ALWAYS_TRUE");
                conditionExpression = (String) decisionConfig.getOrDefault("condition", "true");
            }
            
            boolean decisionResult_bool = evaluateCondition(conditionType, conditionExpression, context);
            
            decisionResult.put("decision", decisionResult_bool ? "true" : "false");
            decisionResult.put("conditionType", conditionType);
            decisionResult.put("conditionExpression", conditionExpression);
            decisionResult.put("evaluatedAt", LocalDateTime.now().toString());
            
            // Set context variable for downstream nodes
            context.put("lastDecisionResult", decisionResult_bool);
            
            logger.debug("Decision evaluation completed: {} -> {}", conditionExpression, decisionResult_bool);
            
        } catch (Exception e) {
            logger.error("Decision node evaluation failed: {}", e.getMessage(), e);
            decisionResult.put("decision", "false");
            decisionResult.put("error", e.getMessage());
        }
        
        return decisionResult;
    }
    
    /**
     * Execute parallel split node - creates multiple execution paths
     * Ensures file data (filenames and binary content) are properly passed to all parallel branches
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeParallelSplitNode(FlowExecutionStep step, Map<String, Object> node, Map<String, Object> context) {
        logger.debug("Executing parallel split node for step: {}", step.getId());
        
        Map<String, Object> splitResult = new HashMap<>();
        
        try {
            // Extract parallel paths configuration from React Flow format
            Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
            int parallelPaths = 2; // Default to 2 paths
            
            if (nodeData != null) {
                Object pathsObj = nodeData.get("parallelPaths");
                if (pathsObj instanceof Number) {
                    parallelPaths = ((Number) pathsObj).intValue();
                }
            }
            
            // Verify file data availability for parallel processing
            List<Map<String, Object>> filesToProcess = (List<Map<String, Object>>) context.getOrDefault("filesToProcess", new ArrayList<>());
            List<Map<String, Object>> senderFiles = (List<Map<String, Object>>) context.getOrDefault("senderFiles", new ArrayList<>());
            List<Map<String, Object>> foundFiles = (List<Map<String, Object>>) context.getOrDefault("foundFiles", new ArrayList<>());
            
            int totalFiles = Math.max(Math.max(filesToProcess.size(), senderFiles.size()), foundFiles.size());
            
            splitResult.put("parallelPaths", parallelPaths);
            splitResult.put("splitType", "parallel");
            splitResult.put("splitTimestamp", LocalDateTime.now().toString());
            splitResult.put("splitNodeId", node.get("id"));
            splitResult.put("filesAvailableForSplit", totalFiles);
            
            // Log file data propagation details
            if (totalFiles > 0) {
                logger.info("Parallel split node ready to distribute {} files across {} execution paths", totalFiles, parallelPaths);
                
                // Add file data details to result for monitoring
                splitResult.put("fileDataKeys", new ArrayList<>(Arrays.asList("filesToProcess", "senderFiles", "foundFiles")));
                splitResult.put("filesToProcessCount", filesToProcess.size());
                splitResult.put("senderFilesCount", senderFiles.size());
                splitResult.put("foundFilesCount", foundFiles.size());
                
                // Log sample file information for debugging
                if (!filesToProcess.isEmpty()) {
                    Map<String, Object> sampleFile = filesToProcess.get(0);
                    logger.debug("Sample file in parallel split - keys: {}", sampleFile.keySet());
                    if (sampleFile.containsKey("fileName")) {
                        logger.debug("Sample file name: {}", sampleFile.get("fileName"));
                    }
                    if (sampleFile.containsKey("filePath")) {
                        logger.debug("Sample file path: {}", sampleFile.get("filePath"));
                    }
                    if (sampleFile.containsKey("fileSize")) {
                        logger.debug("Sample file size: {}", sampleFile.get("fileSize"));
                    }
                }
            } else {
                logger.info("Parallel split node ready to create {} execution paths (no file data available)", parallelPaths);
            }
            
            // The actual parallel execution will be handled by executeNextNodes method
            // which already creates isolated context copies for each branch, including file data
            // The shallow copy in executeNextNodes is sufficient for file data propagation since:
            // 1. File paths/names are strings (immutable)
            // 2. File content is typically stored as byte arrays or streams (referenced, not copied)
            // 3. File metadata maps are independent objects
            
            // Store context snapshot for monitoring/debugging
            Map<String, Object> contextSnapshot = new HashMap<>(context);
            // Don't store actual file content in the result to avoid memory issues
            if (contextSnapshot.containsKey("filesToProcess")) {
                List<Map<String, Object>> files = (List<Map<String, Object>>) contextSnapshot.get("filesToProcess");
                List<Map<String, Object>> fileMetadata = new ArrayList<>();
                for (Map<String, Object> file : files) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("fileName", file.getOrDefault("fileName", "unknown"));
                    metadata.put("filePath", file.getOrDefault("filePath", "unknown"));
                    metadata.put("fileSize", file.getOrDefault("fileSize", 0));
                    metadata.put("lastModified", file.getOrDefault("lastModified", "unknown"));
                    fileMetadata.add(metadata);
                }
                contextSnapshot.put("filesToProcess", fileMetadata); // Replace with metadata only
            }
            splitResult.put("contextSnapshot", contextSnapshot);
            
            return splitResult;
            
        } catch (Exception e) {
            logger.error("Parallel split node execution failed: {}", e.getMessage(), e);
            splitResult.put("error", e.getMessage());
            splitResult.put("parallelPaths", 0);
            splitResult.put("filesAvailableForSplit", 0);
            return splitResult;
        }
    }
    
    /**
     * Execute message end node - similar to end node but with message event handling
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeMessageEndNode(FlowExecutionStep step, Map<String, Object> node, Map<String, Object> context) {
        logger.debug("Executing message end node for step: {}", step.getId());
        
        // Extract message end configuration from node data
        Map<String, Object> nodeData = (Map<String, Object>) node.get("data");
        if (nodeData == null) {
            throw new RuntimeException("Message End node missing data configuration");
        }
        
        // Get event type and message payload
        String eventType = (String) nodeData.getOrDefault("eventType", "MESSAGE_END");
        String messagePayload = (String) nodeData.getOrDefault("messagePayload", "");
        
        // Execute receiver adapter if configured (same as regular end node)
        Object receiverAdapterIdObj = nodeData.get("adapterId");
        Map<String, Object> messageResult = new HashMap<>();
        
        if (receiverAdapterIdObj != null && !receiverAdapterIdObj.toString().isEmpty()) {
            try {
                UUID receiverAdapterId = UUID.fromString(receiverAdapterIdObj.toString());
                
                // Get deployment context
                UUID deploymentId = (UUID) context.get("deploymentId");
                if (deploymentId != null) {
                    Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findById(deploymentId);
                    if (deployedFlowOpt.isPresent()) {
                        DeployedFlow deployedFlow = deployedFlowOpt.get();
                        
                        // Get adapter configuration
                        Optional<Adapter> adapterOpt = adapterRepository.findById(receiverAdapterId);
                        if (adapterOpt.isPresent()) {
                            Adapter adapter = adapterOpt.get();
                            
                            logger.info("Message End node executing adapter: {} with event: {}", adapter.getName(), eventType);
                            
                            // Execute adapter with message context
                            Map<String, Object> messageContext = new HashMap<>(context);
                            messageContext.put("messageEventType", eventType);
                            messageContext.put("messagePayload", messagePayload);
                            
                            Map<String, Object> adapterResult = adapterExecutionService.executeAdapter(receiverAdapterId, messageContext, step);
                            messageResult.putAll(adapterResult);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid adapter ID format in Message End node: " + receiverAdapterIdObj, e);
            }
        }
        
        // Add message end metadata
        messageResult.put("nodeType", "messageEnd");
        messageResult.put("eventType", eventType);
        messageResult.put("messagePayload", messagePayload);
        messageResult.put("timestamp", LocalDateTime.now().toString());
        messageResult.put("status", "completed");
        
        logger.info("Message End node completed with event: {}", eventType);
        return messageResult;
    }
    
    /**
     * Evaluate decision condition based on type and expression
     */
    private boolean evaluateCondition(String conditionType, String expression, Map<String, Object> context) {
        switch (conditionType.toUpperCase()) {
            case "ALWAYS_TRUE":
                return true;
            case "ALWAYS_FALSE":
                return false;
            case "CONTEXT_CONTAINS_KEY":
                return context.containsKey(expression);
            case "CONTEXT_VALUE_EQUALS":
                String[] parts = expression.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String expectedValue = parts[1].trim();
                    return expectedValue.equals(String.valueOf(context.get(key)));
                }
                return false;
            case "FILE_COUNT_GREATER_THAN":
                try {
                    int threshold = Integer.parseInt(expression);
                    @SuppressWarnings("unchecked")
                    List<String> files = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
                    return files.size() > threshold;
                } catch (NumberFormatException e) {
                    logger.warn("Invalid threshold for FILE_COUNT_GREATER_THAN: {}", expression);
                    return false;
                }
            default:
                logger.warn("Unknown condition type: {}", conditionType);
                return true; // Default to true for unknown conditions
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
                return FlowExecutionStep.StepType.ADAPTER_RECEIVER;
            case "messageend":
                return FlowExecutionStep.StepType.ADAPTER_RECEIVER;
            case "adapter":
                return FlowExecutionStep.StepType.ADAPTER_SENDER; // Would be determined by adapter direction
            case "utility":
                return FlowExecutionStep.StepType.UTILITY;
            case "decision":
                return FlowExecutionStep.StepType.DECISION;
            case "parallelsplit":
                return FlowExecutionStep.StepType.UTILITY; // Treat parallel split as utility step
            default:
                return FlowExecutionStep.StepType.UTILITY;
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
     * Extract file metrics from step result and update the step
     */
    private void extractStepFileMetrics(FlowExecutionStep step, Map<String, Object> stepResult) {
        try {
            int fileCount = 0;
            long totalBytes = 0L;
            
            // Look for various file result patterns across different step types
            Object foundFiles = stepResult.get("foundFiles");
            Object processedFiles = stepResult.get("processedFiles");
            Object extractedFiles = stepResult.get("extractedFiles");
            Object filesToProcess = stepResult.get("filesToProcess");
            Object totalBytesProcessed = stepResult.get("totalBytesProcessed");
            
            // Count files from foundFiles (sender adapters)
            if (foundFiles instanceof List) {
                List<?> files = (List<?>) foundFiles;
                fileCount += files.size();
                
                // Sum bytes from file metadata if available
                for (Object file : files) {
                    if (file instanceof Map) {
                        Map<?, ?> fileMap = (Map<?, ?>) file;
                        Object size = fileMap.get("size");
                        if (size instanceof Number) {
                            totalBytes += ((Number) size).longValue();
                        }
                    }
                }
            }
            
            // Count files from processedFiles (receiver adapters)
            if (processedFiles instanceof List) {
                List<?> files = (List<?>) processedFiles;
                fileCount += files.size();
                
                // Sum bytes from file metadata if available
                for (Object file : files) {
                    if (file instanceof Map) {
                        Map<?, ?> fileMap = (Map<?, ?>) file;
                        Object size = fileMap.get("size");
                        if (size instanceof Number) {
                            totalBytes += ((Number) size).longValue();
                        }
                    }
                }
            }
            
            // Count files from extractedFiles (utility operations like ZIP_EXTRACT)
            if (extractedFiles instanceof List) {
                List<?> files = (List<?>) extractedFiles;
                fileCount += files.size();
                
                // Sum bytes from file metadata if available
                for (Object file : files) {
                    if (file instanceof Map) {
                        Map<?, ?> fileMap = (Map<?, ?>) file;
                        Object size = fileMap.get("fileSize");
                        if (size instanceof Number) {
                            totalBytes += ((Number) size).longValue();
                        }
                    }
                }
            }
            
            // Count files from filesToProcess (end nodes, decision nodes)
            if (filesToProcess instanceof List) {
                List<?> files = (List<?>) filesToProcess;
                fileCount += files.size();
                
                // Sum bytes from file metadata if available
                for (Object file : files) {
                    if (file instanceof Map) {
                        Map<?, ?> fileMap = (Map<?, ?>) file;
                        // Try different size field names
                        Object size = fileMap.get("fileSize");
                        if (size == null) {
                            size = fileMap.get("size");
                        }
                        if (size instanceof Number) {
                            totalBytes += ((Number) size).longValue();
                        }
                    }
                }
            }
            
            // Use totalBytesProcessed if available and no bytes calculated from file metadata
            if (totalBytes == 0L && totalBytesProcessed instanceof Number) {
                totalBytes = ((Number) totalBytesProcessed).longValue();
            }
            
            // Update step file metrics
            step.setFilesCount(fileCount);
            step.setBytesProcessed(totalBytes);
            
            if (fileCount > 0 || totalBytes > 0) {
                logger.debug("Step {} ({}) file metrics: {} files, {} bytes", 
                            step.getId(), step.getStepName(), fileCount, totalBytes);
            } else {
                logger.debug("Step {} ({}) has no file metrics", 
                            step.getId(), step.getStepName());
            }
            
        } catch (Exception e) {
            logger.warn("Failed to extract file metrics from step result for step {}: {}", 
                       step.getStepName(), e.getMessage());
        }
    }
    
    /**
     * Update execution file metrics by collecting data from execution steps
     */
    private void updateExecutionFileMetrics(FlowExecution execution) {
        try {
            // Get aggregated statistics for this execution
            Map<String, Object> statistics = stepRepository.getExecutionStatistics(execution.getId());
            
            if (statistics != null) {
                Integer totalFiles = (Integer) statistics.getOrDefault("total_files_processed", 0);
                Long totalBytes = (Long) statistics.getOrDefault("total_bytes_processed", 0L);
                
                execution.setTotalFilesProcessed(totalFiles);
                execution.setTotalBytesProcessed(totalBytes);
                execution.setFilesSuccessful(totalFiles); // Assuming all processed files are successful for now
                execution.setFilesFailed(0);
                
                logger.info("Updated execution {} file metrics: {} files, {} bytes", 
                           execution.getId(), totalFiles, totalBytes);
            }
        } catch (Exception e) {
            logger.warn("Failed to update file metrics for execution {}: {}", execution.getId(), e.getMessage());
            // Don't fail execution if metrics collection fails
        }
    }
    
    /**
     * Update deployment statistics after execution completion
     */
    private void updateDeploymentStatistics(FlowExecution execution, boolean successful) {
        try {
            // Get deployment ID from execution context
            if (execution.getExecutionContext() != null) {
                Object deploymentIdObj = execution.getExecutionContext().get("deploymentId");
                if (deploymentIdObj instanceof UUID) {
                    UUID deploymentId = (UUID) deploymentIdObj;
                    
                    // Get deployed flow
                    Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findById(deploymentId);
                    if (deployedFlowOpt.isPresent()) {
                        DeployedFlow deployedFlow = deployedFlowOpt.get();
                        
                        // Update statistics
                        deployedFlow.recordExecution(execution.getDurationMs(), successful);
                        
                        if (!successful && execution.getErrorMessage() != null) {
                            deployedFlow.recordError(execution.getErrorMessage());
                        } else if (successful && deployedFlow.getConsecutiveFailures() > 0) {
                            deployedFlow.clearErrors();
                        }
                        
                        // Save updated deployment statistics
                        deployedFlowRepository.update(deployedFlow);
                        
                        logger.debug("Updated deployment statistics for deployment: {} (success: {})", 
                                   deploymentId, successful);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to update deployment statistics for execution {}: {}", 
                       execution.getId(), e.getMessage());
        }
    }
    
    /**
     * Get parallel execution timeout from system configuration
     */
    private long getParallelExecutionTimeoutSeconds() {
        try {
            String timeoutStr = configRepository.getValue("flow.parallel.execution.timeout.seconds", "30");
            long timeout = Long.parseLong(timeoutStr);
            
            // Ensure minimum reasonable timeout
            if (timeout < 5) {
                logger.warn("Parallel execution timeout too low ({}s), using minimum 5 seconds", timeout);
                return 5;
            }
            
            // Ensure maximum reasonable timeout
            if (timeout > 300) {
                logger.warn("Parallel execution timeout too high ({}s), using maximum 300 seconds", timeout);
                return 300;
            }
            
            return timeout;
            
        } catch (Exception e) {
            logger.warn("Failed to read parallel execution timeout from configuration: {}, using default 30 seconds", 
                       e.getMessage());
            return 30; // fallback default
        }
    }
}