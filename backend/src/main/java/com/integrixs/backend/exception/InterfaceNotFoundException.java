package com.integrixs.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when an interface is not found.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class InterfaceNotFoundException extends RuntimeException {
    
    private final UUID interfaceId;
    
    public InterfaceNotFoundException(UUID interfaceId) {
        super("Interface not found with ID: " + interfaceId);
        this.interfaceId = interfaceId;
    }
    
    public InterfaceNotFoundException(UUID interfaceId, String message) {
        super(message);
        this.interfaceId = interfaceId;
    }
    
    public InterfaceNotFoundException(String interfaceName) {
        super("Interface not found with name: " + interfaceName);
        this.interfaceId = null;
    }
    
    public UUID getInterfaceId() {
        return interfaceId;
    }
}