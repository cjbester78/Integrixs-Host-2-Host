package com.integrixs.adapters.file;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable result object for individual file processing operations.
 * Used by repository layer for database persistence.
 * Compatible with ProcessedFileRepository requirements.
 */
public class FileProcessingResult {
    
    private UUID id;
    private UUID adapterInterfaceId;
    private Path filePath;
    private String fileName;
    private long fileSize;
    private FileProcessingStatus status;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String contentHash;
    private Map<String, Object> metadata;
    
    public FileProcessingResult() {
        // Default constructor for repository mapping
    }
    
    public FileProcessingResult(String fileName, long fileSize, FileProcessingStatus status) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.status = status;
        this.startTime = LocalDateTime.now();
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getAdapterInterfaceId() { return adapterInterfaceId; }
    public void setAdapterInterfaceId(UUID adapterInterfaceId) { this.adapterInterfaceId = adapterInterfaceId; }
    
    public Path getFilePath() { return filePath; }
    public void setFilePath(Path filePath) { this.filePath = filePath; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public FileProcessingStatus getStatus() { return status; }
    public void setStatus(FileProcessingStatus status) { this.status = status; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    // Utility methods
    public boolean isSuccessful() {
        return status == FileProcessingStatus.SUCCESS;
    }
    
    public long getProcessingTimeMs() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
    
    public void markAsCompleted(boolean successful) {
        this.endTime = LocalDateTime.now();
        this.status = successful ? FileProcessingStatus.SUCCESS : FileProcessingStatus.FAILED;
    }
    
    public void markAsFailed(String errorMessage) {
        this.endTime = LocalDateTime.now();
        this.status = FileProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
    }
    
    @Override
    public String toString() {
        return String.format("FileProcessingResult{fileName='%s', status=%s, fileSize=%d, processingTime=%dms}",
                fileName, status, fileSize, getProcessingTimeMs());
    }
}