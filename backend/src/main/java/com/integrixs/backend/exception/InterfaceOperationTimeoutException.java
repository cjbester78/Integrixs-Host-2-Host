package com.integrixs.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when an interface operation times out.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class InterfaceOperationTimeoutException extends RuntimeException {
    
    private final UUID interfaceId;
    private final String operation;
    private final long timeoutMs;
    
    public InterfaceOperationTimeoutException(UUID interfaceId, String operation, long timeoutMs) {
        super(String.format("Operation '%s' timed out for interface %s after %d ms", 
                           operation, interfaceId, timeoutMs));
        this.interfaceId = interfaceId;
        this.operation = operation;
        this.timeoutMs = timeoutMs;
    }
    
    public InterfaceOperationTimeoutException(UUID interfaceId, String operation, long timeoutMs, Throwable cause) {
        super(String.format("Operation '%s' timed out for interface %s after %d ms", 
                           operation, interfaceId, timeoutMs), cause);
        this.interfaceId = interfaceId;
        this.operation = operation;
        this.timeoutMs = timeoutMs;
    }
    
    public UUID getInterfaceId() {
        return interfaceId;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
}