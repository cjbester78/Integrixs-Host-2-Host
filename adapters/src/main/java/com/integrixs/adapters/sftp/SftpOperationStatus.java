package com.integrixs.adapters.sftp;

/**
 * Status of SFTP operations
 */
public enum SftpOperationStatus {
    PENDING("Operation is pending execution"),
    IN_PROGRESS("Operation is currently in progress"),
    SUCCESS("Operation completed successfully"),
    FAILED("Operation failed with errors"),
    CANCELLED("Operation was cancelled"),
    RETRY("Operation will be retried");
    
    private final String description;
    
    SftpOperationStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}