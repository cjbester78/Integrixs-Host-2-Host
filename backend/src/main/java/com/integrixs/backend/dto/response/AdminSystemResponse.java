package com.integrixs.backend.dto.response;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable response DTO for administrative system operations.
 * Contains system health, metrics, logs, and operational data.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class AdminSystemResponse {
    
    private final String operation;
    private final String status;
    private final Map<String, Object> systemHealth;
    private final Map<String, Object> metrics;
    private final List<Map<String, Object>> logs;
    private final Map<String, Object> statistics;
    private final Map<String, Object> cleanupResult;
    private final Integer totalRecords;
    private final LocalDateTime timestamp;
    private final String exportFormat;
    private final String exportData;
    
    private AdminSystemResponse(Builder builder) {
        this.operation = builder.operation;
        this.status = builder.status;
        this.systemHealth = builder.systemHealth != null ? 
            Collections.unmodifiableMap(builder.systemHealth) : Collections.emptyMap();
        this.metrics = builder.metrics != null ? 
            Collections.unmodifiableMap(builder.metrics) : Collections.emptyMap();
        this.logs = builder.logs != null ? 
            Collections.unmodifiableList(builder.logs) : Collections.emptyList();
        this.statistics = builder.statistics != null ? 
            Collections.unmodifiableMap(builder.statistics) : Collections.emptyMap();
        this.cleanupResult = builder.cleanupResult != null ? 
            Collections.unmodifiableMap(builder.cleanupResult) : Collections.emptyMap();
        this.totalRecords = builder.totalRecords;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.exportFormat = builder.exportFormat;
        this.exportData = builder.exportData;
    }
    
    // Getters
    public String getOperation() { return operation; }
    public String getStatus() { return status; }
    public Map<String, Object> getSystemHealth() { return systemHealth; }
    public Map<String, Object> getMetrics() { return metrics; }
    public List<Map<String, Object>> getLogs() { return logs; }
    public Map<String, Object> getStatistics() { return statistics; }
    public Map<String, Object> getCleanupResult() { return cleanupResult; }
    public Integer getTotalRecords() { return totalRecords; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getExportFormat() { return exportFormat; }
    public String getExportData() { return exportData; }
    
    /**
     * Check if system health is good.
     */
    public boolean isSystemHealthy() {
        return "HEALTHY".equalsIgnoreCase(status) || 
               (systemHealth.containsKey("status") && "UP".equals(systemHealth.get("status")));
    }
    
    /**
     * Check if operation was successful.
     */
    public boolean isOperationSuccessful() {
        return "SUCCESS".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status);
    }
    
    /**
     * Check if response contains logs.
     */
    public boolean hasLogs() {
        return !logs.isEmpty();
    }
    
    /**
     * Check if response contains metrics.
     */
    public boolean hasMetrics() {
        return !metrics.isEmpty();
    }
    
    /**
     * Check if response is for export operation.
     */
    public boolean isExportResponse() {
        return exportFormat != null && exportData != null;
    }
    
    /**
     * Get health status summary.
     */
    public String getHealthStatusSummary() {
        if (systemHealth.containsKey("overallStatus")) {
            return systemHealth.get("overallStatus").toString();
        }
        return isSystemHealthy() ? "HEALTHY" : "DEGRADED";
    }
    
    /**
     * Get logs count.
     */
    public int getLogsCount() {
        return logs.size();
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create health response.
     */
    public static AdminSystemResponse healthResponse(Map<String, Object> healthData) {
        return builder()
            .operation("health")
            .status("HEALTHY")
            .systemHealth(healthData)
            .build();
    }
    
    /**
     * Create metrics response.
     */
    public static AdminSystemResponse metricsResponse(Map<String, Object> metricsData) {
        return builder()
            .operation("metrics")
            .status("SUCCESS")
            .metrics(metricsData)
            .build();
    }
    
    /**
     * Create logs response.
     */
    public static AdminSystemResponse logsResponse(List<Map<String, Object>> logsData, int totalRecords) {
        return builder()
            .operation("logs")
            .status("SUCCESS")
            .logs(logsData)
            .totalRecords(totalRecords)
            .build();
    }
    
    /**
     * Create export response.
     */
    public static AdminSystemResponse exportResponse(String format, String data, int totalRecords) {
        return builder()
            .operation("export")
            .status("SUCCESS")
            .exportFormat(format)
            .exportData(data)
            .totalRecords(totalRecords)
            .build();
    }
    
    /**
     * Create statistics response.
     */
    public static AdminSystemResponse statisticsResponse(Map<String, Object> statsData) {
        return builder()
            .operation("statistics")
            .status("SUCCESS")
            .statistics(statsData)
            .build();
    }
    
    /**
     * Create cleanup response.
     */
    public static AdminSystemResponse cleanupResponse(Map<String, Object> cleanupData) {
        return builder()
            .operation("cleanup")
            .status("SUCCESS")
            .cleanupResult(cleanupData)
            .build();
    }
    
    /**
     * Builder for AdminSystemResponse.
     */
    public static class Builder {
        private String operation;
        private String status;
        private Map<String, Object> systemHealth;
        private Map<String, Object> metrics;
        private List<Map<String, Object>> logs;
        private Map<String, Object> statistics;
        private Map<String, Object> cleanupResult;
        private Integer totalRecords;
        private LocalDateTime timestamp;
        private String exportFormat;
        private String exportData;
        
        private Builder() {}
        
        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        
        public Builder systemHealth(Map<String, Object> systemHealth) {
            this.systemHealth = systemHealth;
            return this;
        }
        
        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder logs(List<Map<String, Object>> logs) {
            this.logs = logs;
            return this;
        }
        
        public Builder statistics(Map<String, Object> statistics) {
            this.statistics = statistics;
            return this;
        }
        
        public Builder cleanupResult(Map<String, Object> cleanupResult) {
            this.cleanupResult = cleanupResult;
            return this;
        }
        
        public Builder totalRecords(Integer totalRecords) {
            this.totalRecords = totalRecords;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder exportFormat(String exportFormat) {
            this.exportFormat = exportFormat;
            return this;
        }
        
        public Builder exportData(String exportData) {
            this.exportData = exportData;
            return this;
        }
        
        public AdminSystemResponse build() {
            return new AdminSystemResponse(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("AdminSystemResponse{operation='%s', status='%s', " +
                           "totalRecords=%d, hasLogs=%s, hasMetrics=%s, timestamp=%s}", 
                           operation, status, totalRecords, hasLogs(), hasMetrics(), timestamp);
    }
}