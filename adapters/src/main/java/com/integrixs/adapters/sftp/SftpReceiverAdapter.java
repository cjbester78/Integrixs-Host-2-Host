package com.integrixs.adapters.sftp;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.util.AdapterConfigUtil;
import com.integrixs.core.util.SftpConnectionUtil;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import com.integrixs.shared.model.SshKey;
import com.integrixs.core.repository.SshKeyRepository;
import com.jcraft.jsch.ChannelSftp;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * SFTP Receiver Adapter - Uploads files to remote SFTP server only.
 * 
 * Single Responsibility: Only upload files to remote SFTP directory.
 * Gets file content from context (provided by sender adapter).
 * 
 * This follows the corrected adapter pattern where:
 * - SFTP Sender: Collects files locally (handled by SftpSenderAdapter)
 * - SFTP Receiver: Puts files to remote SFTP server (this class)
 */
@Component
public class SftpReceiverAdapter extends AbstractAdapterExecutor {
    
    private final SshKeyRepository sshKeyRepository;
    
    @Autowired
    public SftpReceiverAdapter(SshKeyRepository sshKeyRepository) {
        this.sshKeyRepository = sshKeyRepository;
    }
    
    @Override
    public String getSupportedType() {
        return "SFTP";
    }
    
    @Override
    public String getSupportedDirection() {
        return "RECEIVER";
    }
    
    @Override
    protected Map<String, Object> executeInternal(Adapter adapter, Map<String, Object> context, 
                                                 FlowExecutionStep step) {
        
        logger.info("Direction: RECEIVER (uploading to remote SFTP server)");
        
        // Get files from context (passed from sender processing)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filesToProcess = (List<Map<String, Object>>) 
            context.getOrDefault("filesToProcess", new ArrayList<>());
        
        if (filesToProcess.isEmpty()) {
            logger.warn("No files provided for SFTP receiver processing");
            return createSuccessResult(0, 0, 0L, "No files to upload");
        }
        
        logger.info("Processing {} files for SFTP upload", filesToProcess.size());
        
        // Get and validate SFTP configuration
        Map<String, Object> config = adapter.getConfiguration();
        validateSftpReceiverConfiguration(config);
        
        String remoteDirectory = AdapterConfigUtil.getStringConfig(config, "remoteDirectory", true, null);
        logger.info("Configuration - Remote Directory: {}", remoteDirectory);
        
        ChannelSftp sftpChannel = null;
        
        try {
            // Enhance configuration with SSH key data if needed
            Map<String, Object> enhancedConfig = enhanceSftpConfigWithSshKey(config);
            
            // Create SFTP connection using reusable utility with enhanced configuration
            sftpChannel = SftpConnectionUtil.createSftpConnection(enhancedConfig);
            logger.info("SFTP connection established successfully");
            
            // Upload all files to remote directory
            List<Map<String, Object>> uploadedFiles = new ArrayList<>();
            long totalBytes = 0;
            int successCount = 0;
            int errorCount = 0;
            
            for (Map<String, Object> fileData : filesToProcess) {
                String fileName = (String) fileData.get("fileName");
                byte[] fileContent = (byte[]) fileData.get("fileContent");
                
                try {
                    if (fileContent == null) {
                        logger.error("File content is null for file: {}", fileName);
                        errorCount++;
                        continue;
                    }
                    
                    // Create temporary file for upload
                    Path tempFile = Files.createTempFile("sftp_upload_", fileName);
                    Files.write(tempFile, fileContent);
                    
                    // Upload file to remote directory
                    String remoteFilePath = remoteDirectory + "/" + fileName;
                    sftpChannel.put(tempFile.toString(), remoteFilePath);
                    
                    // Verify upload by checking remote file size
                    long remoteSize = sftpChannel.lstat(remoteFilePath).getSize();
                    long localSize = fileContent.length;
                    
                    if (remoteSize == localSize) {
                        Map<String, Object> uploadResult = new HashMap<>();
                        uploadResult.put("fileName", fileName);
                        uploadResult.put("status", "UPLOADED");
                        uploadResult.put("remoteFilePath", remoteFilePath);
                        uploadResult.put("fileSize", localSize);
                        
                        uploadedFiles.add(uploadResult);
                        totalBytes += localSize;
                        successCount++;
                        
                        // Add to step tracking
                        if (step != null) {
                            step.addFileProcessed(fileName, "UPLOADED_TO_SFTP", localSize);
                        }
                        
                        logger.info("Successfully uploaded: {} ({} bytes) to {}", fileName, localSize, remoteFilePath);
                    } else {
                        logger.error("Upload verification failed for {}: local size {} != remote size {}", 
                                   fileName, localSize, remoteSize);
                        
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("fileName", fileName);
                        errorResult.put("status", "VERIFICATION_FAILED");
                        errorResult.put("errorMessage", "Remote file size mismatch");
                        uploadedFiles.add(errorResult);
                        errorCount++;
                    }
                    
                    // Clean up temporary file
                    Files.deleteIfExists(tempFile);
                    
                } catch (Exception e) {
                    logger.error("Error uploading file {}: {}", fileName, e.getMessage(), e);
                    
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("fileName", fileName);
                    errorResult.put("status", "UPLOAD_ERROR");
                    errorResult.put("errorMessage", e.getMessage());
                    uploadedFiles.add(errorResult);
                    errorCount++;
                }
            }
            
            // Create success result
            Map<String, Object> result = createSuccessResult(successCount, errorCount, totalBytes,
                String.format("SFTP receiver completed: %d/%d files uploaded successfully", 
                             successCount, filesToProcess.size()));
            
            result.put("uploadedFiles", uploadedFiles);
            result.put("remoteDirectory", remoteDirectory);
            
            logger.info("✓ Files uploaded: {}/{}", successCount, filesToProcess.size());
            logger.info("✓ Total bytes uploaded: {}", totalBytes);
            
            return result;
            
        } catch (Exception e) {
            logger.error("SFTP receiver execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("SFTP receiver execution failed: " + e.getMessage(), e);
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
        validateSftpReceiverConfiguration(config);
        
        AdapterConfigUtil.logConfigSummary(config, getSupportedType(), getSupportedDirection());
    }
    
    /**
     * Validate SFTP receiver specific configuration.
     * Requires SFTP connection details and remote directory.
     */
    private void validateSftpReceiverConfiguration(Map<String, Object> config) {
        String adapterType = getSupportedType() + " " + getSupportedDirection();
        
        // SFTP receiver needs connection details and remote directory
        AdapterConfigUtil.validateSftpConfig(config, adapterType);
        AdapterConfigUtil.validateRequiredString(config, "remoteDirectory", adapterType);
        
        logger.debug("SFTP receiver configuration validation passed");
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
}