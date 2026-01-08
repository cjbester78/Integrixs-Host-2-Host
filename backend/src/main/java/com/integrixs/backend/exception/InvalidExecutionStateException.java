package com.integrixs.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when attempting an operation on an execution in an invalid state.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class InvalidExecutionStateException extends RuntimeException {
    
    private final UUID executionId;
    private final String currentState;
    private final String attemptedOperation;
    
    public InvalidExecutionStateException(UUID executionId, String currentState, String attemptedOperation) {
        super(String.format("Cannot perform operation '%s' on execution %s in state '%s'", 
                           attemptedOperation, executionId, currentState));
        this.executionId = executionId;
        this.currentState = currentState;
        this.attemptedOperation = attemptedOperation;
    }
    
    public InvalidExecutionStateException(UUID executionId, String currentState, String attemptedOperation, String message) {
        super(message);
        this.executionId = executionId;
        this.currentState = currentState;
        this.attemptedOperation = attemptedOperation;
    }
    
    public InvalidExecutionStateException(UUID executionId, String currentState, String attemptedOperation, 
                                        String message, Throwable cause) {
        super(message, cause);
        this.executionId = executionId;
        this.currentState = currentState;
        this.attemptedOperation = attemptedOperation;
    }
    
    public UUID getExecutionId() {
        return executionId;
    }
    
    public String getCurrentState() {
        return currentState;
    }
    
    public String getAttemptedOperation() {
        return attemptedOperation;
    }
}