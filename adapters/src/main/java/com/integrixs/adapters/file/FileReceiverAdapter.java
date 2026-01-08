package com.integrixs.adapters.file;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.util.AdapterConfigUtil;
import com.integrixs.shared.util.FileUtils;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * File Receiver Adapter - Writes files to target directory.
 * 
 * Single Responsibility: Only write files to target directory from context data.
 * Gets file content from context (provided by sender adapter).
 * 
 * This follows the OOP pattern where:
 * - File Sender: Collects files from source directory 
 * - File Receiver: Writes files to target directory (this class)
 */
@Component
public class FileReceiverAdapter extends AbstractAdapterExecutor {
    
    @Override
    public String getSupportedType() {
        return "FILE";
    }
    
    @Override
    public String getSupportedDirection() {
        return "RECEIVER";
    }
    
    @Override
    protected Map<String, Object> executeInternal(Adapter adapter, Map<String, Object> context, 
                                                 FlowExecutionStep step) {
        
        logger.info("Direction: RECEIVER (writing files to target directory)");
        logger.info("Executing File adapter receiver operation for: {}", adapter.getName());
        
        // Get files from context (passed from sender processing with content)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filesToProcess = (List<Map<String, Object>>) 
            context.getOrDefault("filesToProcess", new ArrayList<>());
        
        if (filesToProcess.isEmpty()) {
            logger.warn("No files provided for receiver processing");
            return createSuccessResult(0, 0, 0L, "No files to write");
        }
        
        logger.info("Processing {} files for receiver delivery", filesToProcess.size());
        
        // Get and validate configuration
        Map<String, Object> config = adapter.getConfiguration();
        validateFileReceiverConfiguration(config);
        
        String targetDirectory = AdapterConfigUtil.getStringConfig(config, "targetDirectory", true, null);
        String processingMode = AdapterConfigUtil.getStringConfig(config, "processingMode", false, "Test");
        
        logger.info("File receiver processing mode: {} (note: receiver only writes files, post-processing handled by sender)", processingMode);
        
        logger.info("Configuration - Target Directory: {}, Processing Mode: {}", 
                   targetDirectory, processingMode);
        
        try {
            // Ensure target directory exists
            Path targetPath = FileUtils.ensureDirectoryExists(targetDirectory);
            
            List<Map<String, Object>> processedFiles = new ArrayList<>();
            List<Map<String, Object>> successfulFiles = new ArrayList<>();
            long totalBytes = 0;
            int successCount = 0;
            int errorCount = 0;
            
            // Write files to receiver directory using content from context
            for (Map<String, Object> fileInfo : filesToProcess) {
                String fileName = (String) fileInfo.get("fileName");
                try {
                    // Extract file content and metadata
                    byte[] fileContent = (byte[]) fileInfo.get("fileContent");
                    if (fileContent == null) {
                        throw new RuntimeException("No file content found for: " + fileName);
                    }
                    
                    String outputFileName = generateOutputFileName(fileName, config);
                    Path outputPath = targetPath.resolve(outputFileName);
                    
                    // Write actual file content to receiver directory
                    Files.write(outputPath, fileContent);
                    long fileSize = Files.size(outputPath);
                    
                    Map<String, Object> fileResult = new HashMap<>();
                    fileResult.put("fileName", fileName);
                    fileResult.put("outputFileName", outputFileName);
                    fileResult.put("fileSize", fileSize);
                    fileResult.put("status", "SUCCESS");
                    fileResult.put("targetPath", outputPath.toString());
                    fileResult.put("processingTime", System.currentTimeMillis());
                    
                    processedFiles.add(fileResult);
                    successfulFiles.add(fileInfo); // Track for archiving
                    totalBytes += fileSize;
                    successCount++;
                    
                    // Add file to step tracking
                    if (step != null) {
                        step.addFileProcessed(fileName, outputPath.toString(), fileSize);
                    }
                    
                    logger.info("Successfully wrote receiver file: {} -> {} ({} bytes)", 
                               fileName, outputPath, fileSize);
                    
                } catch (Exception e) {
                    logger.error("Failed to process receiver file {}: {}", fileName, e.getMessage(), e);
                    
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("fileName", fileName);
                    errorResult.put("status", "FAILED");
                    errorResult.put("errorMessage", e.getMessage());
                    processedFiles.add(errorResult);
                    errorCount++;
                }
            }
            
            // If receiver processing was successful, signal sender to handle post-processing
            if (successCount > 0) {
                // Set flag to indicate receiver processing was successful
                context.put("receiverProcessingSuccessful", true);
                context.put("successfulFiles", successfulFiles);
            }
            
            // Create success result
            Map<String, Object> result = createSuccessResult(successCount, errorCount, totalBytes,
                String.format("File receiver completed: %d/%d files written successfully", 
                             successCount, filesToProcess.size()));
            
            result.put("processedFiles", processedFiles);
            result.put("targetDirectory", targetDirectory);
            result.put("processingMode", processingMode);
            
            logger.info("✓ Files written: {}/{}", successCount, filesToProcess.size());
            logger.info("✓ Total bytes written: {}", totalBytes);
            
            return result;
            
        } catch (Exception e) {
            logger.error("File receiver execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("File receiver execution failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void validateConfiguration(Adapter adapter) {
        // Call parent validation
        super.validateConfiguration(adapter);
        
        Map<String, Object> config = adapter.getConfiguration();
        validateFileReceiverConfiguration(config);
        
        AdapterConfigUtil.logConfigSummary(config, getSupportedType(), getSupportedDirection());
    }
    
    /**
     * Validate File receiver specific configuration.
     * Requires target directory.
     */
    private void validateFileReceiverConfiguration(Map<String, Object> config) {
        String adapterType = getSupportedType() + " " + getSupportedDirection();
        
        // File receiver needs target directory
        AdapterConfigUtil.validateRequiredString(config, "targetDirectory", adapterType);
        
        logger.debug("File receiver configuration validation passed");
    }
    
    /**
     * Generate output file name based on configuration.
     * Can apply prefixes, suffixes, or timestamp-based naming.
     */
    private String generateOutputFileName(String originalFileName, Map<String, Object> config) {
        String outputPrefix = AdapterConfigUtil.getStringConfig(config, "outputFilePrefix", false, "");
        String outputSuffix = AdapterConfigUtil.getStringConfig(config, "outputFileSuffix", false, "");
        boolean addTimestamp = AdapterConfigUtil.getBooleanConfig(config, "addTimestamp", false);
        
        StringBuilder newName = new StringBuilder();
        
        // Add prefix if specified
        if (!outputPrefix.isEmpty()) {
            newName.append(outputPrefix);
        }
        
        // Add original filename (without extension if we're adding timestamp)
        if (addTimestamp) {
            int dotIndex = originalFileName.lastIndexOf('.');
            String nameWithoutExt = dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
            String extension = dotIndex > 0 ? originalFileName.substring(dotIndex) : "";
            
            newName.append(nameWithoutExt)
                   .append("_")
                   .append(System.currentTimeMillis())
                   .append(extension);
        } else {
            newName.append(originalFileName);
        }
        
        // Add suffix if specified (before file extension)
        if (!outputSuffix.isEmpty()) {
            int dotIndex = newName.lastIndexOf(".");
            if (dotIndex > 0) {
                newName.insert(dotIndex, outputSuffix);
            } else {
                newName.append(outputSuffix);
            }
        }
        
        return newName.toString();
    }
    
}