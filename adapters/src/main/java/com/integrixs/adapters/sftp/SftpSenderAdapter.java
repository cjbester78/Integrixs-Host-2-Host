package com.integrixs.adapters.sftp;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.util.AdapterConfigUtil;
import com.integrixs.shared.util.FileUtils;
import com.integrixs.core.util.SftpConnectionUtil;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import com.integrixs.shared.model.SshKey;
import com.integrixs.core.repository.SshKeyRepository;
import com.jcraft.jsch.ChannelSftp;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Vector;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * SFTP Sender Adapter - Downloads files from remote SFTP server.
 * 
 * Single Responsibility: Download files from remote SFTP server directory with optional archiving.
 * This connects to the remote SFTP server and downloads files matching the pattern.
 * 
 * SFTP SENDER flow:
 * 1. Connect to remote SFTP server (host/port/auth)
 * 2. Download files from sourceDirectory matching filePattern  
 * 3. Archive files to archiveDirectory on remote server (optional)
 * 4. Store file content in context for receiver processing
 */
@Component
public class SftpSenderAdapter extends AbstractAdapterExecutor {
    
    private final SshKeyRepository sshKeyRepository;
    
    @Autowired
    public SftpSenderAdapter(SshKeyRepository sshKeyRepository) {
        this.sshKeyRepository = sshKeyRepository;
    }
    
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
        String sourceDirectory = AdapterConfigUtil.getStringConfig(config, "sourceDirectory", true, null);
        String postProcessAction = AdapterConfigUtil.getStringConfig(config, "postProcessAction", false, "ARCHIVE");
        String archiveDirectory = AdapterConfigUtil.getStringConfig(config, "archiveDirectory", false, null);
        
        logger.info("Configuration - Source Directory: {}, File Pattern: {}, Post Process Action: {}", 
                   sourceDirectory, filePattern, postProcessAction);
        
        ChannelSftp sftpChannel = null;
        
        try {
            // Enhance configuration with SSH key data if needed
            Map<String, Object> enhancedConfig = enhanceSftpConfigWithSshKey(config);
            
            // Create SFTP connection with enhanced configuration
            sftpChannel = SftpConnectionUtil.createSftpConnection(enhancedConfig);
            
            // List files in remote directory matching pattern
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> remoteFiles = sftpChannel.ls(sourceDirectory);
            
            List<ChannelSftp.LsEntry> matchingFiles = remoteFiles.stream()
                .filter(entry -> !entry.getAttrs().isDir() && 
                               FileUtils.matchesPattern(entry.getFilename(), filePattern, null))
                .collect(Collectors.toList());
            
            if (matchingFiles.isEmpty()) {
                logger.info("No files found matching pattern '{}' in remote directory '{}'", filePattern, sourceDirectory);
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
                String remoteFilePath = sourceDirectory + "/" + fileName;
                
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
            result.put("sourceDirectory", sourceDirectory);
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
                                    // Implement compression for remote SFTP files
                                    archiveFilePath = compressRemoteFile(sftpChannel, remoteFilePath, archiveFilePath, compressionType);
                                    logger.info("✓ Compressed and archived remote file: {} -> {} ({})", remoteFilePath, archiveFilePath, compressionType);
                                } else {
                                    sftpChannel.rename(remoteFilePath, archiveFilePath);
                                    logger.info("✓ Archived remote file: {} -> {}", remoteFilePath, archiveFilePath);
                                }
                            } else {
                                sftpChannel.rename(remoteFilePath, archiveFilePath);
                                logger.info("✓ Archived remote file: {} -> {}", remoteFilePath, archiveFilePath);
                            }
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
        AdapterConfigUtil.validateRequiredString(config, "sourceDirectory", adapterType);
        
