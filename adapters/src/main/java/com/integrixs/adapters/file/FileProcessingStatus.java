package com.integrixs.adapters.file;

public enum FileProcessingStatus {
    PENDING("File is waiting to be processed"),
    PROCESSING("File is currently being processed"),
    SUCCESS("File was processed successfully"),
    FAILED("File processing failed"),
    SKIPPED("File was skipped due to validation or business rules"),
    RETRYING("File processing is being retried after failure"),
    CANCELLED("File processing was cancelled");
    
    private final String description;
    
    FileProcessingStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == CANCELLED;
    }
    
    public boolean isInProgress() {
        return this == PROCESSING || this == RETRYING;
    }
    
    public boolean isError() {
        return this == FAILED;
    }
    
    @Override
    public String toString() {
        return name() + ": " + description;
    }
}