package com.integrixs.core.service.utility;

import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Utility processor for file operations
 * Handles file split, merge, validate, copy operations following Single Responsibility Principle
 */
@Service
public class FileUtilityProcessor extends AbstractUtilityProcessor {
    
    private static final String UTILITY_TYPE = "FILE";
    private static final long DEFAULT_SPLIT_SIZE = 1024 * 1024; // 1MB
    
    @Override
    public String getUtilityType() {
        return UTILITY_TYPE;
    }
    
    @Override
    public Map<String, Object> executeUtility(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        logExecutionStart(step, "File utility");
        long startTime = System.currentTimeMillis();
        
        try {
            String operation = getConfigValue(configuration, "operation", "copy");
            
            switch (operation.toLowerCase()) {
                case "split":
                    return executeFileSplit(step, context, configuration);
                case "merge":
                    return executeFileMerge(step, context, configuration);
                case "validate":
                    return executeFileValidate(step, context, configuration);
                case "copy":
                    return executeFileCopy(step, context, configuration);
                case "move":
                    return executeFileMove(step, context, configuration);
                case "hash":
                    return executeFileHash(step, context, configuration);
                default:
                    throw new IllegalArgumentException("Unsupported file operation: " + operation);
            }
            
        } catch (Exception e) {
            logExecutionError(step, "File utility", e);
            return createErrorResult("File utility execution failed: " + e.getMessage(), e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logExecutionComplete(step, "File utility", duration);
        }
    }
    
    /**
     * Execute file split operation
     */
    private Map<String, Object> executeFileSplit(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile", "targetDirectory");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String targetDirectory = (String) configuration.get("targetDirectory");
            Long splitSizeBytes = getConfigValue(configuration, "splitSizeBytes", DEFAULT_SPLIT_SIZE);
            String filePrefix = getConfigValue(configuration, "filePrefix", "split_");
            boolean deleteSource = getConfigValue(configuration, "deleteSource", false);
            
            validateFilePath(sourceFile);
            validateFilePath(targetDirectory);
            ensureDirectoryExists(targetDirectory);
            
            Path sourcePath = Paths.get(sourceFile);
            Path targetPath = Paths.get(targetDirectory);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            long fileSize = Files.size(sourcePath);
            int totalParts = (int) Math.ceil((double) fileSize / splitSizeBytes);
            
            List<String> splitFiles = new ArrayList<>();
            long totalSizeWritten = 0;
            
            try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(sourcePath))) {
                byte[] buffer = new byte[splitSizeBytes.intValue()];
                int partNumber = 1;
                
                while (bis.available() > 0) {
                    String partFileName = String.format("%s%03d", filePrefix, partNumber);
                    Path partFile = targetPath.resolve(partFileName);
                    
                    int bytesRead = bis.read(buffer);
                    if (bytesRead > 0) {
                        Files.write(partFile, Arrays.copyOf(buffer, bytesRead));
                        splitFiles.add(partFile.toString());
                        totalSizeWritten += bytesRead;
                        logger.debug("Created split file: {} ({} bytes)", partFile, bytesRead);
                    }
                    
                    partNumber++;
                }
            }
            
            // Delete source file if requested
            if (deleteSource) {
                Files.delete(sourcePath);
                logger.debug("Deleted source file: {}", sourcePath);
            }
            
            // Update execution context
            updateExecutionContext(context, "fileSplitParts", splitFiles);
            updateExecutionContext(context, "fileSplitCount", splitFiles.size());
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("splitFiles", splitFiles);
            resultData.put("totalParts", splitFiles.size());
            resultData.put("expectedParts", totalParts);
            resultData.put("originalFileSize", fileSize);
            resultData.put("totalSizeWritten", totalSizeWritten);
            resultData.put("splitSizeBytes", splitSizeBytes);
            resultData.put("sourceFile", sourceFile);
            resultData.put("targetDirectory", targetDirectory);
            resultData.put("deletedSource", deleteSource);
            
