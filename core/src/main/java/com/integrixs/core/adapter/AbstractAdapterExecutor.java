package com.integrixs.core.adapter;

import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class providing common functionality for all adapter executors.
 * Implements shared logic like logging, error handling, and result formatting.
 * 
 * This follows the Template Method pattern - subclasses implement specific
 * execution logic while common operations are handled here.
 */
public abstract class AbstractAdapterExecutor implements AdapterExecutor {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Override
    public final Map<String, Object> execute(Adapter adapter, Map<String, Object> context, FlowExecutionStep step) {
        logger.info("=== STARTING {} {} ADAPTER EXECUTION ===", 
                   getSupportedType(), getSupportedDirection());
        logger.info("Adapter: {} (ID: {})", adapter.getName(), adapter.getId());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Validate configuration before execution
            validateConfiguration(adapter);
            
            // Execute adapter-specific logic
            result = executeInternal(adapter, context, step);
            
            // Add common result metadata
            result.put("operationType", getSupportedDirection());
            result.put("adapterType", getSupportedType());
            result.put("adapterId", adapter.getId());
            result.put("adapterName", adapter.getName());
            result.put("executionTimestamp", System.currentTimeMillis());
            
            logger.info("=== {} {} ADAPTER COMPLETE ===", 
                       getSupportedType(), getSupportedDirection());
            logExecutionSummary(result);
            
        } catch (Exception e) {
            logger.error("=== {} {} ADAPTER FAILED ===", 
                        getSupportedType(), getSupportedDirection());
            logger.error("✗ Execution failed: {}", e.getMessage(), e);
            
            result.put("error", e.getMessage());
            result.put("hasData", false);
            result.put("successCount", 0);
            result.put("errorCount", 1);
            
            throw new RuntimeException(String.format("%s %s adapter execution failed: %s", 
                                     getSupportedType(), getSupportedDirection(), e.getMessage()), e);
        }
        
        return result;
    }
    
    /**
     * Template method for adapter-specific execution logic.
     * Subclasses implement their specific processing here.
     */
    protected abstract Map<String, Object> executeInternal(Adapter adapter, Map<String, Object> context, 
                                                          FlowExecutionStep step);
    
    /**
     * Log execution summary with common metrics.
     * Can be overridden by subclasses for specific logging needs.
     */
    protected void logExecutionSummary(Map<String, Object> result) {
        Object successCount = result.get("successCount");
        Object errorCount = result.get("errorCount");
        Object totalBytes = result.get("totalBytesProcessed");
        
        if (successCount != null && errorCount != null) {
            logger.info("✓ Success: {} | Errors: {}", successCount, errorCount);
        }
        
        if (totalBytes != null) {
            logger.info("✓ Total bytes processed: {}", totalBytes);
        }
        
        Object message = result.get("message");
        if (message != null) {
            logger.info("✓ {}", message);
        }
    }
    
    /**
     * Create standardized error result for consistent error handling.
     */
    protected Map<String, Object> createErrorResult(String errorMessage, Exception cause) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", errorMessage);
        result.put("hasData", false);
        result.put("successCount", 0);
        result.put("errorCount", 1);
        result.put("operationType", getSupportedDirection());
        result.put("adapterType", getSupportedType());
        
        if (cause != null) {
            result.put("exceptionType", cause.getClass().getSimpleName());
        }
        
        return result;
    }
    
    /**
     * Create standardized success result for consistent response format.
     */
    protected Map<String, Object> createSuccessResult(int successCount, int errorCount, 
                                                     long totalBytes, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("totalBytesProcessed", totalBytes);
        result.put("hasData", successCount > 0);
        result.put("message", message);
        result.put("operationType", getSupportedDirection());
        result.put("adapterType", getSupportedType());
        
        return result;
    }
    
    /**
     * Validate required configuration fields common to all adapters.
     * Subclasses can override to add specific validation.
     */
    @Override
    public void validateConfiguration(Adapter adapter) {
        // Call parent validation
        AdapterExecutor.super.validateConfiguration(adapter);
        
        // Validate adapter type matches this executor
        if (!getSupportedType().equalsIgnoreCase(adapter.getAdapterType())) {
            throw new IllegalArgumentException(String.format(
                "Adapter type mismatch: expected %s, got %s", 
                getSupportedType(), adapter.getAdapterType()));
        }
        
        // Validate adapter direction matches this executor
        String expectedDirection = getSupportedDirection();
        String actualDirection = adapter.isSender() ? "SENDER" : "RECEIVER";
        
        if (!expectedDirection.equalsIgnoreCase(actualDirection)) {
            throw new IllegalArgumentException(String.format(
                "Adapter direction mismatch: expected %s, got %s", 
                expectedDirection, actualDirection));
        }
    }
}