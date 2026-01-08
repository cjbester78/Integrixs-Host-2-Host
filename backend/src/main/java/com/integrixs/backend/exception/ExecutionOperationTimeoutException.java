package com.integrixs.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when an execution operation times out.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class ExecutionOperationTimeoutException extends RuntimeException {
    
    private final UUID executionId;
    private final String operation;
    private final long timeoutMs;
    
    public ExecutionOperationTimeoutException(UUID executionId, String operation, long timeoutMs) {
        super(String.format("Operation '%s' on execution %s timed out after %d ms", 
                           operation, executionId, timeoutMs));
        this.executionId = executionId;
        this.operation = operation;
        this.timeoutMs = timeoutMs;
    }
    
    public ExecutionOperationTimeoutException(UUID executionId, String operation, long timeoutMs, String message) {
        super(message);
        this.executionId = executionId;
        this.operation = operation;
        this.timeoutMs = timeoutMs;
    }
    
    public ExecutionOperationTimeoutException(UUID executionId, String operation, long timeoutMs, 
                                            String message, Throwable cause) {
        super(message, cause);
        this.executionId = executionId;
        this.operation = operation;
        this.timeoutMs = timeoutMs;
    }
    
    public UUID getExecutionId() {
        return executionId;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
}