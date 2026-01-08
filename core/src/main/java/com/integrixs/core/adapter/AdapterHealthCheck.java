package com.integrixs.core.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Interface for adapter health checking capabilities.
 * Provides comprehensive health monitoring with immutable results.
 * Follows OOP principles with proper encapsulation and type safety.
 */
public interface AdapterHealthCheck {
    
    /**
     * Perform a health check on the adapter.
     * 
     * @return immutable health check result
     */
    AdapterHealthResult performHealthCheck();
    
    /**
     * Get the last health check result.
     * 
     * @return last health check result, or null if none performed
     */
    AdapterHealthResult getLastHealthCheck();
    
    /**
     * Check if the adapter is currently healthy.
     * 
     * @return true if adapter is healthy
     */
    boolean isHealthy();
    
    /**
     * Immutable health check result
     */
    class AdapterHealthResult {
        private final boolean healthy;
        private final HealthStatus status;
        private final String message;
        private final LocalDateTime checkTime;
        private final long responseTimeMillis;
        private final List<String> warnings;
        private final List<String> errors;
        private final Map<String, Object> diagnosticInfo;
        
        private AdapterHealthResult(boolean healthy, HealthStatus status, String message,
                                 LocalDateTime checkTime, long responseTimeMillis,
                                 List<String> warnings, List<String> errors,
                                 Map<String, Object> diagnosticInfo) {
            this.healthy = healthy;
            this.status = status;
            this.message = message;
            this.checkTime = checkTime;
            this.responseTimeMillis = responseTimeMillis;
            this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
            this.errors = errors != null ? List.copyOf(errors) : List.of();
            this.diagnosticInfo = diagnosticInfo != null ? Map.copyOf(diagnosticInfo) : Map.of();
        }
        
        public boolean isHealthy() { return healthy; }
        public HealthStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public LocalDateTime getCheckTime() { return checkTime; }
        public long getResponseTimeMillis() { return responseTimeMillis; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getErrors() { return errors; }
        public Map<String, Object> getDiagnosticInfo() { return diagnosticInfo; }
        
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        
        public static AdapterHealthResult healthy(String message, long responseTimeMillis) {
            return new AdapterHealthResult(true, HealthStatus.HEALTHY, message, 
                                         LocalDateTime.now(), responseTimeMillis, null, null, null);
        }
        
        public static AdapterHealthResult healthyWithWarnings(String message, long responseTimeMillis,
                                                            List<String> warnings) {
            return new AdapterHealthResult(true, HealthStatus.DEGRADED, message,
                                         LocalDateTime.now(), responseTimeMillis, warnings, null, null);
        }
        
        public static AdapterHealthResult unhealthy(String message, List<String> errors) {
            return new AdapterHealthResult(false, HealthStatus.UNHEALTHY, message,
                                         LocalDateTime.now(), 0, null, errors, null);
        }
        
        public static AdapterHealthResult unavailable(String message) {
            return new AdapterHealthResult(false, HealthStatus.UNAVAILABLE, message,
                                         LocalDateTime.now(), 0, null, null, null);
        }
        
        public static AdapterHealthResult withDiagnostics(boolean healthy, HealthStatus status, String message,
                                                        long responseTimeMillis, List<String> warnings,
                                                        List<String> errors, Map<String, Object> diagnosticInfo) {
            return new AdapterHealthResult(healthy, status, message, LocalDateTime.now(),
                                         responseTimeMillis, warnings, errors, diagnosticInfo);
        }
    }
    
    /**
     * Health status enumeration
     */
    enum HealthStatus {
        HEALTHY("Healthy", "Adapter is functioning normally"),
        DEGRADED("Degraded", "Adapter is functioning but with warnings"),
        UNHEALTHY("Unhealthy", "Adapter has errors and may not function properly"),
        UNAVAILABLE("Unavailable", "Adapter is not available for health checks"),
        UNKNOWN("Unknown", "Health status cannot be determined");
        
        private final String displayName;
        private final String description;
        
        HealthStatus(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
}