package com.integrixs.core.adapter.sftp;

import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.util.AdapterConfigUtil;
import com.integrixs.core.util.SftpConnectionUtil;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import com.jcraft.jsch.ChannelSftp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class SftpReceiverAdapter extends AbstractAdapterExecutor {
    
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
            // Create SFTP connection using reusable utility
            sftpChannel = SftpConnectionUtil.createSftpConnection(config);
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
}