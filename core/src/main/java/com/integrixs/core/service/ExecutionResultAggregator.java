package com.integrixs.core.service;

import com.integrixs.core.repository.FlowExecutionStepRepository;
import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.FlowExecutionStep;
import com.integrixs.shared.model.DeployedFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for aggregating execution results and updating metrics.
 * Handles file metrics collection, deployment statistics, and result consolidation.
 * Follows OOP principles with immutable result objects and proper encapsulation.
 */
@Service
public class ExecutionResultAggregator {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionResultAggregator.class);
    
    private final FlowExecutionStepRepository stepRepository;
    private final DeployedFlowRepository deployedFlowRepository;
    
    @Autowired
    public ExecutionResultAggregator(FlowExecutionStepRepository stepRepository,
                                   DeployedFlowRepository deployedFlowRepository) {
        this.stepRepository = stepRepository;
        this.deployedFlowRepository = deployedFlowRepository;
    }
    
    /**
     * Immutable step execution result
     */
    public static class StepExecutionResult {
        private final Map<String, Object> outputData;
        private final int filesProcessed;
        private final long bytesProcessed;
        private final boolean successful;
        private final String errorMessage;
        
        public StepExecutionResult(Map<String, Object> outputData, int filesProcessed, 
                                 long bytesProcessed, boolean successful, String errorMessage) {
            this.outputData = new HashMap<>(outputData != null ? outputData : new HashMap<>());
            this.filesProcessed = filesProcessed;
            this.bytesProcessed = bytesProcessed;
            this.successful = successful;
            this.errorMessage = errorMessage;
        }
        
        public Map<String, Object> getOutputData() { 
            return new HashMap<>(outputData); 
        }
        
        public int getFilesProcessed() { return filesProcessed; }
        public long getBytesProcessed() { return bytesProcessed; }
        public boolean isSuccessful() { return successful; }
        public String getErrorMessage() { return errorMessage; }
        
        public static StepExecutionResult success(Map<String, Object> outputData, 
                                                int filesProcessed, long bytesProcessed) {
            return new StepExecutionResult(outputData, filesProcessed, bytesProcessed, true, null);
        }
        
        public static StepExecutionResult failure(String errorMessage) {
            return new StepExecutionResult(null, 0, 0, false, errorMessage);
        }
    }
    
    /**
     * Extract file metrics from step result
     */
    public StepExecutionResult extractStepMetrics(Map<String, Object> stepResult) {
        try {
            int fileCount = 0;
            long totalBytes = 0L;
            
            // Look for various file result patterns
            Object foundFiles = stepResult.get("foundFiles");
            Object processedFiles = stepResult.get("processedFiles");
            Object totalBytesProcessed = stepResult.get("totalBytesProcessed");
            
            // Count files from foundFiles
            if (foundFiles instanceof List) {
                List<?> files = (List<?>) foundFiles;
                fileCount += files.size();
                totalBytes += extractBytesFromFileList(files);
            }
            
            // Count files from processedFiles
            if (processedFiles instanceof List) {
                List<?> files = (List<?>) processedFiles;
                fileCount += files.size();
                totalBytes += extractBytesFromFileList(files);
            }
            
            // Use totalBytesProcessed if available and no bytes calculated from file metadata
            if (totalBytes == 0L && totalBytesProcessed instanceof Number) {
                totalBytes = ((Number) totalBytesProcessed).longValue();
            }
            
            return StepExecutionResult.success(stepResult, fileCount, totalBytes);
            
        } catch (Exception e) {
            logger.warn("Failed to extract file metrics from step result: {}", e.getMessage());
            return StepExecutionResult.success(stepResult, 0, 0);
        }
    }
    
    /**
     * Update step with extracted metrics
     */
    public void updateStepWithMetrics(FlowExecutionStep step, StepExecutionResult result) {
        step.setFilesCount(result.getFilesProcessed());
        step.setBytesProcessed(result.getBytesProcessed());
        step.setOutputData(result.getOutputData());
        
        if (result.getFilesProcessed() > 0 || result.getBytesProcessed() > 0) {
            logger.debug("Updated step {} metrics: {} files, {} bytes", 
                        step.getId(), result.getFilesProcessed(), result.getBytesProcessed());
        }
    }
    
    /**
     * Update execution file metrics by collecting data from execution steps
     */
    public void updateExecutionFileMetrics(FlowExecution execution) {
        try {
            Map<String, Object> statistics = stepRepository.getExecutionStatistics(execution.getId());
            
            if (statistics != null) {
                Integer totalFiles = (Integer) statistics.getOrDefault("total_files_processed", 0);
                Long totalBytes = (Long) statistics.getOrDefault("total_bytes_processed", 0L);
                
                execution.setTotalFilesProcessed(totalFiles);
                execution.setTotalBytesProcessed(totalBytes);
                execution.setFilesSuccessful(totalFiles);
                execution.setFilesFailed(0);
                
                logger.info("Updated execution {} file metrics: {} files, {} bytes", 
                           execution.getId(), totalFiles, totalBytes);
            }
        } catch (Exception e) {
            logger.warn("Failed to update file metrics for execution {}: {}", 
                       execution.getId(), e.getMessage());
        }
    }
    
    /**
     * Update deployment statistics after execution completion
     */
    public void updateDeploymentStatistics(FlowExecution execution, boolean successful) {
        try {
            if (execution.getExecutionContext() == null) {
                return;
            }
            
            Object deploymentIdObj = execution.getExecutionContext().get("deploymentId");
            if (!(deploymentIdObj instanceof UUID)) {
                return;
            }
            
            UUID deploymentId = (UUID) deploymentIdObj;
            Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findById(deploymentId);
            
            if (deployedFlowOpt.isPresent()) {
                DeployedFlow deployedFlow = deployedFlowOpt.get();
                
                // Update statistics
                deployedFlow.recordExecution(execution.getDurationMs(), successful);
                
                if (!successful && execution.getErrorMessage() != null) {
                    deployedFlow.recordError(execution.getErrorMessage());
                } else if (successful && deployedFlow.getConsecutiveFailures() > 0) {
                    deployedFlow.clearErrors();
                }
                
                // Save updated deployment statistics
                deployedFlowRepository.update(deployedFlow);
                
                logger.debug("Updated deployment statistics for deployment: {} (success: {})", 
                           deploymentId, successful);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to update deployment statistics for execution {}: {}", 
                       execution.getId(), e.getMessage());
        }
    }
    
    /**
     * Create execution summary for monitoring
     */
    public Map<String, Object> createExecutionSummary(FlowExecution execution) {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("executionId", execution.getId());
        summary.put("flowId", execution.getFlowId());
        summary.put("flowName", execution.getFlowName());
        summary.put("status", execution.getExecutionStatus());
        summary.put("triggerType", execution.getTriggerType());
        summary.put("startedAt", execution.getStartedAt());
        summary.put("completedAt", execution.getCompletedAt());
        summary.put("durationMs", execution.getDurationMs());
        summary.put("totalFilesProcessed", execution.getTotalFilesProcessed());
        summary.put("totalBytesProcessed", execution.getTotalBytesProcessed());
        summary.put("filesSuccessful", execution.getFilesSuccessful());
        summary.put("filesFailed", execution.getFilesFailed());
        summary.put("errorMessage", execution.getErrorMessage());
        summary.put("retryAttempt", execution.getRetryAttempt());
        
        return summary;
    }
    
    /**
     * Aggregate step results into execution context
     */
    public Map<String, Object> aggregateStepResults(List<Map<String, Object>> stepResults) {
        Map<String, Object> aggregated = new HashMap<>();
        
        for (Map<String, Object> stepResult : stepResults) {
            if (stepResult != null) {
                aggregated.putAll(stepResult);
            }
        }
        
        return aggregated;
    }
    
    /**
     * Extract bytes from file list
     */
    private long extractBytesFromFileList(List<?> files) {
        long totalBytes = 0L;
        
        for (Object file : files) {
            if (file instanceof Map) {
                Map<?, ?> fileMap = (Map<?, ?>) file;
                Object size = fileMap.get("size");
                if (size instanceof Number) {
                    totalBytes += ((Number) size).longValue();
                }
            }
        }
        
        return totalBytes;
    }
}