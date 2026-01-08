package com.integrixs.core.adapter.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SFTP upload command implementation.
 * Handles uploading files to remote SFTP server with verification and proper error handling.
 * Follows OOP principles with immutable results and comprehensive logging.
 */
@Component
public class SftpUploadCommand implements SftpOperationCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(SftpUploadCommand.class);
    
    @Override
    public String getCommandName() {
        return "SFTP_UPLOAD";
    }
    
    @Override
    public String getCommandDescription() {
        return "Upload file to remote SFTP server";
    }
    
    @Override
    public boolean supportsOperation(SftpOperationType operationType) {
        return operationType == SftpOperationType.UPLOAD_FILE;
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
        
        byte[] fileContent = (byte[]) parameters.get("fileContent");
        String localFilePath = (String) parameters.get("localFilePath");
        
        if (fileContent == null && (localFilePath == null || localFilePath.trim().isEmpty())) {
            return SftpOperationResult.failure("Either fileContent or localFilePath must be provided", 
                                             new IllegalArgumentException("No file data provided"));
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
        byte[] fileContent = (byte[]) parameters.get("fileContent");
        String localFilePath = (String) parameters.get("localFilePath");
        boolean createDirectories = Boolean.parseBoolean(String.valueOf(parameters.getOrDefault("createDirectories", "true")));
        boolean overwriteExisting = Boolean.parseBoolean(String.valueOf(parameters.getOrDefault("overwriteExisting", "true")));
        boolean verifyUpload = Boolean.parseBoolean(String.valueOf(parameters.getOrDefault("verifyUpload", "true")));
        
        logger.debug("Starting SFTP upload to: {}", remoteFilePath);
        
        List<String> warnings = new ArrayList<>();
        
        try {
            ChannelSftp channel = connection.getChannel();
            
            // Prepare file content
            if (fileContent == null) {
                // Read from local file
                Path localPath = Paths.get(localFilePath);
                if (!Files.exists(localPath)) {
                    long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    String errorMessage = "Local file does not exist: " + localFilePath;
                    logger.error("SFTP upload failed - local file not found: {}", localFilePath);
                    return SftpOperationResult.failure(errorMessage, 
                                                     new IllegalArgumentException("Local file not found"), 
                                                     getCommandName(), duration);
                }
                
                try {
                    fileContent = Files.readAllBytes(localPath);
                    logger.debug("Read {} bytes from local file: {}", fileContent.length, localFilePath);
                } catch (Exception e) {
                    long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    String errorMessage = "Failed to read local file: " + e.getMessage();
                    logger.error("SFTP upload failed - cannot read local file: {}", e.getMessage(), e);
                    return SftpOperationResult.failure(errorMessage, e, getCommandName(), duration);
                }
            }
            
            // Check if remote file exists
            boolean remoteFileExists = false;
            SftpATTRS existingAttrs = null;
            
            try {
                existingAttrs = channel.lstat(remoteFilePath);
                remoteFileExists = true;
                
                if (!overwriteExisting) {
                    long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    String errorMessage = "Remote file already exists and overwrite is disabled: " + remoteFilePath;
                    logger.error("SFTP upload failed - file exists and overwrite disabled: {}", remoteFilePath);
                    return SftpOperationResult.failure(errorMessage, 
                                                     new IllegalArgumentException("File exists, overwrite disabled"), 
                                                     getCommandName(), duration);
                }
                
                if (existingAttrs.isDir()) {
                    long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    String errorMessage = "Target path is a directory: " + remoteFilePath;
                    logger.error("SFTP upload failed - target is directory: {}", remoteFilePath);
                    return SftpOperationResult.failure(errorMessage, 
                                                     new IllegalArgumentException("Target is a directory"), 
                                                     getCommandName(), duration);
                }
                
                warnings.add("Overwriting existing remote file: " + remoteFilePath);
                
            } catch (Exception e) {
                // File doesn't exist, which is fine
                logger.debug("Remote file does not exist (will create): {}", remoteFilePath);
            }
            
            // Create remote directories if needed
            if (createDirectories) {
                String remoteDir = remoteFilePath.substring(0, Math.max(remoteFilePath.lastIndexOf('/'), 0));
                if (!remoteDir.isEmpty()) {
                    createRemoteDirectories(channel, remoteDir);
                }
            }
            
            // Upload the file
            logger.debug("Uploading {} bytes to: {}", fileContent.length, remoteFilePath);
            
            try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
                channel.put(inputStream, remoteFilePath);
                logger.debug("Upload completed for: {}", remoteFilePath);
            } catch (Exception e) {
                long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                String errorMessage = "Upload failed during transfer: " + e.getMessage();
                logger.error("SFTP upload failed during transfer: {}", e.getMessage(), e);
                return SftpOperationResult.failure(errorMessage, e, getCommandName(), duration);
            }
            
            // Verify upload if requested
            if (verifyUpload) {
                try {
                    SftpATTRS uploadedAttrs = channel.lstat(remoteFilePath);
                    long remoteSize = uploadedAttrs.getSize();
                    
                    if (remoteSize != fileContent.length) {
                        warnings.add("Upload verification warning: remote size (" + remoteSize + 
                                   ") differs from local size (" + fileContent.length + ")");
                        logger.warn("Upload verification warning for {}: remote size {} != local size {}", 
                                   remoteFilePath, remoteSize, fileContent.length);
                    } else {
                        logger.debug("Upload verification passed: {} ({} bytes)", remoteFilePath, remoteSize);
                    }
                    
                } catch (Exception e) {
                    warnings.add("Upload verification failed: " + e.getMessage());
                    logger.warn("Upload verification failed for {}: {}", remoteFilePath, e.getMessage());
                }
            }
            
            // Build result data
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("remoteFilePath", remoteFilePath);
            resultData.put("fileName", Paths.get(remoteFilePath).getFileName().toString());
            resultData.put("fileSize", (long) fileContent.length);
            resultData.put("bytesTransferred", (long) fileContent.length);
            resultData.put("filesProcessed", 1);
            resultData.put("overwriteExisting", remoteFileExists);
            resultData.put("directoriesCreated", createDirectories);
            
            if (localFilePath != null) {
                resultData.put("localFilePath", localFilePath);
            }
            
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            logger.info("SFTP upload completed: {} ({} bytes in {} ms)", 
                       remoteFilePath, fileContent.length, duration);
            
            if (warnings.isEmpty()) {
                return SftpOperationResult.success(resultData, getCommandName(), duration);
            } else {
                return SftpOperationResult.successWithWarnings(resultData, getCommandName(), duration, warnings);
            }
            
        } catch (Exception e) {
            long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            String errorMessage = "SFTP upload operation failed: " + e.getMessage();
            logger.error("SFTP upload operation failed: {}", e.getMessage(), e);
            return SftpOperationResult.failure(errorMessage, e, getCommandName(), duration);
        }
    }
    
    /**
     * Create remote directories recursively if they don't exist.
     */
    private void createRemoteDirectories(ChannelSftp channel, String remotePath) {
        if (remotePath == null || remotePath.isEmpty()) {
            return;
        }
        
        try {
            // Check if directory already exists
            channel.lstat(remotePath);
            logger.debug("Remote directory already exists: {}", remotePath);
            return;
            
        } catch (Exception e) {
            // Directory doesn't exist, need to create it
            logger.debug("Creating remote directory: {}", remotePath);
        }
        
        // Create parent directories first
        int lastSlash = remotePath.lastIndexOf('/');
        if (lastSlash > 0) {
            String parentPath = remotePath.substring(0, lastSlash);
            createRemoteDirectories(channel, parentPath);
        }
        
        // Create this directory
        try {
            channel.mkdir(remotePath);
            logger.debug("Created remote directory: {}", remotePath);
        } catch (Exception e) {
            logger.warn("Failed to create remote directory {}: {}", remotePath, e.getMessage());
        }
    }
}