            String message = String.format("Successfully split file into %d parts (%d bytes total)", 
                splitFiles.size(), totalSizeWritten);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("File split failed", e);
            return createErrorResult("File split failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute file merge operation
     */
    private Map<String, Object> executeFileMerge(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFiles", "targetFile");
            
            @SuppressWarnings("unchecked")
            List<String> sourceFiles = (List<String>) configuration.get("sourceFiles");
            String targetFile = (String) configuration.get("targetFile");
            boolean deleteSource = getConfigValue(configuration, "deleteSource", false);
            boolean validateOrder = getConfigValue(configuration, "validateOrder", true);
            
            validateFilePath(targetFile);
            
            Path targetPath = Paths.get(targetFile);
            ensureDirectoryExists(targetPath.getParent().toString());
            
            // Validate source files exist
            List<Path> sourcePaths = new ArrayList<>();
            long totalSourceSize = 0;
            
            for (String sourceFileStr : sourceFiles) {
                validateFilePath(sourceFileStr);
                Path sourcePath = Paths.get(sourceFileStr);
                
                if (!Files.exists(sourcePath)) {
                    return createErrorResult("Source file does not exist: " + sourceFileStr, null);
                }
                
                sourcePaths.add(sourcePath);
                totalSourceSize += Files.size(sourcePath);
            }
            
            // Sort files if order validation is enabled
            if (validateOrder) {
                sourcePaths.sort(Comparator.comparing(Path::getFileName));
            }
            
            // Merge files
            long totalBytesWritten = 0;
            
            try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(targetPath))) {
                for (Path sourcePath : sourcePaths) {
                    try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(sourcePath))) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                            totalBytesWritten += bytesRead;
                        }
                    }
                    
                    logger.debug("Merged file: {} ({} bytes)", sourcePath, Files.size(sourcePath));
                }
            }
            
            // Delete source files if requested
            List<String> deletedFiles = new ArrayList<>();
            if (deleteSource) {
                for (Path sourcePath : sourcePaths) {
                    Files.delete(sourcePath);
                    deletedFiles.add(sourcePath.toString());
                    logger.debug("Deleted source file: {}", sourcePath);
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "fileMergeResult", targetFile);
            updateExecutionContext(context, "fileMergeSize", totalBytesWritten);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("targetFile", targetFile);
            resultData.put("sourceFileCount", sourceFiles.size());
            resultData.put("totalSourceSize", totalSourceSize);
            resultData.put("totalBytesWritten", totalBytesWritten);
            resultData.put("sourceFiles", sourceFiles);
            resultData.put("deletedFiles", deletedFiles);
            resultData.put("deletedSource", deleteSource);
            
            String message = String.format("Successfully merged %d files into %s (%d bytes)", 
                sourceFiles.size(), targetFile, totalBytesWritten);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("File merge failed", e);
            return createErrorResult("File merge failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute file validation operation
     */
    private Map<String, Object> executeFileValidate(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceDirectory");
            
            String sourceDirectory = (String) configuration.get("sourceDirectory");
            String filePattern = getConfigValue(configuration, "filePattern", "*");
            String hashAlgorithm = getConfigValue(configuration, "hashAlgorithm", "MD5");
            Long expectedSize = getConfigValue(configuration, "expectedSize", null);
            String expectedHash = getConfigValue(configuration, "expectedHash", null);
            
            validateFilePath(sourceDirectory);
            
            Path sourcePath = Paths.get(sourceDirectory);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source directory does not exist: " + sourceDirectory, null);
            }
            
            // Get files to validate
            List<Path> filesToValidate = new ArrayList<>();
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (matchesPattern(file.getFileName().toString(), filePattern)) {
                        filesToValidate.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            if (filesToValidate.isEmpty()) {
                return createSuccessResult("No files found matching pattern: " + filePattern, 
                    Collections.singletonMap("filesValidated", 0));
            }
            
            // Validate files
            List<Map<String, Object>> fileResults = new ArrayList<>();
            int validFiles = 0;
            int invalidFiles = 0;
            long totalSize = 0;
            List<String> errors = new ArrayList<>();
            
            for (Path file : filesToValidate) {
                try {
                    Map<String, Object> fileResult = validateSingleFile(file, hashAlgorithm, expectedSize, expectedHash);
                    fileResults.add(fileResult);
                    
                    boolean isValid = (Boolean) fileResult.get("valid");
                    if (isValid) {
                        validFiles++;
                    } else {
                        invalidFiles++;
                        errors.add((String) fileResult.get("error"));
                    }
                    
                    totalSize += (Long) fileResult.get("size");
                    
                } catch (Exception e) {
                    String error = "Failed to validate file " + file + ": " + e.getMessage();
                    errors.add(error);
                    invalidFiles++;
                    logger.error("Validation error: {}", error, e);
                }
            }
            
            // Update execution context
            updateExecutionContext(context, "fileValidationResults", fileResults);
            updateExecutionContext(context, "fileValidationSummary", 
                Map.of("valid", validFiles, "invalid", invalidFiles, "total", filesToValidate.size()));
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("totalFiles", filesToValidate.size());
            resultData.put("validFiles", validFiles);
            resultData.put("invalidFiles", invalidFiles);
            resultData.put("totalSize", totalSize);
            resultData.put("fileResults", fileResults);
            resultData.put("hashAlgorithm", hashAlgorithm);
            
            if (!errors.isEmpty()) {
                resultData.put("errors", errors);
            }
            
            boolean allValid = invalidFiles == 0;
            String message = String.format("Validated %d files - %d valid, %d invalid", 
                filesToValidate.size(), validFiles, invalidFiles);
            
            return allValid ? createSuccessResult(message, resultData) : 
                createResult(false, message, resultData);
            
        } catch (Exception e) {
            logger.error("File validation failed", e);
            return createErrorResult("File validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute file copy operation
     */
    private Map<String, Object> executeFileCopy(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile", "targetFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String targetFile = (String) configuration.get("targetFile");
            boolean overwrite = getConfigValue(configuration, "overwrite", false);
            boolean preserveAttributes = getConfigValue(configuration, "preserveAttributes", true);
            
            validateFilePath(sourceFile);
            validateFilePath(targetFile);
            
            Path sourcePath = Paths.get(sourceFile);
            Path targetPath = Paths.get(targetFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            if (Files.exists(targetPath) && !overwrite) {
                return createErrorResult("Target file already exists and overwrite is false: " + targetFile, null);
            }
            
            ensureDirectoryExists(targetPath.getParent().toString());
            
            // Copy file
            CopyOption[] options = preserveAttributes ? 
                new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES} :
                new CopyOption[]{StandardCopyOption.REPLACE_EXISTING};
                
            Files.copy(sourcePath, targetPath, options);
            
            long fileSize = Files.size(targetPath);
            
            // Update execution context
            updateExecutionContext(context, "fileCopyResult", targetFile);
            updateExecutionContext(context, "fileCopySize", fileSize);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("targetFile", targetFile);
            resultData.put("fileSize", fileSize);
            resultData.put("overwrite", overwrite);
            resultData.put("preserveAttributes", preserveAttributes);
            
            String message = String.format("Successfully copied file: %s -> %s (%d bytes)", 
                sourceFile, targetFile, fileSize);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("File copy failed", e);
            return createErrorResult("File copy failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute file move operation
     */
    private Map<String, Object> executeFileMove(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile", "targetFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String targetFile = (String) configuration.get("targetFile");
            boolean overwrite = getConfigValue(configuration, "overwrite", false);
            
            validateFilePath(sourceFile);
            validateFilePath(targetFile);
            
            Path sourcePath = Paths.get(sourceFile);
            Path targetPath = Paths.get(targetFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            if (Files.exists(targetPath) && !overwrite) {
                return createErrorResult("Target file already exists and overwrite is false: " + targetFile, null);
            }
            
            ensureDirectoryExists(targetPath.getParent().toString());
            
            long fileSize = Files.size(sourcePath);
            
            // Move file
            CopyOption[] options = overwrite ? 
                new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} :
                new CopyOption[]{};
                
            Files.move(sourcePath, targetPath, options);
            
            // Update execution context
            updateExecutionContext(context, "fileMoveResult", targetFile);
            updateExecutionContext(context, "fileMoveSize", fileSize);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("targetFile", targetFile);
            resultData.put("fileSize", fileSize);
            resultData.put("overwrite", overwrite);
            
            String message = String.format("Successfully moved file: %s -> %s (%d bytes)", 
                sourceFile, targetFile, fileSize);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("File move failed", e);
            return createErrorResult("File move failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute file hash calculation
     */
    private Map<String, Object> executeFileHash(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration) {
        
        try {
            validateConfiguration(configuration, "sourceFile");
            
            String sourceFile = (String) configuration.get("sourceFile");
            String hashAlgorithm = getConfigValue(configuration, "hashAlgorithm", "MD5");
            
            validateFilePath(sourceFile);
            
            Path sourcePath = Paths.get(sourceFile);
            
            if (!Files.exists(sourcePath)) {
                return createErrorResult("Source file does not exist: " + sourceFile, null);
            }
            
            String hash = calculateFileHash(sourcePath, hashAlgorithm);
            long fileSize = Files.size(sourcePath);
            
            // Update execution context
            updateExecutionContext(context, "fileHash", hash);
            updateExecutionContext(context, "fileHashAlgorithm", hashAlgorithm);
            
            // Create result
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("sourceFile", sourceFile);
            resultData.put("hash", hash);
            resultData.put("hashAlgorithm", hashAlgorithm);
            resultData.put("fileSize", fileSize);
            
            String message = String.format("Successfully calculated %s hash for file: %s", 
                hashAlgorithm, sourceFile);
            
            return createSuccessResult(message, resultData);
            
        } catch (Exception e) {
            logger.error("File hash calculation failed", e);
            return createErrorResult("File hash calculation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate a single file
     */
    private Map<String, Object> validateSingleFile(Path file, String hashAlgorithm, 
            Long expectedSize, String expectedHash) throws Exception {
        
        Map<String, Object> result = new HashMap<>();
        result.put("file", file.toString());
        result.put("fileName", file.getFileName().toString());
        
        boolean valid = true;
        List<String> validationErrors = new ArrayList<>();
        
        // Check file exists and is readable
        if (!Files.exists(file) || !Files.isReadable(file)) {
            valid = false;
            validationErrors.add("File does not exist or is not readable");
        } else {
            long actualSize = Files.size(file);
            result.put("size", actualSize);
            
            // Validate size if expected
            if (expectedSize != null && !expectedSize.equals(actualSize)) {
                valid = false;
                validationErrors.add(String.format("Size mismatch: expected %d, actual %d", 
                    expectedSize, actualSize));
            }
            
            // Validate hash if expected
            if (expectedHash != null) {
                String actualHash = calculateFileHash(file, hashAlgorithm);
                result.put("hash", actualHash);
                
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    valid = false;
                    validationErrors.add(String.format("Hash mismatch: expected %s, actual %s", 
                        expectedHash, actualHash));
                }
            }
            
            result.put("lastModified", Files.getLastModifiedTime(file).toString());
        }
        
        result.put("valid", valid);
        if (!validationErrors.isEmpty()) {
            result.put("errors", validationErrors);
            result.put("error", String.join("; ", validationErrors));
        }
        
        return result;
    }
    
    /**
     * Calculate file hash
     */
    private String calculateFileHash(Path file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = bis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        
        return sb.toString();
    }
    
    /**
     * Check if filename matches pattern
     */
    private boolean matchesPattern(String filename, String pattern) {
        if (pattern == null || "*".equals(pattern)) {
            return true;
        }
        
        // Simple wildcard matching
        if (pattern.startsWith("*.")) {
            String extension = pattern.substring(2);
            return filename.toLowerCase().endsWith("." + extension.toLowerCase());
        }
        
        return filename.matches(pattern.replace("*", ".*"));
    }
}