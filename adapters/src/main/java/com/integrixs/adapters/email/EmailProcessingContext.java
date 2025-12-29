package com.integrixs.adapters.email;

import java.time.LocalDateTime;

/**
 * Context information for email processing operations
 */
public class EmailProcessingContext {
    
    private String fileName;
    private long fileSize;
    private String operation;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;
    private int recordCount;
    private int successCount;
    private int errorCount;
    
    public EmailProcessingContext() {
        this.startTime = LocalDateTime.now();
        this.recordCount = 0;
        this.successCount = 0;
        this.errorCount = 0;
    }
    
    public EmailProcessingContext(String fileName, String operation) {
        this();
        this.fileName = fileName;
        this.operation = operation;
    }
    
    // Getters and setters
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public void setOperation(String operation) {
        this.operation = operation;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public int getRecordCount() {
        return recordCount;
    }
    
    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }
    
    public int getSuccessCount() {
        return successCount;
    }
    
    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }
    
    public int getErrorCount() {
        return errorCount;
    }
    
    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }
    
    // Helper methods
    public void markCompleted(String status) {
        this.status = status;
        this.endTime = LocalDateTime.now();
    }
    
    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.endTime = LocalDateTime.now();
    }
    
    public void incrementRecordCount() {
        this.recordCount++;
    }
    
    public void incrementSuccessCount() {
        this.successCount++;
    }
    
    public void incrementErrorCount() {
        this.errorCount++;
    }
    
    public double getSuccessRate() {
        if (recordCount == 0) {
            return 0.0;
        }
        return (double) successCount / recordCount * 100.0;
    }
    
    public java.time.Duration getDuration() {
        if (startTime == null) {
            return java.time.Duration.ZERO;
        }
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end);
    }
    
    @Override
    public String toString() {
        return "EmailProcessingContext{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", operation='" + operation + '\'' +
                ", status='" + status + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", errorMessage='" + errorMessage + '\'' +
                ", recordCount=" + recordCount +
                ", successCount=" + successCount +
                ", errorCount=" + errorCount +
                '}';
    }
}