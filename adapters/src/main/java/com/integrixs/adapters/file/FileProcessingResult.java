package com.integrixs.adapters.file;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileProcessingResult {
    
    private UUID id;
    private UUID adapterInterfaceId;
    private Path filePath;
    private FileProcessingStatus status;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long fileSize;
    private String contentHash;
    private Map<String, Object> metadata;
    private String correlationId;
    
    public FileProcessingResult() {
        this.id = UUID.randomUUID();
        this.metadata = new HashMap<>();
        this.status = FileProcessingStatus.PENDING;
    }
    
    public FileProcessingResult(Path filePath, UUID adapterInterfaceId) {
        this();
        this.filePath = filePath;
        this.adapterInterfaceId = adapterInterfaceId;
    }
    
    // Getters and setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getAdapterInterfaceId() {
        return adapterInterfaceId;
    }
    
    public void setAdapterInterfaceId(UUID adapterInterfaceId) {
        this.adapterInterfaceId = adapterInterfaceId;
    }
    
    public Path getFilePath() {
        return filePath;
    }
    
    public void setFilePath(Path filePath) {
        this.filePath = filePath;
    }
    
    public String getFileName() {
        return filePath != null ? filePath.getFileName().toString() : null;
    }
    
    public FileProcessingStatus getStatus() {
        return status;
    }
    
    public void setStatus(FileProcessingStatus status) {
        this.status = status;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
    
    public long getProcessingTimeMs() {
        if (startTime != null && endTime != null) {
            return ChronoUnit.MILLIS.between(startTime, endTime);
        }
        return 0;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getContentHash() {
        return contentHash;
    }
    
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
    
    // Helper methods
    public boolean isSuccess() {
        return status == FileProcessingStatus.SUCCESS;
    }
    
    public boolean isFailed() {
        return status == FileProcessingStatus.FAILED;
    }
    
    public boolean isPending() {
        return status == FileProcessingStatus.PENDING;
    }
    
    public boolean isProcessing() {
        return status == FileProcessingStatus.PROCESSING;
    }
    
    public void markAsProcessing() {
        this.status = FileProcessingStatus.PROCESSING;
        if (this.startTime == null) {
            this.startTime = LocalDateTime.now();
        }
    }
    
    public void markAsSuccess() {
        this.status = FileProcessingStatus.SUCCESS;
        if (this.endTime == null) {
            this.endTime = LocalDateTime.now();
        }
    }
    
    public void markAsFailed(String errorMessage) {
        this.status = FileProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        if (this.endTime == null) {
            this.endTime = LocalDateTime.now();
        }
    }
    
    @Override
    public String toString() {
        return "FileProcessingResult{" +
                "id=" + id +
                ", filePath=" + filePath +
                ", status=" + status +
                ", fileSize=" + fileSize +
                ", processingTime=" + getProcessingTimeMs() + "ms" +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        FileProcessingResult that = (FileProcessingResult) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}