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
        
        String targetDirectory = AdapterConfigUtil.getStringConfig(config, "targetDirectory", true, null);
        logger.info("Configuration - Target Directory: {}", targetDirectory);
        
        ChannelSftp sftpChannel = null;
        
        try {
            // Enhance configuration with SSH key data if needed
            Map<String, Object> enhancedConfig = enhanceSftpConfigWithSshKey(config);

            // Create SFTP connection using reusable utility with enhanced configuration
            sftpChannel = SftpConnectionUtil.createSftpConnection(enhancedConfig);
            logger.info("SFTP connection established successfully");

            // Phase 3.1: Ensure remote directory exists (if configured)
            ensureRemoteDirectoryExists(sftpChannel, targetDirectory, config);

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

                    // Phase 3.4: Generate output filename based on configured mode
                    String outputFileName = generateSftpOutputFileName(fileName, config);

                    // Create temporary local file for upload
                    Path tempFile = Files.createTempFile("sftp_upload_", fileName);
                    Files.write(tempFile, fileContent);

                    // Phase 3.2 & 3.3: Upload file with configured options
                    String remoteFilePath = uploadFileToRemote(sftpChannel, tempFile, targetDirectory,
                                                               outputFileName, fileContent.length, config);

                    // Verify upload by checking remote file size
                    long remoteSize = sftpChannel.lstat(remoteFilePath).getSize();
                    long localSize = fileContent.length;
                    
                    if (remoteSize == localSize) {
                        Map<String, Object> uploadResult = new HashMap<>();
                        uploadResult.put("fileName", fileName);
                        uploadResult.put("outputFileName", outputFileName);
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

                        logger.info("Successfully uploaded: {} -> {} ({} bytes) to {}", fileName, outputFileName, localSize, remoteFilePath);
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
            result.put("targetDirectory", targetDirectory);
            
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
        AdapterConfigUtil.validateRequiredString(config, "targetDirectory", adapterType);
        
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

    // ==================================================================================
    // PHASE 3: SFTP RECEIVER REMOTE OPERATIONS
    // ==================================================================================

    /**
     * Phase 3.1: Ensure remote directory exists, creating it if configured.
     *
     * @param sftpChannel Active SFTP channel
     * @param targetDirectory Target directory path
     * @param config Adapter configuration
     * @throws Exception if directory doesn't exist and creation fails
     */
    private void ensureRemoteDirectoryExists(ChannelSftp sftpChannel, String targetDirectory,
                                            Map<String, Object> config) throws Exception {
        try {
            // Try to change to target directory
            sftpChannel.cd(targetDirectory);
            logger.debug("Remote directory exists: {}", targetDirectory);

        } catch (com.jcraft.jsch.SftpException e) {
            // Directory doesn't exist
            boolean createRemoteDirectory = AdapterConfigUtil.getBooleanConfig(config, "createRemoteDirectory", false);

            if (!createRemoteDirectory) {
                throw new RuntimeException("Remote directory does not exist and createRemoteDirectory is disabled: " + targetDirectory);
            }

            logger.info("Creating remote directory: {}", targetDirectory);
            createRemoteDirectoryRecursive(sftpChannel, targetDirectory);
            logger.info("Successfully created remote directory: {}", targetDirectory);
        }
    }

    /**
     * Recursively create remote directory and all parent directories.
     *
     * @param sftpChannel Active SFTP channel
     * @param directoryPath Directory path to create
     * @throws Exception if creation fails
     */
    private void createRemoteDirectoryRecursive(ChannelSftp sftpChannel, String directoryPath) throws Exception {
        String[] pathParts = directoryPath.split("/");
        StringBuilder currentPath = new StringBuilder();

        for (String part : pathParts) {
            if (part.isEmpty()) {
                currentPath.append("/");
                continue;
            }

            if (currentPath.length() > 0 && !currentPath.toString().endsWith("/")) {
                currentPath.append("/");
            }
            currentPath.append(part);

            try {
                sftpChannel.cd(currentPath.toString());
            } catch (com.jcraft.jsch.SftpException e) {
                // Directory doesn't exist, create it
                try {
                    sftpChannel.mkdir(currentPath.toString());
                    logger.debug("Created remote directory: {}", currentPath);
                } catch (com.jcraft.jsch.SftpException mkdirEx) {
                    // Might fail if directory was created by another process
                    logger.debug("mkdir failed for {}, attempting to continue: {}", currentPath, mkdirEx.getMessage());
                }
            }
        }
    }

    /**
     * Phase 3.2 & 3.3: Upload file to remote with temp file and permissions support.
     *
     * @param sftpChannel Active SFTP channel
     * @param localTempFile Local temporary file to upload
     * @param targetDirectory Remote target directory
     * @param fileName Final filename on remote
     * @param expectedSize Expected file size for logging
     * @param config Adapter configuration
     * @return Final remote file path
     * @throws Exception if upload fails
     */
    private String uploadFileToRemote(ChannelSftp sftpChannel, Path localTempFile, String targetDirectory,
                                     String fileName, long expectedSize, Map<String, Object> config) throws Exception {
        // Phase 3.2: Check if temporary file upload is enabled
        boolean useTemporaryFileName = AdapterConfigUtil.getBooleanConfig(config, "useTemporaryFileName", false);
        String temporaryFileSuffix = AdapterConfigUtil.getStringConfig(config, "temporaryFileSuffix", false, ".tmp");

        String finalRemotePath = targetDirectory + "/" + fileName;

        if (useTemporaryFileName) {
            // Upload to temporary filename, then rename
            String tempRemotePath = targetDirectory + "/" + fileName + temporaryFileSuffix;

            logger.debug("Uploading to temp file: {}", tempRemotePath);
            sftpChannel.put(localTempFile.toString(), tempRemotePath);

            logger.debug("Renaming {} to {}", tempRemotePath, finalRemotePath);
            sftpChannel.rename(tempRemotePath, finalRemotePath);

            logger.debug("Atomic upload completed via temp file for: {}", fileName);

        } else {
            // Direct upload (default behavior)
            sftpChannel.put(localTempFile.toString(), finalRemotePath);
            logger.debug("Direct upload completed for: {}", fileName);
        }

        // Phase 3.3: Set file permissions if configured
        setRemoteFilePermissions(sftpChannel, finalRemotePath, config);

        return finalRemotePath;
    }

    /**
     * Phase 3.3: Set remote file permissions using chmod.
     *
     * @param sftpChannel Active SFTP channel
     * @param remoteFilePath Remote file path
     * @param config Adapter configuration
     */
    private void setRemoteFilePermissions(ChannelSftp sftpChannel, String remoteFilePath,
                                         Map<String, Object> config) {
        try {
            String permissions = AdapterConfigUtil.getStringConfig(config, "remoteFilePermissions", false, null);

            if (permissions == null || permissions.trim().isEmpty()) {
                return; // No permissions configured
            }

            // Parse octal permission string (e.g., "644", "755")
            int permissionValue = Integer.parseInt(permissions, 8);

            sftpChannel.chmod(permissionValue, remoteFilePath);
            logger.debug("Set remote file permissions {} for: {}", permissions, remoteFilePath);

        } catch (NumberFormatException e) {
            logger.error("Invalid permission format '{}', must be octal (e.g., 644, 755)",
                        config.get("remoteFilePermissions"));
        } catch (com.jcraft.jsch.SftpException e) {
            logger.warn("Failed to set file permissions for {}: {} (server may not support chmod)",
                       remoteFilePath, e.getMessage());
            // Don't fail upload - some SFTP servers don't support chmod
        } catch (Exception e) {
            logger.warn("Error setting file permissions for {}: {}", remoteFilePath, e.getMessage());
            // Don't fail upload - permissions are optional
        }
    }

    /**
     * Phase 3.4: Generate SFTP output filename based on configured mode.
     * Reuses same logic as FILE receiver for consistency.
     *
     * @param originalFileName Original filename from sender
     * @param config Adapter configuration
     * @return Generated output filename
     */
    private String generateSftpOutputFileName(String originalFileName, Map<String, Object> config) {
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
     */
    private String generateTimestampedFilename(String originalFileName) {
        int dotIndex = originalFileName.lastIndexOf('.');
        String nameWithoutExt = dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
        String extension = dotIndex > 0 ? originalFileName.substring(dotIndex) : "";

        String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());

        return nameWithoutExt + "_" + timestamp + extension;
    }

    /**
     * Apply custom filename pattern with variable substitution.
     * Supported variables same as FILE receiver:
     * - {original_name}: Original filename without extension
     * - {timestamp}: yyyyMMddHHmmss
     * - {date}: yyyyMMdd
     * - {extension}: File extension with dot
     * - {uuid}: Random UUID
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
}