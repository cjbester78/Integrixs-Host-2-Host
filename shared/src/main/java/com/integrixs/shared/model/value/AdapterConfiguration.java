package com.integrixs.shared.model.value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable value object representing adapter configuration and performance settings.
 * Part of Phase 5 model layer refactoring following OOP principles.
 */
public final class AdapterConfiguration {
    
    private final AdapterType adapterType;
    private final AdapterDirection direction;
    private final Map<String, Object> configuration;
    private final ConnectionStatus connectionStatus;
    private final PerformanceMetrics performanceMetrics;
    
    private AdapterConfiguration(Builder builder) {
        this.adapterType = builder.adapterType != null ? builder.adapterType : AdapterType.FILE;
        this.direction = builder.direction != null ? builder.direction : AdapterDirection.SENDER;
        this.configuration = builder.configuration != null ? 
            Collections.unmodifiableMap(builder.configuration) : Collections.emptyMap();
        this.connectionStatus = builder.connectionStatus != null ? 
            builder.connectionStatus : ConnectionStatus.notTested();
        this.performanceMetrics = builder.performanceMetrics != null ? 
            builder.performanceMetrics : PerformanceMetrics.empty();
    }
    
    // Getters with defensive copying
    public AdapterType getAdapterType() { return adapterType; }
    public AdapterDirection getDirection() { return direction; }
    
    public Map<String, Object> getConfiguration() {
        return Collections.unmodifiableMap(configuration);
    }
    
    public ConnectionStatus getConnectionStatus() { return connectionStatus; }
    public PerformanceMetrics getPerformanceMetrics() { return performanceMetrics; }
    
    /**
     * Get configuration value by key.
     */
    public Optional<Object> getConfigValue(String key) {
        return Optional.ofNullable(configuration.get(key));
    }
    
    /**
     * Get string configuration value.
     */
    public Optional<String> getStringConfig(String key) {
        return getConfigValue(key).map(Object::toString);
    }
    
    /**
     * Get integer configuration value.
     */
    public Optional<Integer> getIntegerConfig(String key) {
        return getConfigValue(key)
            .filter(v -> v instanceof Number)
            .map(v -> ((Number) v).intValue());
    }
    
    /**
     * Get boolean configuration value.
     */
    public Optional<Boolean> getBooleanConfig(String key) {
        return getConfigValue(key)
            .map(v -> v instanceof Boolean ? (Boolean) v : Boolean.parseBoolean(v.toString()));
    }
    
    /**
     * Check if adapter is sender type.
     */
    public boolean isSender() {
        return direction == AdapterDirection.SENDER;
    }
    
    /**
     * Check if adapter is receiver type.
     */
    public boolean isReceiver() {
        return direction == AdapterDirection.RECEIVER;
    }
    
    /**
     * Check if adapter is SFTP type.
     */
    public boolean isSftpAdapter() {
        return adapterType == AdapterType.SFTP;
    }
    
    /**
     * Check if adapter is FILE type.
     */
    public boolean isFileAdapter() {
        return adapterType == AdapterType.FILE;
    }
    
    /**
     * Check if adapter is EMAIL type.
     */
    public boolean isEmailAdapter() {
        return adapterType == AdapterType.EMAIL;
    }
    
    /**
     * Check if adapter configuration is valid.
     */
    public boolean isValid() {
        return connectionStatus.isValidated() && !configuration.isEmpty();
    }
    
    /**
     * Get display name for adapter.
     */
    public String getDisplayName(String adapterName) {
        return String.format("%s (%s %s)", adapterName, adapterType, direction);
    }
    
    /**
     * Create updated configuration with new connection status.
     */
    public AdapterConfiguration withConnectionStatus(ConnectionStatus newStatus) {
        return builder().from(this).connectionStatus(newStatus).build();
    }
    
    /**
     * Create updated configuration with new performance metrics.
     */
    public AdapterConfiguration withPerformanceMetrics(PerformanceMetrics newMetrics) {
        return builder().from(this).performanceMetrics(newMetrics).build();
    }
    
    /**
     * Create updated configuration with new settings.
     */
    public AdapterConfiguration withConfiguration(Map<String, Object> newConfig) {
        return builder().from(this).configuration(newConfig).build();
    }
    
