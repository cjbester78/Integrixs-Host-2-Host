package com.integrixs.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when an interface service is temporarily unavailable.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class InterfaceServiceUnavailableException extends RuntimeException {
    
    private final UUID interfaceId;
    private final String serviceName;
    private final String reason;
    
    public InterfaceServiceUnavailableException(UUID interfaceId, String serviceName, String reason) {
        super(String.format("Service '%s' is unavailable for interface %s: %s", 
                           serviceName, interfaceId, reason));
        this.interfaceId = interfaceId;
        this.serviceName = serviceName;
        this.reason = reason;
    }
    
    public InterfaceServiceUnavailableException(String serviceName, String reason) {
        super(String.format("Service '%s' is unavailable: %s", serviceName, reason));
        this.interfaceId = null;
        this.serviceName = serviceName;
        this.reason = reason;
    }
    
    public InterfaceServiceUnavailableException(UUID interfaceId, String serviceName, String reason, Throwable cause) {
        super(String.format("Service '%s' is unavailable for interface %s: %s", 
                           serviceName, interfaceId, reason), cause);
        this.interfaceId = interfaceId;
        this.serviceName = serviceName;
        this.reason = reason;
    }
    
    public UUID getInterfaceId() {
        return interfaceId;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getReason() {
        return reason;
    }
}