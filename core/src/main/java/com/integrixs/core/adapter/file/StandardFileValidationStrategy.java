package com.integrixs.core.adapter.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Standard implementation of file validation strategy.
 * Provides comprehensive file validation including size, name patterns, 
 * timestamps, and file accessibility checks.
 * Follows OOP principles with immutable results and configurable validation rules.
 */
@Component
public class StandardFileValidationStrategy implements FileValidationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(StandardFileValidationStrategy.class);
    
    @Override
    public String getStrategyName() {
        return "STANDARD";
    }
    
    @Override
    public String getValidationDescription() {
        return "Standard file validation including size, name patterns, timestamps, and accessibility";
    }
    
    @Override
    public FileValidationResult validateFile(Path filePath, Map<String, Object> config) {
        if (filePath == null) {
            return FileValidationResult.invalid("", 0L, 
                List.of("File path cannot be null"), 
                ValidationCategory.UNKNOWN_ERROR);
        }
        
        String fileName = filePath.getFileName().toString();
        List<String> validationMessages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> validationDetails = new HashMap<>();
        
        try {
            // Check if file exists
            if (!Files.exists(filePath)) {
                validationMessages.add("File does not exist: " + fileName);
                return FileValidationResult.invalid(fileName, 0L, validationMessages, 
                                                  ValidationCategory.INVALID_FORMAT);
            }
            
            // Check if it's a regular file (not directory, symlink, etc.)
            if (!Files.isRegularFile(filePath)) {
                validationMessages.add("Path is not a regular file: " + fileName);
                return FileValidationResult.invalid(fileName, 0L, validationMessages,
                                                  ValidationCategory.INVALID_FORMAT);
            }
            
            long fileSize = Files.size(filePath);
            validationDetails.put("fileSize", fileSize);
            validationDetails.put("lastModified", Files.getLastModifiedTime(filePath).toString());
            
            // Validate file size
            FileValidationResult sizeValidation = validateFileSize(fileSize, fileName, config, warnings);
            if (!sizeValidation.isValid()) {
                return sizeValidation;
            }
            
            // Validate file name pattern
            FileValidationResult nameValidation = validateFileName(fileName, config, warnings);
            if (!nameValidation.isValid()) {
                return FileValidationResult.invalid(fileName, fileSize, nameValidation.getValidationMessages(),
                                                  ValidationCategory.INVALID_NAME);
            }
            
            // Validate file timestamp
            FileValidationResult timestampValidation = validateFileTimestamp(filePath, fileName, fileSize, 
                                                                           config, warnings);
            if (!timestampValidation.isValid()) {
                return timestampValidation;
            }
            
            // Validate file accessibility
            FileValidationResult accessValidation = validateFileAccess(filePath, fileName, fileSize, 
                                                                      config, warnings);
            if (!accessValidation.isValid()) {
                return accessValidation;
            }
            
            // Validate file content if required
            FileValidationResult contentValidation = validateFileContent(filePath, fileName, fileSize,
                                                                        config, warnings, validationDetails);
            if (!contentValidation.isValid()) {
                return contentValidation;
            }
            
            // All validations passed
            if (warnings.isEmpty()) {
                return FileValidationResult.withDetails(true, fileName, fileSize, null, null,
                                                       ValidationCategory.VALID, validationDetails);
            } else {
                return FileValidationResult.withDetails(true, fileName, fileSize, null, warnings,
                                                       ValidationCategory.VALID_WITH_WARNINGS, validationDetails);
            }
            
        } catch (Exception e) {
            logger.error("File validation failed for {}: {}", fileName, e.getMessage(), e);
            validationMessages.add("Validation error: " + e.getMessage());
            return FileValidationResult.invalid(fileName, 0L, validationMessages,
                                              ValidationCategory.UNKNOWN_ERROR);
        }
    }
    
    /**
     * Validate file size constraints
     */
    private FileValidationResult validateFileSize(long fileSize, String fileName, 
                                                Map<String, Object> config, List<String> warnings) {
        
        // Get size constraints from config
        long minFileSize = getLongConfig(config, "validation.minFileSize", 0L);
        long maxFileSize = getLongConfig(config, "validation.maxFileSize", Long.MAX_VALUE);
        long warnFileSize = getLongConfig(config, "validation.warnFileSize", 100 * 1024 * 1024L); // 100MB default
        
        List<String> validationMessages = new ArrayList<>();
        
        if (fileSize < minFileSize) {
            validationMessages.add("File size " + fileSize + " bytes is below minimum " + minFileSize + " bytes");
            return FileValidationResult.invalid(fileName, fileSize, validationMessages,
                                              ValidationCategory.INVALID_SIZE);
        }
        
        if (fileSize > maxFileSize) {
            validationMessages.add("File size " + fileSize + " bytes exceeds maximum " + maxFileSize + " bytes");
            return FileValidationResult.invalid(fileName, fileSize, validationMessages,
                                              ValidationCategory.INVALID_SIZE);
        }
        
        if (fileSize > warnFileSize) {
            warnings.add("File size " + fileSize + " bytes is larger than recommended " + warnFileSize + " bytes");
        }
        
        if (fileSize == 0) {
            warnings.add("File is empty (0 bytes)");
        }
        
        return FileValidationResult.valid(fileName, fileSize);
    }
    
    /**
     * Validate file name pattern
     */
    private FileValidationResult validateFileName(String fileName, Map<String, Object> config, 
                                                List<String> warnings) {
        
        List<String> validationMessages = new ArrayList<>();
        
        // Check required file name pattern
        String requiredPattern = getStringConfig(config, "validation.fileNamePattern", null);
        if (requiredPattern != null && !requiredPattern.trim().isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(requiredPattern);
                if (!pattern.matcher(fileName).matches()) {
                    validationMessages.add("File name '" + fileName + "' does not match required pattern: " + requiredPattern);
                    return FileValidationResult.invalid(fileName, 0L, validationMessages,
                                                      ValidationCategory.INVALID_NAME);
                }
            } catch (Exception e) {
                warnings.add("Invalid file name pattern in configuration: " + requiredPattern);
            }
        }
        
        // Check forbidden file name patterns
        String forbiddenPattern = getStringConfig(config, "validation.forbiddenFileNamePattern", null);
        if (forbiddenPattern != null && !forbiddenPattern.trim().isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(forbiddenPattern);
                if (pattern.matcher(fileName).matches()) {
                    validationMessages.add("File name '" + fileName + "' matches forbidden pattern: " + forbiddenPattern);
                    return FileValidationResult.invalid(fileName, 0L, validationMessages,
                                                      ValidationCategory.INVALID_NAME);
                }
            } catch (Exception e) {
                warnings.add("Invalid forbidden file name pattern in configuration: " + forbiddenPattern);
            }
        }
        
        // Check file extension if required
        String requiredExtension = getStringConfig(config, "validation.requiredExtension", null);
        if (requiredExtension != null && !requiredExtension.trim().isEmpty()) {
            if (!fileName.toLowerCase().endsWith(requiredExtension.toLowerCase())) {
                validationMessages.add("File '" + fileName + "' does not have required extension: " + requiredExtension);
                return FileValidationResult.invalid(fileName, 0L, validationMessages,
                                                  ValidationCategory.INVALID_NAME);
            }
        }
        
        return FileValidationResult.valid(fileName, 0L);
    }
    
    /**
     * Validate file timestamp constraints
     */
    private FileValidationResult validateFileTimestamp(Path filePath, String fileName, long fileSize,
                                                     Map<String, Object> config, List<String> warnings) {
        try {
            Instant lastModified = Files.getLastModifiedTime(filePath).toInstant();
            LocalDateTime fileTime = LocalDateTime.ofInstant(lastModified, ZoneId.systemDefault());
            LocalDateTime now = LocalDateTime.now();
            
            List<String> validationMessages = new ArrayList<>();
            
            // Check minimum age constraint
            long minAgeMinutes = getLongConfig(config, "validation.minFileAgeMinutes", 0L);
            if (minAgeMinutes > 0) {
                LocalDateTime minTime = now.minusMinutes(minAgeMinutes);
                if (fileTime.isAfter(minTime)) {
                    validationMessages.add("File '" + fileName + "' is too recent (modified: " + fileTime + ", minimum age: " + minAgeMinutes + " minutes)");
                    return FileValidationResult.invalid(fileName, fileSize, validationMessages,
                                                      ValidationCategory.INVALID_TIMESTAMP);
                }
            }
            
            // Check maximum age constraint
            long maxAgeMinutes = getLongConfig(config, "validation.maxFileAgeMinutes", Long.MAX_VALUE);
            if (maxAgeMinutes < Long.MAX_VALUE) {
                LocalDateTime maxTime = now.minusMinutes(maxAgeMinutes);
                if (fileTime.isBefore(maxTime)) {
                    validationMessages.add("File '" + fileName + "' is too old (modified: " + fileTime + ", maximum age: " + maxAgeMinutes + " minutes)");
                    return FileValidationResult.invalid(fileName, fileSize, validationMessages,
                                                      ValidationCategory.INVALID_TIMESTAMP);
                }
            }
            
            // Warn about very old or very new files
            if (fileTime.isBefore(now.minusDays(30))) {
                warnings.add("File '" + fileName + "' is older than 30 days (modified: " + fileTime + ")");
            }
            
            if (fileTime.isAfter(now.plusMinutes(5))) {
                warnings.add("File '" + fileName + "' has future timestamp (modified: " + fileTime + ")");
            }
            
        } catch (Exception e) {
            logger.debug("Could not validate file timestamp for {}: {}", fileName, e.getMessage());
            warnings.add("Could not validate file timestamp: " + e.getMessage());
        }
        
        return FileValidationResult.valid(fileName, fileSize);
    }
    
    /**
     * Validate file accessibility and lock status
     */
    private FileValidationResult validateFileAccess(Path filePath, String fileName, long fileSize,
                                                  Map<String, Object> config, List<String> warnings) {
        
        List<String> validationMessages = new ArrayList<>();
        
        // Check if file is readable
        if (!Files.isReadable(filePath)) {
            validationMessages.add("File '" + fileName + "' is not readable");
            return FileValidationResult.invalid(fileName, fileSize, validationMessages,
                                              ValidationCategory.INVALID_PERMISSIONS);
        }
        
        // Check if file appears to be locked (simple heuristic)
        boolean checkFileLock = getBooleanConfig(config, "validation.checkFileLock", true);
        if (checkFileLock) {
            try {
                // Try to open file for reading to check if it's locked
                try (var ignored = Files.newInputStream(filePath)) {
                    // File is accessible
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("lock")) {
                    validationMessages.add("File '" + fileName + "' appears to be locked or in use");
                    return FileValidationResult.invalid(fileName, fileSize, validationMessages,
                                                      ValidationCategory.INVALID_LOCK_STATUS);
                } else {
                    warnings.add("Could not verify file lock status: " + e.getMessage());
                }
            }
        }
        
        return FileValidationResult.valid(fileName, fileSize);
    }
    
    /**
     * Validate file content if content validation is enabled
     */
    private FileValidationResult validateFileContent(Path filePath, String fileName, long fileSize,
                                                   Map<String, Object> config, List<String> warnings,
                                                   Map<String, Object> validationDetails) {
        
        boolean enableContentValidation = getBooleanConfig(config, "validation.enableContentValidation", false);
        if (!enableContentValidation) {
            return FileValidationResult.valid(fileName, fileSize);
        }
        
        List<String> validationMessages = new ArrayList<>();
        
        try {
            // Read first few bytes to check file signature/magic number
            byte[] header = new byte[Math.min(1024, (int) fileSize)];
            try (var inputStream = Files.newInputStream(filePath)) {
                int bytesRead = inputStream.read(header);
                validationDetails.put("headerBytesRead", bytesRead);
                validationDetails.put("fileSignature", bytesToHex(header, Math.min(16, bytesRead)));
            }
            
            // Check for required content patterns
            String requiredContentPattern = getStringConfig(config, "validation.requiredContentPattern", null);
            if (requiredContentPattern != null && !requiredContentPattern.trim().isEmpty()) {
                String headerText = new String(header).toLowerCase();
                if (!headerText.contains(requiredContentPattern.toLowerCase())) {
                    validationMessages.add("File '" + fileName + "' does not contain required content pattern");
                    return FileValidationResult.invalid(fileName, fileSize, validationMessages,
                                                      ValidationCategory.INVALID_CONTENT);
                }
            }
            
            // Check for forbidden content patterns
            String forbiddenContentPattern = getStringConfig(config, "validation.forbiddenContentPattern", null);
            if (forbiddenContentPattern != null && !forbiddenContentPattern.trim().isEmpty()) {
                String headerText = new String(header).toLowerCase();
                if (headerText.contains(forbiddenContentPattern.toLowerCase())) {
                    validationMessages.add("File '" + fileName + "' contains forbidden content pattern");
                    return FileValidationResult.invalid(fileName, fileSize, validationMessages,
                                                      ValidationCategory.INVALID_CONTENT);
                }
            }
            
        } catch (Exception e) {
            logger.debug("Could not validate file content for {}: {}", fileName, e.getMessage());
            warnings.add("Could not validate file content: " + e.getMessage());
        }
        
        return FileValidationResult.valid(fileName, fileSize);
    }
    
    /**
     * Helper methods for configuration extraction with defaults
     */
    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private long getLongConfig(Map<String, Object> config, String key, long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid long value for config {}: {}", key, value);
            }
        }
        return defaultValue;
    }
    
    private boolean getBooleanConfig(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    /**
     * Convert bytes to hexadecimal string for file signature display
     */
    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
            if (i < length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}