package com.integrixs.shared.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DeployedFlow entity representing the execution registry
 * This table determines which flows can be executed and tracks their runtime state
 */
public class DeployedFlow {
    
    private UUID id;
    private UUID flowId;
    private String flowName; // Snapshot at deployment time
    private Integer flowVersion; // Version at deployment time
    
    // Deployment status and metadata
    private DeploymentStatus deploymentStatus;
    private String deploymentEnvironment;
    
    // Adapter links for execution control
    private UUID senderAdapterId;
    private String senderAdapterName; // Snapshot for quick reference
    private UUID receiverAdapterId;
    private String receiverAdapterName; // Snapshot for quick reference
    
    // Runtime execution control
    private Boolean executionEnabled;
    private RuntimeStatus runtimeStatus;
    
    // Execution constraints
    private Integer maxConcurrentExecutions;
    private Integer executionTimeoutMinutes;
    private Map<String, Object> retryPolicy;
    
    // Performance tracking for deployed instance
    private Long totalExecutions;
    private Long successfulExecutions;
    private Long failedExecutions;
    private LocalDateTime lastExecutionAt;
    private String lastExecutionStatus;
    private Long lastExecutionDurationMs;
    private Long averageExecutionTimeMs;
    
    // Error tracking
    private LocalDateTime lastErrorAt;
    private String lastErrorMessage;
    private Integer consecutiveFailures;
    
    // Configuration snapshots at deployment time
    private Map<String, Object> flowConfiguration; // Complete flow definition snapshot
    private Map<String, Object> senderAdapterConfig; // Inbound adapter configuration snapshot
    private Map<String, Object> receiverAdapterConfig; // Outbound adapter configuration snapshot
    
    // Deployment audit
    private LocalDateTime deployedAt;
    private UUID deployedBy;
    private LocalDateTime undeployedAt;
    private UUID undeployedBy;
    private String deploymentNotes;
    
    // Health monitoring
    private Boolean healthCheckEnabled;
    private LocalDateTime lastHealthCheckAt;
    private HealthStatus healthCheckStatus;
    private String healthCheckMessage;
    
    // Note: Using flow-specific deployment tracking instead of generic audit fields
    // - deployed_at/deployed_by replaces created_at/created_by
    // - undeployed_at/undeployed_by tracks undeployment
    // - No need for updated_at/updated_by since flows are deployed or undeployed, not updated
    
    // Enums
    public enum DeploymentStatus {
        DEPLOYED("Flow is deployed and can execute"),
        UNDEPLOYED("Flow is not deployed"),
        DEPLOYING("Flow deployment in progress"),
        UNDEPLOYING("Flow undeployment in progress"),
        FAILED("Deployment failed"),
        SUSPENDED("Flow execution suspended");
        
        private final String description;
        
        DeploymentStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean canExecute() {
            return this == DEPLOYED;
        }
    }
    
    public enum RuntimeStatus {
        ACTIVE("Flow is active and ready to execute"),
        INACTIVE("Flow is inactive"),
        ERROR("Flow has runtime errors"),
        STARTING("Flow is starting up"),
        STOPPING("Flow is shutting down"),
        MAINTENANCE("Flow is in maintenance mode");
        
        private final String description;
        
        RuntimeStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean canExecute() {
            return this == ACTIVE;
        }
    }
    
    public enum HealthStatus {
        HEALTHY("Flow is healthy"),
        UNHEALTHY("Flow has health issues"),
        WARNING("Flow has warnings"),
        UNKNOWN("Health status unknown");
        
        private final String description;
        
        HealthStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Constructors
    public DeployedFlow() {
        this.deploymentStatus = DeploymentStatus.DEPLOYING;
        this.deploymentEnvironment = "production";
        this.executionEnabled = true;
        this.runtimeStatus = RuntimeStatus.INACTIVE;
        this.maxConcurrentExecutions = 1;
        this.executionTimeoutMinutes = 60;
        this.totalExecutions = 0L;
        this.successfulExecutions = 0L;
        this.failedExecutions = 0L;
        this.averageExecutionTimeMs = 0L;
        this.consecutiveFailures = 0;
        this.healthCheckEnabled = true;
        this.healthCheckStatus = HealthStatus.UNKNOWN;
        // Initialize deployment timestamp when object is created
        // (will be set properly when actually deployed)
    }
    
