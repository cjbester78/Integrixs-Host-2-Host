package com.integrixs.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Business-friendly logging service for user-readable flow execution logs
 * Transforms technical logging into clear, business-readable messages
 */
@Component
public class BusinessLogger {
    
    private static final Logger logger = LoggerFactory.getLogger("BUSINESS_FLOW");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    
    private void updateMDC() {
        MDC.put("correlationId", CorrelationContext.getCorrelationId());
        String operationId = CorrelationContext.getOperationId();
        if (operationId != null) {
            MDC.put("operationId", operationId);
        }
        String flowName = CorrelationContext.getFlowName();
        if (flowName != null) {
            MDC.put("flowName", flowName);
        }
        String executionId = CorrelationContext.getExecutionId();
        if (executionId != null) {
            MDC.put("executionId", executionId);
        }
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }

    // Flow Execution Logging
    
    /**
     * Log flow execution start
     */
    public void flowExecutionStarted(String flowName) {
        updateMDC();
        logger.info("{}     Information        Starting async execution for flow {}", getTimestamp(), flowName);
    }

    /**
     * Log message queuing
     */
    public void messageQueuing(String executionId) {
        updateMDC();
        logger.info("{}     Information        Application attempting to send message asynchronously using connection FlowExecution-{}", 
                   getTimestamp(), executionId.substring(0, 8));
        logger.info("{}     Information        Trying to put the message into the processing queue", getTimestamp());
    }

    /**
     * Log async execution start
     */
    public void asyncExecutionStarted() {
        updateMDC();
        logger.info("{}     Information        Starting async execution", getTimestamp());
    }

    /**
     * Log message retrieval from queue
     */
    public void messageRetrievedFromQueue() {
        updateMDC();
        logger.info("{}     Information        The message was successfully retrieved from the processing queue", getTimestamp());
    }

    /**
     * Log execution status change
     */
    public void executionStatusChanged(String status) {
        updateMDC();
        logger.info("{}     Information        Execution status changed to {}", getTimestamp(), status);
    }

    /**
     * Log flow step execution start
     */
    public void flowStepsExecutionStarted(String flowName) {
        updateMDC();
        logger.info("{}     Information        Executing flow steps for {}", getTimestamp(), flowName);
    }

    // Sender Adapter Logging

    /**
     * Log sender adapter processing start
     */
    public void senderAdapterStarted(String adapterName) {
        updateMDC();
        logger.info("{}     Information        Processing sender adapter {}", getTimestamp(), adapterName);
    }

    /**
     * Log connection to source directory
     */
    public void connectingToSourceDirectory(String sourceDirectory) {
        updateMDC();
        logger.info("{}     Information        Retrieving File from Source directory \"{}\"", getTimestamp(), sourceDirectory);
    }

    /**
     * Log file collection
     */
    public void fileCollected(String filename) {
        updateMDC();
        logger.info("{}     Information        File collected: {}", getTimestamp(), filename);
    }

    /**
     * Log multiple files collected
     */
    public void filesCollected(List<String> filenames) {
        updateMDC();
        for (String filename : filenames) {
            logger.info("{}     Information        File collected: {}", getTimestamp(), filename);
        }
    }

    /**
     * Log mapping execution
     */
    public void mappingExecution(String mappingName) {
        updateMDC();
        logger.info("{}     Information        Executing Mapping '{}'", getTimestamp(), mappingName);
    }

    /**
     * Log adapter processing start
     */
    public void adapterProcessingStarted() {
        updateMDC();
        logger.info("{}     Information        Request message entering the adapter processing with user system", getTimestamp());
    }

    /**
     * Log adapter execution
     */
    public void adapterExecution(String adapterName, String adapterType) {
        updateMDC();
        logger.info("{}     Information        Executing {} adapter {} operation for: {}", 
                   getTimestamp(), adapterType, adapterType.toLowerCase(), adapterName);
    }

    /**
     * Log adapter execution completed
     */
    public void adapterExecutionCompleted() {
        updateMDC();
        logger.info("{}     Information        Adapter execution completed successfully", getTimestamp());
    }

    /**
     * Log file archival
     */
    public void fileArchived(String filename, String archiveDirectory) {
        updateMDC();
        logger.info("{}     Information        File {} has successfully been archived in directory {}", 
                   getTimestamp(), filename, archiveDirectory);
    }

    /**
     * Log file deletion
     */
    public void fileDeleted(String filename) {
        updateMDC();
        logger.info("{}     Information        File {} has successfully been deleted", getTimestamp(), filename);
    }

    /**
     * Log file processing completed
     */
    public void fileProcessingCompleted() {
        updateMDC();
        logger.info("{}     Information        File Processing completed", getTimestamp());
    }

    /**
     * Log message status change
     */
    public void messageStatusChanged(String status) {
        updateMDC();
        String friendlyStatus = convertStatusToFriendly(status);
        logger.info("{}     Information        Message status set to {}", getTimestamp(), friendlyStatus);
    }

    /**
     * Log start processing with files
     */
    public void startProcessingWithFiles(int fileCount, String processingType) {
        updateMDC();
        logger.info("{}     Information        START processing using {} files from trigger data for {} processing", 
                   getTimestamp(), fileCount, processingType);
    }

    /**
     * Log step completion
     */
    public void stepCompleted(String stepName) {
        updateMDC();
        logger.info("{}     Information        COMPLETE: Step completed successfully {} completed", getTimestamp(), stepName);
    }

