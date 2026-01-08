package com.integrixs.core.adapter;

import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enhanced abstract base class providing comprehensive functionality for all adapter executors.
 * Implements Template Method pattern with proper lifecycle management, health checking,
 * and metrics collection following OOP principles.
 * 
 * This follows SOLID principles:
 * - Single Responsibility: Focused on adapter execution coordination
 * - Open/Closed: Extensible via template methods, closed for modification
 * - Liskov Substitution: All subclasses can substitute this base class
 * - Interface Segregation: Implements focused interfaces
 * - Dependency Inversion: Depends on abstractions, not concretions
 */
public abstract class AbstractAdapterExecutor implements AdapterExecutor, AdapterLifecycle, 
                                                        AdapterHealthCheck, AdapterMetrics {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    // Lifecycle state management
    private final AtomicReference<AdapterLifecycleState> lifecycleState = 
        new AtomicReference<>(AdapterLifecycleState.UNINITIALIZED);
    private volatile Adapter adapterConfig;
    private volatile LocalDateTime initializationTime;
    private volatile LocalDateTime startTime;
    
    // Health check state
    private volatile AdapterHealthResult lastHealthCheck;
    
    // Metrics state  
    private volatile long totalExecutions = 0;
    private volatile long successfulExecutions = 0;
    private volatile long failedExecutions = 0;
    private volatile long totalBytesProcessed = 0;
    private volatile long totalFilesProcessed = 0;
    private volatile long totalExecutionTimeMillis = 0;
    private volatile long minExecutionTimeMillis = Long.MAX_VALUE;
    private volatile long maxExecutionTimeMillis = 0;
    private volatile LocalDateTime lastExecutionTime;
    private volatile LocalDateTime lastSuccessfulExecution;
    private volatile LocalDateTime lastFailedExecution;
    private volatile String lastError;
    
    @Override
    public final Map<String, Object> execute(Adapter adapter, Map<String, Object> context, FlowExecutionStep step) {
        logger.info("=== STARTING {} {} ADAPTER EXECUTION ===", 
                   getSupportedType(), getSupportedDirection());
        logger.info("Adapter: {} (ID: {})", adapter.getName(), adapter.getId());
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Ensure adapter is properly initialized
            ensureInitialized(adapter);
            
            // Lifecycle hook: pre-execution
            preExecution(adapter, context, step);
            
            // Validate configuration before execution
            validateConfiguration(adapter);
            
            // Execute adapter-specific logic with lifecycle management
            result = executeWithLifecycleManagement(adapter, context, step);
            
            // Add common result metadata
            result.put("operationType", getSupportedDirection());
            result.put("adapterType", getSupportedType());
            result.put("adapterId", adapter.getId());
            result.put("adapterName", adapter.getName());
            result.put("executionTimestamp", System.currentTimeMillis());
            
            // Lifecycle hook: post-execution
            postExecution(adapter, context, step, result);
            
            // Record metrics
            recordSuccessfulExecution(adapter, result, System.currentTimeMillis() - startTime);
            
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
            
            // Record failed execution
            recordFailedExecution(adapter, e.getMessage(), System.currentTimeMillis() - startTime);
            
            // Lifecycle hook: execution error
            onExecutionError(adapter, context, step, e);
            
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
    
    // ========== LIFECYCLE MANAGEMENT METHODS ==========
    
    @Override
    public void initialize() throws AdapterInitializationException {
        if (lifecycleState.get() != AdapterLifecycleState.UNINITIALIZED) {
            throw new AdapterInitializationException("Adapter is already initialized", 
                getAdapterId(), getSupportedType());
        }
        
        try {
            lifecycleState.set(AdapterLifecycleState.INITIALIZED);
            initializationTime = LocalDateTime.now();
            
            // Template method for adapter-specific initialization
            doInitialize();
            
            logger.info("Adapter {} ({}) initialized successfully", getSupportedType(), getAdapterId());
            
        } catch (Exception e) {
            lifecycleState.set(AdapterLifecycleState.ERROR);
            throw new AdapterInitializationException("Adapter initialization failed: " + e.getMessage(), 
                e, getAdapterId(), getSupportedType());
        }
    }
    
    @Override
    public void start() throws AdapterStartupException {
        AdapterLifecycleState currentState = lifecycleState.get();
        if (currentState != AdapterLifecycleState.INITIALIZED && currentState != AdapterLifecycleState.STOPPED) {
            throw new AdapterStartupException("Adapter cannot be started from state: " + currentState, 
                getAdapterId(), getSupportedType());
        }
        
        try {
            lifecycleState.set(AdapterLifecycleState.STARTING);
            
            // Template method for adapter-specific startup
            doStart();
            
            lifecycleState.set(AdapterLifecycleState.RUNNING);
            startTime = LocalDateTime.now();
            
            logger.info("Adapter {} ({}) started successfully", getSupportedType(), getAdapterId());
            
        } catch (Exception e) {
            lifecycleState.set(AdapterLifecycleState.ERROR);
            throw new AdapterStartupException("Adapter startup failed: " + e.getMessage(), 
                e, getAdapterId(), getSupportedType());
        }
    }
    
    @Override
    public void stop() throws AdapterShutdownException {
        AdapterLifecycleState currentState = lifecycleState.get();
        if (currentState != AdapterLifecycleState.RUNNING) {
            logger.warn("Adapter stop called from state: {}", currentState);
            return; // Allow stop from any state for graceful shutdown
        }
        
        try {
            lifecycleState.set(AdapterLifecycleState.STOPPING);
            
            // Template method for adapter-specific shutdown
            doStop();
            
            lifecycleState.set(AdapterLifecycleState.STOPPED);
            
            logger.info("Adapter {} ({}) stopped successfully", getSupportedType(), getAdapterId());
            
        } catch (Exception e) {
            lifecycleState.set(AdapterLifecycleState.ERROR);
            throw new AdapterShutdownException("Adapter shutdown failed: " + e.getMessage(), 
                e, getAdapterId(), getSupportedType());
        }
    }
    
    @Override
    public void cleanup() {
        try {
            // Template method for adapter-specific cleanup
            doCleanup();
            
            lifecycleState.set(AdapterLifecycleState.DISPOSED);
            
            logger.info("Adapter {} ({}) cleaned up successfully", getSupportedType(), getAdapterId());
            
        } catch (Exception e) {
            logger.error("Error during adapter cleanup: {}", e.getMessage(), e);
            lifecycleState.set(AdapterLifecycleState.ERROR);
        }
    }
    
    @Override
    public boolean isInitialized() {
        AdapterLifecycleState state = lifecycleState.get();
        return state != AdapterLifecycleState.UNINITIALIZED && state != AdapterLifecycleState.DISPOSED;
    }
    
    @Override
    public boolean isRunning() {
        return lifecycleState.get() == AdapterLifecycleState.RUNNING;
    }
    
    @Override
    public AdapterLifecycleState getLifecycleState() {
        return lifecycleState.get();
    }
    
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
    
    // ========== HEALTH CHECK METHODS ==========
    
    @Override
    public AdapterHealthResult performHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Basic lifecycle state check
            AdapterLifecycleState currentState = getLifecycleState();
            if (currentState == AdapterLifecycleState.ERROR) {
                lastHealthCheck = AdapterHealthResult.unhealthy("Adapter is in error state", 
                    List.of("Adapter lifecycle state: " + currentState));
                return lastHealthCheck;
            }
            
            if (currentState == AdapterLifecycleState.UNINITIALIZED || 
                currentState == AdapterLifecycleState.DISPOSED) {
                lastHealthCheck = AdapterHealthResult.unavailable("Adapter is not available");
                return lastHealthCheck;
            }
            
            // Template method for adapter-specific health checks
            AdapterHealthResult specificCheck = performSpecificHealthCheck();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (specificCheck != null) {
                lastHealthCheck = AdapterHealthResult.withDiagnostics(
                    specificCheck.isHealthy(), 
                    specificCheck.getStatus(),
                    specificCheck.getMessage(),
                    responseTime,
                    specificCheck.getWarnings(),
                    specificCheck.getErrors(),
                    Map.of("lifecycleState", currentState.getDisplayName(),
                           "lastExecution", lastExecutionTime != null ? lastExecutionTime.toString() : "Never",
                           "totalExecutions", totalExecutions)
                );
            } else {
                lastHealthCheck = AdapterHealthResult.healthy("Basic health check passed", responseTime);
            }
            
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);
            lastHealthCheck = AdapterHealthResult.unhealthy("Health check failed: " + e.getMessage(),
                List.of(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
        
        return lastHealthCheck;
    }
    
    @Override
    public AdapterHealthResult getLastHealthCheck() {
        return lastHealthCheck;
    }
    
    @Override
    public boolean isHealthy() {
        AdapterHealthResult health = getLastHealthCheck();
        return health != null && health.isHealthy();
    }
    
    // ========== METRICS METHODS ==========
    
    @Override
    public void recordExecution(AdapterExecutionMetrics executionMetrics) {
        synchronized (this) {
            totalExecutions++;
            
            if (executionMetrics.isSuccessful()) {
                successfulExecutions++;
                lastSuccessfulExecution = executionMetrics.getExecutionTime();
            } else {
                failedExecutions++;
                lastFailedExecution = executionMetrics.getExecutionTime();
                lastError = executionMetrics.getErrorMessage();
            }
            
            totalBytesProcessed += executionMetrics.getBytesProcessed();
            totalFilesProcessed += executionMetrics.getFilesProcessed();
            totalExecutionTimeMillis += executionMetrics.getDurationMillis();
            
            if (executionMetrics.getDurationMillis() < minExecutionTimeMillis) {
                minExecutionTimeMillis = executionMetrics.getDurationMillis();
            }
            if (executionMetrics.getDurationMillis() > maxExecutionTimeMillis) {
                maxExecutionTimeMillis = executionMetrics.getDurationMillis();
            }
            
            lastExecutionTime = executionMetrics.getExecutionTime();
        }
    }
    
    @Override
    public AdapterMetricsSummary getMetricsSummary() {
        synchronized (this) {
            double successRate = totalExecutions > 0 ? 
                (double) successfulExecutions / totalExecutions * 100.0 : 0.0;
            
            long avgExecutionTime = totalExecutions > 0 ? 
                totalExecutionTimeMillis / totalExecutions : 0;
                
            return new AdapterMetricsSummary(
                getAdapterId(),
                getSupportedType(),
                LocalDateTime.now(),
                totalExecutions,
                successfulExecutions,
                failedExecutions,
                successRate,
                totalBytesProcessed,
                totalFilesProcessed,
                avgExecutionTime,
                minExecutionTimeMillis == Long.MAX_VALUE ? 0 : minExecutionTimeMillis,
                maxExecutionTimeMillis,
                lastExecutionTime,
                lastSuccessfulExecution,
                lastFailedExecution,
                lastError
            );
        }
    }
    
    @Override
    public void resetMetrics() {
        synchronized (this) {
            totalExecutions = 0;
            successfulExecutions = 0;
            failedExecutions = 0;
            totalBytesProcessed = 0;
            totalFilesProcessed = 0;
            totalExecutionTimeMillis = 0;
            minExecutionTimeMillis = Long.MAX_VALUE;
            maxExecutionTimeMillis = 0;
            lastExecutionTime = null;
            lastSuccessfulExecution = null;
            lastFailedExecution = null;
            lastError = null;
        }
    }
    
    // ========== TEMPLATE METHODS FOR SUBCLASSES ==========
    
    /**
     * Template method for adapter-specific initialization.
     * Override to implement adapter-specific initialization logic.
     */
    protected void doInitialize() throws Exception {
        // Default implementation - subclasses can override
        logger.debug("Default initialization for {} adapter", getSupportedType());
    }
    
    /**
     * Template method for adapter-specific startup.
     * Override to implement adapter-specific startup logic.
     */
    protected void doStart() throws Exception {
        // Default implementation - subclasses can override
        logger.debug("Default startup for {} adapter", getSupportedType());
    }
    
    /**
     * Template method for adapter-specific shutdown.
     * Override to implement adapter-specific shutdown logic.
     */
    protected void doStop() throws Exception {
        // Default implementation - subclasses can override
        logger.debug("Default stop for {} adapter", getSupportedType());
    }
    
    /**
     * Template method for adapter-specific cleanup.
     * Override to implement adapter-specific cleanup logic.
     */
    protected void doCleanup() {
        // Default implementation - subclasses can override
        logger.debug("Default cleanup for {} adapter", getSupportedType());
    }
    
    /**
     * Template method for adapter-specific health checks.
     * Override to implement adapter-specific health checking logic.
     * 
     * @return health check result, or null for default health check
     */
    protected AdapterHealthResult performSpecificHealthCheck() {
        // Default implementation - subclasses can override
        return null;
    }
    
    /**
     * Template method called before execution.
     * Override to implement pre-execution logic.
     */
    protected void preExecution(Adapter adapter, Map<String, Object> context, FlowExecutionStep step) {
        // Default implementation - subclasses can override
        logger.debug("Pre-execution hook for {} adapter", getSupportedType());
    }
    
    /**
     * Template method called after successful execution.
     * Override to implement post-execution logic.
     */
    protected void postExecution(Adapter adapter, Map<String, Object> context, 
                               FlowExecutionStep step, Map<String, Object> result) {
        // Default implementation - subclasses can override
        logger.debug("Post-execution hook for {} adapter", getSupportedType());
    }
    
    /**
     * Template method called on execution error.
     * Override to implement error handling logic.
     */
    protected void onExecutionError(Adapter adapter, Map<String, Object> context, 
                                  FlowExecutionStep step, Exception error) {
        // Default implementation - subclasses can override
        logger.debug("Error hook for {} adapter: {}", getSupportedType(), error.getMessage());
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Ensure adapter is initialized before execution
     */
    private void ensureInitialized(Adapter adapter) throws AdapterInitializationException {
        if (!isInitialized()) {
            this.adapterConfig = adapter;
            initialize();
        }
    }
    
    /**
     * Execute with lifecycle management
     */
    private Map<String, Object> executeWithLifecycleManagement(Adapter adapter, 
                                                              Map<String, Object> context, 
                                                              FlowExecutionStep step) {
        // Ensure adapter is running
        if (!isRunning()) {
            try {
                start();
            } catch (AdapterStartupException e) {
                throw new RuntimeException("Failed to start adapter for execution", e);
            }
        }
        
        return executeInternal(adapter, context, step);
    }
    
    /**
     * Record successful execution metrics
     */
    private void recordSuccessfulExecution(Adapter adapter, Map<String, Object> result, long durationMillis) {
        int filesProcessed = getIntFromResult(result, "successCount", 0);
        long bytesProcessed = getLongFromResult(result, "totalBytesProcessed", 0L);
        
        AdapterExecutionMetrics metrics = AdapterExecutionMetrics.builder(
                adapter.getId().toString(), getSupportedType(), getSupportedDirection())
            .duration(durationMillis)
            .successful(true)
            .filesProcessed(filesProcessed)
            .bytesProcessed(bytesProcessed)
            .build();
            
        recordExecution(metrics);
    }
    
    /**
     * Record failed execution metrics
     */
    private void recordFailedExecution(Adapter adapter, String errorMessage, long durationMillis) {
        AdapterExecutionMetrics metrics = AdapterExecutionMetrics.builder(
                adapter.getId().toString(), getSupportedType(), getSupportedDirection())
            .duration(durationMillis)
            .successful(false)
            .errorMessage(errorMessage)
            .build();
            
        recordExecution(metrics);
    }
    
    /**
     * Get adapter ID safely
     */
    private String getAdapterId() {
        return adapterConfig != null ? adapterConfig.getId().toString() : "unknown";
    }
    
    /**
     * Helper to safely extract integer from result map
     */
    private int getIntFromResult(Map<String, Object> result, String key, int defaultValue) {
        Object value = result.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * Helper to safely extract long from result map
     */
    private long getLongFromResult(Map<String, Object> result, String key, long defaultValue) {
        Object value = result.get(key);
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
}