package com.integrixs.adapters.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * SFTP download command implementation.
 * Handles downloading files from remote SFTP server with proper error handling.
 * Follows OOP principles with immutable results and comprehensive logging.
 */
@Component
public class SftpDownloadCommand implements SftpOperationCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(SftpDownloadCommand.class);
    
    @Override
    public String getCommandName() {
        return "SFTP_DOWNLOAD";
    }
    
    @Override
    public String getCommandDescription() {
        return "Download file from remote SFTP server";
    }
    
    @Override
    public boolean supportsOperation(SftpOperationType operationType) {
        return operationType == SftpOperationType.DOWNLOAD_FILE;
    }
    
    @Override
    public SftpOperationResult validateParameters(Map<String, Object> parameters) {
        SftpOperationResult baseValidation = SftpOperationCommand.super.validateParameters(parameters);
        if (!baseValidation.isSuccessful()) {
            return baseValidation;
        }
        
        String remoteFilePath = (String) parameters.get("remoteFilePath");
        if (remoteFilePath == null || remoteFilePath.trim().isEmpty()) {
            return SftpOperationResult.failure("Remote file path is required", 
                                             new IllegalArgumentException("remoteFilePath parameter missing"));
        }
        
        return SftpOperationResult.success(null, getCommandName());
    }
    
    @Override
    public SftpOperationResult execute(SftpConnection connection, Map<String, Object> parameters) {
        LocalDateTime startTime = LocalDateTime.now();
        
        // Validate parameters
        SftpOperationResult validation = validateParameters(parameters);
        if (!validation.isSuccessful()) {
            return validation;
        }
        
        String remoteFilePath = (String) parameters.get("remoteFilePath");
        boolean includeMetadata = Boolean.parseBoolean(String.valueOf(parameters.getOrDefault("includeMetadata", "true")));
        
        logger.debug("Starting SFTP download: {}", remoteFilePath);
        
        try {
            ChannelSftp channel = connection.getChannel();
            
            // Check if file exists and get metadata
            SftpATTRS fileAttrs;
            try {
                fileAttrs = channel.lstat(remoteFilePath);
            } catch (Exception e) {
                long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                String errorMessage = "File not found or inaccessible: " + remoteFilePath;
                logger.error("SFTP download failed - file not found: {}", remoteFilePath);
                return SftpOperationResult.failure(errorMessage, e, getCommandName(), duration);
            }
            
            // Check if it's a regular file
            if (fileAttrs.isDir()) {
                long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                String errorMessage = "Cannot download directory: " + remoteFilePath;
                logger.error("SFTP download failed - target is directory: {}", remoteFilePath);
                return SftpOperationResult.failure(errorMessage, 
                                                 new IllegalArgumentException("Target is a directory"), 
                                                 getCommandName(), duration);
            }
            
            long fileSize = fileAttrs.getSize();
            logger.debug("Downloading file: {} ({} bytes)", remoteFilePath, fileSize);
            
            // Download file content
            byte[] fileContent;
            try (InputStream inputStream = channel.get(remoteFilePath);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                
                fileContent = outputStream.toByteArray();
                
                // Verify download size
                if (fileContent.length != fileSize) {
                    logger.warn("Downloaded size ({}) differs from remote size ({}) for file: {}", 
                               fileContent.length, fileSize, remoteFilePath);
                }
                
                logger.debug("Successfully downloaded: {} ({} bytes)", remoteFilePath, fileContent.length);
                
            } catch (Exception e) {
                long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                String errorMessage = "Download failed: " + e.getMessage();
                logger.error("SFTP download failed during transfer: {}", e.getMessage(), e);
                return SftpOperationResult.failure(errorMessage, e, getCommandName(), duration);
            }
            
            // Build result data
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("fileContent", fileContent);
            resultData.put("fileName", java.nio.file.Paths.get(remoteFilePath).getFileName().toString());
            resultData.put("remoteFilePath", remoteFilePath);
            resultData.put("fileSize", (long) fileContent.length);
            resultData.put("bytesTransferred", (long) fileContent.length);
            resultData.put("filesProcessed", 1);
            
            if (includeMetadata) {
                resultData.put("remoteFileSize", fileSize);
                resultData.put("lastModified", fileAttrs.getMtimeString());
                resultData.put("permissions", fileAttrs.getPermissionsString());
                resultData.put("uid", fileAttrs.getUId());
                resultData.put("gid", fileAttrs.getGId());
            }
            
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            logger.info("SFTP download completed: {} ({} bytes in {} ms)", 
                       remoteFilePath, fileContent.length, duration);
            
            return SftpOperationResult.success(resultData, getCommandName(), duration);
            
        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            String errorMessage = "SFTP download operation failed: " + e.getMessage();
            logger.error("SFTP download operation failed: {}", e.getMessage(), e);
            return SftpOperationResult.failure(errorMessage, e, getCommandName(), duration);
        }
    }
}