package com.integrixs.backend.model;

/**
 * Status of file adapter execution
 */
public enum FileAdapterExecutionStatus {
    SUCCESS("All files processed successfully"),
    PARTIAL_SUCCESS("Some files processed successfully"),
    FAILED("Execution failed"),
    NO_FILES("No files found for processing"),
    SKIPPED("Execution skipped");
    
    private final String description;
    
    FileAdapterExecutionStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}