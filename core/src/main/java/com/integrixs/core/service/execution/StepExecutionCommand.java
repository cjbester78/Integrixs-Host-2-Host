package com.integrixs.core.service.execution;

import com.integrixs.shared.model.FlowExecutionStep;
import java.util.Map;

/**
 * Command interface for executing different types of flow steps.
 * Implements the Command pattern for extensible step execution.
 */
public interface StepExecutionCommand {
    
    /**
     * Execute the step with given configuration and context
     * 
     * @param step The execution step being performed
     * @param nodeConfiguration The node configuration from flow definition
     * @param executionContext The current execution context
     * @return The result of step execution
     */
    Map<String, Object> execute(FlowExecutionStep step, Map<String, Object> nodeConfiguration, 
                               Map<String, Object> executionContext);
    
    /**
     * Get the step type this command handles
     */
    String getStepType();
    
    /**
     * Validate if this command can handle the given step configuration
     */
    boolean canHandle(Map<String, Object> nodeConfiguration);
}