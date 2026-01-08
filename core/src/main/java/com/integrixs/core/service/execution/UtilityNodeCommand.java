package com.integrixs.core.service.execution;

import com.integrixs.core.service.UtilityExecutionService;
import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Command for executing UTILITY node steps.
 * Supports both React Flow node format (data.utilityType) and legacy format (utilityType).
 */
@Component
public class UtilityNodeCommand extends AbstractStepExecutionCommand {
    
    private final UtilityExecutionService utilityExecutionService;
    
    @Autowired
    public UtilityNodeCommand(UtilityExecutionService utilityExecutionService) {
        this.utilityExecutionService = utilityExecutionService;
    }
    
    @Override
    public String getStepType() {
        return "utility";
    }
    
    @Override
    public boolean canHandle(Map<String, Object> nodeConfiguration) {
        String nodeType = getConfigValue(nodeConfiguration, "type", "");
        return "utility".equals(nodeType);
    }
    
    @Override
    protected void validateStepConfiguration(Map<String, Object> nodeConfiguration) {
        super.validateStepConfiguration(nodeConfiguration);
        
        String utilityType = extractUtilityType(nodeConfiguration);
        if (utilityType.isEmpty()) {
            throw new IllegalArgumentException("Utility node missing utilityType in data or node configuration");
        }
    }
    
    @Override
    protected Map<String, Object> executeStep(FlowExecutionStep step, Map<String, Object> nodeConfiguration,
                                            Map<String, Object> executionContext) {
        
        logger.debug("Executing utility node for step: {}", step.getId());
        
        String utilityType = extractUtilityType(nodeConfiguration);
        Map<String, Object> utilityConfiguration = extractUtilityConfiguration(nodeConfiguration);
        
        // Execute real utility operation using UtilityExecutionService
        Map<String, Object> utilityResult = utilityExecutionService.executeUtility(
            utilityType, utilityConfiguration, executionContext, step);
        
        // Add utility metadata
        utilityResult.put("utilityType", utilityType);
        utilityResult.put("success", true);
        
        return utilityResult;
    }
    
    /**
     * Extract utility type from React Flow format (data.utilityType) or legacy format (utilityType)
     */
    private String extractUtilityType(Map<String, Object> nodeConfiguration) {
        Map<String, Object> nodeData = extractNodeData(nodeConfiguration);
        
        if (nodeData.containsKey("utilityType")) {
            return nodeData.get("utilityType").toString();
        } else if (nodeConfiguration.containsKey("utilityType")) {
            // Legacy format
            return nodeConfiguration.get("utilityType").toString();
        }
        
        return "";
    }
    
    /**
     * Extract utility configuration
     */
    private Map<String, Object> extractUtilityConfiguration(Map<String, Object> nodeConfiguration) {
        Map<String, Object> nodeData = extractNodeData(nodeConfiguration);
        
        if (nodeData.containsKey("configuration")) {
            return getConfigMap(nodeData, "configuration");
        } else if (nodeConfiguration.containsKey("configuration")) {
            // Legacy format
            return getConfigMap(nodeConfiguration, "configuration");
        }
        
        return new HashMap<>();
    }
}