        // Validate port if present
        Integer port = AdapterConfigUtil.getIntegerConfig(config, "port", false, 22);
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException(adapterType + " port must be between 1 and 65535");
        }
        
        logger.debug("SFTP sender configuration validation passed");
    }
    
    /**
     * Enhance SFTP configuration with SSH key data from database when needed.
     * Supports all three authentication types:
     * - USERNAME_PASSWORD: Uses password only
     * - SSH_KEY: Uses SSH key only  
     * - DUAL: Uses both SSH key and password
     */
    private Map<String, Object> enhanceSftpConfigWithSshKey(Map<String, Object> originalConfig) {
        Map<String, Object> enhancedConfig = new HashMap<>(originalConfig);
        
        String authenticationType = (String) originalConfig.getOrDefault("authenticationType", "USERNAME_PASSWORD");
        logger.debug("SFTP authentication type: {}", authenticationType);
        
        // For SSH_KEY or DUAL authentication, retrieve SSH key from database
        if ("SSH_KEY".equals(authenticationType) || "DUAL".equals(authenticationType)) {
            String sshKeyName = (String) originalConfig.get("sshKeyName");
            String sshKeyId = (String) originalConfig.get("sshKeyId");
            
            if (sshKeyName != null && !sshKeyName.trim().isEmpty()) {
                try {
                    logger.debug("Looking up SSH key by name: {}", sshKeyName);
                    Optional<SshKey> sshKeyOpt = sshKeyRepository.findByName(sshKeyName);
                    
                    if (sshKeyOpt.isPresent() && sshKeyOpt.get().isActive()) {
                        SshKey sshKey = sshKeyOpt.get();
                        
                        // Add SSH key data to configuration for SftpConnectionUtil
                        enhancedConfig.put("privateKeyContent", sshKey.getPrivateKey());
                        enhancedConfig.put("publicKeyContent", sshKey.getPublicKey());
                        enhancedConfig.put("keyType", sshKey.getKeyType());
                        
                        logger.info("Successfully loaded SSH key: {} ({}) for SFTP authentication", 
                                   sshKeyName, sshKey.getKeyType());
                    } else {
                        String errorMsg = sshKeyOpt.isEmpty() ? 
                            "SSH key not found: " + sshKeyName :
                            "SSH key is inactive: " + sshKeyName;
                        
                        if ("SSH_KEY".equals(authenticationType)) {
                            throw new IllegalArgumentException(errorMsg + " - SSH key authentication required");
                        } else {
                            // DUAL authentication - log warning and continue with password only
                            logger.warn("{} - falling back to password authentication", errorMsg);
                            enhancedConfig.put("authenticationType", "PASSWORD");
                        }
                    }
                } catch (Exception e) {
                    String errorMsg = "Error retrieving SSH key '" + sshKeyName + "': " + e.getMessage();
                    
                    if ("SSH_KEY".equals(authenticationType)) {
                        throw new RuntimeException(errorMsg);
                    } else {
                        // DUAL authentication - log error and continue with password only
                        logger.error("{} - falling back to password authentication", errorMsg);
                        enhancedConfig.put("authenticationType", "PASSWORD");
                    }
                }
            } else if (sshKeyId != null && !sshKeyId.trim().isEmpty()) {
                try {
                    logger.debug("Looking up SSH key by ID: {}", sshKeyId);
                    Optional<SshKey> sshKeyOpt = sshKeyRepository.findById(UUID.fromString(sshKeyId));
                    
                    if (sshKeyOpt.isPresent() && sshKeyOpt.get().isActive()) {
                        SshKey sshKey = sshKeyOpt.get();
                        
                        // Add SSH key data to configuration
                        enhancedConfig.put("privateKeyContent", sshKey.getPrivateKey());
                        enhancedConfig.put("publicKeyContent", sshKey.getPublicKey());
                        enhancedConfig.put("keyType", sshKey.getKeyType());
                        
                        logger.info("Successfully loaded SSH key: {} ({}) for SFTP authentication", 
                                   sshKey.getName(), sshKey.getKeyType());
                    } else {
                        String errorMsg = sshKeyOpt.isEmpty() ? 
                            "SSH key not found: " + sshKeyId :
                            "SSH key is inactive: " + sshKeyId;
                        
                        if ("SSH_KEY".equals(authenticationType)) {
                            throw new IllegalArgumentException(errorMsg + " - SSH key authentication required");
                        } else {
                            // DUAL authentication - log warning and continue with password only
                            logger.warn("{} - falling back to password authentication", errorMsg);
                            enhancedConfig.put("authenticationType", "PASSWORD");
                        }
                    }
                } catch (Exception e) {
                    String errorMsg = "Error retrieving SSH key '" + sshKeyId + "': " + e.getMessage();
                    
                    if ("SSH_KEY".equals(authenticationType)) {
                        throw new RuntimeException(errorMsg);
                    } else {
                        // DUAL authentication - log error and continue with password only
                        logger.error("{} - falling back to password authentication", errorMsg);
                        enhancedConfig.put("authenticationType", "PASSWORD");
                    }
                }
            } else {
                String errorMsg = "SSH key name or ID is required for SSH key authentication";
                
                if ("SSH_KEY".equals(authenticationType)) {
                    throw new IllegalArgumentException(errorMsg);
                } else {
                    // DUAL authentication - log warning and continue with password only
                    logger.warn("{} - falling back to password authentication", errorMsg);
                    enhancedConfig.put("authenticationType", "PASSWORD");
                }
            }
        }
        
        return enhancedConfig;
    }
    
    /**
     * Compress remote file via SFTP
     */
    private String compressRemoteFile(ChannelSftp sftpChannel, String sourceFile, String targetPath, String compressionType) throws Exception {
        if (compressionType == null || compressionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Compression type cannot be null or empty");
        }
        
        // Create unique temporary local files using UUID to avoid collisions
        String tempDir = System.getProperty("java.io.tmpdir");
        String uniqueId = java.util.UUID.randomUUID().toString();
        String tempSourceFile = tempDir + "/sftp_source_" + uniqueId;
        String tempCompressedFile = tempDir + "/sftp_compressed_" + uniqueId;
        
        // Verify source file exists on remote server
        try {
            @SuppressWarnings("unchecked")
            java.util.Vector<ChannelSftp.LsEntry> files = sftpChannel.ls(sourceFile);
            if (files.isEmpty()) {
                throw new java.io.FileNotFoundException("Source file does not exist on remote server: " + sourceFile);
            }
        } catch (com.jcraft.jsch.SftpException e) {
            throw new java.io.FileNotFoundException("Source file does not exist on remote server: " + sourceFile);
        }
        
        try {
            logger.debug("Starting compression of remote file: {} using {}", sourceFile, compressionType);
            
            // Download file to temporary location
            sftpChannel.get(sourceFile, tempSourceFile);
            logger.debug("Downloaded remote file to temporary location: {}", tempSourceFile);
            
            // Verify download was successful
            java.io.File tempFile = new java.io.File(tempSourceFile);
            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new java.io.IOException("Failed to download file or file is empty: " + sourceFile);
            }
            
            // Compress the file based on type
            String compressedExtension = getCompressionExtension(compressionType);
            String finalTargetPath = targetPath;
            if (!finalTargetPath.toLowerCase().endsWith(compressedExtension)) {
                finalTargetPath += compressedExtension;
            }
            
            switch (compressionType.toUpperCase()) {
                case "GZIP":
                case "GZ":
                    compressFileGzip(tempSourceFile, tempCompressedFile);
                    logger.debug("GZIP compression completed for: {}", sourceFile);
                    break;
                case "ZIP":
                    String entryName = new java.io.File(sourceFile).getName();
                    compressFileZip(tempSourceFile, tempCompressedFile, entryName);
                    logger.debug("ZIP compression completed for: {}", sourceFile);
                    break;
                default:
                    logger.warn("Unsupported compression type: {}, archiving without compression", compressionType);
                    sftpChannel.rename(sourceFile, targetPath);
                    return targetPath;
            }
            
            // Verify compressed file was created successfully
            java.io.File compressedFile = new java.io.File(tempCompressedFile);
            if (!compressedFile.exists() || compressedFile.length() == 0) {
                throw new java.io.IOException("Compression failed - compressed file not created or empty");
            }
            
            // Upload compressed file to final destination
            sftpChannel.put(tempCompressedFile, finalTargetPath);
            logger.debug("Uploaded compressed file to: {}", finalTargetPath);
            
            // Verify upload was successful before removing original
            try {
                @SuppressWarnings("unchecked")
                java.util.Vector<ChannelSftp.LsEntry> uploadedFiles = sftpChannel.ls(finalTargetPath);
                if (uploadedFiles.isEmpty()) {
                    throw new java.io.IOException("Upload verification failed - compressed file not found at destination");
                }
            } catch (com.jcraft.jsch.SftpException e) {
                throw new java.io.IOException("Upload verification failed - compressed file not accessible at destination");
            }
            
            // Remove original file only after successful compression and upload
            sftpChannel.rm(sourceFile);
            logger.debug("Removed original file: {}", sourceFile);
            
            return finalTargetPath;
            
        } catch (Exception e) {
            logger.error("Error during remote file compression: {} -> {}", sourceFile, targetPath, e);
            throw new RuntimeException("Remote file compression failed: " + e.getMessage(), e);
        } finally {
            // Clean up temporary files
            cleanupTempFile(tempSourceFile);
            cleanupTempFile(tempCompressedFile);
        }
    }
    
    /**
     * Safely clean up temporary file
     */
    private void cleanupTempFile(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            if (file.exists() && !file.delete()) {
                logger.warn("Failed to delete temporary file: {}", filePath);
                // Try to mark for deletion on exit as fallback
                file.deleteOnExit();
            }
        } catch (Exception e) {
            logger.warn("Error cleaning up temporary file {}: {}", filePath, e.getMessage());
        }
    }
    
    /**
     * Compress file using GZIP compression
     */
    private void compressFileGzip(String sourceFile, String targetFile) throws Exception {
        java.io.File source = new java.io.File(sourceFile);
        if (!source.exists()) {
            throw new java.io.FileNotFoundException("Source file not found: " + sourceFile);
        }
        
        try (java.io.FileInputStream fis = new java.io.FileInputStream(source);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile);
             java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(fos)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                gzos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            
            // Ensure all data is flushed
            gzos.finish();
            
            logger.debug("GZIP compression complete: {} bytes -> {} ({})", 
                        totalBytesRead, new java.io.File(targetFile).length(), targetFile);
            
        } catch (java.io.IOException e) {
            // Clean up partially created file on error
            new java.io.File(targetFile).delete();
            throw new RuntimeException("GZIP compression failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Compress file using ZIP compression
     */
    private void compressFileZip(String sourceFile, String targetFile, String entryName) throws Exception {
        java.io.File source = new java.io.File(sourceFile);
        if (!source.exists()) {
            throw new java.io.FileNotFoundException("Source file not found: " + sourceFile);
        }
        
        if (entryName == null || entryName.trim().isEmpty()) {
            entryName = source.getName();
        }
        
        try (java.io.FileInputStream fis = new java.io.FileInputStream(source);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile);
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos)) {
            
            // Set compression level for better performance/size balance
            zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);
            
            java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(entryName);
            zipEntry.setTime(source.lastModified());
            zos.putNextEntry(zipEntry);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            
            zos.closeEntry();
            zos.finish();
            
            logger.debug("ZIP compression complete: {} bytes -> {} ({})", 
                        totalBytesRead, new java.io.File(targetFile).length(), targetFile);
            
        } catch (java.io.IOException e) {
            // Clean up partially created file on error
            new java.io.File(targetFile).delete();
            throw new RuntimeException("ZIP compression failed: " + e.getMessage(), e);
        }
    }
    
    private String getCompressionExtension(String compressionType) {
        switch (compressionType.toUpperCase()) {
            case "GZIP":
            case "GZ":
                return ".gz";
            case "ZIP":
                return ".zip";
            default:
                return "";
        }
    }
}