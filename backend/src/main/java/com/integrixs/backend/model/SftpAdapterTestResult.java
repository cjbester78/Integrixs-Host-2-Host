package com.integrixs.backend.model;

import java.time.LocalDateTime;

/**
 * Result of SFTP adapter connection test
 */
public class SftpAdapterTestResult {
    private final boolean success;
    private final String message;
    private final Exception exception;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    
    public SftpAdapterTestResult(boolean success, String message, Exception exception,
                                 LocalDateTime startTime, LocalDateTime endTime) {
        this.success = success;
        this.message = message;
        this.exception = exception;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    // Convenience constructor for simple cases
    public SftpAdapterTestResult(String correlationId, boolean success, String details) {
        this(success, details, null, LocalDateTime.now(), LocalDateTime.now());
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Exception getException() { return exception; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
}