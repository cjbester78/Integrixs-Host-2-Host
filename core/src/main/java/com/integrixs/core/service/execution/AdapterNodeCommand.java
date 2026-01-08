package com.integrixs.core.service.execution;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.service.AdapterExecutionService;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Command for executing ADAPTER node steps.
 * Now only used for intermediate adapter nodes (not start/end).
 * Supports both React Flow node format (data.adapterId) and legacy format (adapterId).
 */
@Component
public class AdapterNodeCommand extends AbstractStepExecutionCommand {
    
    private final AdapterRepository adapterRepository;
    private final AdapterExecutionService adapterExecutionService;
    
    @Autowired
    public AdapterNodeCommand(AdapterRepository adapterRepository,
                             AdapterExecutionService adapterExecutionService) {
        this.adapterRepository = adapterRepository;
        this.adapterExecutionService = adapterExecutionService;
    }
    
    @Override
    public String getStepType() {
        return "adapter";
    }
    
    @Override
    public boolean canHandle(Map<String, Object> nodeConfiguration) {
        String nodeType = getConfigValue(nodeConfiguration, "type", "");
        return "adapter".equals(nodeType);
    }
    
    @Override
    protected void validateStepConfiguration(Map<String, Object> nodeConfiguration) {
        super.validateStepConfiguration(nodeConfiguration);
        
        String adapterId = extractAdapterId(nodeConfiguration);
        if (adapterId.isEmpty()) {
            throw new IllegalArgumentException("Intermediate adapter node missing adapterId in data or node configuration");
        }
        
        try {
            UUID.fromString(adapterId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid adapter ID format: " + adapterId, e);
        }
    }
    
    @Override
    protected Map<String, Object> executeStep(FlowExecutionStep step, Map<String, Object> nodeConfiguration,
                                            Map<String, Object> executionContext) {
        
        logger.debug("Executing intermediate adapter node for step: {}", step.getId());
        
        String adapterIdStr = extractAdapterId(nodeConfiguration);
        UUID adapterId = UUID.fromString(adapterIdStr);
        
        // Get adapter configuration
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (adapterOpt.isEmpty()) {
            throw new RuntimeException("Intermediate adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        if (!adapter.isActive()) {
            throw new RuntimeException("Intermediate adapter is inactive: " + adapterId);
        }
        
        logger.info("Executing intermediate adapter: {} ({})", adapter.getName(), adapterId);
        
        // Execute real adapter operation using AdapterExecutionService
        Map<String, Object> adapterResult = adapterExecutionService.executeAdapter(
            adapter, executionContext, step);
        
        // Add intermediate adapter metadata
        adapterResult.put("adapterType", "intermediate");
        adapterResult.put("adapterId", adapterId.toString());
        adapterResult.put("adapterName", adapter.getName());
        adapterResult.put("success", true);
        
        return adapterResult;
    }
    
    /**
     * Extract adapter ID from React Flow format (data.adapterId) or legacy format (adapterId)
     */
    private String extractAdapterId(Map<String, Object> nodeConfiguration) {
        // Check React Flow format first (data object contains node data)
        Map<String, Object> nodeData = extractNodeData(nodeConfiguration);
        
        if (nodeData.containsKey("adapterId")) {
            return nodeData.get("adapterId").toString();
        } else if (nodeConfiguration.containsKey("adapterId")) {
            // Legacy format
            return nodeConfiguration.get("adapterId").toString();
        }
        
        return "";
    }
}