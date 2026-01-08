package com.integrixs.core.service;

import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;

import java.util.Map;

/**
 * INTERFACE for adapter execution - follows DEPENDENCY INVERSION PRINCIPLE.
 * Core module depends on abstraction, concrete implementation in adapters module.
 */
public interface AdapterExecutionService {
    
    /**
     * Execute adapter with given configuration and context.
     * 
     * @param adapter The adapter to execute
     * @param context Execution context
     * @param step Flow execution step for tracking
     * @return Execution result
     */
    Map<String, Object> executeAdapter(Adapter adapter, Map<String, Object> context, FlowExecutionStep step);
    
    /**
     * Test adapter configuration without executing.
     * 
     * @param adapter The adapter to test
     * @return Test result
     */
    Map<String, Object> testAdapterConnection(Adapter adapter);
    
    /**
     * Validate adapter configuration.
     * 
     * @param adapter The adapter to validate
     * @return Validation result
     */
    boolean isValidAdapter(Adapter adapter);
}