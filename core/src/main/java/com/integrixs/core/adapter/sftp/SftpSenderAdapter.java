package com.integrixs.core.adapter.sftp;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.util.AdapterConfigUtil;
import com.integrixs.core.util.FileUtil;
import com.integrixs.core.util.SftpConnectionUtil;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import com.jcraft.jsch.ChannelSftp;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * SFTP Sender Adapter - Downloads files from remote SFTP server.
 * 
 * Single Responsibility: Download files from remote SFTP server directory with optional archiving.
 * This connects to the remote SFTP server and downloads files matching the pattern.
 * 
 * SFTP SENDER flow:
 * 1. Connect to remote SFTP server (host/port/auth)
 * 2. Download files from remoteDirectory matching filePattern  
 * 3. Archive files to archiveDirectory on remote server (optional)
 * 4. Store file content in context for receiver processing
 */
public class SftpSenderAdapter extends AbstractAdapterExecutor {
    
    @Override
    public String getSupportedType() {
        return "SFTP";
    }
    
    @Override
    public String getSupportedDirection() {
        return "SENDER";
    }
    
    @Override
    protected Map<String, Object> executeInternal(Adapter adapter, Map<String, Object> context, 
                                                 FlowExecutionStep step) {
        
        logger.info("Direction: SENDER (downloading files from remote SFTP server)");
        
        // Get and validate configuration
        Map<String, Object> config = adapter.getConfiguration();
        validateSftpSenderConfiguration(config);
        
        String filePattern = AdapterConfigUtil.getStringConfig(config, "filePattern", false, "*");
        String remoteDirectory = AdapterConfigUtil.getStringConfig(config, "remoteDirectory", true, null);
        String postProcessAction = AdapterConfigUtil.getStringConfig(config, "postProcessAction", false, "ARCHIVE");
        String archiveDirectory = AdapterConfigUtil.getStringConfig(config, "archiveDirectory", false, null);
        
        logger.info("Configuration - Remote Directory: {}, File Pattern: {}, Post Process Action: {}", 
                   remoteDirectory, filePattern, postProcessAction);
        
        ChannelSftp sftpChannel = null;
        
        try {
            // Create SFTP connection
            sftpChannel = SftpConnectionUtil.createSftpConnection(config);
            
            // List files in remote directory matching pattern
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> remoteFiles = sftpChannel.ls(remoteDirectory);
            
            List<ChannelSftp.LsEntry> matchingFiles = remoteFiles.stream()
                .filter(entry -> !entry.getAttrs().isDir() && 
                               FileUtil.matchesPattern(entry.getFilename(), filePattern))
                .collect(Collectors.toList());
            
            if (matchingFiles.isEmpty()) {
                logger.info("No files found matching pattern '{}' in remote directory '{}'", filePattern, remoteDirectory);
                return createSuccessResult(0, 0, 0L, "No files found to download");
            }
            
            logger.info("Found {} files to download from remote SFTP server", matchingFiles.size());
            
            // Process each file - download and collect content
            List<Map<String, Object>> processedFiles = new ArrayList<>();
            long totalBytes = 0;
            int successCount = 0;
            int errorCount = 0;
            
            for (ChannelSftp.LsEntry fileEntry : matchingFiles) {
                String fileName = fileEntry.getFilename();
                String remoteFilePath = remoteDirectory + "/" + fileName;
                
                try {
                    // Download file content from SFTP server
                    byte[] fileContent;
                    try (InputStream inputStream = sftpChannel.get(remoteFilePath)) {
                        fileContent = inputStream.readAllBytes();
                    }
                    
                    long fileSize = fileContent.length;
                    
                    Map<String, Object> fileData = new HashMap<>();
                    fileData.put("fileName", fileName);
                    fileData.put("remoteFilePath", remoteFilePath);
                    fileData.put("fileSize", fileSize);
                    fileData.put("fileContent", fileContent);
                    fileData.put("status", "DOWNLOADED");
                    fileData.put("remoteSource", remoteFilePath);
                    
                    processedFiles.add(fileData);
                    totalBytes += fileSize;
                    successCount++;
                    
                    // Add to step tracking
                    if (step != null) {
                        step.addFileProcessed(fileName, "DOWNLOADED_FROM_SFTP", fileSize);
                    }
                    
                    logger.info("Successfully downloaded: {} ({} bytes)", fileName, fileSize);
                    
                    // Handle post-processing based on configuration
                    handlePostProcessing(sftpChannel, config, remoteFilePath, fileName, postProcessAction);
                    
                } catch (Exception e) {
                    logger.error("Error downloading file {}: {}", remoteFilePath, e.getMessage(), e);
                    
                    Map<String, Object> errorData = new HashMap<>();
                    errorData.put("fileName", fileName);
                    errorData.put("status", "DOWNLOAD_ERROR");
                    errorData.put("errorMessage", e.getMessage());
                    errorData.put("remoteSource", remoteFilePath);
                    processedFiles.add(errorData);
                    errorCount++;
                }
            }
            
            // Store downloaded files in context for receiver processing
            context.put("filesToProcess", processedFiles);
            context.put("senderAdapter", adapter);
            
            // Create success result
            Map<String, Object> result = createSuccessResult(successCount, errorCount, totalBytes,
                String.format("SFTP sender completed: %d/%d files downloaded successfully", successCount, matchingFiles.size()));
            
            result.put("foundFiles", processedFiles);
            result.put("filePattern", filePattern);
            result.put("remoteDirectory", remoteDirectory);
            result.put("postProcessAction", postProcessAction);
            result.put("archiveDirectory", archiveDirectory);
            result.put("filesDiscovered", matchingFiles.size());
            
            logger.info("✓ Files downloaded: {}/{}", successCount, matchingFiles.size());
            logger.info("✓ Total bytes downloaded: {}", totalBytes);
            
            return result;
            
        } catch (Exception e) {
            logger.error("SFTP sender execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("SFTP sender execution failed: " + e.getMessage(), e);
        } finally {
            // Cleanup SFTP connection
            SftpConnectionUtil.closeSftpConnection(sftpChannel);
        }
    }
    
    /**
     * Handle post-processing of downloaded files based on configuration.
     * Only uses configuration values that are actually saved for this adapter.
     */
    private void handlePostProcessing(ChannelSftp sftpChannel, Map<String, Object> config, 
                                    String remoteFilePath, String fileName, String postProcessAction) {
        try {
            logger.info("Post-processing file: {} with action: {}", fileName, postProcessAction);
            
            switch (postProcessAction.toUpperCase()) {
                case "ARCHIVE":
                    // Only archive if archive directory is specifically configured
                    if (config.containsKey("archiveDirectory")) {
                        String archiveDirectory = AdapterConfigUtil.getStringConfig(config, "archiveDirectory", false, null);
                        if (archiveDirectory != null && !archiveDirectory.trim().isEmpty()) {
                            String archiveFilePath = archiveDirectory + "/" + fileName;
                            
                            // Check if timestamp should be added to archived filename (only if configured)
                            if (config.containsKey("archiveWithTimestamp")) {
                                boolean archiveWithTimestamp = AdapterConfigUtil.getBooleanConfig(config, "archiveWithTimestamp", false);
                                if (archiveWithTimestamp) {
                                    String baseName = fileName.substring(0, fileName.lastIndexOf('.') != -1 ? fileName.lastIndexOf('.') : fileName.length());
                                    String extension = fileName.lastIndexOf('.') != -1 ? fileName.substring(fileName.lastIndexOf('.')) : "";
                                    String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now());
                                    archiveFilePath = archiveDirectory + "/" + baseName + "_" + timestamp + extension;
                                }
                            }
                            
                            // Handle compression if specified (only if configured)
                            if (config.containsKey("compressionType")) {
                                String compressionType = AdapterConfigUtil.getStringConfig(config, "compressionType", false, "NONE");
                                if (!"NONE".equalsIgnoreCase(compressionType)) {
                                    // For remote SFTP files, compression would require downloading, compressing, and re-uploading
                                    // This is complex for remote files, so log a note about it
                                    logger.info("Note: Compression type '{}' specified but not implemented for remote SFTP files. File archived without compression.", compressionType);
                                }
                            }
                            
                            sftpChannel.rename(remoteFilePath, archiveFilePath);
                            logger.info("✓ Archived remote file: {} -> {}", remoteFilePath, archiveFilePath);
                        } else {
                            logger.warn("Archive directory configured but empty, file remains in original location: {}", remoteFilePath);
                        }
                    } else {
                        logger.warn("Archive directory not configured, file remains in original location: {}", remoteFilePath);
                    }
                    break;
                    
                case "KEEP_AND_MARK":
                    // Use configured processed directory if available
                    String processedDirectory = config.containsKey("processedDirectory") ? 
                        AdapterConfigUtil.getStringConfig(config, "processedDirectory", false, null) : null;
                    
                    // Use configured suffix or default to ".processed" only if suffix is configured
                    String processedFileSuffix = config.containsKey("processedFileSuffix") ? 
                        AdapterConfigUtil.getStringConfig(config, "processedFileSuffix", false, ".processed") : ".processed";
                    
                    if (processedDirectory != null && !processedDirectory.trim().isEmpty()) {
                        // Move to processed directory with suffix
                        String processedFileName = fileName + processedFileSuffix;
                        String processedFilePath = processedDirectory + "/" + processedFileName;
                        sftpChannel.rename(remoteFilePath, processedFilePath);
                        logger.info("✓ Moved to processed directory: {} -> {}", remoteFilePath, processedFilePath);
                    } else {
                        // Just rename in place with suffix
                        String processedFilePath = remoteFilePath + processedFileSuffix;
                        sftpChannel.rename(remoteFilePath, processedFilePath);
                        logger.info("✓ Marked as processed: {} -> {}", remoteFilePath, processedFilePath);
                    }
                    break;
                    
                case "KEEP_AND_REPROCESS":
                    // Do not move, rename, or delete the file - keep it in original location for reprocessing
                    // Log reprocessing delay if configured
                    if (config.containsKey("reprocessingDelay")) {
                        Object delayObj = config.get("reprocessingDelay");
                        String delayInfo = delayObj != null ? delayObj.toString() + "ms" : "default";
                        logger.info("✓ File kept in original location for reprocessing: {} (reprocessing delay: {})", remoteFilePath, delayInfo);
                    } else {
                        logger.info("✓ File kept in original location for reprocessing: {}", remoteFilePath);
                    }
                    break;
                    
                case "DELETE":
                    // Only proceed with delete if confirmation is specifically configured and enabled
                    if (config.containsKey("confirmDelete")) {
                        boolean confirmDelete = AdapterConfigUtil.getBooleanConfig(config, "confirmDelete", false);
                        
                        if (confirmDelete) {
                            String deleteBackupDirectory = config.containsKey("deleteBackupDirectory") ? 
                                AdapterConfigUtil.getStringConfig(config, "deleteBackupDirectory", false, null) : null;
                            
                            if (deleteBackupDirectory != null && !deleteBackupDirectory.trim().isEmpty()) {
                                // Move to backup directory before "deleting"
                                String backupFilePath = deleteBackupDirectory + "/" + fileName;
                                sftpChannel.rename(remoteFilePath, backupFilePath);
                                logger.info("✓ Moved to delete backup directory: {} -> {}", remoteFilePath, backupFilePath);
                            } else {
                                // Actually delete the file
                                sftpChannel.rm(remoteFilePath);
                                logger.info("✓ Deleted remote file: {}", remoteFilePath);
                            }
                        } else {
                            logger.warn("Delete confirmation disabled, file remains: {}", remoteFilePath);
                        }
                    } else {
                        logger.warn("Delete confirmation not configured, file remains: {}", remoteFilePath);
                    }
                    break;
                    
                default:
                    logger.warn("✗ Unknown post-processing action: {}. File remains in original location: {}", postProcessAction, remoteFilePath);
                    break;
            }
            
        } catch (Exception e) {
            logger.warn("Post-processing failed for file {}: {}. File remains in original location.", fileName, e.getMessage());
            // Don't fail the entire operation if post-processing fails
        }
    }
    
    @Override
    public void validateConfiguration(Adapter adapter) {
        // Call parent validation
        super.validateConfiguration(adapter);
        
        Map<String, Object> config = adapter.getConfiguration();
        validateSftpSenderConfiguration(config);
        
        AdapterConfigUtil.logConfigSummary(config, getSupportedType(), getSupportedDirection());
    }
    
    /**
     * Validate SFTP sender specific configuration.
     * Requires SFTP connection details and remote directory.
     */
    private void validateSftpSenderConfiguration(Map<String, Object> config) {
        String adapterType = getSupportedType() + " " + getSupportedDirection();
        
        // SFTP sender needs connection details
        AdapterConfigUtil.validateRequiredString(config, "host", adapterType);
        AdapterConfigUtil.validateRequiredString(config, "username", adapterType);
        AdapterConfigUtil.validateRequiredString(config, "remoteDirectory", adapterType);
        
        // Validate port if present
        Integer port = AdapterConfigUtil.getIntegerConfig(config, "port", false, 22);
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException(adapterType + " port must be between 1 and 65535");
        }
        
        logger.debug("SFTP sender configuration validation passed");
    }
}