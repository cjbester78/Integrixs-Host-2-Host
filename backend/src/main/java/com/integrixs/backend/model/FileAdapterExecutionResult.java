package com.integrixs.backend.model;

import com.integrixs.adapters.file.FileProcessingResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Result of a file adapter execution
 */
public class FileAdapterExecutionResult {
    private UUID adapterInterfaceId;
    private UUID executionId;
    private FileAdapterExecutionStatus status;
    private String message;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int totalFiles;
    private long successfulFiles;
    private long failedFiles;
    private List<FileProcessingResult> fileResults;
    
    public FileAdapterExecutionResult() {
        this.fileResults = new ArrayList<>();
    }
    
    // Getters and setters
    public UUID getAdapterInterfaceId() { return adapterInterfaceId; }
    public void setAdapterInterfaceId(UUID adapterInterfaceId) { this.adapterInterfaceId = adapterInterfaceId; }
    
    public UUID getExecutionId() { return executionId; }
    public void setExecutionId(UUID executionId) { this.executionId = executionId; }
    
    public FileAdapterExecutionStatus getStatus() { return status; }
    public void setStatus(FileAdapterExecutionStatus status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
    
    public long getSuccessfulFiles() { return successfulFiles; }
    public void setSuccessfulFiles(long successfulFiles) { this.successfulFiles = successfulFiles; }
    
    public long getFailedFiles() { return failedFiles; }
    public void setFailedFiles(long failedFiles) { this.failedFiles = failedFiles; }
    
    public List<FileProcessingResult> getFileResults() { return fileResults; }
    public void setFileResults(List<FileProcessingResult> fileResults) { this.fileResults = fileResults; }
    
    public long getExecutionTimeMs() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
    
    @Override
    public String toString() {
        return String.format("FileAdapterExecutionResult{adapterInterfaceId=%s, status=%s, files=%d/%d, duration=%dms}",
                adapterInterfaceId, status, successfulFiles, totalFiles, getExecutionTimeMs());
    }
}