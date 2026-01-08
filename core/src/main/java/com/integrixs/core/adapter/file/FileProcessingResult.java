package com.integrixs.core.adapter.file;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable result object for file processing operations.
 * Contains comprehensive processing metrics and details.
 * Follows OOP principles with proper encapsulation and immutability.
 */
public class FileProcessingResult {
    
    private final int totalFiles;
    private final int successfulFiles;
    private final int failedFiles;
    private final long totalBytesProcessed;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Duration processingDuration;
    private final List<FileProcessingItemResult> itemResults;
    private final List<FileProcessingStageResult> stageResults;
    private final List<String> pipelineErrors;
    private final boolean overallSuccess;
    
    public FileProcessingResult(int totalFiles, int successfulFiles, int failedFiles, 
                              long totalBytesProcessed, LocalDateTime startTime, LocalDateTime endTime,
                              List<FileProcessingItemResult> itemResults,
                              List<FileProcessingStageResult> stageResults,
                              List<String> pipelineErrors, boolean overallSuccess) {
        this.totalFiles = totalFiles;
        this.successfulFiles = successfulFiles;
        this.failedFiles = failedFiles;
        this.totalBytesProcessed = totalBytesProcessed;
        this.startTime = startTime;
        this.endTime = endTime;
        this.processingDuration = Duration.between(startTime, endTime);
        this.itemResults = itemResults != null ? List.copyOf(itemResults) : List.of();
        this.stageResults = stageResults != null ? List.copyOf(stageResults) : List.of();
        this.pipelineErrors = pipelineErrors != null ? List.copyOf(pipelineErrors) : List.of();
        this.overallSuccess = overallSuccess;
    }
    
    // Getters
    public int getTotalFiles() { return totalFiles; }
    public int getSuccessfulFiles() { return successfulFiles; }
    public int getFailedFiles() { return failedFiles; }
    public long getTotalBytesProcessed() { return totalBytesProcessed; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Duration getProcessingDuration() { return processingDuration; }
    public List<FileProcessingItemResult> getItemResults() { return itemResults; }
    public List<FileProcessingStageResult> getStageResults() { return stageResults; }
    public List<String> getPipelineErrors() { return pipelineErrors; }
    public boolean isOverallSuccess() { return overallSuccess; }
    
    // Computed properties
    public double getSuccessRate() {
        return totalFiles > 0 ? (double) successfulFiles / totalFiles * 100.0 : 0.0;
    }
    
    public boolean hasPipelineErrors() { return !pipelineErrors.isEmpty(); }
    public boolean hasFailedFiles() { return failedFiles > 0; }
    
    public long getAverageProcessingTimeMillis() {
        if (totalFiles == 0) return 0;
        return processingDuration.toMillis() / totalFiles;
    }
    
    public String getProcessingSummary() {
        return String.format("Processed %d files: %d successful, %d failed (%.1f%% success rate) in %d ms",
                           totalFiles, successfulFiles, failedFiles, getSuccessRate(),
                           processingDuration.toMillis());
    }
}

/**
 * Immutable result for individual file processing.
 */
class FileProcessingItemResult {
    
    private final String fileName;
    private final long fileSize;
    private final boolean successful;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Duration processingDuration;
    private final List<String> stageResults;
    private final List<String> warnings;
    private final Optional<String> errorMessage;
    private final boolean criticalFailure;
    private final Map<String, Object> processingDetails;
    
    private FileProcessingItemResult(String fileName, long fileSize, boolean successful,
                                   LocalDateTime startTime, LocalDateTime endTime,
                                   List<String> stageResults, List<String> warnings,
                                   String errorMessage, boolean criticalFailure,
                                   Map<String, Object> processingDetails) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.successful = successful;
        this.startTime = startTime;
        this.endTime = endTime;
        this.processingDuration = Duration.between(startTime, endTime);
        this.stageResults = stageResults != null ? List.copyOf(stageResults) : List.of();
        this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
        this.errorMessage = Optional.ofNullable(errorMessage);
        this.criticalFailure = criticalFailure;
        this.processingDetails = processingDetails != null ? Map.copyOf(processingDetails) : Map.of();
    }
    