    // Parallel Processing Logging

    /**
     * Log parallel split start
     */
    public void parallelSplitStarted() {
        updateMDC();
        logger.info("{}     Information        PARALLEL: Splitting execution into parallel paths", getTimestamp());
    }

    /**
     * Log parallel distribution ready
     */
    public void parallelDistributionReady(int fileCount, int pathCount) {
        updateMDC();
        logger.info("{}     Information        Parallel split node ready to distribute {} files across {} execution paths", 
                   getTimestamp(), fileCount, pathCount);
    }

    /**
     * Log parallel split completion
     */
    public void parallelSplitCompleted() {
        updateMDC();
        logger.info("{}     Information        COMPLETE: Step completed successfully for Parallel split", getTimestamp());
    }

    /**
     * Log branch message sent
     */
    public void branchMessageSent(int branchNumber, String targetAdapter) {
        updateMDC();
        logger.info("{}     Information        Branch {} message sent to {}", getTimestamp(), branchNumber, targetAdapter);
    }

    // Receiver Adapter Logging

    /**
     * Log receiver adapter file received
     */
    public void receiverAdapterFileReceived(String adapterName) {
        updateMDC();
        logger.info("{}     Information        File has been received by the receiver adapter {}", getTimestamp(), adapterName);
    }

    /**
     * Log receiver adapter processing
     */
    public void receiverAdapterProcessing() {
        updateMDC();
        logger.info("{}     Information        Request message entering the adapter processing with user system", getTimestamp());
    }

    /**
     * Log receiver message processing completed
     */
    public void receiverProcessingCompleted() {
        updateMDC();
        logger.info("{}     Information        Message Processing completed", getTimestamp());
    }

    /**
     * Log file delivery to target
     */
    public void fileDeliveredToTarget(String targetDirectory, String filename) {
        updateMDC();
        logger.info("{}     Information        Message delivered to {} with filename {}", getTimestamp(), targetDirectory, filename);
    }

    /**
     * Log multiple files delivered to target
     */
    public void filesDeliveredToTarget(String targetDirectory, List<String> filenames) {
        updateMDC();
        for (String filename : filenames) {
            logger.info("{}     Information        Message delivered to {} with filename {}", getTimestamp(), targetDirectory, filename);
        }
    }

    /**
     * Log connecting to target directory
     */
    public void connectingToTargetDirectory(String targetDirectory) {
        updateMDC();
        logger.info("{}     Information        Connecting to target directory {}", getTimestamp(), targetDirectory);
    }

    /**
     * Log file placed in target directory
     */
    public void filePlacedInTargetDirectory(String filename, String targetDirectory) {
        updateMDC();
        logger.info("{}     Information        File {} has successfully been placed in the target directory {}", 
                   getTimestamp(), filename, targetDirectory);
    }

    /**
     * Log branch message status
     */
    public void branchMessageStatus(int branchNumber, String status) {
        updateMDC();
        String friendlyStatus = convertStatusToFriendly(status);
        logger.info("{}     Information        Branch {} Message status set to {}", getTimestamp(), branchNumber, friendlyStatus);
    }

    // Utility Operations Logging

    /**
     * Log utility operation
     */
    public void utilityOperation(int branchNumber, String utilityName) {
        updateMDC();
        logger.info("{}     Information        Branch {} message sent to utility {}", getTimestamp(), branchNumber, utilityName);
    }

    /**
     * Log unzip operation
     */
    public void fileUnzipped(int branchNumber, int fileCount) {
        updateMDC();
        logger.info("{}     Information        Branch {} file Unzipped - {} files extracted", getTimestamp(), branchNumber, fileCount);
    }

    // Final Flow Logging

    /**
     * Log flow execution completed
     */
    public void flowExecutionCompleted() {
        updateMDC();
        logger.info("{}     Information        Flow execution completed successfully", getTimestamp());
    }

    // Helper Methods

    /**
     * Convert technical status to user-friendly status
     */
    private String convertStatusToFriendly(String status) {
        switch (status.toUpperCase()) {
            case "DLVD":
                return "Delivered";
            case "RUNNING":
                return "Running";
            case "COMPLETED":
                return "Delivered";
            case "FAILED":
                return "Failed";
            case "PENDING":
                return "Pending";
            case "DELIVERED":
                return "Delivered";
            default:
                return status;
        }
    }

    // Adapter-specific detailed logging

    /**
     * Log sender adapter started with details
     */
    public void senderAdapterStartedDetailed(String adapterName) {
        updateMDC();
        logger.info("{}     Information        Sender adapter {} started", getTimestamp(), adapterName);
    }

    /**
     * Log receiver adapter started with details
     */
    public void receiverAdapterStartedDetailed(String adapterName) {
        updateMDC();
        logger.info("{}     Information        Receiver adapter {} started", getTimestamp(), adapterName);
    }

    /**
     * Log adapter connection status
     */
    public void adapterConnectionStatus(String adapterName, String status, String details) {
        updateMDC();
        logger.info("{}     Information        Adapter {} connection {}: {}", getTimestamp(), adapterName, status, details);
    }

    /**
     * Log file processing statistics
     */
    public void fileProcessingStats(int fileCount, long totalSize) {
        updateMDC();
        logger.info("{}     Information        Processed {} files, total size: {} bytes", getTimestamp(), fileCount, totalSize);
    }
}