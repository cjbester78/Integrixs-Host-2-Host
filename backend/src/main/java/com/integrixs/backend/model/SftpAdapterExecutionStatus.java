package com.integrixs.backend.model;

/**
 * Status of SFTP adapter execution
 */
public enum SftpAdapterExecutionStatus {
    SUCCESS("Execution completed successfully"),
    FAILED("Execution failed with errors"),
    PARTIAL("Execution completed with some errors");
    
    private final String description;
    
    SftpAdapterExecutionStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() { return description; }
}