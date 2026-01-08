package com.integrixs.adapters.file;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ZipProcessingResult {
    
    private Path zipFilePath;
    private FileProcessingResult parentResult;
    private List<ExtractedFile> extractedFiles;
    private List<FileProcessingResult> fileResults;
    private String error;
    
    public ZipProcessingResult() {
        this.extractedFiles = new ArrayList<>();
        this.fileResults = new ArrayList<>();
    }
    
    public ZipProcessingResult(Path zipFilePath) {
        this();
        this.zipFilePath = zipFilePath;
    }
    
    // Getters and setters
    public Path getZipFilePath() {
        return zipFilePath;
    }
    
    public void setZipFilePath(Path zipFilePath) {
        this.zipFilePath = zipFilePath;
    }
    
    public FileProcessingResult getParentResult() {
        return parentResult;
    }
    
    public void setParentResult(FileProcessingResult parentResult) {
        this.parentResult = parentResult;
    }
    
    public List<ExtractedFile> getExtractedFiles() {
        return extractedFiles;
    }
    
    public void setExtractedFiles(List<ExtractedFile> extractedFiles) {
        this.extractedFiles = extractedFiles;
    }
    
    public void addExtractedFile(ExtractedFile extractedFile) {
        this.extractedFiles.add(extractedFile);
    }
    
    public List<FileProcessingResult> getFileResults() {
        return fileResults;
    }
    
    public void setFileResults(List<FileProcessingResult> fileResults) {
        this.fileResults = fileResults;
    }
    
    public void addFileResult(FileProcessingResult fileResult) {
        this.fileResults.add(fileResult);
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    // Helper methods
    public int getTotalCount() {
        return fileResults.size();
    }
    
    public long getSuccessfulCount() {
        return fileResults.stream()
                .mapToLong(r -> r.isSuccess() ? 1 : 0)
                .sum();
    }
    
    public long getFailedCount() {
        return fileResults.stream()
                .mapToLong(r -> r.isFailed() ? 1 : 0)
                .sum();
    }
    
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }
    
    public boolean allSuccessful() {
        return !fileResults.isEmpty() && getSuccessfulCount() == getTotalCount() && !hasError();
    }
    
    public boolean anyFailed() {
        return getFailedCount() > 0 || hasError();
    }
    
    public long getTotalOriginalSize() {
        return extractedFiles.stream()
                .mapToLong(ExtractedFile::getOriginalSize)
                .sum();
    }
    
    public long getTotalCompressedSize() {
        return extractedFiles.stream()
                .mapToLong(ExtractedFile::getCompressedSize)
                .sum();
    }
    
    public double getOverallCompressionRatio() {
        long totalOriginal = getTotalOriginalSize();
        if (totalOriginal > 0) {
            return (double) getTotalCompressedSize() / totalOriginal;
        }
        return 0.0;
    }
    
    public long getTotalProcessedBytes() {
        return fileResults.stream()
                .filter(FileProcessingResult::isSuccess)
                .mapToLong(FileProcessingResult::getFileSize)
                .sum();
    }
    
    public List<FileProcessingResult> getSuccessfulResults() {
        return fileResults.stream()
                .filter(FileProcessingResult::isSuccess)
                .toList();
    }
    
    public List<FileProcessingResult> getFailedResults() {
        return fileResults.stream()
                .filter(FileProcessingResult::isFailed)
                .toList();
    }
    
    @Override
    public String toString() {
        return "ZipProcessingResult{" +
                "zipFilePath=" + zipFilePath +
                ", totalFiles=" + getTotalCount() +
                ", successful=" + getSuccessfulCount() +
                ", failed=" + getFailedCount() +
                ", totalOriginalSize=" + getTotalOriginalSize() +
                ", totalCompressedSize=" + getTotalCompressedSize() +
                ", compressionRatio=" + String.format("%.2f", getOverallCompressionRatio()) +
                ", error='" + error + '\'' +
                '}';
    }
}