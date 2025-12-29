package com.integrixs.core.adapter;

import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import java.util.Map;

/**
 * Common interface for all adapter executors following OOP principles.
 * Each adapter type (File, SFTP, Email) and direction (Sender, Receiver) 
 * implements this interface with its specific execution logic.
 * 
 * This ensures Single Responsibility Principle - each adapter class 
 * handles only its specific execution logic.
 */
public interface AdapterExecutor {
    
    /**
     * Execute the adapter operation with the given configuration and context.
     * 
     * @param adapter The adapter configuration containing type, direction, and settings
     * @param context The execution context containing data and state
     * @param step The flow execution step for tracking
     * @return Map containing execution results, file data, status, and metrics
     */
    Map<String, Object> execute(Adapter adapter, Map<String, Object> context, FlowExecutionStep step);
    
    /**
     * Get the adapter type this executor supports.
     * 
     * @return The adapter type (FILE, SFTP, EMAIL)
     */
    String getSupportedType();
    
    /**
     * Get the adapter direction this executor supports.
     * 
     * @return The adapter direction (SENDER, RECEIVER)
     */
    String getSupportedDirection();
    
    /**
     * Validate the adapter configuration before execution.
     * This allows each adapter to define its own validation rules.
     * 
     * @param adapter The adapter to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    default void validateConfiguration(Adapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter cannot be null");
        }
        if (adapter.getConfiguration() == null || adapter.getConfiguration().isEmpty()) {
            throw new IllegalArgumentException("Adapter configuration cannot be null or empty");
        }
    }
}