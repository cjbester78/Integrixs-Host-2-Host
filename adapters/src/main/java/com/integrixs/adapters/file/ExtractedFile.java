package com.integrixs.adapters.file;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public class ExtractedFile {
    
    private String originalEntryName;
    private Path extractedPath;
    private long originalSize;
    private long compressedSize;
    private FileTime lastModified;
    
    public ExtractedFile() {
    }
    
    public ExtractedFile(String originalEntryName, Path extractedPath) {
        this.originalEntryName = originalEntryName;
        this.extractedPath = extractedPath;
    }
    
    // Getters and setters
    public String getOriginalEntryName() {
        return originalEntryName;
    }
    
    public void setOriginalEntryName(String originalEntryName) {
        this.originalEntryName = originalEntryName;
    }
    
    public Path getExtractedPath() {
        return extractedPath;
    }
    
    public void setExtractedPath(Path extractedPath) {
        this.extractedPath = extractedPath;
    }
    
    public long getOriginalSize() {
        return originalSize;
    }
    
    public void setOriginalSize(long originalSize) {
        this.originalSize = originalSize;
    }
    
    public long getCompressedSize() {
        return compressedSize;
    }
    
    public void setCompressedSize(long compressedSize) {
        this.compressedSize = compressedSize;
    }
    
    public FileTime getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(FileTime lastModified) {
        this.lastModified = lastModified;
    }
    
    // Helper methods
    public String getExtractedFileName() {
        return extractedPath != null ? extractedPath.getFileName().toString() : null;
    }
    
    public double getCompressionRatio() {
        if (originalSize > 0) {
            return (double) compressedSize / originalSize;
        }
        return 0.0;
    }
    
    public long getSavedSpace() {
        return originalSize - compressedSize;
    }
    
    @Override
    public String toString() {
        return "ExtractedFile{" +
                "originalEntryName='" + originalEntryName + '\'' +
                ", extractedPath=" + extractedPath +
                ", originalSize=" + originalSize +
                ", compressedSize=" + compressedSize +
                ", compressionRatio=" + String.format("%.2f", getCompressionRatio()) +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ExtractedFile that = (ExtractedFile) o;
        
        if (originalEntryName != null ? !originalEntryName.equals(that.originalEntryName) : that.originalEntryName != null)
            return false;
        return extractedPath != null ? extractedPath.equals(that.extractedPath) : that.extractedPath == null;
    }
    
    @Override
    public int hashCode() {
        int result = originalEntryName != null ? originalEntryName.hashCode() : 0;
        result = 31 * result + (extractedPath != null ? extractedPath.hashCode() : 0);
        return result;
    }
}