    // Factory methods
    public static FileProcessingItemResult successful(String fileName, long fileSize,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    List<String> stageResults, List<String> warnings,
                                                    Map<String, Object> processingDetails) {
        return new FileProcessingItemResult(fileName, fileSize, true, startTime, endTime,
                                          stageResults, warnings, null, false, processingDetails);
    }
    
    public static FileProcessingItemResult failed(String fileName, long fileSize,
                                                LocalDateTime startTime, LocalDateTime endTime,
                                                List<String> stageResults, List<String> warnings,
                                                String errorMessage, boolean criticalFailure) {
        return new FileProcessingItemResult(fileName, fileSize, false, startTime, endTime,
                                          stageResults, warnings, errorMessage, criticalFailure, null);
    }
    
    // Getters
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public boolean isSuccessful() { return successful; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Duration getProcessingDuration() { return processingDuration; }
    public List<String> getStageResults() { return stageResults; }
    public List<String> getWarnings() { return warnings; }
    public Optional<String> getErrorMessage() { return errorMessage; }
    public boolean isCriticalFailure() { return criticalFailure; }
    public Map<String, Object> getProcessingDetails() { return processingDetails; }
    
    // Computed properties
    public boolean hasWarnings() { return !warnings.isEmpty(); }
    public long getBytesProcessed() { return successful ? fileSize : 0; }
    
    public String getItemSummary() {
        if (successful) {
            return String.format("%s: SUCCESS (%d bytes, %d ms)%s",
                               fileName, fileSize, processingDuration.toMillis(),
                               hasWarnings() ? " with warnings" : "");
        } else {
            return String.format("%s: FAILED - %s%s",
                               fileName, errorMessage.orElse("Unknown error"),
                               criticalFailure ? " (CRITICAL)" : "");
        }
    }
}

/**
 * Immutable result for pipeline stage execution.
 */
class FileProcessingStageResult {
    
    private final String stageName;
    private final int filesProcessed;
    private final int filesSuccessful;
    private final int filesFailed;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Duration stageDuration;
    private final List<String> stageWarnings;
    private final List<String> stageErrors;
    private final Map<String, Object> stageMetrics;
    
    public FileProcessingStageResult(String stageName, int filesProcessed, int filesSuccessful,
                                   int filesFailed, LocalDateTime startTime, LocalDateTime endTime,
                                   List<String> stageWarnings, List<String> stageErrors,
                                   Map<String, Object> stageMetrics) {
        this.stageName = stageName;
        this.filesProcessed = filesProcessed;
        this.filesSuccessful = filesSuccessful;
        this.filesFailed = filesFailed;
        this.startTime = startTime;
        this.endTime = endTime;
        this.stageDuration = Duration.between(startTime, endTime);
        this.stageWarnings = stageWarnings != null ? List.copyOf(stageWarnings) : List.of();
        this.stageErrors = stageErrors != null ? List.copyOf(stageErrors) : List.of();
        this.stageMetrics = stageMetrics != null ? Map.copyOf(stageMetrics) : Map.of();
    }
    
    // Getters
    public String getStageName() { return stageName; }
    public int getFilesProcessed() { return filesProcessed; }
    public int getFilesSuccessful() { return filesSuccessful; }
    public int getFilesFailed() { return filesFailed; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Duration getStageDuration() { return stageDuration; }
    public List<String> getStageWarnings() { return stageWarnings; }
    public List<String> getStageErrors() { return stageErrors; }
    public Map<String, Object> getStageMetrics() { return stageMetrics; }
    
    // Computed properties
    public boolean hasWarnings() { return !stageWarnings.isEmpty(); }
    public boolean hasErrors() { return !stageErrors.isEmpty(); }
    public double getSuccessRate() {
        return filesProcessed > 0 ? (double) filesSuccessful / filesProcessed * 100.0 : 0.0;
    }
    
    public String getStageSummary() {
        return String.format("Stage '%s': %d/%d files successful (%.1f%%) in %d ms",
                           stageName, filesSuccessful, filesProcessed, getSuccessRate(),
                           stageDuration.toMillis());
    }
}