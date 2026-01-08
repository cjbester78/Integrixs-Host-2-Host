package com.integrixs.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when a requested execution is not found.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class ExecutionNotFoundException extends RuntimeException {
    
    private final UUID executionId;
    
    public ExecutionNotFoundException(UUID executionId) {
        super("Execution not found with ID: " + executionId);
        this.executionId = executionId;
    }
    
    public ExecutionNotFoundException(UUID executionId, String message) {
        super(message);
        this.executionId = executionId;
    }
    
    public ExecutionNotFoundException(UUID executionId, String message, Throwable cause) {
        super(message, cause);
        this.executionId = executionId;
    }
    
    public UUID getExecutionId() {
        return executionId;
    }
}