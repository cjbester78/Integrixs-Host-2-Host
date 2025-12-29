package com.integrixs.adapters.email;

/**
 * Status of Email operations
 */
public enum EmailOperationStatus {
    PENDING("Operation is pending execution"),
    IN_PROGRESS("Operation is currently in progress"),
    SUCCESS("Operation completed successfully"),
    FAILED("Operation failed with errors"),
    CANCELLED("Operation was cancelled"),
    RETRY("Operation will be retried"),
    PARTIAL("Operation partially successful");
    
    private final String description;
    
    EmailOperationStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}