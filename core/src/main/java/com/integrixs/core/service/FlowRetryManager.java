package com.integrixs.core.service;

import com.integrixs.core.repository.FlowExecutionRepository;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.core.logging.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for managing flow execution retry logic
 * Handles retry policies, scheduling, and retry decision making following Single Responsibility Principle
 */
@Service
public class FlowRetryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowRetryManager.class);
    private final EnhancedLogger enhancedLogger = EnhancedLogger.getLogger(FlowRetryManager.class);
    
    private final FlowExecutionRepository executionRepository;
    
    @Autowired
    public FlowRetryManager(FlowExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }
    
    /**
     * Determine if an execution should be retried
     */
    public boolean shouldRetry(FlowExecution execution) {
        Objects.requireNonNull(execution, "Execution cannot be null");
        
        if (execution.getExecutionStatus() != FlowExecution.ExecutionStatus.FAILED) {
            return false;
        }
        
        // Check retry configuration
        Map<String, Object> retryPolicy = getRetryPolicy(execution);
        if (retryPolicy == null) {
            return false;
        }
        
        boolean retryEnabled = (Boolean) retryPolicy.getOrDefault("enabled", false);
        if (!retryEnabled) {
            return false;
        }
        
        int maxRetries = (Integer) retryPolicy.getOrDefault("maxRetries", 3);
        int currentRetries = execution.getRetryAttempt() != null ? execution.getRetryAttempt() : 0;
        
        if (currentRetries >= maxRetries) {
            logger.info("Maximum retries ({}) reached for execution: {}", 
                maxRetries, execution.getId());
            return false;
        }
        
        // Check retry delay
        String delayType = (String) retryPolicy.getOrDefault("delayType", "fixed");
        int delayMinutes = (Integer) retryPolicy.getOrDefault("delayMinutes", 5);
        
        // Check retry timing based on completion time
        if (execution.getCompletedAt() != null) {
            LocalDateTime nextRetryTime = calculateNextRetryTime(execution.getCompletedAt(), 
                delayType, delayMinutes, currentRetries);
            
            if (LocalDateTime.now().isBefore(nextRetryTime)) {
                logger.debug("Too early to retry execution: {}. Next retry at: {}", 
                    execution.getId(), nextRetryTime);
                return false;
            }
        }
        
        // Check if error is retryable
        boolean isRetryableError = isErrorRetryable(execution.getErrorMessage());
        
        logger.info("Retry decision for execution {}: shouldRetry={}, currentRetries={}, maxRetries={}, retryableError={}", 
            execution.getId(), isRetryableError, currentRetries, maxRetries, isRetryableError);
        
        return isRetryableError;
    }
    
    /**
     * Schedule a retry for an execution
     */
    public void scheduleRetry(UUID executionId, UUID scheduledBy) {
        Objects.requireNonNull(executionId, "Execution ID cannot be null");
        Objects.requireNonNull(scheduledBy, "Scheduled by user ID cannot be null");
        
        logger.info("Scheduling retry for execution: {} by user: {}", executionId, scheduledBy);
        
        Optional<FlowExecution> executionOpt = executionRepository.findById(executionId);
        if (!executionOpt.isPresent()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }
        
        FlowExecution execution = executionOpt.get();
        
        if (!shouldRetry(execution)) {
            throw new IllegalStateException("Execution is not eligible for retry: " + executionId);
        }
        
        // Update retry information
        int newRetryCount = (execution.getRetryAttempt() != null ? execution.getRetryAttempt() : 0) + 1;
        execution.setRetryAttempt(newRetryCount);
        execution.setExecutionStatus(FlowExecution.ExecutionStatus.RETRY_PENDING);
        execution.setScheduledFor(calculateNextRetryTime(execution));
        
        // Add retry metadata to execution context
        Map<String, Object> context = execution.getExecutionContext() != null ? 
            new HashMap<>(execution.getExecutionContext()) : new HashMap<>();
        context.put("retryScheduledBy", scheduledBy);
        context.put("retryScheduledAt", LocalDateTime.now());
        execution.setExecutionContext(context);
        
        // Clear previous error state
        execution.setErrorMessage(null);
        execution.setErrorDetails(null);
        
        executionRepository.update(execution);
        
        // Enhanced logging for retry scheduling
        enhancedLogger.flowExecutionStep("RETRY_SCHEDULED", executionId.toString(), 
            "Retry #" + newRetryCount + " scheduled");
        
        logger.info("Retry #{} scheduled for execution: {} at: {}", 
            newRetryCount, executionId, execution.getScheduledFor());
    }
    
    /**
     * Execute a retry for an execution
     */
    public FlowExecution executeRetry(UUID executionId, UUID retriedBy) {
        Objects.requireNonNull(executionId, "Execution ID cannot be null");
        Objects.requireNonNull(retriedBy, "Retried by user ID cannot be null");
        
        logger.info("Executing retry for execution: {} by user: {}", executionId, retriedBy);
        
        Optional<FlowExecution> executionOpt = executionRepository.findById(executionId);
        if (!executionOpt.isPresent()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }
        
        FlowExecution execution = executionOpt.get();
        
        if (execution.getExecutionStatus() != FlowExecution.ExecutionStatus.RETRY_PENDING) {
            throw new IllegalStateException("Execution is not in retry pending state: " + executionId);
        }
        
        // Reset execution for retry
        execution.setExecutionStatus(FlowExecution.ExecutionStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution.setCompletedAt(null);
        execution.setErrorMessage(null);
        execution.setErrorDetails(null);
        execution.setScheduledFor(null);
        
        // Add retry execution metadata to context
        Map<String, Object> context = execution.getExecutionContext() != null ? 
            new HashMap<>(execution.getExecutionContext()) : new HashMap<>();
        context.put("retryExecutedAt", LocalDateTime.now());
        context.put("retryExecutedBy", retriedBy);
        execution.setExecutionContext(context);
        
        // Set correlation context for retry
        CorrelationContext.setCorrelationId(CorrelationContext.generateCorrelationId());
        CorrelationContext.setExecutionId(executionId.toString());
        CorrelationContext.setFlowId(execution.getFlowId().toString());
        // CorrelationContext doesn't have setRetryAttempt method, skip this for now
        
        executionRepository.update(execution);
        
        // Enhanced logging for retry execution
        enhancedLogger.flowExecutionStep("RETRY_EXECUTED", executionId.toString(), 
            "Retry #" + execution.getRetryAttempt() + " started");
        
        logger.info("Retry #{} started for execution: {}", execution.getRetryAttempt(), executionId);
        
        return execution;
    }
    
    /**
     * Get failed executions that are eligible for retry
     */
    public List<FlowExecution> getExecutionsEligibleForRetry() {
        logger.debug("Retrieving executions eligible for retry");
        
        List<FlowExecution> failedExecutions = executionRepository.findByStatus(FlowExecution.ExecutionStatus.FAILED);
        List<FlowExecution> eligibleExecutions = new ArrayList<>();
        
        for (FlowExecution execution : failedExecutions) {
            if (shouldRetry(execution)) {
                eligibleExecutions.add(execution);
            }
        }
        
        logger.debug("Found {} executions eligible for retry out of {} failed executions", 
            eligibleExecutions.size(), failedExecutions.size());
        
        return eligibleExecutions;
    }
    
    /**
     * Get scheduled retry executions that are ready to execute
     */
    public List<FlowExecution> getScheduledRetriesReadyToExecute() {
        logger.debug("Retrieving scheduled retries ready to execute");
        
        List<FlowExecution> scheduledRetries = executionRepository.findByStatus(FlowExecution.ExecutionStatus.RETRY_PENDING);
        List<FlowExecution> readyRetries = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        
        for (FlowExecution execution : scheduledRetries) {
            if (execution.getScheduledFor() != null && execution.getScheduledFor().isBefore(now)) {
                readyRetries.add(execution);
            }
        }
        
        logger.debug("Found {} scheduled retries ready to execute out of {} scheduled", 
            readyRetries.size(), scheduledRetries.size());
        
        return readyRetries;
    }
    
    /**
     * Cancel scheduled retry
     */
    public void cancelScheduledRetry(UUID executionId, UUID cancelledBy, String reason) {
        Objects.requireNonNull(executionId, "Execution ID cannot be null");
        Objects.requireNonNull(cancelledBy, "Cancelled by user ID cannot be null");
        
        logger.info("Cancelling scheduled retry for execution: {} by user: {} reason: {}", 
            executionId, cancelledBy, reason);
        
        Optional<FlowExecution> executionOpt = executionRepository.findById(executionId);
        if (!executionOpt.isPresent()) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }
        
        FlowExecution execution = executionOpt.get();
        
        if (execution.getExecutionStatus() != FlowExecution.ExecutionStatus.RETRY_PENDING) {
            throw new IllegalStateException("Execution is not in retry pending state: " + executionId);
        }
        
        execution.setExecutionStatus(FlowExecution.ExecutionStatus.CANCELLED);
        execution.setScheduledFor(null);
        execution.setErrorMessage("Retry cancelled: " + (reason != null ? reason : "No reason provided"));
        execution.setCompletedAt(LocalDateTime.now());
        
        executionRepository.update(execution);
        
        logger.info("Scheduled retry cancelled for execution: {}", executionId);
    }
    
    // Private helper methods
    
    /**
     * Get retry policy for execution
     */
    private Map<String, Object> getRetryPolicy(FlowExecution execution) {
        Map<String, Object> context = execution.getExecutionContext();
        if (context != null && context.containsKey("retryPolicy")) {
            return (Map<String, Object>) context.get("retryPolicy");
        }
        
        // Default retry policy
        Map<String, Object> defaultPolicy = new HashMap<>();
        defaultPolicy.put("enabled", true);
        defaultPolicy.put("maxRetries", 3);
        defaultPolicy.put("delayType", "exponential");
        defaultPolicy.put("delayMinutes", 5);
        
        return defaultPolicy;
    }
    
    /**
     * Calculate next retry time for execution
     */
    private LocalDateTime calculateNextRetryTime(FlowExecution execution) {
        Map<String, Object> retryPolicy = getRetryPolicy(execution);
        String delayType = (String) retryPolicy.getOrDefault("delayType", "fixed");
        int delayMinutes = (Integer) retryPolicy.getOrDefault("delayMinutes", 5);
        int retryCount = execution.getRetryAttempt() != null ? execution.getRetryAttempt() : 0;
        
        return calculateNextRetryTime(LocalDateTime.now(), delayType, delayMinutes, retryCount);
    }
    
    /**
     * Calculate next retry time based on delay type and attempt number
     */
    private LocalDateTime calculateNextRetryTime(LocalDateTime baseTime, String delayType, 
                                               int delayMinutes, int attemptNumber) {
        switch (delayType.toLowerCase()) {
            case "exponential":
                // Exponential backoff: delay * 2^attemptNumber
                long exponentialDelay = delayMinutes * (long) Math.pow(2, attemptNumber);
                return baseTime.plus(exponentialDelay, ChronoUnit.MINUTES);
            
            case "linear":
                // Linear backoff: delay * (attemptNumber + 1)
                long linearDelay = delayMinutes * (attemptNumber + 1);
                return baseTime.plus(linearDelay, ChronoUnit.MINUTES);
            
            case "fixed":
            default:
                // Fixed delay
                return baseTime.plus(delayMinutes, ChronoUnit.MINUTES);
        }
    }
    
    /**
     * Determine if an error is retryable
     */
    private boolean isErrorRetryable(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        
        String lowerError = errorMessage.toLowerCase();
        
        // Non-retryable errors (configuration, validation, etc.)
        String[] nonRetryableErrors = {
            "authentication", "authorization", "forbidden", "unauthorized",
            "validation", "configuration", "invalid", "malformed",
            "not found", "does not exist", "missing required"
        };
        
        for (String nonRetryable : nonRetryableErrors) {
            if (lowerError.contains(nonRetryable)) {
                return false;
            }
        }
        
        // Retryable errors (network, timeout, temporary issues)
        String[] retryableErrors = {
            "timeout", "connection", "network", "temporary", "unavailable",
            "busy", "overloaded", "rate limit", "socket", "io exception"
        };
        
        for (String retryable : retryableErrors) {
            if (lowerError.contains(retryable)) {
                return true;
            }
        }
        
        // Default to retryable for unknown errors
        return true;
    }
}