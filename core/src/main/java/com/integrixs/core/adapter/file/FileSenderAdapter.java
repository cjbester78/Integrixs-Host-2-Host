package com.integrixs.core.adapter.file;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.util.AdapterConfigUtil;
import com.integrixs.core.util.FileUtil;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;

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
 * Single Responsibility: Only collect files from local source directory with file pattern matching.
 * The collected file content is stored in context for receiver processing.
 * 
 * This follows the OOP pattern where:
 * - File Sender: Collects files from source directory (this class)
 * - File Receiver: Writes files to target directory
 */
public class FileSenderAdapter extends AbstractAdapterExecutor {
    
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
        
        String sourceDirectory = AdapterConfigUtil.getStringConfig(config, "sourceDirectory", true, null);
        String filePattern = AdapterConfigUtil.getStringConfig(config, "filePattern", false, "*");
        String processingMode = AdapterConfigUtil.getStringConfig(config, "processingMode", false, "Test");
        String archiveDirectory = AdapterConfigUtil.getStringConfig(config, "archiveDirectory", false, null);
        
        logger.info("Configuration - Source Directory: {}, File Pattern: {}, Processing Mode: {}", 
                   sourceDirectory, filePattern, processingMode);
        
        try {
            // Ensure source directory exists and is accessible
            Path sourcePath = Paths.get(sourceDirectory);
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                logger.warn("Source directory does not exist or is not accessible: {}", sourceDirectory);
                return createErrorResult("Source directory not accessible: " + sourceDirectory);
            }
            
            // Find files matching the pattern
            List<Path> discoveredFiles = FileUtil.findMatchingFiles(sourcePath, filePattern);
            
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
                    if (!FileUtil.isValidFile(filePath)) {
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
        
        AdapterConfigUtil.logConfigSummary(config, getSupportedType(), getSupportedDirection());
    }
    
    /**
     * Validate File sender specific configuration.
     * Requires source directory and optional file pattern.
     */
    private void validateFileSenderConfiguration(Map<String, Object> config) {
        String adapterType = getSupportedType() + " " + getSupportedDirection();
        
        // File sender needs source directory
        AdapterConfigUtil.validateRequiredString(config, "sourceDirectory", adapterType);
        
        // Validate optional polling interval if present
        int pollingInterval = AdapterConfigUtil.getIntegerConfig(config, "pollingInterval", false, 5000);
        if (pollingInterval < 1000) {
            throw new IllegalArgumentException(adapterType + " pollingInterval must be at least 1000 milliseconds");
        }
        
        logger.debug("File sender configuration validation passed");
    }
    
    
    /**
     * Archive source files after successful processing.
     * This is the sender adapter's responsibility since it owns the source files.
     */
    private void archiveSuccessfulFiles(List<Map<String, Object>> successfulFiles, Map<String, Object> context) {
        logger.info("Archiving {} source files after successful processing", successfulFiles.size());
        
        for (Map<String, Object> fileInfo : successfulFiles) {
            try {
                String originalFilePath = (String) fileInfo.get("originalFilePath");
                String archiveDirectory = (String) fileInfo.get("archiveDirectory");
                
                if (originalFilePath != null && archiveDirectory != null && !archiveDirectory.trim().isEmpty()) {
                    Path sourcePath = Paths.get(originalFilePath);
                    Path archivePath = Paths.get(archiveDirectory);
                    
                    // Use FileUtil for archiving
                    FileUtil.archiveFile(sourcePath, archiveDirectory, true);
                    logger.info("Archived source file: {} to {}", originalFilePath, archiveDirectory);
                } else {
                    logger.debug("No archiving configured for file: {}", fileInfo.get("fileName"));
                }
            } catch (Exception e) {
                logger.error("Failed to archive file {}: {}", fileInfo.get("originalFilePath"), e.getMessage(), e);
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