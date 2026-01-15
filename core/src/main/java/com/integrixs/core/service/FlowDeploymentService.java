package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.DeployedFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing flow deployment operations
 * Handles deployment, undeployment, and deployment validation following Single Responsibility Principle
 */
@Service
public class FlowDeploymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowDeploymentService.class);

    private final FlowCrudService flowCrudService;
    private final FlowValidationService flowValidationService;
    private final DeployedFlowRepository deployedFlowRepository;
    private final AdapterRepository adapterRepository;
    private final DeployedFlowSchedulingService schedulingService;

    @Autowired
    public FlowDeploymentService(FlowCrudService flowCrudService,
                                FlowValidationService flowValidationService,
                                DeployedFlowRepository deployedFlowRepository,
                                AdapterRepository adapterRepository,
                                DeployedFlowSchedulingService schedulingService) {
        this.flowCrudService = flowCrudService;
        this.flowValidationService = flowValidationService;
        this.deployedFlowRepository = deployedFlowRepository;
        this.adapterRepository = adapterRepository;
        this.schedulingService = schedulingService;
    }
    
    /**
     * Deploy a flow
     */
    public Map<String, Object> deployFlow(UUID flowId, UUID deployedBy) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        Objects.requireNonNull(deployedBy, "Deployed by user ID cannot be null");
        
        logger.info("Deploying flow: {} by user: {}", flowId, deployedBy);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Validate flow exists
            Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(flowId);
            if (!flowOpt.isPresent()) {
                result.put("status", "FAILED");
                result.put("message", "Flow not found");
                result.put("errors", List.of("Flow with ID " + flowId + " does not exist"));
                return result;
            }
            
            IntegrationFlow flow = flowOpt.get();

            // Check if flow is already deployed (DEPLOYED status)
            Optional<DeployedFlow> activeDeployment = deployedFlowRepository.findByFlowId(flowId);
            if (activeDeployment.isPresent()) {
                result.put("status", "FAILED");
                result.put("message", "Flow is already deployed");
                result.put("deployedAt", activeDeployment.get().getDeployedAt());
                result.put("deployedBy", activeDeployment.get().getDeployedBy());
                return result;
            }

            // Check if there's an existing deployment record (including UNDEPLOYED)
            Optional<DeployedFlow> existingRecord = deployedFlowRepository.findAnyByFlowId(flowId);
            boolean isRedeploy = existingRecord.isPresent();
            
            // Validate flow before deployment
            Map<String, Object> validationResult = flowValidationService.validateFlow(flow);
            boolean isValid = (Boolean) validationResult.get("isValid");
            
            if (!isValid) {
                result.put("status", "FAILED");
                result.put("message", "Flow validation failed");
                result.put("validationErrors", validationResult.get("errors"));
                return result;
            }
            
            // Check if flow is active
            if (!flow.getActive()) {
                result.put("status", "FAILED");
                result.put("message", "Cannot deploy inactive flow");
                result.put("errors", List.of("Flow must be active to be deployed"));
                return result;
            }

            // Extract sender and receiver adapter IDs from flow definition
            UUID senderAdapterId = null;
            String senderAdapterName = null;
            UUID receiverAdapterId = null;
            String receiverAdapterName = null;
            List<UUID> allAdapterIds = new ArrayList<>();
            Map<UUID, Adapter> flowAdapters = new HashMap<>();

            Map<String, Object> flowDef = flow.getFlowDefinition();
            if (flowDef != null && flowDef.containsKey("nodes")) {
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) flowDef.get("nodes");
                for (Map<String, Object> node : nodes) {
                    if ("adapter".equals(node.get("type"))) {
                        Map<String, Object> data = (Map<String, Object>) node.get("data");
                        if (data != null) {
                            String direction = (String) data.get("direction");
                            String adapterIdStr = (String) data.get("adapterId");
                            String adapterName = (String) data.get("label");

                            if (adapterIdStr != null && !adapterIdStr.trim().isEmpty()) {
                                try {
                                    UUID adapterId = UUID.fromString(adapterIdStr);
                                    allAdapterIds.add(adapterId);

                                    if ("SENDER".equalsIgnoreCase(direction)) {
                                        senderAdapterId = adapterId;
                                        senderAdapterName = adapterName;
                                    } else if ("RECEIVER".equalsIgnoreCase(direction)) {
                                        receiverAdapterId = adapterId;
                                        receiverAdapterName = adapterName;
                                    }
                                } catch (IllegalArgumentException e) {
                                    logger.warn("Invalid adapter ID in flow definition: {}", adapterIdStr);
                                }
                            }
                        }
                    }
                }
            }

            // STEP 1: Validate all adapters exist and are active
            logger.info("Step 1: Validating {} adapters for deployment...", allAdapterIds.size());
            for (UUID adapterId : allAdapterIds) {
                Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
                if (adapterOpt.isEmpty()) {
                    result.put("status", "FAILED");
                    result.put("message", "Adapter not found: " + adapterId);
                    return result;
                }
                Adapter adapter = adapterOpt.get();
                if (!adapter.isActive()) {
                    result.put("status", "FAILED");
                    result.put("message", "Adapter " + adapter.getName() + " is inactive and cannot be started");
                    return result;
                }
                flowAdapters.put(adapterId, adapter);
            }
            logger.info("Step 1 completed: All adapters validated");

            // STEP 2: Start all adapters
            logger.info("Step 2: Starting {} adapters...", allAdapterIds.size());
            List<UUID> startedAdapterIds = new ArrayList<>();

            try {
                for (UUID adapterId : allAdapterIds) {
                    Adapter adapter = flowAdapters.get(adapterId);

                    // Only start if not already started
                    if (adapter.getStatus() != Adapter.AdapterStatus.STARTED) {
                        logger.info("Starting adapter: {} ({})", adapter.getName(), adapterId);
                        adapterRepository.updateStatus(adapterId, Adapter.AdapterStatus.STARTED);
                        startedAdapterIds.add(adapterId);
                    } else {
                        logger.info("Adapter {} already STARTED", adapter.getName());
                    }
                }
                logger.info("Step 2 completed: All adapters started");

            } catch (Exception adapterStartupError) {
                // ROLLBACK: Stop any adapters that we started
                logger.error("Adapter startup failed, rolling back: {}", adapterStartupError.getMessage());

                for (UUID adapterId : startedAdapterIds) {
                    try {
                        Adapter adapter = flowAdapters.get(adapterId);
                        adapterRepository.updateStatus(adapterId, Adapter.AdapterStatus.STOPPED);
                        logger.info("Rolled back adapter to STOPPED: {}", adapter.getName());
                    } catch (Exception rollbackError) {
                        logger.warn("Failed to rollback adapter {}: {}", adapterId, rollbackError.getMessage());
                    }
                }

                result.put("status", "FAILED");
                result.put("message", "Failed to start adapters: " + adapterStartupError.getMessage());
                return result;
            }

            // STEP 3: Create or update deployment record
            DeployedFlow deployedFlow;
            UUID deploymentId;

            if (isRedeploy) {
                // Re-deploy: update existing record
                deployedFlow = existingRecord.get();
                deployedFlow.setFlowName(flow.getName());
                deployedFlow.setFlowVersion(flow.getFlowVersion());
                deployedFlow.setDeployedAt(LocalDateTime.now());
                deployedFlow.setDeployedBy(deployedBy);
                deployedFlow.setDeploymentStatus(DeployedFlow.DeploymentStatus.DEPLOYED);
                deployedFlow.setRuntimeStatus(DeployedFlow.RuntimeStatus.ACTIVE);
                deployedFlow.setExecutionEnabled(true);
                deployedFlow.setHealthCheckEnabled(true);
                deployedFlow.setMaxConcurrentExecutions(1);
                deployedFlow.setExecutionTimeoutMinutes(60);
                deployedFlow.setSenderAdapterId(senderAdapterId);
                deployedFlow.setSenderAdapterName(senderAdapterName);
                deployedFlow.setReceiverAdapterId(receiverAdapterId);
                deployedFlow.setReceiverAdapterName(receiverAdapterName);
                deployedFlow.setFlowConfiguration(flowDef != null ? flowDef : new HashMap<>());

                deployedFlowRepository.redeploy(deployedFlow);
                deploymentId = deployedFlow.getId();
                logger.info("Re-deployed flow: {} with existing deployment ID: {}", flowId, deploymentId);
            } else {
                // New deployment: create new record
                deployedFlow = new DeployedFlow();
                deployedFlow.setFlowId(flowId);
                deployedFlow.setFlowName(flow.getName());
                deployedFlow.setFlowVersion(flow.getFlowVersion());
                deployedFlow.setDeployedAt(LocalDateTime.now());
                deployedFlow.setDeployedBy(deployedBy);
                deployedFlow.setDeploymentStatus(DeployedFlow.DeploymentStatus.DEPLOYED);
                deployedFlow.setRuntimeStatus(DeployedFlow.RuntimeStatus.ACTIVE);
                deployedFlow.setExecutionEnabled(true);
                deployedFlow.setHealthCheckEnabled(true);
                deployedFlow.setMaxConcurrentExecutions(1);
                deployedFlow.setExecutionTimeoutMinutes(60);
                deployedFlow.setSenderAdapterId(senderAdapterId);
                deployedFlow.setSenderAdapterName(senderAdapterName);
                deployedFlow.setReceiverAdapterId(receiverAdapterId);
                deployedFlow.setReceiverAdapterName(receiverAdapterName);
                deployedFlow.setFlowConfiguration(flowDef != null ? flowDef : new HashMap<>());

                deploymentId = deployedFlowRepository.deploy(deployedFlow);
                deployedFlow.setId(deploymentId);
                logger.info("Newly deployed flow: {} with deployment ID: {}", flowId, deploymentId);
            }
            
            result.put("status", "SUCCESS");
            result.put("message", isRedeploy ? "Flow re-deployed successfully" : "Flow deployed successfully");
            result.put("deploymentId", deploymentId);
            result.put("deployedAt", deployedFlow.getDeployedAt());
            result.put("flowName", flow.getName());
            result.put("flowVersion", flow.getFlowVersion());

            logger.info("Successfully {} flow: {} with deployment ID: {}", isRedeploy ? "re-deployed" : "deployed", flowId, deploymentId);

            // Notify scheduling service to start adapters for this deployment
            try {
                schedulingService.onFlowDeployed(deployedFlow);
            } catch (Exception scheduleEx) {
                logger.error("Failed to start adapters for deployed flow {}: {}", flowId, scheduleEx.getMessage(), scheduleEx);
                result.put("warning", "Flow deployed but adapter scheduling failed: " + scheduleEx.getMessage());
            }

        } catch (Exception e) {
            logger.error("Failed to deploy flow: " + flowId, e);
            result.put("status", "ERROR");
            result.put("message", "Deployment failed due to system error");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Undeploy a flow
     */
    public Map<String, Object> undeployFlow(UUID flowId, UUID undeployedBy) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        Objects.requireNonNull(undeployedBy, "Undeployed by user ID cannot be null");
        
        logger.info("Undeploying flow: {} by user: {}", flowId, undeployedBy);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Check if flow is deployed
            Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findByFlowId(flowId);
            if (!deployedFlowOpt.isPresent()) {
                result.put("status", "FAILED");
                result.put("message", "Flow is not deployed");
                return result;
            }
            
            DeployedFlow deployedFlow = deployedFlowOpt.get();
            
            // Update deployment status to undeployed
            deployedFlow.setDeploymentStatus(DeployedFlow.DeploymentStatus.UNDEPLOYED);
            deployedFlow.setRuntimeStatus(DeployedFlow.RuntimeStatus.INACTIVE);
            deployedFlow.setExecutionEnabled(false);
            deployedFlow.setUndeployedAt(LocalDateTime.now());
            deployedFlow.setUndeployedBy(undeployedBy);
            
            deployedFlowRepository.update(deployedFlow);

            // Notify scheduling service to stop adapters for this deployment
            try {
                schedulingService.onFlowUndeployed(deployedFlow.getId());
            } catch (Exception scheduleEx) {
                logger.error("Failed to stop adapters for undeployed flow {}: {}", flowId, scheduleEx.getMessage(), scheduleEx);
            }

            result.put("status", "SUCCESS");
            result.put("message", "Flow undeployed successfully");
            result.put("undeployedAt", deployedFlow.getUndeployedAt());
            result.put("flowName", deployedFlow.getFlowName());
            result.put("originalDeploymentDate", deployedFlow.getDeployedAt());

            logger.info("Successfully undeployed flow: {}", flowId);

        } catch (Exception e) {
            logger.error("Failed to undeploy flow: " + flowId, e);
            result.put("status", "ERROR");
            result.put("message", "Undeployment failed due to system error");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Validate flow deployment readiness
     */
    public Map<String, Object> validateDeployment(UUID flowId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        
        logger.debug("Validating deployment readiness for flow: {}", flowId);
        
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            // Check if flow exists
            Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(flowId);
            if (!flowOpt.isPresent()) {
                errors.add("Flow not found");
                result.put("isValid", false);
                result.put("errors", errors);
                result.put("warnings", warnings);
                return result;
            }
            
            IntegrationFlow flow = flowOpt.get();
            
            // Check if flow is already deployed
            Optional<DeployedFlow> existingDeployment = deployedFlowRepository.findByFlowId(flowId);
            if (existingDeployment.isPresent()) {
                errors.add("Flow is already deployed");
            }
            
            // Check if flow is active
            if (!flow.getActive()) {
                errors.add("Flow must be active to be deployed");
            }
            
            // Validate flow definition
            Map<String, Object> validationResult = flowValidationService.validateFlow(flow);
            boolean flowValid = (Boolean) validationResult.get("isValid");
            
            if (!flowValid) {
                List<String> validationErrors = (List<String>) validationResult.get("errors");
                errors.addAll(validationErrors);
            }
            
            List<String> validationWarnings = (List<String>) validationResult.get("warnings");
            if (validationWarnings != null) {
                warnings.addAll(validationWarnings);
            }
            
            // Check package context if applicable
            if (flow.getPackageId() != null) {
                Map<String, Object> packageValidation = flowValidationService.validateFlowInPackageContext(flowId, flow.getPackageId());
                boolean packageValid = (Boolean) packageValidation.get("isValid");
                
                if (!packageValid) {
                    List<String> packageErrors = (List<String>) packageValidation.get("errors");
                    errors.addAll(packageErrors);
                }
            }
            
            // Check if flow has required components
            if (flow.getFlowDefinition() == null || flow.getFlowDefinition().isEmpty()) {
                errors.add("Flow definition is required for deployment");
            }
            
            result.put("isValid", errors.isEmpty());
            result.put("errors", errors);
            result.put("warnings", warnings);
            result.put("flowName", flow.getName());
            result.put("flowType", flow.getFlowType());
            result.put("packageId", flow.getPackageId());
            result.put("validatedAt", LocalDateTime.now());
            
            if (errors.isEmpty()) {
                logger.debug("Flow {} is ready for deployment", flowId);
            } else {
                logger.warn("Flow {} is not ready for deployment. Errors: {}", flowId, errors);
            }
            
        } catch (Exception e) {
            logger.error("Failed to validate deployment for flow: " + flowId, e);
            errors.add("Validation failed due to system error: " + e.getMessage());
            result.put("isValid", false);
            result.put("errors", errors);
            result.put("warnings", warnings);
        }
        
        return result;
    }
    
    /**
     * Get deployment status for a flow
     */
    public Map<String, Object> getDeploymentStatus(UUID flowId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        
        logger.debug("Getting deployment status for flow: {}", flowId);
        
        Map<String, Object> status = new HashMap<>();
        
        Optional<DeployedFlow> deployedFlowOpt = deployedFlowRepository.findByFlowId(flowId);
        
        if (deployedFlowOpt.isPresent()) {
            DeployedFlow deployedFlow = deployedFlowOpt.get();
            status.put("isDeployed", DeployedFlow.DeploymentStatus.DEPLOYED == deployedFlow.getDeploymentStatus());
            status.put("deploymentStatus", deployedFlow.getDeploymentStatus());
            status.put("deployedAt", deployedFlow.getDeployedAt());
            status.put("deployedBy", deployedFlow.getDeployedBy());
            status.put("flowVersion", deployedFlow.getFlowVersion());
            
            if (deployedFlow.getUndeployedAt() != null) {
                status.put("undeployedAt", deployedFlow.getUndeployedAt());
                status.put("undeployedBy", deployedFlow.getUndeployedBy());
            }
        } else {
            status.put("isDeployed", false);
            status.put("deploymentStatus", "NOT_DEPLOYED");
        }
        
        return status;
    }
    
    /**
     * Get all deployed flows
     */
    public List<DeployedFlow> getDeployedFlows() {
        logger.debug("Retrieving all deployed flows");
        return deployedFlowRepository.findByDeploymentStatus(DeployedFlow.DeploymentStatus.DEPLOYED);
    }
    
    /**
     * Get deployed flows by package
     */
    public List<DeployedFlow> getDeployedFlowsByPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Retrieving deployed flows for package: {}", packageId);
        // Repository doesn't have package-specific method, filter by package through flow reference
        return deployedFlowRepository.findByDeploymentStatus(DeployedFlow.DeploymentStatus.DEPLOYED)
            .stream()
            .filter(deployed -> {
                Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(deployed.getFlowId());
                return flowOpt.isPresent() && packageId.equals(flowOpt.get().getPackageId());
            })
            .toList();
    }
}