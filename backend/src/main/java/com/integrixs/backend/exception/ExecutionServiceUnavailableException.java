package com.integrixs.backend.exception;

/**
 * Exception thrown when execution services are temporarily unavailable.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class ExecutionServiceUnavailableException extends RuntimeException {
    
    private final String serviceName;
    private final String reason;
    
    public ExecutionServiceUnavailableException(String serviceName, String reason) {
        super(String.format("Service '%s' is temporarily unavailable: %s", serviceName, reason));
        this.serviceName = serviceName;
        this.reason = reason;
    }
    
    public ExecutionServiceUnavailableException(String serviceName, String reason, String message) {
        super(message);
        this.serviceName = serviceName;
        this.reason = reason;
    }
    
    public ExecutionServiceUnavailableException(String serviceName, String reason, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.reason = reason;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getReason() {
        return reason;
    }
}