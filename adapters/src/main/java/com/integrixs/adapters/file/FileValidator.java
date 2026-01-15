package com.integrixs.adapters.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                try {
                    processCustomValidationRule(rule, filePath, result);
                } catch (Exception e) {
                    logger.warn("Failed to process custom validation rule: {} - {}", rule, e.getMessage());
                    result.addWarning("Custom validation rule failed: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Process individual custom validation rule
     */
    @SuppressWarnings("unchecked")
    private void processCustomValidationRule(Object rule, Path filePath, FileValidationResult result) {
        if (rule instanceof Map) {
            Map<String, Object> ruleMap = (Map<String, Object>) rule;
            String ruleType = (String) ruleMap.get("type");
            
            if (ruleType == null) {
                result.addWarning("Custom rule missing 'type' field");
                return;
            }
            
            logger.debug("Processing custom validation rule type: {} for file: {}", ruleType, filePath);
            
            switch (ruleType.toLowerCase()) {
                case "filename_regex":
                    validateFilenameRegex(ruleMap, filePath, result);
                    break;
                case "file_size_range":
                    validateFileSizeRange(ruleMap, filePath, result);
                    break;
                case "content_contains":
                    validateContentContains(ruleMap, filePath, result);
                    break;
                case "content_excludes":
                    validateContentExcludes(ruleMap, filePath, result);
                    break;
                case "header_validation":
                    validateFileHeader(ruleMap, filePath, result);
                    break;
                case "line_count":
                    validateLineCount(ruleMap, filePath, result);
                    break;
                default:
                    result.addWarning("Unknown custom validation rule type: " + ruleType);
            }
        } else {
            result.addWarning("Custom validation rule must be a configuration object");
        }
    }
    
    private void validateFilenameRegex(Map<String, Object> rule, Path filePath, FileValidationResult result) {
        String regex = (String) rule.get("pattern");
        String errorMessage = (String) rule.getOrDefault("errorMessage", "Filename does not match custom pattern");
        
        if (regex == null || regex.trim().isEmpty()) {
            result.addWarning("Filename regex validation rule missing 'pattern' field");
            return;
        }
        
        try {
            Pattern pattern = Pattern.compile(regex);
            String fileName = filePath.getFileName().toString();
            
            if (!pattern.matcher(fileName).matches()) {
                boolean isRequired = Boolean.TRUE.equals(rule.get("required"));
                String fullMessage = errorMessage + " (pattern: " + regex + ", filename: " + fileName + ")";
                
                if (isRequired) {
                    result.addError(fullMessage);
                } else {
                    result.addWarning(fullMessage);
                }
            }
        } catch (java.util.regex.PatternSyntaxException e) {
            result.addWarning("Invalid regex pattern in custom rule: " + regex + " - " + e.getMessage());
        } catch (Exception e) {
            result.addWarning("Error validating filename regex: " + e.getMessage());
        }
    }
    
    private void validateFileSizeRange(Map<String, Object> rule, Path filePath, FileValidationResult result) {
        Object minSizeObj = rule.get("minSize");
        Object maxSizeObj = rule.get("maxSize");
        String errorMessage = (String) rule.getOrDefault("errorMessage", "File size not within custom range");
        
        if (minSizeObj == null && maxSizeObj == null) {
            result.addWarning("File size range validation rule missing both 'minSize' and 'maxSize' fields");
            return;
        }
        
        try {
            long fileSize = Files.size(filePath);
            
            if (minSizeObj != null) {
                if (!(minSizeObj instanceof Number)) {
                    result.addWarning("Invalid minSize value in custom rule: " + minSizeObj);
                    return;
                }
                
                long minSize = ((Number) minSizeObj).longValue();
                if (minSize < 0) {
                    result.addWarning("Invalid negative minSize value: " + minSize);
                    return;
                }
                
                if (fileSize < minSize) {
                    String sizeMessage = String.format("%s (actual: %d bytes, minimum: %d bytes)", 
                                                     errorMessage, fileSize, minSize);
                    result.addError(sizeMessage);
                }
            }
            
            if (maxSizeObj != null) {
                if (!(maxSizeObj instanceof Number)) {
                    result.addWarning("Invalid maxSize value in custom rule: " + maxSizeObj);
                    return;
                }
                
                long maxSize = ((Number) maxSizeObj).longValue();
                if (maxSize < 0) {
                    result.addWarning("Invalid negative maxSize value: " + maxSize);
                    return;
                }
                
                if (fileSize > maxSize) {
                    String sizeMessage = String.format("%s (actual: %d bytes, maximum: %d bytes)", 
                                                     errorMessage, fileSize, maxSize);
                    result.addError(sizeMessage);
                }
            }
            
            // Validate that min <= max if both are provided
            if (minSizeObj != null && maxSizeObj != null) {
                long minSize = ((Number) minSizeObj).longValue();
                long maxSize = ((Number) maxSizeObj).longValue();
                if (minSize > maxSize) {
                    result.addWarning("Invalid size range: minSize (" + minSize + ") is greater than maxSize (" + maxSize + ")");
                }
            }
            
        } catch (IOException e) {
            result.addWarning("Failed to read file size for validation: " + e.getMessage());
        } catch (Exception e) {
            result.addWarning("Failed to validate file size range: " + e.getMessage());
        }
    }
    
    private void validateContentContains(Map<String, Object> rule, Path filePath, FileValidationResult result) {
        String searchText = (String) rule.get("text");
        String errorMessage = (String) rule.getOrDefault("errorMessage", "File content does not contain required text");
        boolean caseInsensitive = Boolean.TRUE.equals(rule.get("caseInsensitive"));
        
        if (searchText == null || searchText.trim().isEmpty()) {
            result.addWarning("Content contains validation rule missing 'text' field");
            return;
        }
        
        // Limit file size for content reading to prevent memory issues
        final long MAX_CONTENT_SIZE = 10 * 1024 * 1024; // 10MB
        
        try {
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_CONTENT_SIZE) {
                result.addWarning("File too large for content validation (max 10MB): " + fileSize + " bytes");
                return;
            }
            
            String content = Files.readString(filePath);
            String searchContent = caseInsensitive ? content.toLowerCase() : content;
            String searchTarget = caseInsensitive ? searchText.toLowerCase() : searchText;
            
            if (!searchContent.contains(searchTarget)) {
                String message = String.format("%s: '%s' (case %s)", 
                                             errorMessage, searchText, 
                                             caseInsensitive ? "insensitive" : "sensitive");
                result.addError(message);
            }
        } catch (IOException e) {
            result.addWarning("Failed to read file content for validation: " + e.getMessage());
        } catch (Exception e) {
            result.addWarning("Failed to validate content contains: " + e.getMessage());
        }
    }
    
    private void validateContentExcludes(Map<String, Object> rule, Path filePath, FileValidationResult result) {
        String forbiddenText = (String) rule.get("text");
        String errorMessage = (String) rule.getOrDefault("errorMessage", "File content contains forbidden text");
        boolean caseInsensitive = Boolean.TRUE.equals(rule.get("caseInsensitive"));
        
        if (forbiddenText == null || forbiddenText.trim().isEmpty()) {
            result.addWarning("Content excludes validation rule missing 'text' field");
            return;
        }
        
        // Limit file size for content reading to prevent memory issues
        final long MAX_CONTENT_SIZE = 10 * 1024 * 1024; // 10MB
        
        try {
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_CONTENT_SIZE) {
                result.addWarning("File too large for content validation (max 10MB): " + fileSize + " bytes");
                return;
            }
            
            String content = Files.readString(filePath);
            String searchContent = caseInsensitive ? content.toLowerCase() : content;
            String forbiddenTarget = caseInsensitive ? forbiddenText.toLowerCase() : forbiddenText;
            
            if (searchContent.contains(forbiddenTarget)) {
                String message = String.format("%s: '%s' (case %s)", 
                                             errorMessage, forbiddenText, 
                                             caseInsensitive ? "insensitive" : "sensitive");
                result.addError(message);
            }
        } catch (IOException e) {
            result.addWarning("Failed to read file content for validation: " + e.getMessage());
        } catch (Exception e) {
            result.addWarning("Failed to validate content excludes: " + e.getMessage());
        }
    }
    
    private void validateFileHeader(Map<String, Object> rule, Path filePath, FileValidationResult result) {
        String expectedHeader = (String) rule.get("expectedHeader");
        String errorMessage = (String) rule.getOrDefault("errorMessage", "File header does not match expected format");
        Object linesToCheckObj = rule.get("linesToCheck");
        
        if (expectedHeader == null || expectedHeader.trim().isEmpty()) {
            result.addWarning("Header validation rule missing 'expectedHeader' field");
            return;
        }
        
        int linesToCheck = 1; // Default to 1 line
        if (linesToCheckObj != null) {
            if (linesToCheckObj instanceof Number) {
                int lines = ((Number) linesToCheckObj).intValue();
                if (lines > 0 && lines <= 100) { // Reasonable limit
                    linesToCheck = lines;
                } else {
                    result.addWarning("Invalid linesToCheck value (must be 1-100): " + lines);
                    return;
                }
            } else {
                result.addWarning("Invalid linesToCheck value type: " + linesToCheckObj);
                return;
            }
        }
        
        try {
            List<String> lines = Files.readAllLines(filePath);
            if (lines.isEmpty()) {
                result.addError(errorMessage + " - File is empty");
                return;
            }
            
            StringBuilder headerBuilder = new StringBuilder();
            int checkLines = Math.min(linesToCheck, lines.size());
            
            for (int i = 0; i < checkLines; i++) {
                if (i > 0) headerBuilder.append("\n");
                headerBuilder.append(lines.get(i));
            }
            
            String actualHeader = headerBuilder.toString();
            boolean matches = actualHeader.startsWith(expectedHeader);
            
            if (!matches) {
                // Truncate long headers for error message
                String displayActual = actualHeader.length() > 100 ? 
                    actualHeader.substring(0, 100) + "..." : actualHeader;
                String displayExpected = expectedHeader.length() > 100 ? 
                    expectedHeader.substring(0, 100) + "..." : expectedHeader;
                    
                result.addError(String.format("%s. Expected: '%s', Found: '%s'", 
                                            errorMessage, displayExpected, displayActual));
            }
        } catch (IOException e) {
            result.addWarning("Failed to read file for header validation: " + e.getMessage());
        } catch (Exception e) {
            result.addWarning("Failed to validate file header: " + e.getMessage());
        }
    }
    
    private void validateLineCount(Map<String, Object> rule, Path filePath, FileValidationResult result) {
        Object minLinesObj = rule.get("minLines");
        Object maxLinesObj = rule.get("maxLines");
        String errorMessage = (String) rule.getOrDefault("errorMessage", "File line count not within expected range");
        
        if (minLinesObj == null && maxLinesObj == null) {
            result.addWarning("Line count validation rule missing both 'minLines' and 'maxLines' fields");
            return;
        }
        
        try {
            List<String> lines = Files.readAllLines(filePath);
            int lineCount = lines.size();
            
            if (minLinesObj != null) {
                if (!(minLinesObj instanceof Number)) {
                    result.addWarning("Invalid minLines value in custom rule: " + minLinesObj);
                    return;
                }
                
                int minLines = ((Number) minLinesObj).intValue();
                if (minLines < 0) {
                    result.addWarning("Invalid negative minLines value: " + minLines);
                    return;
                }
                
                if (lineCount < minLines) {
                    String countMessage = String.format("%s (actual: %d lines, minimum: %d lines)", 
                                                       errorMessage, lineCount, minLines);
                    result.addError(countMessage);
                }
            }
            
            if (maxLinesObj != null) {
                if (!(maxLinesObj instanceof Number)) {
                    result.addWarning("Invalid maxLines value in custom rule: " + maxLinesObj);
                    return;
                }
                
                int maxLines = ((Number) maxLinesObj).intValue();
                if (maxLines < 0) {
                    result.addWarning("Invalid negative maxLines value: " + maxLines);
                    return;
                }
                
                if (lineCount > maxLines) {
                    String countMessage = String.format("%s (actual: %d lines, maximum: %d lines)", 
                                                       errorMessage, lineCount, maxLines);
                    result.addError(countMessage);
                }
            }
            
            // Validate that min <= max if both are provided
            if (minLinesObj != null && maxLinesObj != null) {
                int minLines = ((Number) minLinesObj).intValue();
                int maxLines = ((Number) maxLinesObj).intValue();
                if (minLines > maxLines) {
                    result.addWarning("Invalid line count range: minLines (" + minLines + ") is greater than maxLines (" + maxLines + ")");
                }
            }
            
        } catch (IOException e) {
            result.addWarning("Failed to read file for line count validation: " + e.getMessage());
        } catch (Exception e) {
            result.addWarning("Failed to validate line count: " + e.getMessage());
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