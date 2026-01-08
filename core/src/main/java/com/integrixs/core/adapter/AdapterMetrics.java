package com.integrixs.core.adapter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Interface for adapter metrics collection capabilities.
 * Provides comprehensive metrics with immutable result objects.
 * Follows OOP principles with proper encapsulation and type safety.
 */
public interface AdapterMetrics {
    
    /**
     * Record adapter execution metrics.
     * 
     * @param executionMetrics the metrics to record
     */
    void recordExecution(AdapterExecutionMetrics executionMetrics);
    
    /**
     * Get current adapter metrics summary.
     * 
     * @return immutable metrics summary
     */
    AdapterMetricsSummary getMetricsSummary();
    
    /**
     * Reset all metrics counters.
     */
    void resetMetrics();
    
    /**
     * Immutable adapter execution metrics
     */
    class AdapterExecutionMetrics {
        private final String adapterId;
        private final String adapterType;
        private final String operationType;
        private final LocalDateTime executionTime;
        private final long durationMillis;
        private final boolean successful;
        private final long bytesProcessed;
        private final int filesProcessed;
        private final String errorMessage;
        private final Map<String, Object> customMetrics;
        
        private AdapterExecutionMetrics(String adapterId, String adapterType, String operationType,
                                     LocalDateTime executionTime, long durationMillis, boolean successful,
                                     long bytesProcessed, int filesProcessed, String errorMessage,
                                     Map<String, Object> customMetrics) {
            this.adapterId = adapterId;
            this.adapterType = adapterType;
            this.operationType = operationType;
            this.executionTime = executionTime;
            this.durationMillis = durationMillis;
            this.successful = successful;
            this.bytesProcessed = bytesProcessed;
            this.filesProcessed = filesProcessed;
            this.errorMessage = errorMessage;
            this.customMetrics = customMetrics != null ? Map.copyOf(customMetrics) : Map.of();
        }
        
        public String getAdapterId() { return adapterId; }
        public String getAdapterType() { return adapterType; }
        public String getOperationType() { return operationType; }
        public LocalDateTime getExecutionTime() { return executionTime; }
        public long getDurationMillis() { return durationMillis; }
        public boolean isSuccessful() { return successful; }
        public long getBytesProcessed() { return bytesProcessed; }
        public int getFilesProcessed() { return filesProcessed; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, Object> getCustomMetrics() { return customMetrics; }
        
        public static Builder builder(String adapterId, String adapterType, String operationType) {
            return new Builder(adapterId, adapterType, operationType);
        }
        
        public static class Builder {
            private final String adapterId;
            private final String adapterType;
            private final String operationType;
            private LocalDateTime executionTime = LocalDateTime.now();
            private long durationMillis = 0;
            private boolean successful = true;
            private long bytesProcessed = 0;
            private int filesProcessed = 0;
            private String errorMessage = null;
            private Map<String, Object> customMetrics = null;
            
            private Builder(String adapterId, String adapterType, String operationType) {
                this.adapterId = adapterId;
                this.adapterType = adapterType;
                this.operationType = operationType;
            }
            
            public Builder executionTime(LocalDateTime executionTime) {
                this.executionTime = executionTime;
                return this;
            }
            
            public Builder duration(long durationMillis) {
                this.durationMillis = durationMillis;
                return this;
            }
            
            public Builder successful(boolean successful) {
                this.successful = successful;
                return this;
            }
            
            public Builder bytesProcessed(long bytesProcessed) {
                this.bytesProcessed = bytesProcessed;
                return this;
            }
            
            public Builder filesProcessed(int filesProcessed) {
                this.filesProcessed = filesProcessed;
                return this;
            }
            
            public Builder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }
            
            public Builder customMetrics(Map<String, Object> customMetrics) {
                this.customMetrics = customMetrics;
                return this;
            }
            
            public AdapterExecutionMetrics build() {
                return new AdapterExecutionMetrics(adapterId, adapterType, operationType,
                                                 executionTime, durationMillis, successful,
                                                 bytesProcessed, filesProcessed, errorMessage, customMetrics);
            }
        }
    }
    
    /**
     * Immutable adapter metrics summary
     */
    class AdapterMetricsSummary {
        private final String adapterId;
        private final String adapterType;
        private final LocalDateTime summaryTime;
        private final long totalExecutions;
        private final long successfulExecutions;
        private final long failedExecutions;
        private final double successRate;
        private final long totalBytesProcessed;
        private final long totalFilesProcessed;
        private final long averageExecutionTimeMillis;
        private final long minExecutionTimeMillis;
        private final long maxExecutionTimeMillis;
        private final LocalDateTime lastExecutionTime;
        private final LocalDateTime lastSuccessfulExecution;
        private final LocalDateTime lastFailedExecution;
        private final String lastError;
        
        public AdapterMetricsSummary(String adapterId, String adapterType, LocalDateTime summaryTime,
                                   long totalExecutions, long successfulExecutions, long failedExecutions,
                                   double successRate, long totalBytesProcessed, long totalFilesProcessed,
                                   long averageExecutionTimeMillis, long minExecutionTimeMillis,
                                   long maxExecutionTimeMillis, LocalDateTime lastExecutionTime,
                                   LocalDateTime lastSuccessfulExecution, LocalDateTime lastFailedExecution,
                                   String lastError) {
            this.adapterId = adapterId;
            this.adapterType = adapterType;
            this.summaryTime = summaryTime;
            this.totalExecutions = totalExecutions;
            this.successfulExecutions = successfulExecutions;
            this.failedExecutions = failedExecutions;
            this.successRate = successRate;
            this.totalBytesProcessed = totalBytesProcessed;
            this.totalFilesProcessed = totalFilesProcessed;
            this.averageExecutionTimeMillis = averageExecutionTimeMillis;
            this.minExecutionTimeMillis = minExecutionTimeMillis;
            this.maxExecutionTimeMillis = maxExecutionTimeMillis;
            this.lastExecutionTime = lastExecutionTime;
            this.lastSuccessfulExecution = lastSuccessfulExecution;
            this.lastFailedExecution = lastFailedExecution;
            this.lastError = lastError;
        }
        
        public String getAdapterId() { return adapterId; }
        public String getAdapterType() { return adapterType; }
        public LocalDateTime getSummaryTime() { return summaryTime; }
        public long getTotalExecutions() { return totalExecutions; }
        public long getSuccessfulExecutions() { return successfulExecutions; }
        public long getFailedExecutions() { return failedExecutions; }
        public double getSuccessRate() { return successRate; }
        public long getTotalBytesProcessed() { return totalBytesProcessed; }
        public long getTotalFilesProcessed() { return totalFilesProcessed; }
        public long getAverageExecutionTimeMillis() { return averageExecutionTimeMillis; }
        public long getMinExecutionTimeMillis() { return minExecutionTimeMillis; }
        public long getMaxExecutionTimeMillis() { return maxExecutionTimeMillis; }
        public LocalDateTime getLastExecutionTime() { return lastExecutionTime; }
        public LocalDateTime getLastSuccessfulExecution() { return lastSuccessfulExecution; }
        public LocalDateTime getLastFailedExecution() { return lastFailedExecution; }
        public String getLastError() { return lastError; }
    }
}