package com.integrixs.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Immutable generic API response wrapper for standardized REST API responses.
 * Contains success status, message, data payload, and timestamp.
 * Part of Phase 5.3 DTO enhancement following OOP principles.
 * 
 * @param <T> The type of data payload
 */
public final class ApiResponse<T> {
    
    @JsonProperty("success")
    private final boolean success;
    
    @JsonProperty("message")
    private final String message;
    
    @JsonProperty("data")
    private final T data;
    
    @JsonProperty("timestamp")
    private final long timestamp;
    
    @JsonProperty("correlationId")
    private final String correlationId;
    
    private ApiResponse(Builder<T> builder) {
        this.success = builder.success;
        this.message = Objects.requireNonNull(builder.message, "Message cannot be null");
        this.data = builder.data;
        this.timestamp = builder.timestamp;
        this.correlationId = builder.correlationId;
    }
    
    // Backward compatibility constructors for legacy code
    
    /**
     * @deprecated Use ApiResponse.success() or ApiResponse.error() static factory methods instead
     */
    @Deprecated
    public ApiResponse(boolean success, String message) {
        this.success = success;
        this.message = Objects.requireNonNull(message, "Message cannot be null");
        this.data = null;
        this.timestamp = System.currentTimeMillis();
        this.correlationId = null;
    }
    
    /**
     * @deprecated Use ApiResponse.success() or ApiResponse.error() static factory methods instead
     */
    @Deprecated
    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = Objects.requireNonNull(message, "Message cannot be null");
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.correlationId = null;
    }
    
    /**
     * @deprecated Use ApiResponse.success() or ApiResponse.error() static factory methods instead
     */
    @Deprecated
    public ApiResponse(String message, T data) {
        this.success = true;
        this.message = Objects.requireNonNull(message, "Message cannot be null");
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.correlationId = null;
    }
    
    /**
     * @deprecated Use ApiResponse.success() or ApiResponse.error() static factory methods instead
     */
    @Deprecated
    public ApiResponse(T data) {
        this.success = true;
        this.message = "Operation completed successfully";
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.correlationId = null;
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public long getTimestamp() { return timestamp; }
    public String getCorrelationId() { return correlationId; }
    
    /**
     * Check if response has data payload.
     */
    public boolean hasData() {
        return data != null;
    }
    
    /**
     * Check if response represents an error.
     */
    public boolean isError() {
        return !success;
    }
    
    /**
     * Get data payload with null safety.
     */
    public T getDataOrDefault(T defaultValue) {
        return data != null ? data : defaultValue;
    }
    
    /**
     * Get formatted timestamp as LocalDateTime.
     */
    public LocalDateTime getTimestampAsDateTime() {
        return LocalDateTime.ofEpochSecond(timestamp / 1000, (int) ((timestamp % 1000) * 1_000_000), ZoneOffset.UTC);
    }
    
    // Static Factory Methods
    
    /**
     * Create successful response with data and message.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Create successful response with data and default message.
     */
    public static <T> ApiResponse<T> success(T data) {
        return success("Operation completed successfully", data);
    }
    
    /**
     * Create successful response with message only (no data).
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(null)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Create error response with message only.
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .data(null)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Create error response with message and error details.
     */
    public static <T> ApiResponse<T> error(String message, T errorDetails) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .data(errorDetails)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Create error response with message and correlation ID.
     */
    public static <T> ApiResponse<T> error(String message, String correlationId) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .data(null)
            .correlationId(correlationId)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Create builder instance for constructing ApiResponse.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    /**
     * Builder for ApiResponse following builder pattern.
     */
    public static class Builder<T> {
        private boolean success = true;
        private String message = "Operation completed successfully";
        private T data;
        private long timestamp = System.currentTimeMillis();
        private String correlationId;
        
        private Builder() {}
        
        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }
        
        public Builder<T> timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder<T> correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder<T> now() {
            this.timestamp = System.currentTimeMillis();
            return this;
        }
        
        public ApiResponse<T> build() {
            return new ApiResponse<>(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ApiResponse{success=%b, message='%s', hasData=%b, timestamp=%d, correlationId='%s'}", 
                           success, message, hasData(), timestamp, correlationId);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiResponse<?> that = (ApiResponse<?>) o;
        return success == that.success && 
               timestamp == that.timestamp && 
               Objects.equals(message, that.message) && 
               Objects.equals(data, that.data) &&
               Objects.equals(correlationId, that.correlationId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, message, data, timestamp, correlationId);
    }
}