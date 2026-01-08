package com.integrixs.core.service.execution;

import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command for executing DECISION node steps.
 * Supports both React Flow node format (data.conditionType) and legacy format (decisionConfig).
 */
@Component
public class DecisionNodeCommand extends AbstractStepExecutionCommand {
    
    @Override
    public String getStepType() {
        return "decision";
    }
    
    @Override
    public boolean canHandle(Map<String, Object> nodeConfiguration) {
        String nodeType = getConfigValue(nodeConfiguration, "type", "");
        return "decision".equals(nodeType);
    }
    
    @Override
    protected Map<String, Object> executeStep(FlowExecutionStep step, Map<String, Object> nodeConfiguration,
                                            Map<String, Object> executionContext) {
        
        logger.debug("Executing decision node for step: {}", step.getId());
        
        Map<String, Object> decisionResult = new HashMap<>();
        
        try {
            // Extract decision configuration from React Flow format or legacy format
            String conditionType = "ALWAYS_TRUE";
            String conditionExpression = "true";
            
            Map<String, Object> nodeData = extractNodeData(nodeConfiguration);
            
            if (nodeData.containsKey("conditionType")) {
                conditionType = getConfigValue(nodeData, "conditionType", "ALWAYS_TRUE");
                conditionExpression = getConfigValue(nodeData, "condition", "true");
            } else {
                // Legacy format
                Map<String, Object> decisionConfig = getConfigMap(nodeConfiguration, "decisionConfig");
                conditionType = getConfigValue(decisionConfig, "conditionType", "ALWAYS_TRUE");
                conditionExpression = getConfigValue(decisionConfig, "condition", "true");
            }
            
            boolean evaluationResult = evaluateCondition(conditionType, conditionExpression, executionContext);
            
            decisionResult.put("decision", evaluationResult ? "true" : "false");
            decisionResult.put("conditionType", conditionType);
            decisionResult.put("conditionExpression", conditionExpression);
            decisionResult.put("evaluatedAt", LocalDateTime.now().toString());
            decisionResult.put("success", true);
            
            // Set context variable for downstream nodes
            executionContext.put("lastDecisionResult", evaluationResult);
            
            logger.debug("Decision evaluation completed: {} -> {}", conditionExpression, evaluationResult);
            
        } catch (Exception e) {
            logger.error("Decision node evaluation failed: {}", e.getMessage(), e);
            decisionResult.put("decision", "false");
            decisionResult.put("error", e.getMessage());
            decisionResult.put("success", false);
        }
        
        return decisionResult;
    }
    
    /**
     * Evaluate decision condition based on type and expression
     */
    private boolean evaluateCondition(String conditionType, String expression, Map<String, Object> context) {
        switch (conditionType.toUpperCase()) {
            case "ALWAYS_TRUE":
                return true;
            case "ALWAYS_FALSE":
                return false;
            case "CONTEXT_CONTAINS_KEY":
                return context.containsKey(expression);
            case "CONTEXT_VALUE_EQUALS":
                String[] parts = expression.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String expectedValue = parts[1].trim();
                    return expectedValue.equals(String.valueOf(context.get(key)));
                }
                return false;
            case "FILE_COUNT_GREATER_THAN":
                try {
                    int threshold = Integer.parseInt(expression);
                    @SuppressWarnings("unchecked")
                    List<String> files = (List<String>) context.getOrDefault("filesToProcess", new ArrayList<>());
                    return files.size() > threshold;
                } catch (NumberFormatException e) {
                    logger.warn("Invalid threshold for FILE_COUNT_GREATER_THAN: {}", expression);
                    return false;
                }
            default:
                logger.warn("Unknown condition type: {}", conditionType);
                return true; // Default to true for unknown conditions
        }
    }
}