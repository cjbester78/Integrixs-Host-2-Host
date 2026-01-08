package com.integrixs.backend.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request object for execution operations (retry, cancel).
 * Contains execution ID, user context, and operation metadata.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class ExecutionOperationRequest {
    
    private final UUID executionId;
    private final UUID userId;
    private final String operation;
    private final String reason;
    private final LocalDateTime requestedAt;
    private final String correlationId;
    
    private ExecutionOperationRequest(Builder builder) {
        this.executionId = builder.executionId;
        this.userId = builder.userId;
        this.operation = builder.operation;
        this.reason = builder.reason;
        this.requestedAt = builder.requestedAt != null ? builder.requestedAt : LocalDateTime.now();
        this.correlationId = builder.correlationId != null ? builder.correlationId : UUID.randomUUID().toString();
    }
    
    // Getters
    public UUID getExecutionId() { return executionId; }
    public UUID getUserId() { return userId; }
    public String getOperation() { return operation; }
    public String getReason() { return reason; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public String getCorrelationId() { return correlationId; }
    
    /**
     * Check if request has a reason.
     */
    public boolean hasReason() {
        return reason != null && !reason.trim().isEmpty();
    }
    
    /**
     * Check if this is a retry operation.
     */
    public boolean isRetryOperation() {
        return "RETRY".equalsIgnoreCase(operation);
    }
    
    /**
     * Check if this is a cancel operation.
     */
    public boolean isCancelOperation() {
        return "CANCEL".equalsIgnoreCase(operation);
    }
    
    /**
     * Create builder instance for constructing ExecutionOperationRequest.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create retry operation request.
     */
    public static ExecutionOperationRequest retryRequest(UUID executionId, UUID userId) {
        return builder()
            .executionId(executionId)
            .userId(userId)
            .operation("RETRY")
            .build();
    }
    
    /**
     * Create retry operation request with reason.
     */
    public static ExecutionOperationRequest retryRequest(UUID executionId, UUID userId, String reason) {
        return builder()
            .executionId(executionId)
            .userId(userId)
            .operation("RETRY")
            .reason(reason)
            .build();
    }
    
    /**
     * Create cancel operation request.
     */
    public static ExecutionOperationRequest cancelRequest(UUID executionId, UUID userId) {
        return builder()
            .executionId(executionId)
            .userId(userId)
            .operation("CANCEL")
            .build();
    }
    
    /**
     * Create cancel operation request with reason.
     */
    public static ExecutionOperationRequest cancelRequest(UUID executionId, UUID userId, String reason) {
        return builder()
            .executionId(executionId)
            .userId(userId)
            .operation("CANCEL")
            .reason(reason)
            .build();
    }
    
    /**
     * Builder for ExecutionOperationRequest following builder pattern.
     */
    public static class Builder {
        private UUID executionId;
        private UUID userId;
        private String operation;
        private String reason;
        private LocalDateTime requestedAt;
        private String correlationId;
        
        private Builder() {}
        
        public Builder executionId(UUID executionId) {
            this.executionId = executionId;
            return this;
        }
        
        public Builder executionId(String executionId) {
            this.executionId = executionId != null && !executionId.trim().isEmpty() ? 
                UUID.fromString(executionId) : null;
            return this;
        }
        
        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId != null && !userId.trim().isEmpty() ? 
                UUID.fromString(userId) : null;
            return this;
        }
        
        public Builder operation(String operation) {
            this.operation = operation != null ? operation.trim().toUpperCase() : null;
            return this;
        }
        
        public Builder reason(String reason) {
            this.reason = reason != null && !reason.trim().isEmpty() ? reason.trim() : null;
            return this;
        }
        
        public Builder requestedAt(LocalDateTime requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public ExecutionOperationRequest build() {
            if (executionId == null) {
                throw new IllegalArgumentException("Execution ID is required");
            }
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }
            if (operation == null || operation.trim().isEmpty()) {
                throw new IllegalArgumentException("Operation is required");
            }
            
            return new ExecutionOperationRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionOperationRequest{executionId=%s, userId=%s, operation='%s', " +
                           "hasReason=%b, requestedAt=%s, correlationId='%s'}", 
                           executionId, userId, operation, hasReason(), requestedAt, correlationId);
    }
}