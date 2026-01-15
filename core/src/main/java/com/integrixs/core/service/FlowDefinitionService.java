package com.integrixs.core.service;

import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.FlowUtilityRepository;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Facade service for Integration Flow operations
 * Coordinates between specialized services following Facade Pattern
 * 
 * This service replaces the original monolithic FlowDefinitionService
 * and provides a unified interface while delegating to focused services
 */
@Service
public class FlowDefinitionService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowDefinitionService.class);
    
    private final FlowCrudService flowCrudService;
    private final FlowValidationService flowValidationService;
    private final FlowStatisticsService flowStatisticsService;
    private final FlowPackageService flowPackageService;
    private final FlowImportExportService flowImportExportService;
    private final FlowDeploymentService flowDeploymentService;
    private final AdapterRepository adapterRepository;
    private final FlowUtilityRepository utilityRepository;
    
    @Autowired
    public FlowDefinitionService(FlowCrudService flowCrudService,
                                      FlowValidationService flowValidationService,
                                      FlowStatisticsService flowStatisticsService,
                                      FlowPackageService flowPackageService,
                                      FlowImportExportService flowImportExportService,
                                      FlowDeploymentService flowDeploymentService,
                                      AdapterRepository adapterRepository,
                                      FlowUtilityRepository utilityRepository) {
        this.flowCrudService = flowCrudService;
        this.flowValidationService = flowValidationService;
        this.flowStatisticsService = flowStatisticsService;
        this.flowPackageService = flowPackageService;
        this.flowImportExportService = flowImportExportService;
        this.flowDeploymentService = flowDeploymentService;
        this.adapterRepository = adapterRepository;
        this.utilityRepository = utilityRepository;
    }
    
    // === CRUD Operations (delegated to FlowCrudService) ===
    
    public List<IntegrationFlow> getAllFlows() {
        return flowCrudService.getAllFlows();
    }
    
    public List<IntegrationFlow> getActiveFlows() {
        return flowCrudService.getActiveFlows();
    }
    
    public List<IntegrationFlow> getScheduledFlows() {
        return flowCrudService.getScheduledFlows();
    }
    
    public Optional<IntegrationFlow> getFlowById(UUID id) {
        return flowCrudService.getFlowById(id);
    }
    
    public Optional<IntegrationFlow> getFlowByName(String name) {
        return flowCrudService.getFlowByName(name);
    }
    
    public boolean existsByName(String name) {
        return flowCrudService.existsByName(name);
    }
    
    public IntegrationFlow createFlow(IntegrationFlow flow, UUID packageId, UUID createdBy) {
        return flowPackageService.createFlowInPackage(flow, packageId, createdBy);
    }
    
    public IntegrationFlow createFlow(IntegrationFlow flow, UUID createdBy) {
        // For backward compatibility - create without package context
        logger.warn("Creating flow without package context is deprecated");
        return flowCrudService.createFlow(flow, null, createdBy);
    }
    
    public IntegrationFlow updateFlow(UUID id, IntegrationFlow flow, UUID updatedBy) {
        return flowCrudService.updateFlow(id, flow, updatedBy);
    }
    
    public void setFlowActive(UUID id, boolean active) {
        flowCrudService.setFlowActive(id, active);
    }
    
    public boolean deleteFlow(UUID id) {
        return flowCrudService.deleteFlow(id);
    }
    
    // === Validation Operations (delegated to FlowValidationService) ===
    
    public Map<String, Object> validateFlow(UUID id) {
        return flowValidationService.validateFlow(id);
    }
    
    public Map<String, Object> validateFlow(IntegrationFlow flow) {
        return flowValidationService.validateFlow(flow);
    }
    
    public Map<String, Object> validateFlowInPackageContext(UUID flowId, UUID packageId) {
        return flowValidationService.validateFlowInPackageContext(flowId, packageId);
    }
    
    public List<Adapter> getAvailableAdapters() {
        return flowValidationService.getAvailableAdapters();
    }
    
    public List<FlowUtility> getAvailableUtilities() {
        return flowValidationService.getAvailableUtilities();
    }
    
    public List<Adapter> getAvailableAdaptersForPackage(UUID packageId) {
        return flowValidationService.getAvailableAdaptersForPackage(packageId);
    }

    public Map<String, Object> validateFlowActivation(UUID flowId) {
        return flowValidationService.validateFlowActivation(flowId);
    }

    // === Statistics Operations (delegated to FlowStatisticsService) ===
    
    public Map<String, Object> getFlowStatistics() {
        return flowStatisticsService.getFlowStatistics();
    }
    
    public Map<String, Object> getFlowStatisticsByPackage(UUID packageId) {
        return flowStatisticsService.getFlowStatisticsByPackage(packageId);
    }
    
    public FlowStatisticsService.PackageFlowSummary getPackageFlowSummary(UUID packageId) {
        return flowStatisticsService.getPackageFlowSummary(packageId);
    }
    
    public long countFlowsInPackage(UUID packageId) {
        return flowStatisticsService.countFlowsInPackage(packageId);
    }
    
    public Map<String, Object> getDeploymentStatus(UUID flowId) {
        return flowStatisticsService.getDeploymentStatus(flowId);
    }
    
    // === Package Operations (delegated to FlowPackageService) ===
    
    public List<IntegrationFlow> getFlowsByPackageId(UUID packageId) {
        return flowPackageService.getFlowsByPackage(packageId);
    }
    
    public List<IntegrationFlow> getActiveFlowsByPackage(UUID packageId) {
        return flowPackageService.getActiveFlowsByPackage(packageId);
    }
    
    public List<IntegrationFlow> getFlowsByTypeAndPackage(String flowType, UUID packageId) {
        return flowPackageService.getFlowsByTypeAndPackage(flowType, packageId);
    }
    
    public List<IntegrationFlow> getFlowsByPackage(UUID packageId) {
        return flowPackageService.getFlowsByPackage(packageId);
    }
    
    public List<IntegrationFlow> getFlowsByType(String flowType) {
        return flowCrudService.getFlowsByType(flowType);
    }
    
    public List<IntegrationFlow> getScheduledFlowsByPackage(UUID packageId) {
        return flowPackageService.getScheduledFlowsByPackage(packageId);
    }
    
    public boolean existsByNameInPackage(String name, UUID packageId) {
        return flowValidationService.existsByNameInPackage(name, packageId);
    }
    
    public void moveFlowToPackage(UUID flowId, UUID toPackageId, UUID movedBy) {
        flowPackageService.moveFlowToPackage(flowId, toPackageId, movedBy);
    }
    
    public boolean moveFlowBetweenPackages(UUID flowId, UUID sourcePackageId, UUID targetPackageId, UUID userId) {
        return flowPackageService.moveFlowBetweenPackages(flowId, sourcePackageId, targetPackageId, userId);
    }
    
    public IntegrationFlow updateFlowInPackage(UUID flowId, UUID packageId, IntegrationFlow flow, UUID updatedBy) {
        return flowPackageService.updateFlowInPackage(flowId, packageId, flow, updatedBy);
    }
    
    // === Import/Export Operations (delegated to FlowImportExportService) ===
    
    public Map<String, Object> exportFlow(UUID id) {
        FlowImportExportService.FlowExportResult result = flowImportExportService.exportFlow(id);
        return result.getExportData().orElse(new HashMap<>());
    }
    
    public IntegrationFlow importFlow(Map<String, Object> importData, UUID importedBy) {
        FlowImportExportService.FlowImportResult result = flowImportExportService.importFlow(importData, importedBy);

        // CRITICAL: Check if import actually succeeded
        if (!result.isSuccessful()) {
            String errorMsg = result.getErrorMessage().orElse("Unknown import error");
            throw new RuntimeException("Flow import failed: " + errorMsg);
        }

        return result.getImportedFlow().orElseThrow(() ->
            new RuntimeException("Flow import succeeded but no flow was returned"));
    }
    
    // === Deployment Operations (delegated to FlowDeploymentService) ===
    
    public Map<String, Object> deployFlow(UUID flowId, UUID deployedBy) {
        return flowDeploymentService.deployFlow(flowId, deployedBy);
    }
    
    public Map<String, Object> undeployFlow(UUID flowId, UUID undeployedBy) {
        return flowDeploymentService.undeployFlow(flowId, undeployedBy);
    }
    
    public Map<String, Object> validateDeployment(UUID flowId) {
        return flowDeploymentService.validateDeployment(flowId);
    }
    
    // === Backward Compatibility Methods ===
    // These methods maintain the original API for existing controllers
    
    /**
     * Legacy method for package flow summary
     * @deprecated Use getPackageFlowSummary instead
     */
    @Deprecated
    public Map<String, Object> getPackageFlowSummaryAsMap(UUID packageId) {
        FlowStatisticsService.PackageFlowSummary summary = getPackageFlowSummary(packageId);
        Map<String, Object> result = new HashMap<>();
        result.put("packageId", summary.getPackageId());
        result.put("totalFlows", summary.getTotalFlows());
        result.put("activeFlows", summary.getActiveFlows());
        result.put("scheduledFlows", summary.getScheduledFlows());
        result.put("deployedFlows", summary.getDeployedFlows());
        result.put("flowsByType", summary.getFlowsByType());
        result.put("activePercentage", summary.getActivePercentage());
        result.put("deployedPercentage", summary.getDeployedPercentage());
        return result;
    }
}