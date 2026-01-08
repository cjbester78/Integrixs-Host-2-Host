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
        String processingMode = getStringConfigValue(config, "processingMode", false, "Test");
        String archiveDirectory = getStringConfigValue(config, "archiveDirectory", false, null);
        
        logger.info("Configuration - Source Directory: {}, File Pattern: {}, Processing Mode: {}", 
                   sourceDirectory, filePattern, processingMode);
        
        if ("Test".equalsIgnoreCase(processingMode)) {
            logger.info("Test mode active: Files will remain in source directory for reprocessing (archive settings ignored)");
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
            
            for (Path filePath : discoveredFiles) {
                try {
                    long startTime = System.currentTimeMillis();
                    String fileName = filePath.getFileName().toString();
                    long fileSize = Files.size(filePath);
                    
                    // Validate file before processing
                    if (!isValidFileToProcess(filePath)) {
                        logger.warn("Skipping invalid file: {}", filePath);
                        errorCount++;
                        continue;
                    }
                    
                    // Read file content into memory for receiver processing
                    byte[] fileContent = Files.readAllBytes(filePath);
                    
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("fileName", fileName);
                    fileInfo.put("originalFilePath", filePath.toString());
                    fileInfo.put("fileSize", fileSize);
                    fileInfo.put("fileContent", fileContent);
                    fileInfo.put("processingMode", processingMode);
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
            result.put("processingMode", processingMode);
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
                
                // Get processing mode from configuration (primary field as per documentation)
                String processingMode = getStringConfigValue(adapterConfig, "processingMode", false, "Test");
                
                logger.info("Post-processing file: {} with processing mode: {}", fileName, processingMode);
                
                switch (processingMode.toUpperCase()) {
                    case "TEST":
                        // Test mode: Leave file in source directory unchanged for reprocessing
                        // Ignore all archive configuration fields when in Test mode
                        logger.info("✓ Test mode: File remains in source directory for reprocessing: {}", fileName);
                        break;
                        
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
                        
                    case "DELETE":
                        // Delete mode: Delete the file after successful processing
                        Path sourcePath = Paths.get(originalFilePath);
                        if (Files.exists(sourcePath)) {
                            Files.delete(sourcePath);
                            logger.info("✓ File deleted after successful processing: {}", fileName);
                        } else {
                            logger.warn("File no longer exists for deletion: {}", fileName);
                        }
                        break;
                        
                    default:
                        logger.warn("Unknown processing mode '{}', defaulting to Test mode (keeping file in original location): {}", processingMode, fileName);
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
}