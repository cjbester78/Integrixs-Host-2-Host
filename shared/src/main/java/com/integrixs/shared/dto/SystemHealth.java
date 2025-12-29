package com.integrixs.shared.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * System health status information
 */
public class SystemHealth {
    
    public enum HealthStatus {
        HEALTHY,
        WARNING,
        ERROR,
        UNKNOWN
    }
    
    private HealthStatus status;
    private LocalDateTime timestamp;
    private LocalDateTime lastCheckTime;
    private long uptimeMs;
    private Map<String, Boolean> connectionStatus;
    private int activeOperations;
    private int totalOperationsToday;
    private double successRate;
    private String systemVersion;
    private Map<String, Object> metrics;
    private String lastError;
    
    // Individual health component flags
    private boolean configurationHealthy;
    private boolean diskSpaceHealthy;
    private boolean logsHealthy;
    private boolean overallHealthy;
    
    public SystemHealth() {
        this.timestamp = LocalDateTime.now();
        this.status = HealthStatus.UNKNOWN;
    }
    
    // Getters and setters
    public HealthStatus getStatus() { return status; }
    public void setStatus(HealthStatus status) { this.status = status; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public long getUptimeMs() { return uptimeMs; }
    public void setUptimeMs(long uptimeMs) { this.uptimeMs = uptimeMs; }
    
    public Map<String, Boolean> getConnectionStatus() { return connectionStatus; }
    public void setConnectionStatus(Map<String, Boolean> connectionStatus) { this.connectionStatus = connectionStatus; }
    
    public int getActiveOperations() { return activeOperations; }
    public void setActiveOperations(int activeOperations) { this.activeOperations = activeOperations; }
    
    public int getTotalOperationsToday() { return totalOperationsToday; }
    public void setTotalOperationsToday(int totalOperationsToday) { this.totalOperationsToday = totalOperationsToday; }
    
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    
    public String getSystemVersion() { return systemVersion; }
    public void setSystemVersion(String systemVersion) { this.systemVersion = systemVersion; }
    
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    
    public LocalDateTime getLastCheckTime() { return lastCheckTime; }
    public void setLastCheckTime(LocalDateTime lastCheckTime) { this.lastCheckTime = lastCheckTime; }
    
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    
    public boolean isConfigurationHealthy() { return configurationHealthy; }
    public void setConfigurationHealthy(boolean configurationHealthy) { this.configurationHealthy = configurationHealthy; }
    
    public boolean isDiskSpaceHealthy() { return diskSpaceHealthy; }
    public void setDiskSpaceHealthy(boolean diskSpaceHealthy) { this.diskSpaceHealthy = diskSpaceHealthy; }
    
    public boolean isLogsHealthy() { return logsHealthy; }
    public void setLogsHealthy(boolean logsHealthy) { this.logsHealthy = logsHealthy; }
    
    public boolean isOverallHealthy() { return overallHealthy; }
    public void setOverallHealthy(boolean overallHealthy) { this.overallHealthy = overallHealthy; }
}