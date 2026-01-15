package com.integrixs.adapters.file;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.service.AdapterConfigurationService;
import com.integrixs.core.service.FileOperationsService;
import com.integrixs.core.util.AdapterConfigUtil;
import com.integrixs.shared.util.FileUtils;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * File Sender Adapter - Collects files from local source directory.
 * 
 * FOLLOWS OOP PRINCIPLES:
 * - Single Responsibility: Only collect files from local source directory with file pattern matching
 * - Dependency Injection: Uses Spring to inject dependencies
 * - Open/Closed: Extensible through configuration without modification
 */
@Component
public class FileSenderAdapter extends AbstractAdapterExecutor {
    
    private final AdapterConfigurationService configService;
    private final FileOperationsService fileService;
    
    // Default constructor for factory - TO BE REMOVED in Phase 1.3
    public FileSenderAdapter() {
        this.configService = null; // Will cause NPE if used - need proper DI
        this.fileService = null; // Will cause NPE if used - need proper DI
    }
    
    @Autowired
    public FileSenderAdapter(AdapterConfigurationService configService, FileOperationsService fileService) {
        this.configService = configService;
        this.fileService = fileService;
    }
    
    @Override
    public String getSupportedType() {
        return "FILE";
    }
    
    @Override
    public String getSupportedDirection() {
        return "SENDER";
    }
    
