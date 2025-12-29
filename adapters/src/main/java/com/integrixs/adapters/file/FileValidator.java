package com.integrixs.adapters.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FileValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(FileValidator.class);
    
    private final FileAdapterConfig config;
    
    public FileValidator(FileAdapterConfig config) {
        this.config = config;
    }
    
    /**
     * Validates a file against all configured validation rules
     */
    public FileValidationResult validate(Path filePath) {
        logger.debug("Validating file: {}", filePath);
        
        FileValidationResult result = new FileValidationResult();
        result.setFilePath(filePath);
        result.setValidationTime(LocalDateTime.now());
        
        try {
            // Basic file existence and accessibility checks
            validateFileExists(filePath, result);
            validateFileAccessible(filePath, result);
            
            if (result.hasErrors()) {
                return result; // Stop early if basic checks fail
            }
            
            // Size validations
            validateFileSize(filePath, result);
            validateFileAge(filePath, result);
            
            // Content validations
            if (config.isValidationEnabled()) {
                validateFileContent(filePath, result);
                validateFileFormat(filePath, result);
                validateFileEncoding(filePath, result);
            }
            
            // Business rule validations
            validateBusinessRules(filePath, result);
            
            result.setValid(!result.hasErrors());
            
            if (result.isValid()) {
                logger.debug("File validation passed: {}", filePath);
            } else {
                logger.warn("File validation failed: {} - {} errors, {} warnings", 
                           filePath, result.getErrors().size(), result.getWarnings().size());
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during file validation: {}", filePath, e);
            result.addError("Validation error: " + e.getMessage());
            result.setValid(false);
        }
        
        return result;
    }
    
    /**
     * Validates that the file exists and is a regular file
     */
    private void validateFileExists(Path filePath, FileValidationResult result) {
        if (!Files.exists(filePath)) {
            result.addError("File does not exist: " + filePath);
            return;
        }
        
        if (!Files.isRegularFile(filePath)) {
            result.addError("Path is not a regular file: " + filePath);
        }
    }
    
    /**
     * Validates that the file is readable
     */
    private void validateFileAccessible(Path filePath, FileValidationResult result) {
        if (!Files.isReadable(filePath)) {
            result.addError("File is not readable: " + filePath);
        }
    }
    
    /**
     * Validates file size constraints
     */
    private void validateFileSize(Path filePath, FileValidationResult result) throws IOException {
        long fileSize = Files.size(filePath);
        long maxSize = config.getMaxFileSize();
        
        if (maxSize > 0 && fileSize > maxSize) {
            result.addError(String.format("File too large: %d bytes (max: %d bytes)", fileSize, maxSize));
        }
        
        // Check for empty files if configured
        if (fileSize == 0) {
            boolean allowEmpty = config.getBoolean("allowEmptyFiles", false);
            if (!allowEmpty) {
                result.addWarning("File is empty: " + filePath);
            }
        }
        
        // Add minimum size validation
        long minSize = config.getLong("minFileSize", 0L);
        if (minSize > 0 && fileSize < minSize) {
            result.addError(String.format("File too small: %d bytes (min: %d bytes)", fileSize, minSize));
        }
    }
    
    /**
     * Validates file age constraints
     */
    private void validateFileAge(Path filePath, FileValidationResult result) throws IOException {
        long ageThresholdMs = config.getFileAgeThresholdMs();
        
        if (ageThresholdMs > 0) {
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            long currentTime = System.currentTimeMillis();
            long ageMs = currentTime - lastModified;
            
            if (ageMs < ageThresholdMs) {
                result.addWarning(String.format("File may be too recent (age: %d ms, threshold: %d ms)", 
                                               ageMs, ageThresholdMs));
            }
        }
        
        // Check for future modification times (clock skew detection)
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();
        long currentTime = System.currentTimeMillis();
        
        if (lastModified > currentTime + 60000) { // Allow 1 minute clock skew
            result.addWarning("File has future modification time, possible clock skew: " + 
                            LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(lastModified), 
                                ZoneId.systemDefault()));
        }
    }
    
    /**
     * Validates file content structure and format
     */
    private void validateFileContent(Path filePath, FileValidationResult result) {
        try {
            byte[] content = Files.readAllBytes(filePath);
            
            // Check for null bytes (binary vs text validation)
            if (containsNullBytes(content) && config.getBoolean("requireTextFiles", false)) {
                result.addError("File contains null bytes, appears to be binary");
                return;
            }
            
            // Validate line endings
            validateLineEndings(content, result);
            
            // Validate character encoding
            validateContentEncoding(content, result);
            
            // Content pattern validation
            validateContentPatterns(content, result);
            
        } catch (IOException e) {
            result.addError("Failed to read file for content validation: " + e.getMessage());
        }
    }
    
    /**
     * Validates file format based on extension and magic bytes
     */
    private void validateFileFormat(Path filePath, FileValidationResult result) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        List<String> allowedExtensions = config.getList("allowedExtensions");
        
        if (allowedExtensions != null && !allowedExtensions.isEmpty()) {
            boolean hasValidExtension = false;
            for (String ext : allowedExtensions) {
                if (fileName.endsWith("." + ext.toLowerCase())) {
                    hasValidExtension = true;
                    break;
                }
            }
            
            if (!hasValidExtension) {
                result.addError("File extension not allowed. Allowed: " + allowedExtensions);
            }
        }
        
        // Validate against forbidden extensions
        List<String> forbiddenExtensions = config.getList("forbiddenExtensions");
        if (forbiddenExtensions != null) {
            for (String ext : forbiddenExtensions) {
                if (fileName.endsWith("." + ext.toLowerCase())) {
                    result.addError("File extension forbidden: " + ext);
                }
            }
        }
        
        // Magic byte validation for specific file types
        validateMagicBytes(filePath, result);
    }
    
    /**
     * Validates file encoding
     */
    private void validateFileEncoding(Path filePath, FileValidationResult result) {
        String requiredEncoding = config.getString("requiredEncoding");
        if (requiredEncoding != null && !requiredEncoding.isEmpty()) {
            try {
                byte[] content = Files.readAllBytes(filePath);
                String detectedEncoding = detectEncoding(content);
                
                if (!requiredEncoding.equalsIgnoreCase(detectedEncoding)) {
                    result.addWarning(String.format("File encoding mismatch. Required: %s, Detected: %s", 
                                                   requiredEncoding, detectedEncoding));
                }
            } catch (IOException e) {
                result.addWarning("Failed to validate encoding: " + e.getMessage());
            }
        }
    }
    
    /**
     * Validates business-specific rules
     */
    private void validateBusinessRules(Path filePath, FileValidationResult result) {
        // Naming convention validation
        validateNamingConvention(filePath, result);
        
        // Duplicate file validation
        validateDuplicateFile(filePath, result);
        
        // Custom validation rules
        validateCustomRules(filePath, result);
    }
    
    /**
     * Validates file naming conventions
     */
    private void validateNamingConvention(Path filePath, FileValidationResult result) {
        String fileName = filePath.getFileName().toString();
        String namingPattern = config.getString("namingPattern");
        
        if (namingPattern != null && !namingPattern.isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(namingPattern);
                if (!pattern.matcher(fileName).matches()) {
                    result.addError("Filename does not match required pattern: " + namingPattern);
                }
            } catch (Exception e) {
                logger.warn("Invalid naming pattern: {}", namingPattern, e);
                result.addWarning("Invalid naming pattern configured: " + namingPattern);
            }
        }
        
        // Check for forbidden characters
        String forbiddenChars = config.getString("forbiddenChars", "<>:\"|?*");
        for (char c : forbiddenChars.toCharArray()) {
            if (fileName.indexOf(c) >= 0) {
                result.addWarning("Filename contains forbidden character: " + c);
            }
        }
    }
    
    /**
     * Validates that file is not a duplicate based on content hash
     */
    private void validateDuplicateFile(Path filePath, FileValidationResult result) {
        // This would typically check against a database of processed files
        // For now, just add metadata for future duplicate detection
        try {
            byte[] content = Files.readAllBytes(filePath);
            String contentHash = calculateContentHash(content);
            result.addMetadata("contentHash", contentHash);
            
            // In a full implementation, this would query the database
            // to check if this content hash has been processed before
            
        } catch (IOException e) {
            result.addWarning("Failed to calculate content hash for duplicate detection: " + e.getMessage());
        }
    }
    
    /**
     * Validates custom rules defined in configuration
     */
    private void validateCustomRules(Path filePath, FileValidationResult result) {
        @SuppressWarnings("unchecked")
        List<Object> customRules = (List<Object>) config.getRawConfiguration().get("customValidationRules");
        
        if (customRules != null) {
            for (Object rule : customRules) {
                // Custom rule processing would be implemented here
                // This is a placeholder for extensible validation rules
                logger.debug("Processing custom validation rule: {}", rule);
            }
        }
    }
    
    // Helper methods
    private boolean containsNullBytes(byte[] content) {
        for (byte b : content) {
            if (b == 0) return true;
        }
        return false;
    }
    
    private void validateLineEndings(byte[] content, FileValidationResult result) {
        String lineEndingStyle = config.getString("lineEndingStyle");
        if (lineEndingStyle != null) {
            boolean hasUnixEndings = false;
            boolean hasWindowsEndings = false;
            
            for (int i = 0; i < content.length - 1; i++) {
                if (content[i] == '\r' && content[i + 1] == '\n') {
                    hasWindowsEndings = true;
                } else if (content[i] == '\n' && (i == 0 || content[i - 1] != '\r')) {
                    hasUnixEndings = true;
                }
            }
            
            switch (lineEndingStyle.toUpperCase()) {
                case "UNIX":
                    if (hasWindowsEndings) {
                        result.addWarning("File contains Windows line endings, expected Unix");
                    }
                    break;
                case "WINDOWS":
                    if (hasUnixEndings) {
                        result.addWarning("File contains Unix line endings, expected Windows");
                    }
                    break;
                case "CONSISTENT":
                    if (hasUnixEndings && hasWindowsEndings) {
                        result.addWarning("File contains mixed line endings");
                    }
                    break;
            }
        }
    }
    
    private void validateContentEncoding(byte[] content, FileValidationResult result) {
        // Simplified encoding validation - in practice, would use proper charset detection
        if (content.length > 3) {
            // Check for BOM
            if (content[0] == (byte) 0xEF && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF) {
                result.addMetadata("hasBOM", true);
                if (config.getBoolean("forbidBOM", false)) {
                    result.addWarning("File contains UTF-8 BOM");
                }
            }
        }
    }
    
    private void validateContentPatterns(byte[] content, FileValidationResult result) {
        String requiredPattern = config.getString("requiredContentPattern");
        if (requiredPattern != null && !requiredPattern.isEmpty()) {
            String contentString = new String(content);
            try {
                Pattern pattern = Pattern.compile(requiredPattern, Pattern.DOTALL);
                if (!pattern.matcher(contentString).find()) {
                    result.addError("File content does not contain required pattern: " + requiredPattern);
                }
            } catch (Exception e) {
                result.addWarning("Invalid content pattern configured: " + requiredPattern);
            }
        }
    }
    
    private void validateMagicBytes(Path filePath, FileValidationResult result) {
        try {
            byte[] header = Files.readAllBytes(filePath);
            if (header.length >= 4) {
                // Check for common file type magic bytes
                if (header[0] == 'P' && header[1] == 'K') {
                    result.addMetadata("detectedType", "ZIP");
                } else if (header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
                    result.addMetadata("detectedType", "PDF");
                }
                // Add more magic byte checks as needed
            }
        } catch (IOException e) {
            result.addWarning("Failed to read file header for magic byte validation");
        }
    }
    
    private String detectEncoding(byte[] content) {
        // Simplified encoding detection
        return "UTF-8";
    }
    
    private String calculateContentHash(byte[] content) {
        return String.valueOf(content.hashCode());
    }
}