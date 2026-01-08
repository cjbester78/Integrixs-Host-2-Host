package com.integrixs.adapters.execution;

// Import proper adapters from core module
import com.integrixs.adapters.file.FileSenderAdapter;
import com.integrixs.adapters.file.FileReceiverAdapter;
import com.integrixs.adapters.sftp.SftpAdapter;
import com.integrixs.adapters.sftp.SftpOperationResult;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive flow execution example showing step-by-step logging
 * This demonstrates how to execute a complete flow with detailed monitoring
 */
public class FlowAdapterExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowAdapterExecutor.class);
    
    /**
     * Execute a complete file transfer flow with comprehensive logging
     * 
     * Flow: Sender File Adapter -> Unzip Utility -> Receiver SFTP Adapter
     */
    public void executeFileTransferFlow(String executionId, Adapter senderAdapter, 
                                      Adapter receiverAdapter, String localDirectory) {
        
        // Set execution context in MDC for all logs
        MDC.put("executionId", executionId);
        MDC.put("flowId", UUID.randomUUID().toString());
        MDC.put("flowName", "File Transfer Flow");
        MDC.put("logCategory", "FLOW_EXECUTION");
        
        try {
            logger.info("=== STARTING FLOW EXECUTION ===");
            logger.info("Execution ID: {}", executionId);
            logger.info("Flow: Sender File Adapter -> Unzip Utility -> Receiver SFTP Adapter");
            logger.info("Local directory: {}", localDirectory);
            
            // STEP 1: Execute Sender File Adapter
            logger.info("=== FLOW STEP 1: SENDER FILE ADAPTER ===");
            
            FileSenderAdapter fileSenderAdapter = new FileSenderAdapter();
            Map<String, Object> context = new HashMap<>();
            FlowExecutionStep senderStep = new FlowExecutionStep();
            senderStep.setStepName("File Sender");
            senderStep.setAdapterId(senderAdapter.getId());
            
            Map<String, Object> senderResult = fileSenderAdapter.executeInternal(senderAdapter, context, senderStep);
            
            if (senderResult.containsKey("error")) {
                logger.error("Sender adapter failed: {}", senderResult.get("error"));
                return;
            }
            
            int filesDiscovered = (Integer) senderResult.getOrDefault("filesDiscovered", 0);
            int successCount = (Integer) senderResult.getOrDefault("successCount", 0);
            
            if (filesDiscovered == 0) {
                logger.info("No files found for processing. Flow execution completed.");
                return;
            }
            
            logger.info("✓ Files discovered: {}, successfully read: {}", filesDiscovered, successCount);
                
            logger.info("Sender processing complete: {}/{} files successful", 
                       successCount, filesDiscovered);
            
            if (successCount == 0) {
                logger.error("No files processed successfully. Aborting flow execution.");
                return;
            }
            
            // STEP 2: Data passing between flow steps
            logger.info("=== FLOW DATA TRANSFER ===");
            logger.info("Passing {} processed files to receiver adapter", successCount);
            
            // STEP 3: Execute Receiver SFTP Adapter
            logger.info("=== FLOW STEP 2: RECEIVER SFTP ADAPTER ===");
            SftpAdapter receiverSftpAdapter = new SftpAdapter(receiverAdapter);
            
            // Initialize SFTP adapter
            receiverSftpAdapter.initialize();
            
            // Get files from context (set by FileSenderAdapter)
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> filesToProcess = 
                (java.util.List<Map<String, Object>>) context.getOrDefault("filesToProcess", new java.util.ArrayList<>());
            
            int uploadedFiles = 0;
            for (Map<String, Object> fileInfo : filesToProcess) {
                if ("READ_SUCCESS".equals(fileInfo.get("status"))) {
                    
                    // Determine remote file path
                    String fileName = (String) fileInfo.get("fileName");
                    String remoteFilePath = "/receiver/" + fileName;
                    
                    logger.info("Uploading file {}/{}: {}", uploadedFiles + 1, successCount, fileName);
                    
                    // Get file content and original path
                    byte[] fileContent = (byte[]) fileInfo.get("fileContent");
                    String originalFilePath = (String) fileInfo.get("originalFilePath");
                    
                    // Upload file with execution context
                    SftpOperationResult uploadResult = receiverSftpAdapter.uploadFileContent(
                        fileContent, remoteFilePath, executionId);
                    
                    if (uploadResult.getStatus().name().equals("SUCCESS")) {
                        uploadedFiles++;
                        logger.info("✓ Successfully uploaded: {}", fileName);
                    } else {
                        logger.error("✗ Failed to upload: {} - {}", fileName, 
                                   uploadResult.getErrorMessage());
                    }
                }
            }
            
            // STEP 4: Flow completion summary
            logger.info("=== FLOW EXECUTION COMPLETE ===");
            logger.info("✓ Sender files discovered: {}", discoveredFiles.size());
            logger.info("✓ Sender files processed: {}", successfulFiles);
            logger.info("✓ Receiver files uploaded: {}", uploadedFiles);
            logger.info("✓ Success rate: {:.1f}%", (uploadedFiles * 100.0) / discoveredFiles.size());
            logger.info("✓ Flow execution completed successfully");
            
            // Cleanup
            receiverSftpAdapter.cleanup();
            
        } catch (Exception e) {
            logger.error("=== FLOW EXECUTION FAILED ===");
            logger.error("✗ Flow execution failed with error: {}", e.getMessage());
            logger.error("✗ Exception type: {}", e.getClass().getSimpleName());
            if (logger.isDebugEnabled()) {
                logger.error("✗ Full stack trace:", e);
            }
            throw new RuntimeException("Flow execution failed", e);
            
        } finally {
            // Clear MDC context
            MDC.remove("executionId");
            MDC.remove("flowId");
            MDC.remove("flowName");
            MDC.remove("logCategory");
        }
    }
    
    /**
     * Execute an sender SFTP -> File processing flow
     */
    public void executeSftpToFileFlow(String executionId, Adapter sftpAdapter, 
                                     Adapter fileAdapter, String remoteDirectory) {
        
        // Set execution context
        MDC.put("executionId", executionId);
        MDC.put("flowId", UUID.randomUUID().toString());
        MDC.put("flowName", "SFTP to File Processing Flow");
        MDC.put("logCategory", "FLOW_EXECUTION");
        
        try {
            logger.info("=== STARTING SFTP TO FILE FLOW ===");
            logger.info("Execution ID: {}", executionId);
            logger.info("Flow: Sender SFTP Adapter -> File Processing Adapter");
            logger.info("Remote directory: {}", remoteDirectory);
            
            // STEP 1: Execute Sender SFTP Adapter
            logger.info("=== FLOW STEP 1: SENDER SFTP ADAPTER ===");
            SftpAdapter senderSftpAdapter = new SftpAdapter(sftpAdapter);
            senderSftpAdapter.initialize();
            
            // List remote files
            var remoteFiles = senderSftpAdapter.listRemoteFiles(remoteDirectory);
            logger.info("Found {} files in remote directory", remoteFiles.size());
            
            if (remoteFiles.isEmpty()) {
                logger.info("No files found for download. Flow execution completed.");
                return;
            }
            
            // Download files
            int downloadedFiles = 0;
            for (var remoteFile : remoteFiles) {
                Path localFilePath = Path.of("/tmp/downloads/" + remoteFile.getFileName());
                
                logger.info("Downloading file {}/{}: {}", downloadedFiles + 1, 
                           remoteFiles.size(), remoteFile.getFileName());
                
                SftpOperationResult downloadResult = senderSftpAdapter.downloadFile(
                    remoteFile.getFullPath(), localFilePath, executionId);
                
                if (downloadResult.getStatus().name().equals("SUCCESS")) {
                    downloadedFiles++;
                    logger.info("✓ Successfully downloaded: {}", remoteFile.getFileName());
                } else {
                    logger.error("✗ Failed to download: {} - {}", remoteFile.getFileName(),
                               downloadResult.getErrorMessage());
                }
            }
            
            logger.info("Download phase complete: {}/{} files successful", 
                       downloadedFiles, remoteFiles.size());
            
            // STEP 2: Execute File Processing Adapter (using FileSenderAdapter)
            logger.info("=== FLOW STEP 2: FILE PROCESSING ADAPTER ===");
            FileSenderAdapter fileProcessingAdapter = new FileSenderAdapter();
            FlowExecutionStep processingStep = new FlowExecutionStep();
            processingStep.setStepName("File Processing");
            processingStep.setAdapterId(fileAdapter.getId());
            
            Map<String, Object> processingContext = new HashMap<>();
            Map<String, Object> processingResult = fileProcessingAdapter.executeInternal(fileAdapter, processingContext, processingStep);
            
            int processedFilesCount = (Integer) processingResult.getOrDefault("successCount", 0);
            
            if (processedFilesCount > 0) {
                logger.info("File processing complete: {} files successful", 
                           processedFilesCount);
            }
            
            // STEP 3: Flow completion
            logger.info("=== SFTP TO FILE FLOW COMPLETE ===");
            logger.info("✓ Remote files discovered: {}", remoteFiles.size());
            logger.info("✓ Files downloaded: {}", downloadedFiles);
            logger.info("✓ Files processed: {}", localFiles.size());
            logger.info("✓ Flow execution completed successfully");
            
            senderSftpAdapter.cleanup();
            
        } catch (Exception e) {
            logger.error("=== SFTP TO FILE FLOW FAILED ===");
            logger.error("✗ Flow execution failed: {}", e.getMessage());
            throw new RuntimeException("SFTP to File flow execution failed", e);
            
        } finally {
            // Clear MDC context
            MDC.remove("executionId");
            MDC.remove("flowId");
            MDC.remove("flowName");
            MDC.remove("logCategory");
        }
    }
}