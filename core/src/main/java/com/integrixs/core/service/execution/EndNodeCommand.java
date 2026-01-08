package com.integrixs.core.service.execution;

import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command for executing END node steps.
 * In new structure, END nodes are flow control only - receiver adapter execution 
 * happens in separate adapter nodes connected via edges.
 */
@Component
public class EndNodeCommand extends AbstractStepExecutionCommand {
    
    @Override
    public String getStepType() {
        return "end";
    }
    
    @Override
    public boolean canHandle(Map<String, Object> nodeConfiguration) {
        String nodeType = getConfigValue(nodeConfiguration, "type", "");
        return "end".equals(nodeType) || "end-process".equals(nodeType);
    }
    
    @Override
    protected Map<String, Object> executeStep(FlowExecutionStep step, Map<String, Object> nodeConfiguration,
                                            Map<String, Object> executionContext) {
        
        logger.debug("Executing end node for step: {} (Flow control - data passed to receiver adapters)", 
                    step.getId());
        
        // In new structure, end nodes don't execute adapters - they pass data to separate adapter nodes
        // Extract any files from the execution context that need to be passed to receiver adapters
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filesToProcess = (List<Map<String, Object>>) 
            executionContext.getOrDefault("filesToProcess", new ArrayList<>());
        
        Map<String, Object> result = new HashMap<>();
        
        if (!filesToProcess.isEmpty()) {
            logger.info("END node: Received {} files from execution context to pass to receiver adapters", 
                       filesToProcess.size());
            
            // Pass files to the context for receiver adapter nodes connected via edges
            executionContext.put("receiverFiles", filesToProcess);
            result.put("hasData", true);
            result.put("filesToProcess", filesToProcess);
            
            // Log files being passed
            for (int i = 0; i < filesToProcess.size(); i++) {
                Map<String, Object> file = filesToProcess.get(i);
                logger.debug("END node: File {} - {}", i + 1, file.getOrDefault("name", "unknown"));
            }
        } else {
            logger.debug("END node: No files to pass to receiver adapters");
            result.put("hasData", false);
            result.put("filesToProcess", new ArrayList<>());
        }
        
        // Add end node metadata
        result.put("nodeType", "end");
        result.put("executionMode", "flow_control");
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("status", "completed");
        result.put("success", true);
        
        return result;
    }
}