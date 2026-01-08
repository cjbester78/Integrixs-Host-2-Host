package com.integrixs.core.adapter.sftp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable result object for SFTP operations.
 * Contains comprehensive operation results with metadata and error handling.
 * Follows OOP principles with proper encapsulation and immutability.
 */
public class SftpOperationResult {
    
    private final boolean successful;
    private final String operationName;
    private final LocalDateTime executionTime;
    private final long durationMs;
    private final Optional<String> errorMessage;
    private final Optional<Exception> exception;
    private final Map<String, Object> resultData;
    private final List<String> warnings;
    private final SftpOperationMetrics metrics;
    
    private SftpOperationResult(boolean successful, String operationName, LocalDateTime executionTime,
                              long durationMs, String errorMessage, Exception exception,
                              Map<String, Object> resultData, List<String> warnings,
                              SftpOperationMetrics metrics) {
        this.successful = successful;
        this.operationName = operationName;
        this.executionTime = executionTime;
        this.durationMs = durationMs;
        this.errorMessage = Optional.ofNullable(errorMessage);
        this.exception = Optional.ofNullable(exception);
        this.resultData = resultData != null ? Map.copyOf(resultData) : Map.of();
        this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
        this.metrics = metrics;
    }
    
    // Getters
    public boolean isSuccessful() { return successful; }
    public String getOperationName() { return operationName; }
    public LocalDateTime getExecutionTime() { return executionTime; }
    public long getDurationMs() { return durationMs; }
    public Optional<String> getErrorMessage() { return errorMessage; }
    public Optional<Exception> getException() { return exception; }
    public Map<String, Object> getResultData() { return resultData; }
    public List<String> getWarnings() { return warnings; }
    public SftpOperationMetrics getMetrics() { return metrics; }
    
    // Computed properties
    public boolean hasWarnings() { return !warnings.isEmpty(); }
    public boolean hasException() { return exception.isPresent(); }
    
    // Factory methods for success results
    public static SftpOperationResult success(Map<String, Object> resultData, String operationName) {
        return success(resultData, operationName, 0L);
    }
    
    public static SftpOperationResult success(Map<String, Object> resultData, String operationName, long durationMs) {
        SftpOperationMetrics metrics = new SftpOperationMetrics(
            resultData != null ? (Long) resultData.getOrDefault("bytesTransferred", 0L) : 0L,
            resultData != null ? (Integer) resultData.getOrDefault("filesProcessed", 0) : 0,
            0 // No errors for success
        );
        
        return new SftpOperationResult(true, operationName, LocalDateTime.now(), durationMs,
                                     null, null, resultData, null, metrics);
    }
    
    public static SftpOperationResult successWithWarnings(Map<String, Object> resultData, String operationName,
                                                        long durationMs, List<String> warnings) {
        SftpOperationMetrics metrics = new SftpOperationMetrics(
            resultData != null ? (Long) resultData.getOrDefault("bytesTransferred", 0L) : 0L,
            resultData != null ? (Integer) resultData.getOrDefault("filesProcessed", 0) : 0,
            0 // No errors for success
        );
        
        return new SftpOperationResult(true, operationName, LocalDateTime.now(), durationMs,
                                     null, null, resultData, warnings, metrics);
    }
    
    // Factory methods for failure results
    public static SftpOperationResult failure(String errorMessage, Exception exception) {
        return failure(errorMessage, exception, "UNKNOWN_OPERATION", 0L);
    }
    
    public static SftpOperationResult failure(String errorMessage, Exception exception, 
                                            String operationName, long durationMs) {
        SftpOperationMetrics metrics = new SftpOperationMetrics(0L, 0, 1);
        
        return new SftpOperationResult(false, operationName, LocalDateTime.now(), durationMs,
                                     errorMessage, exception, null, null, metrics);
    }
    
    public static SftpOperationResult failureWithData(String errorMessage, Exception exception,
                                                     String operationName, long durationMs,
                                                     Map<String, Object> partialData) {
        SftpOperationMetrics metrics = new SftpOperationMetrics(
            partialData != null ? (Long) partialData.getOrDefault("bytesTransferred", 0L) : 0L,
            partialData != null ? (Integer) partialData.getOrDefault("filesProcessed", 0) : 0,
            1
        );
        
        return new SftpOperationResult(false, operationName, LocalDateTime.now(), durationMs,
                                     errorMessage, exception, partialData, null, metrics);
    }
    
    // Builder pattern for complex results
    public static Builder builder(String operationName) {
        return new Builder(operationName);
    }
    
