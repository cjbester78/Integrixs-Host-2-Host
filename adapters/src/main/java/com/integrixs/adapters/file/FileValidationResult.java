package com.integrixs.adapters.file;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileValidationResult {
    
    private Path filePath;
    private LocalDateTime validationTime;
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private Map<String, Object> metadata;
    private long validationDurationMs;
    
    public FileValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.valid = true;
    }
    
    public FileValidationResult(Path filePath) {
        this();
        this.filePath = filePath;
    }
    
    // Getters and setters
    public Path getFilePath() {
        return filePath;
    }
    
    public void setFilePath(Path filePath) {
        this.filePath = filePath;
    }
    
    public LocalDateTime getValidationTime() {
        return validationTime;
    }
    
    public void setValidationTime(LocalDateTime validationTime) {
        this.validationTime = validationTime;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
    
    public void addError(String error) {
        this.errors.add(error);
        this.valid = false;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
    
    public void addWarning(String warning) {
        this.warnings.add(warning);
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
    
    public long getValidationDurationMs() {
        return validationDurationMs;
    }
    
    public void setValidationDurationMs(long validationDurationMs) {
        this.validationDurationMs = validationDurationMs;
    }
    
    // Helper methods
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public int getErrorCount() {
        return errors.size();
    }
    
    public int getWarningCount() {
        return warnings.size();
    }
    
    public boolean hasIssues() {
        return hasErrors() || hasWarnings();
    }
    
    public String getFirstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }
    
    public String getAllErrors() {
        return String.join("; ", errors);
    }
    
    public String getAllWarnings() {
        return String.join("; ", warnings);
    }
    
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Validation %s", valid ? "PASSED" : "FAILED"));
        
        if (hasErrors()) {
            summary.append(String.format(" - %d error(s)", getErrorCount()));
        }
        
        if (hasWarnings()) {
            summary.append(String.format(" - %d warning(s)", getWarningCount()));
        }
        
        return summary.toString();
    }
    
    /**
     * Creates a validation result for a file that passed all validations
     */
    public static FileValidationResult success(Path filePath) {
        FileValidationResult result = new FileValidationResult(filePath);
        result.setValid(true);
        result.setValidationTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * Creates a validation result for a file that failed validation
     */
    public static FileValidationResult failure(Path filePath, String error) {
        FileValidationResult result = new FileValidationResult(filePath);
        result.addError(error);
        result.setValidationTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * Creates a validation result for a file that has warnings but passed validation
     */
    public static FileValidationResult warning(Path filePath, String warning) {
        FileValidationResult result = new FileValidationResult(filePath);
        result.addWarning(warning);
        result.setValid(true);
        result.setValidationTime(LocalDateTime.now());
        return result;
    }
    
    /**
     * Merges another validation result into this one
     */
    public void merge(FileValidationResult other) {
        if (other == null) return;
        
        this.errors.addAll(other.errors);
        this.warnings.addAll(other.warnings);
        this.metadata.putAll(other.metadata);
        
        if (!other.valid) {
            this.valid = false;
        }
    }
    
    @Override
    public String toString() {
        return "FileValidationResult{" +
                "filePath=" + filePath +
                ", valid=" + valid +
                ", errors=" + errors.size() +
                ", warnings=" + warnings.size() +
                ", validationTime=" + validationTime +
                '}';
    }
    
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FileValidationResult for: ").append(filePath).append("\n");
        sb.append("Status: ").append(valid ? "VALID" : "INVALID").append("\n");
        sb.append("Validation Time: ").append(validationTime).append("\n");
        
        if (hasErrors()) {
            sb.append("Errors (").append(errors.size()).append("):\n");
            for (int i = 0; i < errors.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(errors.get(i)).append("\n");
            }
        }
        
        if (hasWarnings()) {
            sb.append("Warnings (").append(warnings.size()).append("):\n");
            for (int i = 0; i < warnings.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(warnings.get(i)).append("\n");
            }
        }
        
        if (!metadata.isEmpty()) {
            sb.append("Metadata:\n");
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        return sb.toString();
    }
}