    public DeployedFlow(UUID flowId, String flowName, Integer flowVersion, 
                       UUID senderAdapterId, String senderAdapterName, UUID deployedBy) {
        this();
        this.flowId = flowId;
        this.flowName = flowName;
        this.flowVersion = flowVersion;
        this.senderAdapterId = senderAdapterId;
        this.senderAdapterName = senderAdapterName;
        this.deployedBy = deployedBy;
        this.deployedAt = LocalDateTime.now();
    }
    
    // Business logic methods
    public boolean canExecute() {
        return deploymentStatus != null && deploymentStatus.canExecute() &&
               runtimeStatus != null && runtimeStatus.canExecute() &&
               executionEnabled != null && executionEnabled;
    }
    
    public boolean isHealthy() {
        return healthCheckStatus == HealthStatus.HEALTHY;
    }
    
    public boolean hasErrors() {
        return runtimeStatus == RuntimeStatus.ERROR || 
               healthCheckStatus == HealthStatus.UNHEALTHY ||
               (consecutiveFailures != null && consecutiveFailures > 0);
    }
    
    public double getSuccessRate() {
        if (totalExecutions == null || totalExecutions == 0) {
            return 100.0;
        }
        return (double) (successfulExecutions != null ? successfulExecutions : 0) / totalExecutions * 100.0;
    }
    
    public void recordExecution(long executionDurationMs, boolean successful) {
        this.totalExecutions = (this.totalExecutions != null ? this.totalExecutions : 0) + 1;
        this.lastExecutionAt = LocalDateTime.now();
        this.lastExecutionDurationMs = executionDurationMs;
        
        if (successful) {
            this.successfulExecutions = (this.successfulExecutions != null ? this.successfulExecutions : 0) + 1;
            this.consecutiveFailures = 0;
            this.lastExecutionStatus = "SUCCESS";
        } else {
            this.failedExecutions = (this.failedExecutions != null ? this.failedExecutions : 0) + 1;
            this.consecutiveFailures = (this.consecutiveFailures != null ? this.consecutiveFailures : 0) + 1;
            this.lastExecutionStatus = "FAILED";
        }
        
        // Update average execution time
        if (this.averageExecutionTimeMs == null || this.averageExecutionTimeMs == 0) {
            this.averageExecutionTimeMs = executionDurationMs;
        } else {
            // Weighted average (80% previous + 20% current)
            this.averageExecutionTimeMs = (long) (this.averageExecutionTimeMs * 0.8 + executionDurationMs * 0.2);
        }
        
        // Execution recording tracked via lastExecutionAt timestamp
    }
    
    public void recordError(String errorMessage) {
        this.lastErrorAt = LocalDateTime.now();
        this.lastErrorMessage = errorMessage;
        this.consecutiveFailures = (this.consecutiveFailures != null ? this.consecutiveFailures : 0) + 1;
        this.runtimeStatus = RuntimeStatus.ERROR;
        // Error tracking via lastErrorAt timestamp
    }
    
    public void clearErrors() {
        this.lastErrorAt = null;
        this.lastErrorMessage = null;
        this.consecutiveFailures = 0;
        if (this.runtimeStatus == RuntimeStatus.ERROR) {
            this.runtimeStatus = RuntimeStatus.ACTIVE;
        }
        // Error clearing tracked via runtime status change
    }
    
    public void updateHealthStatus(HealthStatus status, String message) {
        this.healthCheckStatus = status;
        this.healthCheckMessage = message;
        this.lastHealthCheckAt = LocalDateTime.now();
        // Health update tracked via lastHealthCheckAt timestamp
    }
    
