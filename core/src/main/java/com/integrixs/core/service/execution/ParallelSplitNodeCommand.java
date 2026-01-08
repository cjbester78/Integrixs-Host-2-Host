package com.integrixs.core.service.execution;

import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command for executing PARALLEL SPLIT node steps.
 * Creates multiple execution paths and ensures file data is properly passed to all parallel branches.
 */
@Component
public class ParallelSplitNodeCommand extends AbstractStepExecutionCommand {
    
    @Override
    public String getStepType() {
        return "parallelsplit";
    }
    
    @Override
    public boolean canHandle(Map<String, Object> nodeConfiguration) {
        String nodeType = getConfigValue(nodeConfiguration, "type", "");
        return "parallelsplit".equals(nodeType);
    }
    
    @Override
    protected Map<String, Object> executeStep(FlowExecutionStep step, Map<String, Object> nodeConfiguration,
                                            Map<String, Object> executionContext) {
        
        logger.debug("Executing parallel split node for step: {}", step.getId());
        
        Map<String, Object> splitResult = new HashMap<>();
        
        try {
            // Extract parallel paths configuration from React Flow format
            Map<String, Object> nodeData = extractNodeData(nodeConfiguration);
            int parallelPaths = 2; // Default to 2 paths
            
            Object pathsObj = nodeData.get("parallelPaths");
            if (pathsObj instanceof Number) {
                parallelPaths = ((Number) pathsObj).intValue();
            }
            
            // Verify file data availability for parallel processing
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filesToProcess = (List<Map<String, Object>>) 
                executionContext.getOrDefault("filesToProcess", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> senderFiles = (List<Map<String, Object>>) 
                executionContext.getOrDefault("senderFiles", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foundFiles = (List<Map<String, Object>>) 
                executionContext.getOrDefault("foundFiles", new ArrayList<>());
            
            int totalFiles = Math.max(Math.max(filesToProcess.size(), senderFiles.size()), foundFiles.size());
            
            splitResult.put("parallelPaths", parallelPaths);
            splitResult.put("splitType", "parallel");
            splitResult.put("splitTimestamp", LocalDateTime.now().toString());
            splitResult.put("splitNodeId", nodeConfiguration.get("id"));
            splitResult.put("filesAvailableForSplit", totalFiles);
            splitResult.put("success", true);
            
            // Log file data propagation details
            if (totalFiles > 0) {
                logger.info("Parallel split node ready to distribute {} files across {} execution paths", 
                          totalFiles, parallelPaths);
                
                // Add file data details to result for monitoring
                splitResult.put("fileDataKeys", new ArrayList<>(Arrays.asList("filesToProcess", "senderFiles", "foundFiles")));
                splitResult.put("filesToProcessCount", filesToProcess.size());
                splitResult.put("senderFilesCount", senderFiles.size());
                splitResult.put("foundFilesCount", foundFiles.size());
                
                // Log sample file information for debugging
                if (!filesToProcess.isEmpty()) {
                    Map<String, Object> sampleFile = filesToProcess.get(0);
                    logger.debug("Sample file in parallel split - keys: {}", sampleFile.keySet());
                    logFileInfo("Sample file name", sampleFile, "fileName");
                    logFileInfo("Sample file path", sampleFile, "filePath");
                    logFileInfo("Sample file size", sampleFile, "fileSize");
                }
            } else {
                logger.info("Parallel split node ready to create {} execution paths (no file data available)", 
                          parallelPaths);
            }
            
            // The actual parallel execution will be handled by executeNextNodes method
            // which already creates isolated context copies for each branch, including file data
            
            // Store context snapshot for monitoring/debugging
            Map<String, Object> contextSnapshot = createContextSnapshot(executionContext);
            splitResult.put("contextSnapshot", contextSnapshot);
            
            return splitResult;
            
        } catch (Exception e) {
            logger.error("Parallel split node execution failed: {}", e.getMessage(), e);
            splitResult.put("error", e.getMessage());
            splitResult.put("parallelPaths", 0);
            splitResult.put("filesAvailableForSplit", 0);
            splitResult.put("success", false);
            return splitResult;
        }
    }
    
    /**
     * Create sanitized context snapshot to avoid memory issues
     */
    private Map<String, Object> createContextSnapshot(Map<String, Object> executionContext) {
        Map<String, Object> contextSnapshot = new HashMap<>(executionContext);
        
        // Don't store actual file content in the result to avoid memory issues
        if (contextSnapshot.containsKey("filesToProcess")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) contextSnapshot.get("filesToProcess");
            List<Map<String, Object>> fileMetadata = new ArrayList<>();
            
            for (Map<String, Object> file : files) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("fileName", file.getOrDefault("fileName", "unknown"));
                metadata.put("filePath", file.getOrDefault("filePath", "unknown"));
                metadata.put("fileSize", file.getOrDefault("fileSize", 0));
                metadata.put("lastModified", file.getOrDefault("lastModified", "unknown"));
                fileMetadata.add(metadata);
            }
            contextSnapshot.put("filesToProcess", fileMetadata); // Replace with metadata only
        }
        
        return contextSnapshot;
    }
    
    /**
     * Helper method for logging file information
     */
    private void logFileInfo(String description, Map<String, Object> file, String key) {
        if (file.containsKey(key)) {
            logger.debug("{}: {}", description, file.get(key));
        }
    }
}