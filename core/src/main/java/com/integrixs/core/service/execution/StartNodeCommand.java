package com.integrixs.core.service.execution;

import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command for executing START node steps.
 * In new structure, START nodes are flow control only - sender adapter execution 
 * happens separately and data is passed via triggerData.
 */
@Component
public class StartNodeCommand extends AbstractStepExecutionCommand {
    
    @Override
    public String getStepType() {
        return "start";
    }
    
    @Override
    public boolean canHandle(Map<String, Object> nodeConfiguration) {
        String nodeType = getConfigValue(nodeConfiguration, "type", "");
        return "start".equals(nodeType) || "start-process".equals(nodeType);
    }
    
    @Override
    protected Map<String, Object> executeStep(FlowExecutionStep step, Map<String, Object> nodeConfiguration,
                                            Map<String, Object> executionContext) {
        
        logger.debug("Executing start node for step: {} (Flow control - data passed from sender adapter)", 
                    step.getId());
        
        // In new structure, start nodes don't execute adapters - they receive data from separate adapter execution
        // The triggerData should contain the data from the sender adapter execution
        Object triggerData = executionContext.get("triggerData");
        if (triggerData == null) {
            // No trigger data - this could be manual execution or flow testing
            triggerData = new HashMap<String, Object>();
            logger.debug("No trigger data provided to START node - initializing with empty data for flow control");
        }
        
        // Process trigger data from sender adapter execution
        Map<String, Object> result = new HashMap<>();
        
        if (triggerData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> triggerMap = (Map<String, Object>) triggerData;
            
            // Extract files that were processed by the sender adapter
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> foundFiles = (List<Map<String, Object>>) 
                triggerMap.getOrDefault("foundFiles", new ArrayList<>());
            
            if (!foundFiles.isEmpty()) {
                // Add files to execution context for downstream processing
                executionContext.put("filesToProcess", foundFiles);
                executionContext.put("senderProcessedFiles", foundFiles);
                result.put("hasData", true);
                result.put("foundFiles", foundFiles);
                
                logger.info("START node received {} files from sender adapter, added to execution context", 
                          foundFiles.size());
            } else {
                result.put("hasData", false);
                result.put("foundFiles", new ArrayList<>());
                logger.debug("START node received trigger data but no files to process");
            }
            
            // Pass through any additional trigger data
            for (Map.Entry<String, Object> entry : triggerMap.entrySet()) {
                if (!"foundFiles".equals(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            result.put("hasData", false);
            result.put("foundFiles", new ArrayList<>());
            logger.warn("START node received non-map trigger data: {}", 
                       triggerData != null ? triggerData.getClass().getSimpleName() : "null");
        }
        
        // Add start node metadata
        result.put("nodeType", "start");
        result.put("executionMode", "flow_control");
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("success", true);
        
        return result;
    }
}