    public String getFormattedAverageExecutionTime() {
        if (averageExecutionTimeMs == null || averageExecutionTimeMs == 0) {
            return "0ms";
        }
        
        if (averageExecutionTimeMs < 1000) {
            return averageExecutionTimeMs + "ms";
        } else if (averageExecutionTimeMs < 60000) {
            return String.format("%.1fs", averageExecutionTimeMs / 1000.0);
        } else {
            long minutes = averageExecutionTimeMs / 60000;
            long seconds = (averageExecutionTimeMs % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    public String getDisplayName() {
        return String.format("%s (v%d) - %s", flowName, flowVersion, deploymentEnvironment);
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getFlowId() {
        return flowId;
    }
    
    public void setFlowId(UUID flowId) {
        this.flowId = flowId;
    }
    
    public String getFlowName() {
        return flowName;
    }
    
    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }
    
    public Integer getFlowVersion() {
        return flowVersion;
    }
    
    public void setFlowVersion(Integer flowVersion) {
        this.flowVersion = flowVersion;
    }
    
    public DeploymentStatus getDeploymentStatus() {
        return deploymentStatus;
    }
    
    public void setDeploymentStatus(DeploymentStatus deploymentStatus) {
        this.deploymentStatus = deploymentStatus;
        // Status change tracked via deployment-specific timestamps
    }
    
    public String getDeploymentEnvironment() {
        return deploymentEnvironment;
    }
    
    public void setDeploymentEnvironment(String deploymentEnvironment) {
        this.deploymentEnvironment = deploymentEnvironment;
    }
    
    public UUID getSenderAdapterId() {
        return senderAdapterId;
    }
    
    public void setSenderAdapterId(UUID senderAdapterId) {
        this.senderAdapterId = senderAdapterId;
    }
    
    public String getSenderAdapterName() {
        return senderAdapterName;
    }
    
    public void setSenderAdapterName(String senderAdapterName) {
        this.senderAdapterName = senderAdapterName;
    }
    
    public UUID getReceiverAdapterId() {
        return receiverAdapterId;
    }
    
    public void setReceiverAdapterId(UUID receiverAdapterId) {
        this.receiverAdapterId = receiverAdapterId;
    }
    
    public String getReceiverAdapterName() {
        return receiverAdapterName;
    }
    
    public void setReceiverAdapterName(String receiverAdapterName) {
        this.receiverAdapterName = receiverAdapterName;
    }
    
    public Boolean getExecutionEnabled() {
        return executionEnabled;
    }
    
    public void setExecutionEnabled(Boolean executionEnabled) {
        this.executionEnabled = executionEnabled;
        // Execution status tracked via deployment-specific timestamps
    }
    
    public RuntimeStatus getRuntimeStatus() {
        return runtimeStatus;
    }
    
    public void setRuntimeStatus(RuntimeStatus runtimeStatus) {
        this.runtimeStatus = runtimeStatus;
        // Runtime status tracked via deployment-specific timestamps
    }
    
    public Integer getMaxConcurrentExecutions() {
        return maxConcurrentExecutions;
    }
    
    public void setMaxConcurrentExecutions(Integer maxConcurrentExecutions) {
        this.maxConcurrentExecutions = maxConcurrentExecutions;
    }
    
    public Integer getExecutionTimeoutMinutes() {
        return executionTimeoutMinutes;
    }
    
    public void setExecutionTimeoutMinutes(Integer executionTimeoutMinutes) {
        this.executionTimeoutMinutes = executionTimeoutMinutes;
    }
    
    public Map<String, Object> getRetryPolicy() {
        return retryPolicy;
    }
    
    public void setRetryPolicy(Map<String, Object> retryPolicy) {
        this.retryPolicy = retryPolicy;
    }
    
    public Long getTotalExecutions() {
        return totalExecutions;
    }
    
    public void setTotalExecutions(Long totalExecutions) {
        this.totalExecutions = totalExecutions;
    }
    
    public Long getSuccessfulExecutions() {
        return successfulExecutions;
    }
    
    public void setSuccessfulExecutions(Long successfulExecutions) {
        this.successfulExecutions = successfulExecutions;
    }
    
    public Long getFailedExecutions() {
        return failedExecutions;
    }
    
    public void setFailedExecutions(Long failedExecutions) {
        this.failedExecutions = failedExecutions;
    }
    
    public LocalDateTime getLastExecutionAt() {
        return lastExecutionAt;
    }
    
    public void setLastExecutionAt(LocalDateTime lastExecutionAt) {
        this.lastExecutionAt = lastExecutionAt;
    }
    
    public String getLastExecutionStatus() {
        return lastExecutionStatus;
    }
    
    public void setLastExecutionStatus(String lastExecutionStatus) {
        this.lastExecutionStatus = lastExecutionStatus;
    }
    
    public Long getLastExecutionDurationMs() {
        return lastExecutionDurationMs;
    }
    
    public void setLastExecutionDurationMs(Long lastExecutionDurationMs) {
        this.lastExecutionDurationMs = lastExecutionDurationMs;
    }
    
    public Long getAverageExecutionTimeMs() {
        return averageExecutionTimeMs;
    }
    
    public void setAverageExecutionTimeMs(Long averageExecutionTimeMs) {
        this.averageExecutionTimeMs = averageExecutionTimeMs;
    }
    
    public LocalDateTime getLastErrorAt() {
        return lastErrorAt;
    }
    
    public void setLastErrorAt(LocalDateTime lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }
    
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
    
    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }
    
    public Integer getConsecutiveFailures() {
        return consecutiveFailures;
    }
    
    public void setConsecutiveFailures(Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }
    
    public Map<String, Object> getFlowConfiguration() {
        return flowConfiguration;
    }
    
    public void setFlowConfiguration(Map<String, Object> flowConfiguration) {
        this.flowConfiguration = flowConfiguration;
    }
    
    public Map<String, Object> getSenderAdapterConfig() {
        return senderAdapterConfig;
    }
    
    public void setSenderAdapterConfig(Map<String, Object> senderAdapterConfig) {
        this.senderAdapterConfig = senderAdapterConfig;
    }
    
    public Map<String, Object> getReceiverAdapterConfig() {
        return receiverAdapterConfig;
    }
    
    public void setReceiverAdapterConfig(Map<String, Object> receiverAdapterConfig) {
        this.receiverAdapterConfig = receiverAdapterConfig;
    }
    
    public LocalDateTime getDeployedAt() {
        return deployedAt;
    }
    
    public void setDeployedAt(LocalDateTime deployedAt) {
        this.deployedAt = deployedAt;
    }
    
    public UUID getDeployedBy() {
        return deployedBy;
    }
    
    public void setDeployedBy(UUID deployedBy) {
        this.deployedBy = deployedBy;
    }
    
    public LocalDateTime getUndeployedAt() {
        return undeployedAt;
    }
    
    public void setUndeployedAt(LocalDateTime undeployedAt) {
        this.undeployedAt = undeployedAt;
    }
    
    public UUID getUndeployedBy() {
        return undeployedBy;
    }
    
    public void setUndeployedBy(UUID undeployedBy) {
        this.undeployedBy = undeployedBy;
    }
    
    public String getDeploymentNotes() {
        return deploymentNotes;
    }
    
    public void setDeploymentNotes(String deploymentNotes) {
        this.deploymentNotes = deploymentNotes;
    }
    
    public Boolean getHealthCheckEnabled() {
        return healthCheckEnabled;
    }
    
    public void setHealthCheckEnabled(Boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }
    
    public LocalDateTime getLastHealthCheckAt() {
        return lastHealthCheckAt;
    }
    
    public void setLastHealthCheckAt(LocalDateTime lastHealthCheckAt) {
        this.lastHealthCheckAt = lastHealthCheckAt;
    }
    
    public HealthStatus getHealthCheckStatus() {
        return healthCheckStatus;
    }
    
    public void setHealthCheckStatus(HealthStatus healthCheckStatus) {
        this.healthCheckStatus = healthCheckStatus;
    }
    
    public String getHealthCheckMessage() {
        return healthCheckMessage;
    }
    
    public void setHealthCheckMessage(String healthCheckMessage) {
        this.healthCheckMessage = healthCheckMessage;
    }
    
    // Removed redundant audit fields (createdAt, updatedAt, updatedBy)
    // Use flow-specific deployment tracking fields instead:
    // - getDeployedAt() instead of getCreatedAt()
    // - getDeployedBy() instead of getCreatedBy() 
    // - getUndeployedAt() and getUndeployedBy() for undeployment tracking
    
    @Override
    public String toString() {
        return "DeployedFlow{" +
                "id=" + id +
                ", flowName='" + flowName + '\'' +
                ", flowVersion=" + flowVersion +
                ", deploymentStatus=" + deploymentStatus +
                ", runtimeStatus=" + runtimeStatus +
                ", environment='" + deploymentEnvironment + '\'' +
                ", executionEnabled=" + executionEnabled +
                ", totalExecutions=" + totalExecutions +
                ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                ", deployedAt=" + deployedAt +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeployedFlow that = (DeployedFlow) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}