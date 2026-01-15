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
        String postProcessAction = AdapterConfigUtil.getStringConfig(config, "postProcessAction", false, "ARCHIVE");
        
        logger.info("File receiver post process action: {} (note: receiver only writes files, post-processing handled by sender)", postProcessAction);
        
        logger.info("Configuration - Target Directory: {}, Post Process Action: {}", 
                   targetDirectory, postProcessAction);
        
        try {
            // Ensure target directory exists
            Path targetPath = FileUtils.ensureDirectoryExists(targetDirectory);
            
            List<Map<String, Object>> processedFiles = new ArrayList<>();
            List<Map<String, Object>> successfulFiles = new ArrayList<>();
            long totalBytes = 0;
            int successCount = 0;
            int errorCount = 0;
            
            // Phase 2.5: Get maximum concurrency setting
            int maxConcurrency = AdapterConfigUtil.getIntConfig(config, "maximumConcurrency", 1);
            int currentConcurrency = 0;

            // Write files to receiver directory using content from context
            for (Map<String, Object> fileInfo : filesToProcess) {
                String fileName = (String) fileInfo.get("fileName");
                try {
                    // Extract file content and metadata
                    byte[] fileContent = (byte[]) fileInfo.get("fileContent");
                    if (fileContent == null) {
                        throw new RuntimeException("No file content found for: " + fileName);
                    }

                    // Phase 2.4: Check empty message handling
                    if (!shouldProcessMessage(fileContent, config, fileName)) {
                        logger.info("Skipping empty message for file: {}", fileName);
                        continue;
                    }

                    // Phase 2.5: Simple concurrency control (synchronous for now, proper implementation would use thread pool)
                    if (currentConcurrency >= maxConcurrency) {
                        logger.debug("Concurrency limit reached ({}), processing sequentially", maxConcurrency);
                    }
                    currentConcurrency++;

                    // Phase 2.1 & 2.2: Generate output filename based on configured mode
                    String outputFileName = generateOutputFileNameEnhanced(fileName, config);
                    Path outputPath = targetPath.resolve(outputFileName);

                    // Phase 2.3: Write file using configured write mode
                    writeFileWithMode(outputPath, fileContent, config);
                    long fileSize = Files.size(outputPath);

                    currentConcurrency--;

                    
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
            result.put("postProcessAction", postProcessAction);
            
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
     *
     * @deprecated Use generateOutputFileNameEnhanced() for Phase 2 implementation
     */
    @Deprecated
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

    // ==================================================================================
    // PHASE 2: FILE RECEIVER OUTPUT CONFIGURATION METHODS
    // ==================================================================================

    /**
     * Phase 2.1 & 2.2: Generate output filename based on configured mode.
     * Supports UseOriginal, AddTimestamp, and Custom modes with variable substitution.
     *
     * @param originalFileName Original filename from sender
     * @param config Adapter configuration
     * @return Generated output filename
     */
    private String generateOutputFileNameEnhanced(String originalFileName, Map<String, Object> config) {
        try {
            // Get output filename mode (default: UseOriginal for backward compatibility)
            String outputFilenameMode = AdapterConfigUtil.getStringConfig(
                config, "outputFilenameMode", false, "UseOriginal");

            switch (outputFilenameMode) {
                case "UseOriginal":
                    return originalFileName;

                case "AddTimestamp":
                    return generateTimestampedFilename(originalFileName);

                case "Custom":
                    String customPattern = AdapterConfigUtil.getStringConfig(
                        config, "customFilenamePattern", false, null);

                    if (customPattern == null || customPattern.trim().isEmpty()) {
                        logger.warn("Custom filename mode selected but no pattern configured, using original filename");
                        return originalFileName;
                    }

                    return applyCustomFilenamePattern(originalFileName, customPattern);

                default:
                    logger.warn("Unknown outputFilenameMode '{}', using original filename", outputFilenameMode);
                    return originalFileName;
            }

        } catch (Exception e) {
            logger.error("Error generating output filename for {}: {}", originalFileName, e.getMessage(), e);
            return originalFileName; // Fallback to original on error
        }
    }

    /**
     * Generate filename with timestamp appended.
     * Format: originalname_yyyyMMddHHmmss.ext
     *
     * @param originalFileName Original filename
     * @return Filename with timestamp
     */
    private String generateTimestampedFilename(String originalFileName) {
        int dotIndex = originalFileName.lastIndexOf('.');
        String nameWithoutExt = dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
        String extension = dotIndex > 0 ? originalFileName.substring(dotIndex) : "";

        String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());

        return nameWithoutExt + "_" + timestamp + extension;
    }

    /**
     * Phase 2.2: Apply custom filename pattern with variable substitution.
     * Supported variables:
     * - {original_name}: Original filename without extension
     * - {timestamp}: yyyyMMddHHmmss
     * - {date}: yyyyMMdd
     * - {extension}: File extension with dot
     * - {uuid}: Random UUID
     *
     * @param originalFileName Original filename
     * @param pattern Custom pattern
     * @return Filename with variables substituted
     */
    private String applyCustomFilenamePattern(String originalFileName, String pattern) {
        try {
            // Extract filename parts
            int dotIndex = originalFileName.lastIndexOf('.');
            String nameWithoutExt = dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
            String extension = dotIndex > 0 ? originalFileName.substring(dotIndex) : "";

            // Generate timestamp and date strings
            java.util.Date now = new java.util.Date();
            String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(now);
            String date = new java.text.SimpleDateFormat("yyyyMMdd").format(now);
            String uuid = java.util.UUID.randomUUID().toString();

            // Perform variable substitution
            String result = pattern
                .replace("{original_name}", nameWithoutExt)
                .replace("{timestamp}", timestamp)
                .replace("{date}", date)
                .replace("{extension}", extension)
                .replace("{uuid}", uuid);

            logger.debug("Applied custom filename pattern: {} -> {}", originalFileName, result);

            return result;

        } catch (Exception e) {
            logger.error("Error applying custom filename pattern to {}: {}", originalFileName, e.getMessage(), e);
            return originalFileName; // Fallback to original on error
        }
    }

    /**
     * Phase 2.3: Write file content using configured write mode.
     * Supports two modes:
     * - Directly: Write directly to target file
     * - Create Temp File: Write to .tmp, then rename atomically
     *
     * @param outputPath Final output file path
     * @param fileContent File content bytes
     * @param config Adapter configuration
     * @throws Exception if write fails
     */
    private void writeFileWithMode(Path outputPath, byte[] fileContent, Map<String, Object> config) throws Exception {
        // Get write mode (default: Directly for backward compatibility)
        String writeMode = AdapterConfigUtil.getStringConfig(config, "writeMode", false, "Directly");

        switch (writeMode) {
            case "Directly":
                // Write directly to target file
                Files.write(outputPath, fileContent);
                logger.debug("Wrote file directly: {}", outputPath);
                break;

            case "Create Temp File":
                // Write to temporary file, then rename atomically
                Path tempPath = Paths.get(outputPath.toString() + ".tmp");

                try {
                    // Write to temp file
                    Files.write(tempPath, fileContent);
                    logger.debug("Wrote to temp file: {}", tempPath);

                    // Atomically rename to final filename
                    Files.move(tempPath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                                     java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    logger.debug("Renamed temp file to final: {}", outputPath);

                } catch (Exception e) {
                    // Clean up temp file on error
                    try {
                        if (Files.exists(tempPath)) {
                            Files.delete(tempPath);
                        }
                    } catch (Exception cleanupEx) {
                        logger.warn("Failed to cleanup temp file: {}", tempPath);
                    }
                    throw e;
                }
                break;

            default:
                logger.warn("Unknown writeMode '{}', using 'Directly'", writeMode);
                Files.write(outputPath, fileContent);
                break;
        }
    }

    /**
     * Phase 2.4: Determine if message should be processed based on empty message handling.
     *
     * @param fileContent File content bytes
     * @param config Adapter configuration
     * @param fileName Filename for logging
     * @return true if message should be processed, false if should be skipped
     */
    private boolean shouldProcessMessage(byte[] fileContent, Map<String, Object> config, String fileName) {
        try {
            // Check if content is empty
            if (fileContent != null && fileContent.length > 0) {
                return true; // Not empty, process normally
            }

            // Content is empty - check configuration
            String emptyMessageHandling = AdapterConfigUtil.getStringConfig(
                config, "emptyMessageHandling", false, "Write Empty File");

            switch (emptyMessageHandling) {
                case "Write Empty File":
                    logger.debug("Processing empty message for file {} as configured", fileName);
                    return true;

                case "Skip Empty Messages":
                    logger.info("Skipping empty message for file {}: emptyMessageHandling = Skip Empty Messages", fileName);
                    return false;

                default:
                    logger.warn("Unknown emptyMessageHandling value '{}', defaulting to 'Write Empty File'",
                               emptyMessageHandling);
                    return true;
            }

        } catch (Exception e) {
            logger.error("Error checking empty message handling for {}: {}", fileName, e.getMessage());
            return true; // On error, allow processing
        }
    }

}