    /**
     * Create configuration from legacy values.
     */
    public static AdapterConfiguration fromLegacy(String adapterType, String direction,
                                                Map<String, Object> configuration,
                                                Boolean connectionValidated, LocalDateTime lastTestAt,
                                                String testResult, Long averageExecutionTimeMs,
                                                BigDecimal successRatePercent) {
        AdapterType type;
        try {
            type = AdapterType.valueOf(adapterType != null ? adapterType.toUpperCase() : "FILE");
        } catch (IllegalArgumentException e) {
            type = AdapterType.FILE;
        }
        
        AdapterDirection dir;
        try {
            dir = AdapterDirection.valueOf(direction != null ? direction.toUpperCase() : "SENDER");
        } catch (IllegalArgumentException e) {
            dir = AdapterDirection.SENDER;
        }
        
        ConnectionStatus connStatus = connectionValidated != null && connectionValidated ?
            ConnectionStatus.validated(testResult, lastTestAt) :
            ConnectionStatus.failed(testResult, lastTestAt);
        
        PerformanceMetrics metrics = PerformanceMetrics.of(
            averageExecutionTimeMs != null ? averageExecutionTimeMs : 0L,
            successRatePercent != null ? successRatePercent : BigDecimal.valueOf(100.0)
        );
        
        return builder()
            .adapterType(type)
            .direction(dir)
            .configuration(configuration)
            .connectionStatus(connStatus)
            .performanceMetrics(metrics)
            .build();
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for AdapterConfiguration.
     */
    public static class Builder {
        private AdapterType adapterType;
        private AdapterDirection direction;
        private Map<String, Object> configuration;
        private ConnectionStatus connectionStatus;
        private PerformanceMetrics performanceMetrics;
        
        private Builder() {}
        
        public Builder adapterType(AdapterType adapterType) {
            this.adapterType = adapterType;
            return this;
        }
        
        public Builder direction(AdapterDirection direction) {
            this.direction = direction;
            return this;
        }
        
        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration;
            return this;
        }
        
        public Builder connectionStatus(ConnectionStatus connectionStatus) {
            this.connectionStatus = connectionStatus;
            return this;
        }
        
        public Builder performanceMetrics(PerformanceMetrics performanceMetrics) {
            this.performanceMetrics = performanceMetrics;
            return this;
        }
        
        public Builder from(AdapterConfiguration existing) {
            this.adapterType = existing.adapterType;
            this.direction = existing.direction;
            this.configuration = existing.configuration.isEmpty() ? null : existing.configuration;
            this.connectionStatus = existing.connectionStatus;
            this.performanceMetrics = existing.performanceMetrics;
            return this;
        }
        
        public AdapterConfiguration build() {
            return new AdapterConfiguration(this);
        }
    }
    
    /**
     * Adapter type enumeration.
     */
    public enum AdapterType {
        SFTP("SFTP file transfer"),
        FILE("Local file operations"),
        EMAIL("Email communications");
        
        private final String description;
        
        AdapterType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Adapter direction enumeration.
     */
    public enum AdapterDirection {
        SENDER("Sends files/data out"),
        RECEIVER("Receives files/data in");
        
        private final String description;
        
        AdapterDirection(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Connection status value object.
     */
    public static final class ConnectionStatus {
        private final boolean validated;
        private final String testResult;
        private final LocalDateTime lastTestAt;
        
        private ConnectionStatus(boolean validated, String testResult, LocalDateTime lastTestAt) {
            this.validated = validated;
            this.testResult = testResult;
            this.lastTestAt = lastTestAt;
        }
        
        public boolean isValidated() { return validated; }
        public Optional<String> getTestResult() { return Optional.ofNullable(testResult); }
        public Optional<LocalDateTime> getLastTestAt() { return Optional.ofNullable(lastTestAt); }
        
        public static ConnectionStatus validated(String result, LocalDateTime testTime) {
            return new ConnectionStatus(true, result, testTime != null ? testTime : LocalDateTime.now());
        }
        
        public static ConnectionStatus failed(String result, LocalDateTime testTime) {
            return new ConnectionStatus(false, result, testTime != null ? testTime : LocalDateTime.now());
        }
        
        public static ConnectionStatus notTested() {
            return new ConnectionStatus(false, null, null);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConnectionStatus that = (ConnectionStatus) o;
            return validated == that.validated &&
                   Objects.equals(testResult, that.testResult) &&
                   Objects.equals(lastTestAt, that.lastTestAt);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(validated, testResult, lastTestAt);
        }
    }
    
    /**
     * Performance metrics value object.
     */
    public static final class PerformanceMetrics {
        private final long averageExecutionTimeMs;
        private final BigDecimal successRatePercent;
        
        private PerformanceMetrics(long averageExecutionTimeMs, BigDecimal successRatePercent) {
            this.averageExecutionTimeMs = Math.max(0, averageExecutionTimeMs);
            this.successRatePercent = successRatePercent != null ? successRatePercent : BigDecimal.valueOf(100.0);
        }
        
        public long getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
        public BigDecimal getSuccessRatePercent() { return successRatePercent; }
        
        public boolean isHealthy() {
            return successRatePercent.compareTo(BigDecimal.valueOf(95.0)) >= 0 && 
                   averageExecutionTimeMs < 10000; // 10 seconds threshold
        }
        
        public static PerformanceMetrics of(long avgTimeMs, BigDecimal successRate) {
            return new PerformanceMetrics(avgTimeMs, successRate);
        }
        
        public static PerformanceMetrics empty() {
            return new PerformanceMetrics(0L, BigDecimal.valueOf(100.0));
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PerformanceMetrics that = (PerformanceMetrics) o;
            return averageExecutionTimeMs == that.averageExecutionTimeMs &&
                   Objects.equals(successRatePercent, that.successRatePercent);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(averageExecutionTimeMs, successRatePercent);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdapterConfiguration that = (AdapterConfiguration) o;
        return adapterType == that.adapterType &&
               direction == that.direction &&
               Objects.equals(configuration, that.configuration) &&
               Objects.equals(connectionStatus, that.connectionStatus) &&
               Objects.equals(performanceMetrics, that.performanceMetrics);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(adapterType, direction, configuration, connectionStatus, performanceMetrics);
    }
    
    @Override
    public String toString() {
        return String.format("AdapterConfiguration{type=%s, direction=%s, validated=%s}",
                           adapterType, direction, connectionStatus.isValidated());
    }
}