package com.integrixs.shared.model.value;

import java.util.Objects;

/**
 * Immutable value object representing execution performance metrics.
 * Part of Phase 5 model layer refactoring following OOP principles.
 */
public final class ExecutionMetrics {
    
    private final long totalExecutions;
    private final long successfulExecutions;
    private final long failedExecutions;
    private final long averageExecutionTimeMs;
    
    private ExecutionMetrics(Builder builder) {
        this.totalExecutions = builder.totalExecutions;
        this.successfulExecutions = builder.successfulExecutions;
        this.failedExecutions = builder.failedExecutions;
        this.averageExecutionTimeMs = builder.averageExecutionTimeMs;
    }
    
    // Getters
    public long getTotalExecutions() { return totalExecutions; }
    public long getSuccessfulExecutions() { return successfulExecutions; }
    public long getFailedExecutions() { return failedExecutions; }
    public long getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
    
    /**
     * Calculate success rate as percentage.
     */
    public double getSuccessRate() {
        if (totalExecutions == 0) {
            return 100.0;
        }
        return (double) successfulExecutions / totalExecutions * 100.0;
    }
    
    /**
     * Calculate failure rate as percentage.
     */
    public double getFailureRate() {
        return 100.0 - getSuccessRate();
    }
    
    /**
     * Get formatted average execution time.
     */
    public String getFormattedAverageExecutionTime() {
        if (averageExecutionTimeMs == 0) {
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
    
    /**
     * Record a new execution and return updated metrics.
     */
    public ExecutionMetrics recordExecution(long executionTimeMs, boolean successful) {
        long newTotal = this.totalExecutions + 1;
        long newSuccessful = this.successfulExecutions + (successful ? 1 : 0);
        long newFailed = this.failedExecutions + (successful ? 0 : 1);
        
        // Update average execution time using weighted average
        long newAverage;
        if (this.averageExecutionTimeMs == 0) {
            newAverage = executionTimeMs;
        } else {
            // 80% previous average + 20% current execution
            newAverage = (long) (this.averageExecutionTimeMs * 0.8 + executionTimeMs * 0.2);
        }
        
        return builder()
            .totalExecutions(newTotal)
            .successfulExecutions(newSuccessful)
            .failedExecutions(newFailed)
            .averageExecutionTimeMs(newAverage)
            .build();
    }
    
    /**
     * Check if metrics indicate healthy performance.
     */
    public boolean isHealthy() {
        return getSuccessRate() >= 95.0 && averageExecutionTimeMs < 30000; // 30 seconds threshold
    }
    
    /**
     * Get performance status indicator.
     */
    public String getPerformanceStatus() {
        double successRate = getSuccessRate();
        if (successRate >= 99.0) {
            return "EXCELLENT";
        } else if (successRate >= 95.0) {
            return "GOOD";
        } else if (successRate >= 90.0) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }
    
    /**
     * Create empty metrics.
     */
    public static ExecutionMetrics empty() {
        return builder().build();
    }
    
    /**
     * Create metrics with initial values.
     */
    public static ExecutionMetrics of(long total, long successful, long failed, long avgTimeMs) {
        return builder()
            .totalExecutions(total)
            .successfulExecutions(successful)
            .failedExecutions(failed)
            .averageExecutionTimeMs(avgTimeMs)
            .build();
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ExecutionMetrics.
     */
    public static class Builder {
        private long totalExecutions = 0L;
        private long successfulExecutions = 0L;
        private long failedExecutions = 0L;
        private long averageExecutionTimeMs = 0L;
        
        private Builder() {}
        
        public Builder totalExecutions(long totalExecutions) {
            this.totalExecutions = Math.max(0, totalExecutions);
            return this;
        }
        
        public Builder successfulExecutions(long successfulExecutions) {
            this.successfulExecutions = Math.max(0, successfulExecutions);
            return this;
        }
        
        public Builder failedExecutions(long failedExecutions) {
            this.failedExecutions = Math.max(0, failedExecutions);
            return this;
        }
        
        public Builder averageExecutionTimeMs(long averageExecutionTimeMs) {
            this.averageExecutionTimeMs = Math.max(0, averageExecutionTimeMs);
            return this;
        }
        
        public ExecutionMetrics build() {
            // Validate that totals make sense
            long calculatedTotal = successfulExecutions + failedExecutions;
            if (totalExecutions == 0 && calculatedTotal > 0) {
                totalExecutions = calculatedTotal;
            } else if (totalExecutions > 0 && calculatedTotal == 0) {
                // If total is set but success/failed aren't, assume all successful
                successfulExecutions = totalExecutions;
            }
            
            return new ExecutionMetrics(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionMetrics that = (ExecutionMetrics) o;
        return totalExecutions == that.totalExecutions &&
               successfulExecutions == that.successfulExecutions &&
               failedExecutions == that.failedExecutions &&
               averageExecutionTimeMs == that.averageExecutionTimeMs;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(totalExecutions, successfulExecutions, failedExecutions, averageExecutionTimeMs);
    }
    
    @Override
    public String toString() {
        return String.format("ExecutionMetrics{total=%d, successful=%d, failed=%d, avgTime=%dms, successRate=%.1f%%}",
                           totalExecutions, successfulExecutions, failedExecutions, averageExecutionTimeMs, getSuccessRate());
    }
}