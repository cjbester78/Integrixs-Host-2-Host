package com.integrixs.core.service.execution;

import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for step execution commands.
 * Provides common functionality and template method pattern for step execution.
 */
public abstract class AbstractStepExecutionCommand implements StepExecutionCommand {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Override
    public final Map<String, Object> execute(FlowExecutionStep step, Map<String, Object> nodeConfiguration, 
                                           Map<String, Object> executionContext) {
        
        logger.debug("Executing {} step: {}", getStepType(), step.getStepName());
        
        try {
            // Pre-execution validation
            validateStepConfiguration(nodeConfiguration);
            validateExecutionContext(executionContext);
            
            // Execute the specific step logic
            Map<String, Object> result = executeStep(step, nodeConfiguration, executionContext);
            
            // Post-execution processing
            enrichResult(result, step, nodeConfiguration);
            
            logger.debug("Successfully executed {} step: {}", getStepType(), step.getStepName());
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to execute {} step {}: {}", getStepType(), step.getStepName(), e.getMessage(), e);
            
            Map<String, Object> errorResult = createErrorResult(e);
            enrichResult(errorResult, step, nodeConfiguration);
            return errorResult;
        }
    }
    
    /**
     * Execute the specific step logic - implemented by concrete commands
     */
    protected abstract Map<String, Object> executeStep(FlowExecutionStep step, 
                                                      Map<String, Object> nodeConfiguration,
                                                      Map<String, Object> executionContext);
    
    /**
     * Validate step configuration before execution
     */
    protected void validateStepConfiguration(Map<String, Object> nodeConfiguration) {
        if (nodeConfiguration == null) {
            throw new IllegalArgumentException("Node configuration cannot be null");
        }
    }
    
    /**
     * Validate execution context before execution
     */
    protected void validateExecutionContext(Map<String, Object> executionContext) {
        if (executionContext == null) {
            throw new IllegalArgumentException("Execution context cannot be null");
        }
    }
    
    /**
     * Enrich result with common metadata
     */
    protected void enrichResult(Map<String, Object> result, FlowExecutionStep step, 
                              Map<String, Object> nodeConfiguration) {
        result.put("stepType", getStepType());
        result.put("stepId", step.getId());
        result.put("stepName", step.getStepName());
        result.put("executionTimestamp", LocalDateTime.now().toString());
    }
    
    /**
     * Create error result for failed executions
     */
    protected Map<String, Object> createErrorResult(Exception e) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("success", false);
        errorResult.put("error", e.getMessage());
        errorResult.put("errorType", e.getClass().getSimpleName());
        return errorResult;
    }
    
    /**
     * Extract node data from React Flow format or legacy format
     */
    protected Map<String, Object> extractNodeData(Map<String, Object> nodeConfiguration) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeData = (Map<String, Object>) nodeConfiguration.get("data");
        return nodeData != null ? nodeData : nodeConfiguration;
    }
    
    /**
     * Get configuration value with fallback
     */
    protected String getConfigValue(Map<String, Object> nodeData, String key, String defaultValue) {
        Object value = nodeData.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Get configuration as map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getConfigMap(Map<String, Object> nodeData, String key) {
        Object value = nodeData.get(key);
        return value instanceof Map ? (Map<String, Object>) value : new HashMap<>();
    }
}