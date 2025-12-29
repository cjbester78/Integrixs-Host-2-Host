package com.integrixs.adapters.sftp;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Result of an SFTP operation (upload/download)
 */
public class SftpOperationResult {
    
    private UUID id;
    private SftpOperation operation;
    private SftpOperationStatus status;
    private Path localPath;
    private String remotePath;
    private String message;
    private String errorMessage;
    private Exception exception;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long bytesTransferred;
    private Map<String, Object> metadata;
    
    public SftpOperationResult() {
        this.id = UUID.randomUUID();
        this.status = SftpOperationStatus.PENDING;
        this.metadata = new HashMap<>();
    }
    
    public SftpOperationResult(SftpOperation operation) {
        this();
        this.operation = operation;
    }
    
    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public SftpOperation getOperation() { return operation; }
    public void setOperation(SftpOperation operation) { this.operation = operation; }
    
    public SftpOperationStatus getStatus() { return status; }
    public void setStatus(SftpOperationStatus status) { this.status = status; }
    
    public Path getLocalPath() { return localPath; }
    public void setLocalPath(Path localPath) { this.localPath = localPath; }
    
    public String getRemotePath() { return remotePath; }
    public void setRemotePath(String remotePath) { this.remotePath = remotePath; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Exception getException() { return exception; }
    public void setException(Exception exception) { this.exception = exception; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public long getBytesTransferred() { return bytesTransferred; }
    public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    // Helper methods
    public long getDurationMs() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
    
    public String getFormattedDuration() {
        long duration = getDurationMs();
        if (duration < 1000) {
            return duration + "ms";
        } else if (duration < 60000) {
            return String.format("%.1fs", duration / 1000.0);
        } else {
            long minutes = duration / 60000;
            long seconds = (duration % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    public String getFormattedBytes() {
        if (bytesTransferred < 1024) return bytesTransferred + " B";
        if (bytesTransferred < 1024 * 1024) return String.format("%.1f KB", bytesTransferred / 1024.0);
        if (bytesTransferred < 1024 * 1024 * 1024) return String.format("%.1f MB", bytesTransferred / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytesTransferred / (1024.0 * 1024.0 * 1024.0));
    }
    
    public double getTransferRateKbps() {
        long durationMs = getDurationMs();
        if (durationMs > 0 && bytesTransferred > 0) {
            return (bytesTransferred / 1024.0) / (durationMs / 1000.0);
        }
        return 0;
    }
    
    public boolean isSuccess() {
        return status == SftpOperationStatus.SUCCESS;
    }
    
    public boolean isFailed() {
        return status == SftpOperationStatus.FAILED;
    }
    
    @Override
    public String toString() {
        return String.format("SftpOperationResult{id=%s, operation=%s, status=%s, local=%s, remote=%s, bytes=%s, duration=%s}",
                id, operation, status, 
                localPath != null ? localPath.getFileName() : null, 
                remotePath, getFormattedBytes(), getFormattedDuration());
    }
}