    @Override
    protected Map<String, Object> executeInternal(Adapter adapter, Map<String, Object> context, 
                                                 FlowExecutionStep step) {
        
        logger.info("Direction: SENDER (collecting files from source directory)");
        logger.info("Sender adapter {} started", adapter.getName());
        logger.info("Request message entering the adapter processing with user system");
        
        // Get and validate configuration
        Map<String, Object> config = adapter.getConfiguration();
        validateFileSenderConfiguration(config);
        
        String sourceDirectory = getStringConfigValue(config, "sourceDirectory", true, null);
        String filePattern = getStringConfigValue(config, "filePattern", false, "*");
        String postProcessAction = getStringConfigValue(config, "postProcessAction", false, "ARCHIVE");
        String archiveDirectory = getStringConfigValue(config, "archiveDirectory", false, null);
        
        logger.info("Configuration - Source Directory: {}, File Pattern: {}, Post Process Action: {}", 
                   sourceDirectory, filePattern, postProcessAction);
        
        if ("KEEP_AND_REPROCESS".equalsIgnoreCase(postProcessAction)) {
            logger.info("Keep and Reprocess mode: Files will remain in source directory for reprocessing");
        }
        
        try {
            // Ensure source directory exists and is accessible
            Path sourcePath = Paths.get(sourceDirectory);
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                logger.warn("Source directory does not exist or is not accessible: {}", sourceDirectory);
                return createErrorResult("Source directory not accessible: " + sourceDirectory);
            }
            
            // Find files matching the pattern
            List<Path> discoveredFiles = findMatchingFilesInDirectory(sourcePath, filePattern);
            
            if (discoveredFiles.isEmpty()) {
                logger.info("No files available from bank");
                return createSuccessResult(0, 0, 0L, "No files found to collect");
            }
            
            logger.info("Found {} files for processing", discoveredFiles.size());
            logger.info("Retrieving File from Source directory \"{}\"", sourceDirectory);
            
            // Process each file - READ CONTENT, DO NOT ARCHIVE YET
            List<Map<String, Object>> processedFiles = new ArrayList<>();
            long totalBytes = 0;
            int successCount = 0;
            int errorCount = 0;
            int skippedCount = 0;

            for (Path filePath : discoveredFiles) {
                try {
                    long startTime = System.currentTimeMillis();
                    String fileName = filePath.getFileName().toString();

                    // Phase 1 Validations - Apply all configured checks
                    FileValidationResult validationResult = validateFileForProcessing(filePath, config);
                    if (!validationResult.isValid()) {
                        logger.warn("Skipping file {}: {}", fileName, validationResult.getReason());
                        skippedCount++;

                        // Handle skipped file based on type
                        if (validationResult.shouldArchiveToError()) {
                            handleErrorFile(filePath, validationResult.getReason(), config);
                        }
                        continue;
                    }

                    long fileSize = Files.size(filePath);

                    // Read file content into memory for receiver processing
                    byte[] fileContent = Files.readAllBytes(filePath);
                    
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("fileName", fileName);
                    fileInfo.put("originalFilePath", filePath.toString());
                    fileInfo.put("fileSize", fileSize);
                    fileInfo.put("fileContent", fileContent);
                    fileInfo.put("postProcessAction", postProcessAction);
                    fileInfo.put("archiveDirectory", archiveDirectory);
                    fileInfo.put("adapterConfig", config); // Pass full adapter config for archiving
                    fileInfo.put("status", "READ_SUCCESS");
                    fileInfo.put("processingTimeMs", System.currentTimeMillis() - startTime);
                    
                    processedFiles.add(fileInfo);
                    totalBytes += fileSize;
                    successCount++;
                    
                    // Add file to step tracking
                    if (step != null) {
                        step.addFileProcessed(fileName, "READ_FOR_PROCESSING", fileSize);
                    }
                    
                    logger.info("File collected: {} ({} bytes)", fileName, fileSize);
                    
                } catch (Exception e) {
                    logger.error("Failed to read file {}: {}", filePath, e.getMessage(), e);
                    
                    Map<String, Object> errorInfo = new HashMap<>();
                    errorInfo.put("fileName", filePath.getFileName().toString());
                    errorInfo.put("originalFilePath", filePath.toString());
                    errorInfo.put("status", "READ_FAILED");
                    errorInfo.put("errorMessage", e.getMessage());
                    processedFiles.add(errorInfo);
                    errorCount++;
                }
            }
            
            // Store file data in context for receiver processing
            context.put("filesToProcess", processedFiles);
            context.put("senderAdapter", adapter);
            
            // Handle post-processing based on context signals
            Boolean receiverProcessingSuccessful = (Boolean) context.get("receiverProcessingSuccessful");
            if (receiverProcessingSuccessful != null && receiverProcessingSuccessful) {
                // Receiver was successful, archive the source files
                logger.info("Receiver processing was successful, archiving source files");
                
                List<Map<String, Object>> successfulFilesForArchiving = processedFiles.stream()
                    .filter(fileInfo -> "READ_SUCCESS".equals(fileInfo.get("status")))
                    .collect(Collectors.toList());
                    
                if (!successfulFilesForArchiving.isEmpty()) {
                    archiveSuccessfulFiles(successfulFilesForArchiving, context);
                }
            } else if (context.get("skipSenderPostProcessing") == null) {
                // This is a standalone sender adapter (no receiver processing), apply post-processing now
                logger.info("Applying post-processing for standalone sender adapter");
                
                List<Map<String, Object>> successfulFilesForArchiving = processedFiles.stream()
                    .filter(fileInfo -> "READ_SUCCESS".equals(fileInfo.get("status")))
                    .collect(Collectors.toList());
                    
                if (!successfulFilesForArchiving.isEmpty()) {
                    archiveSuccessfulFiles(successfulFilesForArchiving, context);
                }
            }
            
            // Create success result
            Map<String, Object> result = createSuccessResult(successCount, errorCount, totalBytes,
                String.format("File sender completed: %d/%d files collected successfully", 
                             successCount, discoveredFiles.size()));
            
            result.put("foundFiles", processedFiles);
            result.put("sourceDirectory", sourceDirectory);
            result.put("filePattern", filePattern);
            result.put("postProcessAction", postProcessAction);
            result.put("filesDiscovered", discoveredFiles.size());
            
            logger.info("✓ Files collected: {}/{}", successCount, discoveredFiles.size());
            logger.info("✓ Total bytes collected: {}", totalBytes);
            logger.info("File Processing completed");
            logger.info("Message status set to Delivered");
            
            return result;
            
        } catch (Exception e) {
            logger.error("File sender execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("File sender execution failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void validateConfiguration(Adapter adapter) {
        // Call parent validation
        super.validateConfiguration(adapter);
        
        Map<String, Object> config = adapter.getConfiguration();
        validateFileSenderConfiguration(config);
        
        logConfigSummary(config, getSupportedType(), getSupportedDirection());
    }
    
    /**
     * Validate File sender specific configuration.
     * Requires source directory and optional file pattern.
     */
    private void validateFileSenderConfiguration(Map<String, Object> config) {
        String adapterType = getSupportedType() + " " + getSupportedDirection();
        
        if (configService != null) {
            // Use the configuration service for validation
            AdapterConfigurationService.ValidationResult result = 
                configService.validateFileConfiguration(config, getSupportedType(), getSupportedDirection());
            
            if (!result.isValid()) {
                throw new IllegalArgumentException(result.getErrorMessage().orElse("Configuration validation failed"));
            }
        } else {
            // Fallback to static utility during transition
            AdapterConfigUtil.validateFileConfig(config, getSupportedType(), getSupportedDirection());
        }
        
        logger.debug("File sender configuration validation passed");
    }
    
    // Helper methods to handle transition period - TO BE REMOVED in Phase 1.3
    private String getStringConfigValue(Map<String, Object> config, String fieldName, boolean required, String defaultValue) {
        if (configService != null) {
            return configService.getStringConfig(config, fieldName, required, defaultValue);
        }
        // Fallback to static utility during transition
        return AdapterConfigUtil.getStringConfig(config, fieldName, required, defaultValue);
    }
    
    private boolean getBooleanConfigValue(Map<String, Object> config, String fieldName, boolean defaultValue) {
        if (configService != null) {
            return configService.getBooleanConfig(config, fieldName, defaultValue);
        }
        // Fallback to static utility during transition
        return AdapterConfigUtil.getBooleanConfig(config, fieldName, defaultValue);
    }
    
    private void logConfigSummary(Map<String, Object> config, String adapterType, String direction) {
        if (configService != null) {
            configService.logConfigurationSummary(config, adapterType, direction);
        } else {
            // Fallback to static utility during transition
            AdapterConfigUtil.logConfigSummary(config, adapterType, direction);
        }
    }
    
    private List<Path> findMatchingFilesInDirectory(Path directory, String pattern) {
        if (fileService != null) {
            FileOperationsService.FileSearchCriteria criteria = 
                FileOperationsService.FileSearchCriteria.of(directory, pattern);
            return fileService.findFiles(criteria);
        } else {
            // Fallback to static utility during transition
            return FileUtils.findMatchingFiles(directory, pattern);
        }
    }
    
    private boolean isValidFileToProcess(Path filePath) {
        if (fileService != null) {
            return fileService.isValidFile(filePath);
        } else {
            // Fallback to static utility during transition
            return FileUtils.isValidFile(filePath);
        }
    }
    
    private void archiveFileWithOptions(Path sourcePath, String archiveDirectory, boolean addTimestamp) {
        if (fileService != null) {
            Path archiveDir = Paths.get(archiveDirectory);
            FileOperationsService.ArchiveOptions options = addTimestamp ? 
                FileOperationsService.ArchiveOptions.withTimestamp() : 
                FileOperationsService.ArchiveOptions.defaultOptions();
            
            FileOperationsService.FileOperationResult result = 
                fileService.archiveFile(sourcePath, archiveDir, options);
            
            if (!result.isSuccessful()) {
                logger.error("Failed to archive file: {}", result.getMessage());
                throw new RuntimeException("Archive operation failed: " + result.getMessage());
            }
        } else {
            // Fallback to static utility during transition
            try {
                FileUtils.archiveFile(sourcePath, archiveDirectory, addTimestamp);
            } catch (Exception e) {
                logger.error("Failed to archive file using fallback: {}", e.getMessage());
                throw new RuntimeException("Archive operation failed: " + e.getMessage());
            }
        }
    }
    
    
    /**
     * Archive source files after successful processing.
     * This is the sender adapter's responsibility since it owns the source files.
     * Supports comprehensive post-processing configurations including ARCHIVE, KEEP_AND_MARK, 
     * KEEP_AND_REPROCESS, and DELETE modes.
     */
    private void archiveSuccessfulFiles(List<Map<String, Object>> successfulFiles, Map<String, Object> context) {
        logger.info("Post-processing {} source files after successful processing", successfulFiles.size());
        
        for (Map<String, Object> fileInfo : successfulFiles) {
            try {
                String fileName = (String) fileInfo.get("fileName");
                String originalFilePath = (String) fileInfo.get("originalFilePath");
                Map<String, Object> adapterConfig = (Map<String, Object>) fileInfo.get("adapterConfig");
                
                // Get post process action from configuration (primary field as per documentation)
                String postProcessAction = getStringConfigValue(adapterConfig, "postProcessAction", false, "ARCHIVE");

                logger.info("Post-processing file: {} with post process action: {}", fileName, postProcessAction);

                switch (postProcessAction.toUpperCase()) {
                    case "ARCHIVE":
                        // Archive mode: Move file to archive directory
                        String archiveDirectory = getStringConfigValue(adapterConfig, "archiveDirectory", false, null);
                        if (archiveDirectory != null && !archiveDirectory.trim().isEmpty()) {
                            Path sourcePath = Paths.get(originalFilePath);

                            // Check for timestamp option
                            boolean addTimestamp = getBooleanConfigValue(adapterConfig, "addTimestamp", false);

                            // Archive the file
                            archiveFileWithOptions(sourcePath, archiveDirectory, addTimestamp);

                            logger.info("✓ Archived file: {} to {} (timestamp: {})",
                                       fileName, archiveDirectory, addTimestamp);
                        } else {
                            logger.warn("Archive mode specified but no archive directory configured, keeping file: {}", fileName);
                        }
                        break;

                    case "KEEP_AND_MARK":
                        // Keep file and mark as processed (rename with .processed suffix)
                        Path markSourcePath = Paths.get(originalFilePath);
                        if (Files.exists(markSourcePath)) {
                            Path processedPath = markSourcePath.resolveSibling(fileName + ".processed");
                            Files.move(markSourcePath, processedPath);
                            logger.info("✓ File marked as processed: {} -> {}", fileName, processedPath.getFileName());
                        }
                        break;

                    case "KEEP_AND_REPROCESS":
                        // Keep file in source directory for reprocessing (no action needed)
                        logger.info("✓ File kept for reprocessing: {}", fileName);
                        break;

                    case "DELETE":
                        // Delete mode: Delete the file after successful processing
                        Path deletePath = Paths.get(originalFilePath);
                        if (Files.exists(deletePath)) {
                            Files.delete(deletePath);
                            logger.info("✓ File deleted after successful processing: {}", fileName);
                        } else {
                            logger.warn("File no longer exists for deletion: {}", fileName);
                        }
                        break;

                    default:
                        logger.warn("Unknown post process action '{}', defaulting to ARCHIVE mode: {}", postProcessAction, fileName);
                        break;
                }
                
            } catch (Exception e) {
                logger.warn("Post-processing failed for file {}: {}. File remains in original location.", 
                           fileInfo.get("fileName"), e.getMessage());
            }
        }
    }
    
    /**
     * Create error result for file sender failures.
     */
    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", errorMessage);
        result.put("hasData", false);
        result.put("foundFiles", new ArrayList<>());
        result.put("successCount", 0);
        result.put("errorCount", 0);
        result.put("totalBytesProcessed", 0L);
        return result;
    }

    // ==================================================================================
    // PHASE 1: FILE VALIDATION METHODS
    // ==================================================================================

    /**
     * Phase 1: Comprehensive file validation applying all configured checks.
     * Validates file against all Phase 1 configurations in order of importance.
     *
     * @param filePath Path to file being validated
     * @param config Adapter configuration map
     * @return FileValidationResult indicating if file should be processed
     */
    private FileValidationResult validateFileForProcessing(Path filePath, Map<String, Object> config) {
        try {
            String fileName = filePath.getFileName().toString();

            // 1. Basic file validity check (existing functionality)
            if (!isValidFileToProcess(filePath)) {
                return FileValidationResult.invalid("File is not valid or accessible");
            }

            // 2. Exclusion mask check (Phase 1.4)
            if (!passesExclusionMaskCheck(filePath, config)) {
                return FileValidationResult.invalid("File matches exclusion mask pattern");
            }

            // 3. Read-only file check (Phase 1.5)
            if (!passesReadOnlyFileCheck(filePath, config)) {
                return FileValidationResult.invalid("File is read-only and processReadOnlyFiles is disabled");
            }

            // 4. File size validation (Phase 1.2)
            FileValidationResult sizeResult = validateFileSize(filePath, config);
            if (!sizeResult.isValid()) {
                return sizeResult;
            }

            // 5. Empty file handling (Phase 1.3)
            FileValidationResult emptyResult = validateEmptyFile(filePath, config);
            if (!emptyResult.isValid()) {
                return emptyResult;
            }

            // 6. File stability check (Phase 1.1) - Do last as it may wait
            if (!isFileStable(filePath, config)) {
                return FileValidationResult.invalid("File is still being modified");
            }

            return FileValidationResult.valid();

        } catch (Exception e) {
            logger.error("Error validating file {}: {}", filePath, e.getMessage(), e);
            return FileValidationResult.invalidWithError("Validation error: " + e.getMessage());
        }
    }

    /**
     * Phase 1.1: Check if file is stable (not being actively written).
     * Waits configured milliseconds and verifies file modification time hasn't changed.
     *
     * @param filePath Path to file
     * @param config Adapter configuration
     * @return true if file is stable or check is disabled (0 msecs)
     */
    private boolean isFileStable(Path filePath, Map<String, Object> config) {
        try {
            // Get configured wait time (default: 0 = no check)
            int waitMsecs = AdapterConfigUtil.getIntConfig(config, "msecsToWaitBeforeModificationCheck", 0);

            if (waitMsecs <= 0) {
                return true; // Check disabled, assume stable
            }

            // Get initial modification time
            long initialModTime = Files.getLastModifiedTime(filePath).toMillis();

            logger.debug("Checking file stability for {}, waiting {} ms", filePath.getFileName(), waitMsecs);
            Thread.sleep(waitMsecs);

            // Check if file still exists and modification time hasn't changed
            if (!Files.exists(filePath)) {
                logger.warn("File disappeared during stability check: {}", filePath);
                return false;
            }

            long currentModTime = Files.getLastModifiedTime(filePath).toMillis();
            boolean stable = (initialModTime == currentModTime);

            if (!stable) {
                logger.info("File {} is still being modified (mod time changed during check)", filePath.getFileName());
            }

            return stable;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("File stability check interrupted for {}", filePath);
            return false;
        } catch (Exception e) {
            logger.error("Error checking file stability for {}: {}", filePath, e.getMessage());
            return true; // On error, allow processing (existing behavior)
        }
    }

    /**
     * Phase 1.2: Validate file size against configured maximum.
     *
     * @param filePath Path to file
     * @param config Adapter configuration
     * @return ValidationResult indicating if file size is acceptable
     */
    private FileValidationResult validateFileSize(Path filePath, Map<String, Object> config) {
        try {
            // Get configured maximum file size (default: 0 = no limit)
            long maxFileSize = AdapterConfigUtil.getLongConfig(config, "maximumFileSize", 0L);

            if (maxFileSize <= 0) {
                return FileValidationResult.valid(); // No limit configured
            }

            long fileSize = Files.size(filePath);

            if (fileSize > maxFileSize) {
                String reason = String.format("File size %d bytes exceeds maximum %d bytes", fileSize, maxFileSize);
                logger.warn("Skipping file {}: {}", filePath.getFileName(), reason);
                return FileValidationResult.invalid(reason);
            }

            return FileValidationResult.valid();

        } catch (Exception e) {
            logger.error("Error checking file size for {}: {}", filePath, e.getMessage());
            return FileValidationResult.valid(); // On error, allow processing
        }
    }

    /**
     * Phase 1.3: Validate empty file based on configured handling.
     *
     * @param filePath Path to file
     * @param config Adapter configuration
     * @return ValidationResult indicating if empty file should be processed
     */
    private FileValidationResult validateEmptyFile(Path filePath, Map<String, Object> config) {
        try {
            long fileSize = Files.size(filePath);

            if (fileSize > 0) {
                return FileValidationResult.valid(); // Not empty, process normally
            }

            // File is empty - check configuration
            String emptyFileHandling = AdapterConfigUtil.getStringConfig(
                config, "emptyFileHandling", false, "Do Not Create Message");

            switch (emptyFileHandling) {
                case "Do Not Create Message":
                    logger.info("Skipping empty file {}: emptyFileHandling = Do Not Create Message",
                               filePath.getFileName());
                    return FileValidationResult.invalid("File is empty and emptyFileHandling is 'Do Not Create Message'");

                case "Skip Empty Files":
                    logger.info("Skipping empty file {}: emptyFileHandling = Skip Empty Files",
                               filePath.getFileName());
                    return FileValidationResult.invalid("File is empty and emptyFileHandling is 'Skip Empty Files'");

                case "Process Empty Files":
                    logger.debug("Processing empty file {} as configured", filePath.getFileName());
                    return FileValidationResult.valid();

                default:
                    logger.warn("Unknown emptyFileHandling value '{}', defaulting to 'Do Not Create Message'",
                               emptyFileHandling);
                    return FileValidationResult.invalid("File is empty (unknown handling mode, defaulting to skip)");
            }

        } catch (Exception e) {
            logger.error("Error checking if file is empty {}: {}", filePath, e.getMessage());
            return FileValidationResult.valid(); // On error, allow processing
        }
    }

    /**
     * Phase 1.4: Check if file matches exclusion mask pattern.
     *
     * @param filePath Path to file
     * @param config Adapter configuration
     * @return true if file should be processed (doesn't match exclusion), false if should be excluded
     */
    private boolean passesExclusionMaskCheck(Path filePath, Map<String, Object> config) {
        try {
            String exclusionMask = AdapterConfigUtil.getStringConfig(config, "exclusionMask", false, null);

            if (exclusionMask == null || exclusionMask.trim().isEmpty()) {
                return true; // No exclusion mask configured
            }

            String fileName = filePath.getFileName().toString();
            boolean matches = matchesGlobPattern(fileName, exclusionMask);

            if (matches) {
                logger.info("Excluding file {} matching exclusion mask '{}'", fileName, exclusionMask);
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.error("Error checking exclusion mask for {}: {}", filePath, e.getMessage());
            return true; // On error, allow processing
        }
    }

    /**
     * Phase 1.5: Check if read-only files should be processed.
     *
     * @param filePath Path to file
     * @param config Adapter configuration
     * @return true if file should be processed
     */
    private boolean passesReadOnlyFileCheck(Path filePath, Map<String, Object> config) {
        try {
            boolean processReadOnly = AdapterConfigUtil.getBooleanConfig(config, "processReadOnlyFiles", false);

            if (processReadOnly) {
                return true; // Process all files including read-only
            }

            // Check if file is read-only (not writable)
            boolean isReadOnly = !Files.isWritable(filePath);

            if (isReadOnly) {
                logger.info("Skipping read-only file {}: processReadOnlyFiles is disabled",
                           filePath.getFileName());
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.error("Error checking read-only status for {}: {}", filePath, e.getMessage());
            return true; // On error, allow processing
        }
    }

    /**
     * Phase 1.6: Handle files that failed validation (archive to error directory if configured).
     *
     * @param filePath Path to file that failed
     * @param reason Reason for failure
     * @param config Adapter configuration
     */
    private void handleErrorFile(Path filePath, String reason, Map<String, Object> config) {
        try {
            boolean archiveErrorFiles = AdapterConfigUtil.getBooleanConfig(config, "archiveFaultySourceFiles", false);

            if (!archiveErrorFiles) {
                return; // Error archiving not enabled
            }

            String errorDirectory = AdapterConfigUtil.getStringConfig(config, "archiveErrorDirectory", false, null);

            if (errorDirectory == null || errorDirectory.trim().isEmpty()) {
                logger.warn("archiveFaultySourceFiles enabled but archiveErrorDirectory not configured");
                return;
            }

            // Create error directory if it doesn't exist
            Path errorDirPath = Paths.get(errorDirectory);
            if (!Files.exists(errorDirPath)) {
                Files.createDirectories(errorDirPath);
                logger.info("Created error archive directory: {}", errorDirectory);
            }

            // Move file to error directory with timestamp
            String fileName = filePath.getFileName().toString();
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            String errorFileName = timestamp + "_" + fileName;
            Path errorFilePath = errorDirPath.resolve(errorFileName);

            Files.move(filePath, errorFilePath);
            logger.info("Moved error file {} to {}: {}", fileName, errorFilePath, reason);

        } catch (Exception e) {
            logger.error("Failed to archive error file {}: {}", filePath, e.getMessage(), e);
            // Don't throw - this is best-effort error handling
        }
    }

    /**
     * Helper method to match filename against glob pattern.
     * Supports wildcards: * (matches any characters), ? (matches single character).
     *
     * @param filename Filename to test
     * @param pattern Glob pattern (e.g., *.tmp, temp_*, file?.txt)
     * @return true if filename matches pattern
     */
    private boolean matchesGlobPattern(String filename, String pattern) {
        try {
            // Convert glob pattern to PathMatcher
            java.nio.file.FileSystem fs = java.nio.file.FileSystems.getDefault();
            java.nio.file.PathMatcher matcher = fs.getPathMatcher("glob:" + pattern);

            // Create a Path from filename and test it
            java.nio.file.Path path = java.nio.file.Paths.get(filename);
            return matcher.matches(path);

        } catch (Exception e) {
            logger.warn("Error matching pattern '{}' against filename '{}': {}",
                       pattern, filename, e.getMessage());
            return false; // On error, don't exclude
        }
    }

    /**
     * Inner class to represent file validation result.
     * Encapsulates validation outcome and reason for invalid files.
     */
    private static class FileValidationResult {
        private final boolean valid;
        private final String reason;
        private final boolean archiveToError;

        private FileValidationResult(boolean valid, String reason, boolean archiveToError) {
            this.valid = valid;
            this.reason = reason;
            this.archiveToError = archiveToError;
        }

        public static FileValidationResult valid() {
            return new FileValidationResult(true, null, false);
        }

        public static FileValidationResult invalid(String reason) {
            return new FileValidationResult(false, reason, false);
        }

        public static FileValidationResult invalidWithError(String reason) {
            return new FileValidationResult(false, reason, true);
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }

        public boolean shouldArchiveToError() {
            return archiveToError;
        }
    }
}