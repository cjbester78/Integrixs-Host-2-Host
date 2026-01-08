package com.integrixs.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when interface connection test fails.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public class InterfaceTestConnectionException extends RuntimeException {
    
    private final UUID interfaceId;
    private final String testType;
    private final String reason;
    private final long testDurationMs;
    
    public InterfaceTestConnectionException(UUID interfaceId, String testType, String reason, long testDurationMs) {
        super(String.format("Connection test failed for interface %s (type: %s): %s", 
                           interfaceId, testType, reason));
        this.interfaceId = interfaceId;
        this.testType = testType;
        this.reason = reason;
        this.testDurationMs = testDurationMs;
    }
    
    public InterfaceTestConnectionException(UUID interfaceId, String testType, String reason, long testDurationMs, Throwable cause) {
        super(String.format("Connection test failed for interface %s (type: %s): %s", 
                           interfaceId, testType, reason), cause);
        this.interfaceId = interfaceId;
        this.testType = testType;
        this.reason = reason;
        this.testDurationMs = testDurationMs;
    }
    
    public UUID getInterfaceId() {
        return interfaceId;
    }
    
    public String getTestType() {
        return testType;
    }
    
    public String getReason() {
        return reason;
    }
    
    public long getTestDurationMs() {
        return testDurationMs;
    }
}