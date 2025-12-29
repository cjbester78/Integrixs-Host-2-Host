package com.integrixs.backend.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result of Email adapter execution operations
 */
public class EmailAdapterExecutionResult {
    
    private final String correlationId;
    private final UUID adapterId;
    private final String operation;
    private final String status;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private final boolean successful;
    private final String error;
    private final List<String> messages;
    private final Map<String, Object> metadata;
    
    public EmailAdapterExecutionResult(String correlationId, UUID adapterId, String operation, 
                                     String status, LocalDateTime startedAt, LocalDateTime completedAt,
                                     boolean successful, String error, List<String> messages, 
                                     Map<String, Object> metadata) {
        this.correlationId = correlationId;
        this.adapterId = adapterId;
        this.operation = operation;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.successful = successful;
        this.error = error;
        this.messages = messages;
        this.metadata = metadata;
    }
    
    // Getters
    public String getCorrelationId() {
        return correlationId;
    }
    
    public UUID getAdapterId() {
        return adapterId;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public String getStatus() {
        return status;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public String getError() {
        return error;
    }
    
    public List<String> getMessages() {
        return messages;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public java.time.Duration getDuration() {
        if (startedAt == null || completedAt == null) {
            return java.time.Duration.ZERO;
        }
        return java.time.Duration.between(startedAt, completedAt);
    }
    
    @Override
    public String toString() {
        return "EmailAdapterExecutionResult{" +
                "correlationId='" + correlationId + '\'' +
                ", adapterId=" + adapterId +
                ", operation='" + operation + '\'' +
                ", status='" + status + '\'' +
                ", successful=" + successful +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                '}';
    }
}