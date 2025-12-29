package com.integrixs.backend.model;

import com.integrixs.adapters.sftp.SftpOperationResult;

import java.util.List;
import java.util.UUID;

/**
 * Result of SFTP adapter execution
 */
public class SftpAdapterExecutionResult {
    private final UUID executionId;
    private final UUID adapterInterfaceId;
    private final SftpAdapterExecutionStatus status;
    private final List<SftpOperationResult> operationResults;
    private final int successCount;
    private final int failedCount;
    private final long totalBytes;
    private final String errorMessage;
    
    public SftpAdapterExecutionResult(UUID executionId, UUID adapterInterfaceId,
                                      SftpAdapterExecutionStatus status,
                                      List<SftpOperationResult> operationResults,
                                      int successCount, int failedCount, long totalBytes,
                                      String errorMessage) {
        this.executionId = executionId;
        this.adapterInterfaceId = adapterInterfaceId;
        this.status = status;
        this.operationResults = operationResults;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.totalBytes = totalBytes;
        this.errorMessage = errorMessage;
    }
    
    // Getters
    public UUID getExecutionId() { return executionId; }
    public UUID getAdapterInterfaceId() { return adapterInterfaceId; }
    public SftpAdapterExecutionStatus getStatus() { return status; }
    public List<SftpOperationResult> getOperationResults() { return operationResults; }
    public int getSuccessCount() { return successCount; }
    public int getFailedCount() { return failedCount; }
    public long getTotalBytes() { return totalBytes; }
    public String getErrorMessage() { return errorMessage; }
    
    public boolean isSuccess() { return status == SftpAdapterExecutionStatus.SUCCESS; }
    public int getTotalCount() { return successCount + failedCount; }
}