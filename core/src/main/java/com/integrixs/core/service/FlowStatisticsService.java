package com.integrixs.core.service;

import com.integrixs.core.repository.DeployedFlowRepository;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.shared.model.DeployedFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating statistics and analytics for Integration Flows
 * Handles all statistical calculations following Single Responsibility Principle
 */
@Service
public class FlowStatisticsService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowStatisticsService.class);
    
    private final FlowCrudService flowCrudService;
    private final DeployedFlowRepository deployedFlowRepository;
    
    @Autowired
    public FlowStatisticsService(FlowCrudService flowCrudService,
                                DeployedFlowRepository deployedFlowRepository) {
        this.flowCrudService = flowCrudService;
        this.deployedFlowRepository = deployedFlowRepository;
    }
    
    /**
     * Get overall flow statistics
     */
    public Map<String, Object> getFlowStatistics() {
        logger.debug("Retrieving overall flow statistics");
        
        List<IntegrationFlow> allFlows = flowCrudService.getAllFlows();
        List<IntegrationFlow> activeFlows = flowCrudService.getActiveFlows();
        List<IntegrationFlow> scheduledFlows = flowCrudService.getScheduledFlows();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFlows", allFlows.size());
        stats.put("activeFlows", activeFlows.size());
        stats.put("scheduledFlows", scheduledFlows.size());
        stats.put("inactiveFlows", allFlows.size() - activeFlows.size());
        
        // Calculate deployment statistics
        long deployedFlows = allFlows.stream()
            .mapToLong(flow -> {
                Optional<DeployedFlow> deployed = deployedFlowRepository.findByFlowId(flow.getId());
                return deployed.isPresent() ? 1 : 0;
            }).sum();
        
        stats.put("deployedFlows", deployedFlows);
        stats.put("undeployedFlows", allFlows.size() - deployedFlows);
        
        // Flow type distribution
        Map<String, Long> flowsByType = allFlows.stream()
            .collect(Collectors.groupingBy(
                flow -> flow.getFlowType() != null ? flow.getFlowType() : "Unknown",
                Collectors.counting()
            ));
        stats.put("flowsByType", flowsByType);
        
        // Calculate percentages
        if (allFlows.size() > 0) {
            stats.put("activePercentage", (double) activeFlows.size() / allFlows.size() * 100);
            stats.put("deployedPercentage", (double) deployedFlows / allFlows.size() * 100);
            stats.put("scheduledPercentage", (double) scheduledFlows.size() / allFlows.size() * 100);
        } else {
            stats.put("activePercentage", 0.0);
            stats.put("deployedPercentage", 0.0);
            stats.put("scheduledPercentage", 0.0);
        }
        
        return stats;
    }
    
    /**
     * Get flow statistics by package
     */
    public Map<String, Object> getFlowStatisticsByPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Retrieving flow statistics for package: {}", packageId);
        
        List<IntegrationFlow> flows = flowCrudService.getFlowsByPackageId(packageId);
        Map<String, Object> statistics = new HashMap<>();
        
        statistics.put("packageId", packageId);
        statistics.put("totalFlows", flows.size());
        statistics.put("activeFlows", flows.stream().mapToInt(f -> f.getActive() ? 1 : 0).sum());
        statistics.put("scheduledFlows", flows.stream().mapToInt(f -> f.getScheduleEnabled() ? 1 : 0).sum());
        statistics.put("byType", flows.stream()
            .collect(Collectors.groupingBy(
                flow -> flow.getFlowType() != null ? flow.getFlowType() : "Unknown",
                Collectors.counting())));
        
        // Count deployed flows
        long deployedFlows = flows.stream()
            .mapToLong(f -> {
                Optional<DeployedFlow> deployed = deployedFlowRepository.findByFlowId(f.getId());
                return deployed.isPresent() ? 1 : 0;
            }).sum();
        statistics.put("deployedFlows", deployedFlows);
        
        // Calculate health metrics
        statistics.put("healthScore", calculatePackageHealthScore(flows));
        
        return statistics;
    }
    
    /**
     * Get package flow summary
     */
    public PackageFlowSummary getPackageFlowSummary(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Retrieving flow summary for package: {}", packageId);
        
        List<IntegrationFlow> flows = flowCrudService.getFlowsByPackageId(packageId);
        
        long totalFlows = flows.size();
        long activeFlows = flows.stream().mapToLong(f -> f.getActive() ? 1 : 0).sum();
        long scheduledFlows = flows.stream().mapToLong(f -> f.getScheduleEnabled() ? 1 : 0).sum();
        
        // Count deployed flows
        long deployedFlows = flows.stream()
            .mapToLong(f -> {
                Optional<DeployedFlow> deployed = deployedFlowRepository.findByFlowId(f.getId());
                return deployed.isPresent() ? 1 : 0;
            }).sum();
        
        // Flow type distribution
        Map<String, Long> flowsByType = flows.stream()
            .collect(Collectors.groupingBy(
                flow -> flow.getFlowType() != null ? flow.getFlowType() : "Unknown",
                Collectors.counting()
            ));
        
        // Mock execution statistics (would come from execution service in real implementation)
        long totalExecutions = 0;
        long successfulExecutions = 0;
        double successRate = totalExecutions > 0 ? (double) successfulExecutions / totalExecutions * 100 : 0;
        
        return new PackageFlowSummary(
            packageId, totalFlows, activeFlows, scheduledFlows, deployedFlows,
            flowsByType, totalExecutions, successfulExecutions, successRate
        );
    }
    
    /**
     * Count flows in a package
     */
    public long countFlowsInPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        logger.debug("Counting flows in package: {}", packageId);
        return flowCrudService.getFlowsByPackageId(packageId).size();
    }
    
    /**
     * Get deployment status for a flow
     */
    public Map<String, Object> getDeploymentStatus(UUID flowId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null");
        logger.debug("Retrieving deployment status for flow: {}", flowId);
        
        Optional<IntegrationFlow> flowOpt = flowCrudService.getFlowById(flowId);
        if (!flowOpt.isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("flowExists", false);
            result.put("isDeployed", false);
            return result;
        }
        
        IntegrationFlow flow = flowOpt.get();
        Optional<DeployedFlow> deployedOpt = deployedFlowRepository.findByFlowId(flowId);
        
        Map<String, Object> status = new HashMap<>();
        status.put("flowExists", true);
        status.put("flowName", flow.getName());
        status.put("flowActive", flow.getActive());
        status.put("isDeployed", deployedOpt.isPresent());
        
        if (deployedOpt.isPresent()) {
            DeployedFlow deployed = deployedOpt.get();
            status.put("deployedAt", deployed.getDeployedAt());
            status.put("deployedBy", deployed.getDeployedBy());
            status.put("deploymentStatus", deployed.getDeploymentStatus());
        }
        
        return status;
    }
    
    // Private helper methods
    
    private double calculatePackageHealthScore(List<IntegrationFlow> flows) {
        if (flows.isEmpty()) {
            return 100.0; // Empty package is considered healthy
        }
        
        double score = 100.0;
        
        // Deduct points for inactive flows
        long inactiveFlows = flows.stream().mapToLong(f -> f.getActive() ? 0 : 1).sum();
        score -= (inactiveFlows * 10.0 / flows.size());
        
        // Deduct points for unscheduled flows that should be scheduled
        long unscheduledFlows = flows.stream().mapToLong(f -> f.getScheduleEnabled() ? 0 : 1).sum();
        score -= (unscheduledFlows * 5.0 / flows.size());
        
        return Math.max(0.0, score);
    }
    
    /**
     * Inner class for package flow summary
     */
    public static class PackageFlowSummary {
        private final UUID packageId;
        private final long totalFlows;
        private final long activeFlows;
        private final long scheduledFlows;
        private final long deployedFlows;
        private final Map<String, Long> flowsByType;
        private final long totalExecutions;
        private final long successfulExecutions;
        private final double successRate;
        
        public PackageFlowSummary(UUID packageId, long totalFlows, long activeFlows, 
                                 long scheduledFlows, long deployedFlows, Map<String, Long> flowsByType,
                                 long totalExecutions, long successfulExecutions, double successRate) {
            this.packageId = packageId;
            this.totalFlows = totalFlows;
            this.activeFlows = activeFlows;
            this.scheduledFlows = scheduledFlows;
            this.deployedFlows = deployedFlows;
            this.flowsByType = flowsByType != null ? new HashMap<>(flowsByType) : new HashMap<>();
            this.totalExecutions = totalExecutions;
            this.successfulExecutions = successfulExecutions;
            this.successRate = successRate;
        }
        
        // Getters
        public UUID getPackageId() { return packageId; }
        public long getTotalFlows() { return totalFlows; }
        public long getActiveFlows() { return activeFlows; }
        public long getScheduledFlows() { return scheduledFlows; }
        public long getDeployedFlows() { return deployedFlows; }
        public long getInactiveFlows() { return totalFlows - activeFlows; }
        public long getUnscheduledFlows() { return totalFlows - scheduledFlows; }
        public long getUndeployedFlows() { return totalFlows - deployedFlows; }
        public Map<String, Long> getFlowsByType() { return flowsByType; }
        public long getTotalExecutions() { return totalExecutions; }
        public long getSuccessfulExecutions() { return successfulExecutions; }
        public long getFailedExecutions() { return totalExecutions - successfulExecutions; }
        public double getSuccessRate() { return successRate; }
        
        // Helper methods
        public boolean hasFlows() { return totalFlows > 0; }
        public boolean allFlowsActive() { return totalFlows > 0 && activeFlows == totalFlows; }
        public boolean allFlowsScheduled() { return totalFlows > 0 && scheduledFlows == totalFlows; }
        public boolean allFlowsDeployed() { return totalFlows > 0 && deployedFlows == totalFlows; }
        public double getActivePercentage() { 
            return totalFlows > 0 ? (double) activeFlows / totalFlows * 100 : 0; 
        }
        public double getDeployedPercentage() { 
            return totalFlows > 0 ? (double) deployedFlows / totalFlows * 100 : 0; 
        }
        public double getScheduledPercentage() { 
            return totalFlows > 0 ? (double) scheduledFlows / totalFlows * 100 : 0; 
        }
    }
}