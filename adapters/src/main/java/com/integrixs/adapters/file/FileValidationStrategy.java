package com.integrixs.adapters.file;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for file validation operations.
 * Implements Strategy pattern for different file validation approaches.
 * Follows OOP principles with immutable validation results.
 */
public interface FileValidationStrategy {
    
    /**
     * Validate a file according to the strategy's criteria.
     * 
     * @param filePath the file to validate
     * @param config adapter configuration for validation rules
     * @return immutable validation result
     */
    FileValidationResult validateFile(Path filePath, Map<String, Object> config);
    
    /**
     * Get the strategy name for identification.
     * 
     * @return strategy name
     */
    String getStrategyName();
    
    /**
     * Get validation rules description.
     * 
     * @return description of validation rules
     */
    String getValidationDescription();
    
    /**
     * Immutable file validation result
     */
    class FileValidationResult {
        private final boolean valid;
        private final String fileName;
        private final long fileSize;
        private final List<String> validationMessages;
        private final List<String> warnings;
        private final ValidationCategory category;
        private final Map<String, Object> validationDetails;
        
        private FileValidationResult(boolean valid, String fileName, long fileSize,
                                   List<String> validationMessages, List<String> warnings,
                                   ValidationCategory category, Map<String, Object> validationDetails) {
            this.valid = valid;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.validationMessages = validationMessages != null ? List.copyOf(validationMessages) : List.of();
            this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
            this.category = category;
            this.validationDetails = validationDetails != null ? Map.copyOf(validationDetails) : Map.of();
        }
        
        public boolean isValid() { return valid; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public List<String> getValidationMessages() { return validationMessages; }
        public List<String> getWarnings() { return warnings; }
        public ValidationCategory getCategory() { return category; }
        public Map<String, Object> getValidationDetails() { return validationDetails; }
        
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean hasValidationMessages() { return !validationMessages.isEmpty(); }
        
        public static FileValidationResult valid(String fileName, long fileSize) {
            return new FileValidationResult(true, fileName, fileSize, null, null, 
                                          ValidationCategory.VALID, null);
        }
        
        public static FileValidationResult validWithWarnings(String fileName, long fileSize, 
                                                           List<String> warnings) {
            return new FileValidationResult(true, fileName, fileSize, null, warnings,
                                          ValidationCategory.VALID_WITH_WARNINGS, null);
        }
        
        public static FileValidationResult invalid(String fileName, long fileSize, 
                                                 List<String> validationMessages,
                                                 ValidationCategory category) {
            return new FileValidationResult(false, fileName, fileSize, validationMessages, null,
                                          category, null);
        }
        
        public static FileValidationResult withDetails(boolean valid, String fileName, long fileSize,
                                                     List<String> validationMessages, List<String> warnings,
                                                     ValidationCategory category, Map<String, Object> details) {
            return new FileValidationResult(valid, fileName, fileSize, validationMessages, warnings,
                                          category, details);
        }
    }
    
    /**
     * Validation categories for classification
     */
    enum ValidationCategory {
        VALID("Valid", "File passes all validation checks"),
        VALID_WITH_WARNINGS("Valid with Warnings", "File is valid but has warnings"),
        INVALID_FORMAT("Invalid Format", "File format is not supported"),
        INVALID_SIZE("Invalid Size", "File size is outside acceptable range"),
        INVALID_NAME("Invalid Name", "File name doesn't match required pattern"),
        INVALID_CONTENT("Invalid Content", "File content validation failed"),
        INVALID_PERMISSIONS("Invalid Permissions", "File permissions are insufficient"),
        INVALID_LOCK_STATUS("Invalid Lock Status", "File is locked or in use"),
        INVALID_TIMESTAMP("Invalid Timestamp", "File timestamp is outside acceptable range"),
        UNKNOWN_ERROR("Unknown Error", "Validation failed with unknown error");
        
        private final String displayName;
        private final String description;
        
        ValidationCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
}