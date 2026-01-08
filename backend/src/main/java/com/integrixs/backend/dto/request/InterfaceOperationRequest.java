package com.integrixs.backend.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request DTO for interface operations (test, execute, start, stop, enable, disable).
 * Contains operation type, interface ID, and user context.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class InterfaceOperationRequest {
    
    private final UUID interfaceId;
    private final String operation;
    private final UUID requestedBy;
    private final LocalDateTime requestedAt;
    private final boolean requiresAuth;
    private final String reason;
    
    private InterfaceOperationRequest(Builder builder) {
        this.interfaceId = builder.interfaceId;
        this.operation = builder.operation;
        this.requestedBy = builder.requestedBy;
        this.requestedAt = builder.requestedAt != null ? builder.requestedAt : LocalDateTime.now();
        this.requiresAuth = builder.requiresAuth;
        this.reason = builder.reason;
    }
    
    // Getters
    public UUID getInterfaceId() { return interfaceId; }
    public String getOperation() { return operation; }
    public UUID getRequestedBy() { return requestedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public boolean isRequiresAuth() { return requiresAuth; }
    public String getReason() { return reason; }
    
    /**
     * Check if operation is a lifecycle operation.
     */
    public boolean isLifecycleOperation() {
        return operation != null && 
               (operation.equalsIgnoreCase("start") || 
                operation.equalsIgnoreCase("stop") || 
                operation.equalsIgnoreCase("enable") || 
                operation.equalsIgnoreCase("disable"));
    }
    
    /**
     * Check if operation is a test operation.
     */
    public boolean isTestOperation() {
        return "test".equalsIgnoreCase(operation);
    }
    
    /**
     * Check if operation is an execution operation.
     */
    public boolean isExecuteOperation() {
        return "execute".equalsIgnoreCase(operation);
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create test connection request.
     */
    public static InterfaceOperationRequest testRequest(UUID interfaceId, UUID requestedBy) {
        return builder()
            .interfaceId(interfaceId)
            .operation("test")
            .requestedBy(requestedBy)
            .requiresAuth(true)
            .build();
    }
    
    /**
     * Create execute request.
     */
    public static InterfaceOperationRequest executeRequest(UUID interfaceId, UUID requestedBy) {
        return builder()
            .interfaceId(interfaceId)
            .operation("execute")
            .requestedBy(requestedBy)
            .requiresAuth(true)
            .build();
    }
    
    /**
     * Create start request.
     */
    public static InterfaceOperationRequest startRequest(UUID interfaceId, UUID requestedBy) {
        return builder()
            .interfaceId(interfaceId)
            .operation("start")
            .requestedBy(requestedBy)
            .requiresAuth(true)
            .build();
    }
    
    /**
     * Create stop request.
     */
    public static InterfaceOperationRequest stopRequest(UUID interfaceId, UUID requestedBy) {
        return builder()
            .interfaceId(interfaceId)
            .operation("stop")
            .requestedBy(requestedBy)
            .requiresAuth(true)
            .build();
    }
    
    /**
     * Create enable/disable request.
     */
    public static InterfaceOperationRequest enableRequest(UUID interfaceId, boolean enabled, UUID requestedBy) {
        return builder()
            .interfaceId(interfaceId)
            .operation(enabled ? "enable" : "disable")
            .requestedBy(requestedBy)
            .requiresAuth(true)
            .reason(enabled ? "Enable interface" : "Disable interface")
            .build();
    }
    
    /**
     * Builder for InterfaceOperationRequest.
     */
    public static class Builder {
        private UUID interfaceId;
        private String operation;
        private UUID requestedBy;
        private LocalDateTime requestedAt;
        private boolean requiresAuth = true;
        private String reason;
        
        private Builder() {}
        
        public Builder interfaceId(UUID interfaceId) {
            this.interfaceId = interfaceId;
            return this;
        }
        
        public Builder operation(String operation) {
            this.operation = operation != null ? operation.toLowerCase() : null;
            return this;
        }
        
        public Builder requestedBy(UUID requestedBy) {
            this.requestedBy = requestedBy;
            return this;
        }
        
        public Builder requestedAt(LocalDateTime requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }
        
        public Builder requiresAuth(boolean requiresAuth) {
            this.requiresAuth = requiresAuth;
            return this;
        }
        
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }
        
        public InterfaceOperationRequest build() {
            if (interfaceId == null) {
                throw new IllegalArgumentException("Interface ID is required");
            }
            if (operation == null || operation.trim().isEmpty()) {
                throw new IllegalArgumentException("Operation is required");
            }
            if (requestedBy == null) {
                throw new IllegalArgumentException("Requested by user ID is required");
            }
            
            return new InterfaceOperationRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("InterfaceOperationRequest{interfaceId=%s, operation='%s', " +
                           "requestedBy=%s, requestedAt=%s, requiresAuth=%s}", 
                           interfaceId, operation, requestedBy, requestedAt, requiresAuth);
    }
}