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
        String archiveDirectory = AdapterConfigUtil.getStringConfig(config, "archiveDirectory", false, null);
        
        logger.info("Configuration - Remote Directory: {}, File Pattern: {}, Archive Directory: {}", 
                   remoteDirectory, filePattern, archiveDirectory);
        
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
                    
                    // Archive file on remote server if archive directory is configured
                    if (archiveDirectory != null && !archiveDirectory.trim().isEmpty()) {
                        try {
                            String archiveFilePath = archiveDirectory + "/" + fileName;
                            sftpChannel.rename(remoteFilePath, archiveFilePath);
                            logger.info("Archived remote file: {} -> {}", remoteFilePath, archiveFilePath);
                        } catch (Exception e) {
                            logger.warn("Failed to archive remote file {}: {}", remoteFilePath, e.getMessage());
                            // Don't fail the entire operation if archiving fails
                        }
                    }
                    
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