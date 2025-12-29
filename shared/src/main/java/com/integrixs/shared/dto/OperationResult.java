package com.integrixs.shared.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of a file transfer operation
 */
public class OperationResult {
    
    public enum Status {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED
    }
    
    private String operationId;
    private String bankName;
    private String operationType;
    private Status status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int filesProcessed;
    private long bytesTransferred;
    private String errorMessage;
    private List<String> processedFiles;
    private long durationMs;
    
    public OperationResult() {}
    
    public OperationResult(String operationId, String bankName, String operationType) {
        this.operationId = operationId;
        this.bankName = bankName;
        this.operationType = operationType;
        this.status = Status.PENDING;
        this.startTime = LocalDateTime.now();
        this.filesProcessed = 0;
        this.bytesTransferred = 0L;
    }
    
    public void markStarted() {
        this.status = Status.RUNNING;
        this.startTime = LocalDateTime.now();
    }
    
    public void markCompleted(int filesProcessed, List<String> processedFiles) {
        this.status = Status.SUCCESS;
        this.endTime = LocalDateTime.now();
        this.filesProcessed = filesProcessed;
        this.processedFiles = processedFiles;
        if (this.startTime != null) {
            this.durationMs = java.time.Duration.between(this.startTime, this.endTime).toMillis();
        }
    }
    
    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.endTime = LocalDateTime.now();
        this.errorMessage = errorMessage;
        if (this.startTime != null) {
            this.durationMs = java.time.Duration.between(this.startTime, this.endTime).toMillis();
        }
    }
    
    // Getters and setters
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public int getFilesProcessed() { return filesProcessed; }
    public void setFilesProcessed(int filesProcessed) { this.filesProcessed = filesProcessed; }
    
    public long getBytesTransferred() { return bytesTransferred; }
    public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public List<String> getProcessedFiles() { return processedFiles; }
    public void setProcessedFiles(List<String> processedFiles) { this.processedFiles = processedFiles; }
    
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    
    @Override
    public String toString() {
        return "OperationResult{" +
                "operationId='" + operationId + '\'' +
                ", bankName='" + bankName + '\'' +
                ", operationType='" + operationType + '\'' +
                ", status=" + status +
                ", filesProcessed=" + filesProcessed +
                ", durationMs=" + durationMs +
                '}';
    }
}