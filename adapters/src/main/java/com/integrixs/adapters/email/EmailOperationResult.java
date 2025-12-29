package com.integrixs.adapters.email;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Result of Email adapter operations
 */
public class EmailOperationResult {
    
    private final String correlationId;
    private final EmailOperation operation;
    private EmailOperationStatus status;
    private final LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String error;
    private final List<String> messages;
    private final Map<String, Object> metadata;
    
    public EmailOperationResult(String correlationId, EmailOperation operation, EmailOperationStatus status) {
        this.correlationId = correlationId;
        this.operation = operation;
        this.status = status;
        this.startedAt = LocalDateTime.now();
        this.messages = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    // Getters and setters
    public String getCorrelationId() {
        return correlationId;
    }
    
    public EmailOperation getOperation() {
        return operation;
    }
    
    public EmailOperationStatus getStatus() {
        return status;
    }
    
    public void setStatus(EmailOperationStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public List<String> getMessages() {
        return messages;
    }
    
    public void addMessage(String message) {
        this.messages.add(message);
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public boolean isSuccessful() {
        return status == EmailOperationStatus.SUCCESS;
    }
    
    public boolean isFailed() {
        return status == EmailOperationStatus.FAILED;
    }
    
    public boolean isCompleted() {
        return status == EmailOperationStatus.SUCCESS || status == EmailOperationStatus.FAILED || status == EmailOperationStatus.CANCELLED;
    }
    
    @Override
    public String toString() {
        return "EmailOperationResult{" +
                "correlationId='" + correlationId + '\'' +
                ", operation=" + operation +
                ", status=" + status +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", error='" + error + '\'' +
                ", messages=" + messages.size() +
                ", metadata=" + metadata.size() +
                '}';
    }
}