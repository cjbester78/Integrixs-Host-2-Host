package com.integrixs.core.service.execution;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.core.service.AdapterExecutionService;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.DeployedFlow;
import com.integrixs.shared.model.FlowExecutionStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Command for executing MESSAGE END node steps.
 * Similar to end node but with message event handling and optional adapter execution.
 */
@Component
public class MessageEndNodeCommand extends AbstractStepExecutionCommand {
    
    private final AdapterRepository adapterRepository;
    private final DeployedFlowRepository deployedFlowRepository;
    private final AdapterExecutionService adapterExecutionService;
    
    @Autowired
    public MessageEndNodeCommand(AdapterRepository adapterRepository,
                                DeployedFlowRepository deployedFlowRepository,
                                AdapterExecutionService adapterExecutionService) {
        this.adapterRepository = adapterRepository;
        this.deployedFlowRepository = deployedFlowRepository;
        this.adapterExecutionService = adapterExecutionService;
    }
    
    @Override
    public String getStepType() {
        return "messageend";
    }
    
    @Override
    public boolean canHandle(Map<String, Object> nodeConfiguration) {
        String nodeType = getConfigValue(nodeConfiguration, "type", "");
        return "messageend".equals(nodeType);
    }
    
    @Override
    protected void validateStepConfiguration(Map<String, Object> nodeConfiguration) {
        super.validateStepConfiguration(nodeConfiguration);
        
        Map<String, Object> nodeData = extractNodeData(nodeConfiguration);
        if (nodeData.isEmpty()) {
            throw new IllegalArgumentException("Message End node missing data configuration");
        }
    }
    
    @Override
    protected Map<String, Object> executeStep(FlowExecutionStep step, Map<String, Object> nodeConfiguration,
                                            Map<String, Object> executionContext) {
        
        logger.debug("Executing message end node for step: {}", step.getId());
        
        // Extract message end configuration from node data
        Map<String, Object> nodeData = extractNodeData(nodeConfiguration);
        
        // Get event type and message payload
        String eventType = getConfigValue(nodeData, "eventType", "MESSAGE_END");
        String messagePayload = getConfigValue(nodeData, "messagePayload", "");
        
        // Execute receiver adapter if configured (same as regular end node)
        Object receiverAdapterIdObj = nodeData.get("adapterId");
        Map<String, Object> messageResult = new HashMap<>();
        
        if (receiverAdapterIdObj != null && !receiverAdapterIdObj.toString().isEmpty()) {
            try {
                UUID receiverAdapterId = UUID.fromString(receiverAdapterIdObj.toString());
                executeReceiverAdapter(receiverAdapterId, eventType, messagePayload, 
                                     executionContext, step, messageResult);
                
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid adapter ID format in Message End node: " + receiverAdapterIdObj, e);
            }
        }
        
        // Add message end metadata
        messageResult.put("nodeType", "messageEnd");
        messageResult.put("eventType", eventType);
        messageResult.put("messagePayload", messagePayload);
        messageResult.put("timestamp", LocalDateTime.now().toString());
        messageResult.put("status", "completed");
        messageResult.put("success", true);
        
        logger.info("Message End node completed with event: {}", eventType);
        return messageResult;
    }
    
    /**
     * Execute receiver adapter with message context
     */
    private void executeReceiverAdapter(UUID receiverAdapterId, String eventType, String messagePayload,
                                      Map<String, Object> executionContext, FlowExecutionStep step,
                                      Map<String, Object> messageResult) {
        
        // Get deployment context
        UUID deploymentId = (UUID) executionContext.get("deploymentId");
        if (deploymentId == null) {
            logger.warn("No deployment context available for Message End adapter execution");
            return;
        }
        
        Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findById(deploymentId);
        if (deployedFlowOpt.isEmpty()) {
            logger.warn("Deployed flow not found for deployment ID: {}", deploymentId);
            return;
        }
        
        // Get adapter configuration
        Optional<Adapter> adapterOpt = adapterRepository.findById(receiverAdapterId);
        if (adapterOpt.isEmpty()) {
            logger.warn("Receiver adapter not found: {}", receiverAdapterId);
            return;
        }
        
        Adapter adapter = adapterOpt.get();
        
        logger.info("Message End node executing adapter: {} with event: {}", adapter.getName(), eventType);
        
        // Execute adapter with message context
        Map<String, Object> messageContext = new HashMap<>(executionContext);
        messageContext.put("messageEventType", eventType);
        messageContext.put("messagePayload", messagePayload);
        
        Map<String, Object> adapterResult = adapterExecutionService.executeAdapter(
            receiverAdapterId, messageContext, step);
        messageResult.putAll(adapterResult);
    }
}