package com.integrixs.backend.dto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable error details object for standardized error responses.
 * Contains correlation ID, error codes, timestamps, and additional context.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class ErrorDetails {
    
    private final String correlationId;
    private final String errorCode;
    private final String errorMessage;
    private final LocalDateTime timestamp;
    private final String path;
    private final Map<String, Object> details;
    
    private ErrorDetails(Builder builder) {
        this.correlationId = builder.correlationId;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
        this.timestamp = builder.timestamp;
        this.path = builder.path;
        this.details = builder.details != null ? 
            Collections.unmodifiableMap(new HashMap<>(builder.details)) : Collections.emptyMap();
    }
    
    // Getters
    public String getCorrelationId() { return correlationId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getPath() { return path; }
    public Map<String, Object> getDetails() { return details; }
    
    /**
     * Check if error details has additional details.
     */
    public boolean hasDetails() {
        return !details.isEmpty();
    }
    
    /**
     * Get specific detail value with type safety.
     */
    @SuppressWarnings("unchecked")
    public <T> T getDetail(String key, Class<T> type, T defaultValue) {
        if (!details.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = details.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        
        return defaultValue;
    }
    
    /**
     * Get detail as string.
     */
    public String getDetailAsString(String key, String defaultValue) {
        Object value = details.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Create builder instance for constructing ErrorDetails.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create simple error details with basic information.
     */
    public static ErrorDetails simple(String correlationId, String errorCode, String errorMessage) {
        return builder()
            .correlationId(correlationId)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Builder for ErrorDetails following builder pattern.
     */
    public static class Builder {
        private String correlationId;
        private String errorCode;
        private String errorMessage;
        private LocalDateTime timestamp;
        private String path;
        private Map<String, Object> details;
        
        private Builder() {}
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public Builder addDetail(String key, Object value) {
            if (this.details == null) {
                this.details = new HashMap<>();
            }
            this.details.put(key, value);
            return this;
        }
        
        public Builder addDetails(Map<String, Object> additionalDetails) {
            if (additionalDetails != null && !additionalDetails.isEmpty()) {
                if (this.details == null) {
                    this.details = new HashMap<>();
                }
                this.details.putAll(additionalDetails);
            }
            return this;
        }
        
        public ErrorDetails build() {
            return new ErrorDetails(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ErrorDetails{correlationId='%s', errorCode='%s', errorMessage='%s', " +
                           "timestamp=%s, path='%s', detailCount=%d}", 
                           correlationId, errorCode, errorMessage, timestamp, path, details.size());
    }
}