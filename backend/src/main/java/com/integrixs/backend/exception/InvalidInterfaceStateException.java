package com.integrixs.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when an interface operation is attempted on an interface in an invalid state.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class InvalidInterfaceStateException extends RuntimeException {
    
    private final UUID interfaceId;
    private final String currentState;
    private final String attemptedOperation;
    
    public InvalidInterfaceStateException(UUID interfaceId, String currentState, String attemptedOperation) {
        super(String.format("Cannot perform operation '%s' on interface %s in state '%s'", 
                           attemptedOperation, interfaceId, currentState));
        this.interfaceId = interfaceId;
        this.currentState = currentState;
        this.attemptedOperation = attemptedOperation;
    }
    
    public InvalidInterfaceStateException(UUID interfaceId, String currentState, String attemptedOperation, String message) {
        super(message);
        this.interfaceId = interfaceId;
        this.currentState = currentState;
        this.attemptedOperation = attemptedOperation;
    }
    
    public UUID getInterfaceId() {
        return interfaceId;
    }
    
    public String getCurrentState() {
        return currentState;
    }
    
    public String getAttemptedOperation() {
        return attemptedOperation;
    }
}