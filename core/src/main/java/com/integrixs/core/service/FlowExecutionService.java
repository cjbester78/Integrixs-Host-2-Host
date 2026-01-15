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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Facade service for flow execution operations
 * Coordinates between specialized services following Facade Pattern
 * 
 * This service replaces the original monolithic FlowExecutionService
 * and provides a unified interface while delegating to focused services
 */
@Service
public class FlowExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionService.class);
    private final EnhancedLogger enhancedLogger = EnhancedLogger.getLogger(FlowExecutionService.class);
    private static final Marker FLOW_EXECUTION_MARKER = MarkerFactory.getMarker("FLOW_EXECUTION");
    
    // Specialized services
    private final FlowStepExecutor stepExecutor;
    private final FlowRetryManager retryManager;
    private final FlowExecutionMonitor executionMonitor;
    
    // Repositories (for basic CRUD operations)
    private final FlowExecutionRepository executionRepository;
    private final FlowExecutionStepRepository stepRepository;
    private final IntegrationFlowRepository flowRepository;
    private final DeployedFlowRepository deployedFlowRepository;
    private final AdapterRepository adapterRepository;
    private final SystemConfigurationRepository configRepository;
    
    // Legacy dependencies
    private final AdapterManagementService adapterManagementService;
    private final AdapterExecutionService adapterExecutionService;
    private final UtilityExecutionService utilityExecutionService;
    private final FlowWebSocketService webSocketService;
    
    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(10);
    
    @Autowired
    public FlowExecutionService(FlowStepExecutor stepExecutor,
                                     FlowRetryManager retryManager,
                                     FlowExecutionMonitor executionMonitor,
                                     FlowExecutionRepository executionRepository,
                                     FlowExecutionStepRepository stepRepository,
                                     IntegrationFlowRepository flowRepository,
                                     DeployedFlowRepository deployedFlowRepository,
                                     AdapterRepository adapterRepository,
                                     AdapterManagementService adapterManagementService,
                                     AdapterExecutionService adapterExecutionService,
                                     UtilityExecutionService utilityExecutionService,
                                     FlowWebSocketService webSocketService,
                                     SystemConfigurationRepository configRepository) {
        this.stepExecutor = stepExecutor;
        this.retryManager = retryManager;
        this.executionMonitor = executionMonitor;
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
    
    // === CRUD Operations ===
    
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
        return executionRepository.findByStatus(FlowExecution.ExecutionStatus.RUNNING);
    }
    
    /**
     * Get failed executions for retry
     */
    public List<FlowExecution> getFailedExecutionsForRetry() {
        logger.debug("Retrieving failed executions eligible for retry");
        return retryManager.getExecutionsEligibleForRetry();
    }
    
    /**
     * Get execution by ID
     */
    public Optional<FlowExecution> getExecutionById(UUID id) {
        logger.debug("Retrieving execution: {}", id);
        return executionRepository.findById(id);
    }
    
    /**
     * Get execution steps
     */
    public List<FlowExecutionStep> getExecutionSteps(UUID executionId) {
        logger.debug("Retrieving steps for execution: {}", executionId);
        return stepRepository.findByExecutionId(executionId);
    }
    
    // === Flow Execution Operations (delegated to specialized services) ===
    
    /**
     * Execute flow manually
     */
    public FlowExecution executeFlow(UUID flowId, Map<String, Object> payload, UUID triggeredBy) {
        return executeFlow(flowId, payload, triggeredBy, FlowExecution.TriggerType.MANUAL);
    }
    
    /**
     * Execute flow with trigger type
     */
    public FlowExecution executeFlow(UUID flowId, Map<String, Object> payload, UUID triggeredBy, FlowExecution.TriggerType triggerType) {
        // Set up correlation context for enterprise logging
        CorrelationContext.setCorrelationId(CorrelationContext.generateCorrelationId());
        CorrelationContext.setOperationId("FLOW_EXEC_" + flowId.toString().substring(0, 8).toUpperCase());
        CorrelationContext.setMessageId(CorrelationContext.generateMessageId());
        CorrelationContext.setFlowId(flowId.toString());
        
        // Enterprise-style logging - flow entry
        enhancedLogger.adapterMessageEntry("FLOW", triggeredBy != null ? triggeredBy.toString() : "system");
        enhancedLogger.moduleProcessing("h2h/flow-execution/" + flowId.toString().substring(0, 8));
        
        logger.info(FLOW_EXECUTION_MARKER, "{} execution requested for flow: {} by user: {}", 
                   triggerType.name().toLowerCase(), flowId, triggeredBy);
        
        // Check if flow can execute using deployment registry
        Map<String, Object> executionCheck = deployedFlowRepository.checkFlowExecution(flowId);
        boolean canExecute = (Boolean) executionCheck.get("canExecute");
        String blockingReason = (String) executionCheck.get("reason");
        
        if (!canExecute) {
            logger.warn("Flow execution blocked for flow {}: {}", flowId, blockingReason);
            throw new IllegalArgumentException("Flow execution blocked: " + blockingReason);
        }
        
        // Get flow definition
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
        FlowExecution execution = new FlowExecution(flowId, flow.getName(), triggerType, triggeredBy);
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
        executionContext.put("correlationId", CorrelationContext.getCorrelationId());
        executionContext.put("messageId", CorrelationContext.getMessageId());
        
        execution.setExecutionContext(executionContext);
        
        // Save execution record
        UUID executionId = executionRepository.save(execution);
        execution.setId(executionId);
        
        try {
            // Send real-time update
            executionMonitor.sendRealTimeUpdate(execution, "EXECUTION_STARTED");
            
            // Execute flow steps using specialized service
            stepExecutor.executeFlowSteps(execution, flow);
            
            // Update execution status
            execution.setExecutionStatus(FlowExecution.ExecutionStatus.COMPLETED);
            execution.setCompletedAt(LocalDateTime.now());
            executionRepository.update(execution);
            
            // Send completion update
            executionMonitor.sendRealTimeUpdate(execution, "EXECUTION_COMPLETED");
            
            logger.info("Flow execution completed successfully: {}", executionId);
            
        } catch (Exception e) {
            logger.error("Flow execution failed: {} - {}", executionId, e.getMessage(), e);
            
            // Update execution with error
            execution.setExecutionStatus(FlowExecution.ExecutionStatus.FAILED);
            execution.setCompletedAt(LocalDateTime.now());
            execution.setErrorMessage(e.getMessage());
            execution.setErrorDetails(getErrorDetails(e));
            executionRepository.update(execution);
            
            // Send failure update
            executionMonitor.sendRealTimeUpdate(execution, "EXECUTION_FAILED");
            
            throw new RuntimeException("Flow execution failed: " + e.getMessage(), e);
        }
        
        return execution;
    }
    
    /**
     * Execute flow asynchronously
     */
    @Async
    public CompletableFuture<FlowExecution> executeFlowAsync(FlowExecution execution, IntegrationFlow flow) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Restore correlation context for async execution
                if (execution.getExecutionContext() != null) {
                    String correlationId = (String) execution.getExecutionContext().get("correlationId");
                    String messageId = (String) execution.getExecutionContext().get("messageId");
                    if (correlationId != null) {
                        CorrelationContext.setCorrelationId(correlationId);
                    }
                    if (messageId != null) {
                        CorrelationContext.setMessageId(messageId);
                    }
                }
                
                CorrelationContext.setExecutionId(execution.getId().toString());
                CorrelationContext.setFlowId(execution.getFlowId().toString());
                
                // Execute using step executor
                stepExecutor.executeFlowSteps(execution, flow);
                
                // Update completion
                execution.setExecutionStatus(FlowExecution.ExecutionStatus.COMPLETED);
                execution.setCompletedAt(LocalDateTime.now());
                executionRepository.update(execution);
                
                executionMonitor.sendRealTimeUpdate(execution, "ASYNC_EXECUTION_COMPLETED");
                
                return execution;
                
            } catch (Exception e) {
                execution.setExecutionStatus(FlowExecution.ExecutionStatus.FAILED);
                execution.setCompletedAt(LocalDateTime.now());
                execution.setErrorMessage(e.getMessage());
                execution.setErrorDetails(getErrorDetails(e));
                executionRepository.update(execution);
                
                executionMonitor.sendRealTimeUpdate(execution, "ASYNC_EXECUTION_FAILED");
                
                throw new RuntimeException("Async flow execution failed: " + e.getMessage(), e);
            } finally {
                CorrelationContext.clear();
            }
        }, parallelExecutor);
    }
    
    // === Retry Operations (delegated to FlowRetryManager) ===
    
    /**
     * Retry execution
     */
    public FlowExecution retryExecution(UUID executionId, UUID triggeredBy) {
        logger.info("Retrying execution: {} by user: {}", executionId, triggeredBy);
        
        FlowExecution execution = retryManager.executeRetry(executionId, triggeredBy);
        
        // Get flow definition for retry
        Optional<IntegrationFlow> flowOpt = flowRepository.findById(execution.getFlowId());
        if (flowOpt.isEmpty()) {
            throw new IllegalArgumentException("Flow not found: " + execution.getFlowId());
        }
        
        try {
            // Execute retry using step executor
            stepExecutor.executeFlowSteps(execution, flowOpt.get());
            
            execution.setExecutionStatus(FlowExecution.ExecutionStatus.COMPLETED);
            execution.setCompletedAt(LocalDateTime.now());
            executionRepository.update(execution);
            
            executionMonitor.sendRealTimeUpdate(execution, "RETRY_COMPLETED");
            
            return execution;
            
        } catch (Exception e) {
            execution.setExecutionStatus(FlowExecution.ExecutionStatus.FAILED);
            execution.setCompletedAt(LocalDateTime.now());
            execution.setErrorMessage(e.getMessage());
            execution.setErrorDetails(getErrorDetails(e));
            executionRepository.update(execution);
            
            executionMonitor.sendRealTimeUpdate(execution, "RETRY_FAILED");
            
            throw new RuntimeException("Retry execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Schedule retry for execution
     */
    public void scheduleRetry(UUID executionId, UUID scheduledBy) {
        retryManager.scheduleRetry(executionId, scheduledBy);
    }
    
    /**
     * Cancel execution
     */
    public void cancelExecution(UUID executionId, UUID cancelledBy) {
        logger.info("Cancelling execution: {} by user: {}", executionId, cancelledBy);
        
        Optional<FlowExecution> executionOpt = getExecutionById(executionId);
        if (!executionOpt.isPresent()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }
        
        FlowExecution execution = executionOpt.get();
        
        if (execution.getExecutionStatus() == FlowExecution.ExecutionStatus.RUNNING) {
            execution.setExecutionStatus(FlowExecution.ExecutionStatus.CANCELLED);
            execution.setCompletedAt(LocalDateTime.now());
            execution.setErrorMessage("Execution cancelled by user: " + cancelledBy);
            executionRepository.update(execution);
            
            executionMonitor.sendRealTimeUpdate(execution, "EXECUTION_CANCELLED");
            
            logger.info("Execution cancelled: {}", executionId);
        } else if (execution.getExecutionStatus() == FlowExecution.ExecutionStatus.RETRY_PENDING) {
            retryManager.cancelScheduledRetry(executionId, cancelledBy, "Cancelled by user");
            
            executionMonitor.sendRealTimeUpdate(execution, "RETRY_CANCELLED");
            
            logger.info("Scheduled retry cancelled: {}", executionId);
        } else {
            throw new IllegalStateException("Cannot cancel execution in status: " + execution.getExecutionStatus());
        }
    }
    
    // === Statistics and Monitoring Operations (delegated to FlowExecutionMonitor) ===
    
    /**
     * Get execution statistics
     */
    public Map<String, Object> getExecutionStatistics() {
        return executionMonitor.getExecutionStatistics();
    }
    
    /**
     * Get flow-specific execution statistics
     */
    public Map<String, Object> getFlowExecutionStatistics(UUID flowId) {
        return executionMonitor.getFlowExecutionStatistics(flowId);
    }
    
    /**
     * Get real-time metrics
     */
    public Map<String, Object> getRealTimeMetrics() {
        return executionMonitor.getRealTimeMetrics();
    }
    
    /**
     * Get performance trends
     */
    public Map<String, Object> getPerformanceTrends(int days) {
        return executionMonitor.getPerformanceTrends(days);
    }
    
    /**
     * Get long-running executions
     */
    public List<FlowExecution> getLongRunningExecutions(int thresholdMinutes) {
        return executionMonitor.getLongRunningExecutions(thresholdMinutes);
    }
    
    // === Helper Methods ===
    
    private Map<String, Object> getErrorDetails(Exception e) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("exception", e.getClass().getSimpleName());
        errorDetails.put("message", e.getMessage());
        errorDetails.put("timestamp", LocalDateTime.now());
        
        if (e.getCause() != null) {
            errorDetails.put("cause", e.getCause().getMessage());
        }
        
        return errorDetails;
    }
}