    public static class Builder {
        private final String operationName;
        private boolean successful = true;
        private LocalDateTime executionTime = LocalDateTime.now();
        private long durationMs = 0L;
        private String errorMessage = null;
        private Exception exception = null;
        private Map<String, Object> resultData = null;
        private List<String> warnings = null;
        private long bytesTransferred = 0L;
        private int filesProcessed = 0;
        private int errorCount = 0;
        
        private Builder(String operationName) {
            this.operationName = operationName;
        }
        
        public Builder successful(boolean successful) {
            this.successful = successful;
            return this;
        }
        
        public Builder executionTime(LocalDateTime executionTime) {
            this.executionTime = executionTime;
            return this;
        }
        
        public Builder duration(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }
        
        public Builder error(String errorMessage, Exception exception) {
            this.successful = false;
            this.errorMessage = errorMessage;
            this.exception = exception;
            this.errorCount = 1;
            return this;
        }
        
        public Builder resultData(Map<String, Object> resultData) {
            this.resultData = resultData;
            return this;
        }
        
        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }
        
        public Builder bytesTransferred(long bytesTransferred) {
            this.bytesTransferred = bytesTransferred;
            return this;
        }
        
        public Builder filesProcessed(int filesProcessed) {
            this.filesProcessed = filesProcessed;
            return this;
        }
        
        public Builder errorCount(int errorCount) {
            this.errorCount = errorCount;
            return this;
        }
        
        public SftpOperationResult build() {
            SftpOperationMetrics metrics = new SftpOperationMetrics(bytesTransferred, filesProcessed, errorCount);
            
            return new SftpOperationResult(successful, operationName, executionTime, durationMs,
                                         errorMessage, exception, resultData, warnings, metrics);
        }
    }
    
    /**
     * Get operation summary for logging and monitoring.
     */
    public String getOperationSummary() {
        if (successful) {
            return String.format("%s: SUCCESS (%d ms, %d bytes, %d files)%s",
                               operationName, durationMs, metrics.getBytesTransferred(), 
                               metrics.getFilesProcessed(),
                               hasWarnings() ? " with " + warnings.size() + " warnings" : "");
        } else {
            return String.format("%s: FAILED (%d ms) - %s",
                               operationName, durationMs, 
                               errorMessage.orElse("Unknown error"));
        }
    }
    
    /**
     * Merge this result with another result for batch operations.
     */
    public SftpOperationResult mergeWith(SftpOperationResult other) {
        if (other == null) {
            return this;
        }
        
        boolean mergedSuccessful = this.successful && other.successful;
        long totalDuration = this.durationMs + other.durationMs;
        
        // Merge metrics
        SftpOperationMetrics mergedMetrics = new SftpOperationMetrics(
            this.metrics.getBytesTransferred() + other.metrics.getBytesTransferred(),
            this.metrics.getFilesProcessed() + other.metrics.getFilesProcessed(),
            this.metrics.getErrorCount() + other.metrics.getErrorCount()
        );
        
        // Merge warnings
        List<String> mergedWarnings = new java.util.ArrayList<>(this.warnings);
        mergedWarnings.addAll(other.warnings);
        
        // Merge result data
        Map<String, Object> mergedData = new java.util.HashMap<>(this.resultData);
        mergedData.putAll(other.resultData);
        
        String mergedError = null;
        Exception mergedException = null;
        
        if (!mergedSuccessful) {
            if (!this.successful) {
                mergedError = this.errorMessage.orElse("Operation failed");
                mergedException = this.exception.orElse(null);
            } else {
                mergedError = other.errorMessage.orElse("Operation failed");
                mergedException = other.exception.orElse(null);
            }
        }
        
        return new SftpOperationResult(mergedSuccessful, 
                                     this.operationName + "+" + other.operationName,
                                     this.executionTime, totalDuration, mergedError, mergedException,
                                     mergedData, mergedWarnings, mergedMetrics);
    }
    
    @Override
    public String toString() {
        return getOperationSummary();
    }
}

/**
 * Immutable metrics for SFTP operations.
 */
class SftpOperationMetrics {
    private final long bytesTransferred;
    private final int filesProcessed;
    private final int errorCount;
    
    public SftpOperationMetrics(long bytesTransferred, int filesProcessed, int errorCount) {
        this.bytesTransferred = bytesTransferred;
        this.filesProcessed = filesProcessed;
        this.errorCount = errorCount;
    }
    
    public long getBytesTransferred() { return bytesTransferred; }
    public int getFilesProcessed() { return filesProcessed; }
    public int getErrorCount() { return errorCount; }
    
    public double getThroughputBytesPerSecond(long durationMs) {
        return durationMs > 0 ? (double) bytesTransferred / (durationMs / 1000.0) : 0.0;
    }
    
    public double getFilesPerSecond(long durationMs) {
        return durationMs > 0 ? (double) filesProcessed / (durationMs / 1000.0) : 0.0;
    }
}