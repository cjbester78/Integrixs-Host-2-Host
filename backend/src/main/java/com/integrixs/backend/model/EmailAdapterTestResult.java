package com.integrixs.backend.model;

import java.time.LocalDateTime;

/**
 * Result of Email adapter connection test
 */
public class EmailAdapterTestResult {
    
    private final String correlationId;
    private final boolean successful;
    private final String message;
    private final LocalDateTime timestamp;
    
    public EmailAdapterTestResult(String correlationId, boolean successful, String message) {
        this.correlationId = correlationId;
        this.successful = successful;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters
    public String getCorrelationId() {
        return correlationId;
    }
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public String getMessage() {
        return message;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "EmailAdapterTestResult{" +
                "correlationId='" + correlationId + '\'' +
                ", successful=" + successful +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}