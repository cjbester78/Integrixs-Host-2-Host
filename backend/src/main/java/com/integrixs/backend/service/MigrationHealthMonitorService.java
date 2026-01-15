package com.integrixs.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Migration Health Monitor Service for continuous monitoring of migration integrity.
 * Follows OOP principles:
 * - Observer Pattern: Notifies subscribers of health status changes
 * - Strategy Pattern: Different monitoring strategies for various metrics
 * - Command Pattern: Encapsulates health check operations
 * - State Pattern: Manages different health states
 * - Factory Pattern: Creates appropriate health check handlers
 */
@Service
public class MigrationHealthMonitorService {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationHealthMonitorService.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final MigrationVerificationService migrationVerificationService;
    
    // Observer pattern - list of subscribers
    private final List<HealthStatusObserver> observers = new ArrayList<>();
    
    // Current health status cache
    private final Map<String, HealthMetric> currentMetrics = new ConcurrentHashMap<>();
    
    // Health history for trend analysis
    private final List<HealthSnapshot> healthHistory = new ArrayList<>();
    
    @Autowired
    public MigrationHealthMonitorService(
            JdbcTemplate jdbcTemplate, 
            MigrationVerificationService migrationVerificationService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
        this.migrationVerificationService = Objects.requireNonNull(migrationVerificationService, 
            "MigrationVerificationService cannot be null");
    }
    
    /**
     * Observer interface for health status notifications
     */
    public interface HealthStatusObserver {
        void onHealthStatusChanged(HealthStatusChangeEvent event);
        void onCriticalIssueDetected(CriticalIssueEvent event);
        void onHealthImproved(HealthImprovementEvent event);
    }
    
    /**
     * Event classes for health monitoring
     */
    public static class HealthStatusChangeEvent {
        private final String status;
        private final LocalDateTime timestamp;
        
        public HealthStatusChangeEvent(String status) {
            this.status = status;
            this.timestamp = LocalDateTime.now();
        }
        
        public String getStatus() { return status; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class CriticalIssueEvent {
        private final String issue;
        private final LocalDateTime timestamp;
        
        public CriticalIssueEvent(String issue) {
            this.issue = issue;
            this.timestamp = LocalDateTime.now();
        }
        
        public String getIssue() { return issue; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class HealthImprovementEvent {
        private final String improvement;
        private final LocalDateTime timestamp;
        
        public HealthImprovementEvent(String improvement) {
            this.improvement = improvement;
            this.timestamp = LocalDateTime.now();
        }
        
        public String getImprovement() { return improvement; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    /**
     * Health metric data structure
     */
    public static class HealthMetric {
        private final String name;
        private final double value;
        private final double threshold;
        private final HealthStatus status;
        private final String message;
        private final LocalDateTime timestamp;
        private final Map<String, Object> details;
        
        public HealthMetric(String name, double value, double threshold, HealthStatus status, 
                           String message, Map<String, Object> details) {
            this.name = name;
            this.value = value;
            this.threshold = threshold;
            this.status = status;
            this.message = message;
            this.timestamp = LocalDateTime.now();
            this.details = details != null ? details : new HashMap<>();
        }
        
        // Getters
        public String getName() { return name; }
        public double getValue() { return value; }
        public double getThreshold() { return threshold; }
        public HealthStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getDetails() { return details; }
    }
    
    /**
     * Health status enumeration
     */
    public enum HealthStatus {
        EXCELLENT(0, "All systems operating optimally"),
        GOOD(1, "Systems operating within normal parameters"),
        WARNING(2, "Minor issues detected, monitoring required"),
        CRITICAL(3, "Critical issues detected, immediate attention required"),
        FAILED(4, "System failure detected, urgent intervention needed");
        
        private final int severity;
        private final String description;
        
        HealthStatus(int severity, String description) {
            this.severity = severity;
            this.description = description;
        }
        
        public int getSeverity() { return severity; }
        public String getDescription() { return description; }
    }
    
    /**
     * Health snapshot for historical tracking
     */
    public static class HealthSnapshot {
        private final LocalDateTime timestamp;
        private final HealthStatus overallStatus;
        private final Map<String, HealthMetric> metrics;
        private final List<String> issues;
        
        public HealthSnapshot(HealthStatus overallStatus, Map<String, HealthMetric> metrics, List<String> issues) {
            this.timestamp = LocalDateTime.now();
            this.overallStatus = overallStatus;
            this.metrics = new HashMap<>(metrics);
            this.issues = new ArrayList<>(issues);
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public HealthStatus getOverallStatus() { return overallStatus; }
        public Map<String, HealthMetric> getMetrics() { return metrics; }
        public List<String> getIssues() { return issues; }
    }
    
    /**
     * Health monitoring strategy interface
     */
    public interface HealthCheckStrategy {
        String getMetricName();
        HealthMetric performHealthCheck();
        double getThreshold();
        boolean isCritical();
    }
    
    /**
     * Package association health check strategy
     */
    public class PackageAssociationHealthCheck implements HealthCheckStrategy {
        @Override
        public String getMetricName() {
            return "package_associations";
        }
        
        @Override
        public HealthMetric performHealthCheck() {
            try {
                String sql = """
                    SELECT 
                        (SELECT COUNT(*) FROM package_adapters WHERE package_id IS NULL) as orphaned_adapters,
                        (SELECT COUNT(*) FROM package_flows WHERE package_id IS NULL) as orphaned_flows,
                        (SELECT COUNT(*) FROM package_adapters) as total_adapters,
                        (SELECT COUNT(*) FROM package_flows) as total_flows
                """;
                
                Map<String, Object> result = jdbcTemplate.queryForMap(sql);
                
                int orphanedAdapters = ((Number) result.get("orphaned_adapters")).intValue();
                int orphanedFlows = ((Number) result.get("orphaned_flows")).intValue();
                int totalAdapters = ((Number) result.get("total_adapters")).intValue();
                int totalFlows = ((Number) result.get("total_flows")).intValue();
                
                int totalOrphaned = orphanedAdapters + orphanedFlows;
                int totalAssets = totalAdapters + totalFlows;
                
                double associationRate = totalAssets > 0 ? ((totalAssets - totalOrphaned) / (double) totalAssets) * 100 : 100;
                
                HealthStatus status = getHealthStatusForAssociationRate(associationRate);
                String message = String.format("%.1f%% assets properly associated (%d orphaned out of %d)", 
                                              associationRate, totalOrphaned, totalAssets);
                
                Map<String, Object> details = new HashMap<>();
                details.put("orphanedAdapters", orphanedAdapters);
                details.put("orphanedFlows", orphanedFlows);
                details.put("totalAdapters", totalAdapters);
                details.put("totalFlows", totalFlows);
                details.put("associationRate", associationRate);
                
                return new HealthMetric(getMetricName(), associationRate, getThreshold(), status, message, details);
                
            } catch (Exception e) {
                logger.error("Error performing package association health check", e);
                return new HealthMetric(getMetricName(), 0, getThreshold(), HealthStatus.FAILED, 
                                      "Health check failed: " + e.getMessage(), null);
            }
        }
        
        @Override
        public double getThreshold() {
            return 95.0; // 95% association rate threshold
        }
        
        @Override
        public boolean isCritical() {
            return true;
        }
        
        private HealthStatus getHealthStatusForAssociationRate(double rate) {
            if (rate >= 99.0) return HealthStatus.EXCELLENT;
            if (rate >= 95.0) return HealthStatus.GOOD;
            if (rate >= 85.0) return HealthStatus.WARNING;
            if (rate >= 50.0) return HealthStatus.CRITICAL;
            return HealthStatus.FAILED;
        }
    }
    
    /**
     * Data integrity health check strategy
     */
    public class DataIntegrityHealthCheck implements HealthCheckStrategy {
        @Override
        public String getMetricName() {
            return "data_integrity";
        }
        
        @Override
        public HealthMetric performHealthCheck() {
            try {
                MigrationVerificationService.VerificationResult result = 
                    migrationVerificationService.verifyDataIntegrity();
                
                double integrityScore = result.isSuccessful() ? 100.0 : 
                    Math.max(0, 100.0 - (result.getIssues().size() * 20.0));
                
                HealthStatus status = getHealthStatusForIntegrityScore(integrityScore);
                
                return new HealthMetric(getMetricName(), integrityScore, getThreshold(), 
                                      status, result.getMessage(), result.getDetails());
                
            } catch (Exception e) {
                logger.error("Error performing data integrity health check", e);
                return new HealthMetric(getMetricName(), 0, getThreshold(), HealthStatus.FAILED, 
                                      "Integrity check failed: " + e.getMessage(), null);
            }
        }
        
        @Override
        public double getThreshold() {
            return 90.0; // 90% integrity score threshold
        }
        
        @Override
        public boolean isCritical() {
            return true;
        }
        
        private HealthStatus getHealthStatusForIntegrityScore(double score) {
            if (score >= 95.0) return HealthStatus.EXCELLENT;
            if (score >= 90.0) return HealthStatus.GOOD;
            if (score >= 70.0) return HealthStatus.WARNING;
            if (score >= 40.0) return HealthStatus.CRITICAL;
            return HealthStatus.FAILED;
        }
    }
    
    /**
     * System performance health check strategy
     */
    public class SystemPerformanceHealthCheck implements HealthCheckStrategy {
        @Override
        public String getMetricName() {
            return "system_performance";
        }
        
        @Override
        public HealthMetric performHealthCheck() {
            try {
                long startTime = System.currentTimeMillis();
                
                // Perform sample queries to measure performance
                String testSql = """
                    SELECT COUNT(*) as package_count FROM integration_packages;
                """;

                Integer packageCount = jdbcTemplate.queryForObject(testSql, Integer.class);
                long queryTime = System.currentTimeMillis() - startTime;
                
                double performanceScore = calculatePerformanceScore(queryTime);
                HealthStatus status = getHealthStatusForPerformance(performanceScore);
                
                String message = String.format("System response time: %dms (Score: %.1f)", queryTime, performanceScore);
                
                Map<String, Object> details = new HashMap<>();
                details.put("queryTime", queryTime);
                details.put("packageCount", packageCount);
                details.put("performanceScore", performanceScore);
                
                return new HealthMetric(getMetricName(), performanceScore, getThreshold(), status, message, details);
                
            } catch (Exception e) {
                logger.error("Error performing system performance health check", e);
                return new HealthMetric(getMetricName(), 0, getThreshold(), HealthStatus.FAILED, 
                                      "Performance check failed: " + e.getMessage(), null);
            }
        }
        
        @Override
        public double getThreshold() {
            return 80.0; // 80% performance score threshold
        }
        
        @Override
        public boolean isCritical() {
            return false;
        }
        
        private double calculatePerformanceScore(long queryTimeMs) {
            // Score based on query response time
            if (queryTimeMs <= 100) return 100.0;
            if (queryTimeMs <= 500) return 90.0;
            if (queryTimeMs <= 1000) return 80.0;
            if (queryTimeMs <= 2000) return 60.0;
            if (queryTimeMs <= 5000) return 40.0;
            return 20.0;
        }
        
        private HealthStatus getHealthStatusForPerformance(double score) {
            if (score >= 90.0) return HealthStatus.EXCELLENT;
            if (score >= 80.0) return HealthStatus.GOOD;
            if (score >= 60.0) return HealthStatus.WARNING;
            if (score >= 40.0) return HealthStatus.CRITICAL;
            return HealthStatus.FAILED;
        }
    }
    
    /**
     * Factory for creating health check strategies
     */
    public class HealthCheckStrategyFactory {
        private final List<HealthCheckStrategy> strategies = Arrays.asList(
            new PackageAssociationHealthCheck(),
            new DataIntegrityHealthCheck(),
            new SystemPerformanceHealthCheck()
        );
        
        public List<HealthCheckStrategy> getAllStrategies() {
            return new ArrayList<>(strategies);
        }
        
        public List<HealthCheckStrategy> getCriticalStrategies() {
            return strategies.stream()
                .filter(HealthCheckStrategy::isCritical)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Scheduled health monitoring (runs every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void performScheduledHealthCheck() {
        logger.info("Starting scheduled migration health check");
        
        try {
            MigrationHealthReport report = performComprehensiveHealthCheck();
            updateHealthMetrics(report);
            notifyObserversIfNeeded(report);
            
            logger.info("Scheduled health check completed - Overall status: {}", report.getOverallStatus());
            
        } catch (Exception e) {
            logger.error("Error during scheduled health check", e);
        }
    }
    
    /**
     * Perform comprehensive health check
     */
    public MigrationHealthReport performComprehensiveHealthCheck() {
        logger.info("Performing comprehensive migration health check");
        
        HealthCheckStrategyFactory factory = new HealthCheckStrategyFactory();
        Map<String, HealthMetric> metrics = new HashMap<>();
        List<String> issues = new ArrayList<>();
        
        // Execute all health check strategies
        for (HealthCheckStrategy strategy : factory.getAllStrategies()) {
            try {
                HealthMetric metric = strategy.performHealthCheck();
                metrics.put(strategy.getMetricName(), metric);
                
                // Collect issues from failed checks
                if (metric.getStatus().getSeverity() >= HealthStatus.WARNING.getSeverity()) {
                    issues.add(String.format("[%s] %s", strategy.getMetricName().toUpperCase(), metric.getMessage()));
                }
                
            } catch (Exception e) {
                logger.error("Error executing health check strategy: {}", strategy.getMetricName(), e);
                issues.add(String.format("[%s] Health check failed: %s", strategy.getMetricName().toUpperCase(), e.getMessage()));
            }
        }
        
        // Determine overall status
        HealthStatus overallStatus = calculateOverallHealthStatus(metrics);
        
        return new MigrationHealthReport(overallStatus, metrics, issues, LocalDateTime.now());
    }
    
    /**
     * Migration health report data structure
     */
    public static class MigrationHealthReport {
        private final HealthStatus overallStatus;
        private final Map<String, HealthMetric> metrics;
        private final List<String> issues;
        private final LocalDateTime timestamp;
        
        public MigrationHealthReport(HealthStatus overallStatus, Map<String, HealthMetric> metrics, 
                                   List<String> issues, LocalDateTime timestamp) {
            this.overallStatus = overallStatus;
            this.metrics = new HashMap<>(metrics);
            this.issues = new ArrayList<>(issues);
            this.timestamp = timestamp;
        }
        
        // Getters
        public HealthStatus getOverallStatus() { return overallStatus; }
        public Map<String, HealthMetric> getMetrics() { return metrics; }
        public List<String> getIssues() { return issues; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    /**
     * Calculate overall health status from individual metrics
     */
    private HealthStatus calculateOverallHealthStatus(Map<String, HealthMetric> metrics) {
        if (metrics.isEmpty()) {
            return HealthStatus.FAILED;
        }
        
        // Find the worst status among all metrics
        HealthStatus worstStatus = HealthStatus.EXCELLENT;
        for (HealthMetric metric : metrics.values()) {
            if (metric.getStatus().getSeverity() > worstStatus.getSeverity()) {
                worstStatus = metric.getStatus();
            }
        }
        
        return worstStatus;
    }
    
    /**
     * Update current health metrics and maintain history
     */
    private void updateHealthMetrics(MigrationHealthReport report) {
        // Update current metrics
        currentMetrics.clear();
        currentMetrics.putAll(report.getMetrics());
        
        // Add to history (keep last 100 snapshots)
        healthHistory.add(new HealthSnapshot(report.getOverallStatus(), report.getMetrics(), report.getIssues()));
        if (healthHistory.size() > 100) {
            healthHistory.remove(0);
        }
    }
    
    /**
     * Notify observers of health status changes
     */
    private void notifyObserversIfNeeded(MigrationHealthReport report) {
        HealthStatus currentStatus = report.getOverallStatus();
        HealthStatus previousStatus = getPreviousHealthStatus();
        
        // Check if status has changed significantly
        boolean statusChanged = hasSignificantStatusChange(previousStatus, currentStatus);
        boolean isCritical = currentStatus.getSeverity() >= HealthStatus.CRITICAL.getSeverity();
        boolean hasNewIssues = hasNewCriticalIssues(report);
        
        // Notify observers if there are significant changes
        if (statusChanged || isCritical || hasNewIssues) {
            String statusMessage = String.format("Health status changed from %s to %s", 
                                                 previousStatus, currentStatus);
            HealthStatusChangeEvent event = new HealthStatusChangeEvent(statusMessage);
            
            notifyAllObservers(event);
            
            // Log significant status changes
            if (statusChanged) {
                logger.warn("Migration health status changed from {} to {} - Issues: {}", 
                           previousStatus, currentStatus, report.getIssues());
            }
            
            if (isCritical) {
                logger.error("CRITICAL migration health issues detected: {}", report.getIssues());
            }
        }
    }
    
    /**
     * Get the previous health status from history
     */
    private HealthStatus getPreviousHealthStatus() {
        if (healthHistory.size() < 2) {
            return HealthStatus.EXCELLENT; // Default to best status if no history
        }
        
        return healthHistory.get(healthHistory.size() - 2).getOverallStatus();
    }
    
    /**
     * Check if there's a significant status change
     */
    private boolean hasSignificantStatusChange(HealthStatus previous, HealthStatus current) {
        if (previous == null || current == null) {
            return true;
        }
        
        // Status change is significant if severity level changes
        return previous.getSeverity() != current.getSeverity();
    }
    
    /**
     * Check if there are new critical issues not seen in previous checks
     */
    private boolean hasNewCriticalIssues(MigrationHealthReport report) {
        if (healthHistory.isEmpty()) {
            return !report.getIssues().isEmpty();
        }
        
        HealthSnapshot lastSnapshot = healthHistory.get(healthHistory.size() - 1);
        Set<String> previousIssues = new HashSet<>(lastSnapshot.getIssues());
        Set<String> currentIssues = new HashSet<>(report.getIssues());
        
        // Check for new issues not in previous snapshot
        currentIssues.removeAll(previousIssues);
        
        return !currentIssues.isEmpty() && 
               currentIssues.stream().anyMatch(issue -> 
                   issue.toLowerCase().contains("critical") || 
                   issue.toLowerCase().contains("error") ||
                   issue.toLowerCase().contains("failed")
               );
    }
    
    /**
     * Notify all registered observers with proper error isolation
     */
    private void notifyAllObservers(HealthStatusChangeEvent event) {
        if (event == null) {
            logger.warn("Cannot notify observers - event is null");
            return;
        }
        
        // Create a snapshot of observers to avoid concurrent modification
        List<HealthStatusObserver> observerSnapshot;
        synchronized (observers) {
            if (observers.isEmpty()) {
                logger.debug("No observers registered for health status changes");
                return;
            }
            observerSnapshot = new ArrayList<>(observers);
        }
        
        logger.debug("Notifying {} observer(s) of health status change", observerSnapshot.size());
        
        int notificationCount = 0;
        int errorCount = 0;
        
        for (HealthStatusObserver observer : observerSnapshot) {
            try {
                if (observer == null) {
                    logger.warn("Null observer found in observer list - skipping");
                    continue;
                }
                
                long startTime = System.currentTimeMillis();
                observer.onHealthStatusChanged(event);
                long duration = System.currentTimeMillis() - startTime;
                
                notificationCount++;
                logger.debug("Notified observer {} in {}ms", observer.getClass().getSimpleName(), duration);
                
                // Warn about slow observers
                if (duration > 5000) { // 5 seconds
                    logger.warn("Observer {} took {}ms to process health status change - consider async processing", 
                              observer.getClass().getSimpleName(), duration);
                }
                
            } catch (Exception e) {
                errorCount++;
                logger.error("Error notifying observer {} of health status change: {} - {}", 
                           observer.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage(), e);
                
                // Consider removing observers that consistently fail
                // This could be implemented as a failure counter per observer
            }
        }
        
        logger.debug("Health status notification complete: {}/{} observers notified successfully, {} errors", 
                    notificationCount, observerSnapshot.size(), errorCount);
        
        if (errorCount > 0) {
            logger.warn("Failed to notify {} out of {} observers", errorCount, observerSnapshot.size());
        }
    }
    
    /**
     * Register a health status observer with validation
     */
    public void addObserver(HealthStatusObserver observer) {
        if (observer == null) {
            logger.warn("Cannot add null observer");
            return;
        }
        
        synchronized (observers) {
            if (observers.contains(observer)) {
                logger.debug("Observer {} already registered", observer.getClass().getSimpleName());
                return;
            }
            
            // Prevent adding too many observers to avoid performance issues
            if (observers.size() >= 50) {
                logger.warn("Maximum number of observers (50) reached - cannot add observer: {}", 
                          observer.getClass().getSimpleName());
                return;
            }
            
            observers.add(observer);
            logger.info("Registered health status observer: {} (total: {})", 
                       observer.getClass().getSimpleName(), observers.size());
        }
    }
    
    /**
     * Unregister a health status observer
     */
    public void removeObserver(HealthStatusObserver observer) {
        if (observer == null) {
            logger.warn("Cannot remove null observer");
            return;
        }
        
        synchronized (observers) {
            if (observers.remove(observer)) {
                logger.info("Unregistered health status observer: {} (remaining: {})", 
                           observer.getClass().getSimpleName(), observers.size());
            } else {
                logger.debug("Observer {} was not registered", observer.getClass().getSimpleName());
            }
        }
    }
    
    /**
     * Remove all observers
     */
    public void clearObservers() {
        synchronized (observers) {
            int removedCount = observers.size();
            observers.clear();
            if (removedCount > 0) {
                logger.info("Removed all {} health status observers", removedCount);
            }
        }
    }
    
    /**
     * Get count of registered observers
     */
    public int getObserverCount() {
        return observers.size();
    }
    
    /**
     * Get current health status
     */
    public MigrationHealthReport getCurrentHealthStatus() {
        if (currentMetrics.isEmpty()) {
            return performComprehensiveHealthCheck();
        }
        
        HealthStatus overallStatus = calculateOverallHealthStatus(currentMetrics);
        List<String> currentIssues = currentMetrics.values().stream()
            .filter(metric -> metric.getStatus().getSeverity() >= HealthStatus.WARNING.getSeverity())
            .map(metric -> String.format("[%s] %s", metric.getName().toUpperCase(), metric.getMessage()))
            .collect(Collectors.toList());
        
        return new MigrationHealthReport(overallStatus, currentMetrics, currentIssues, LocalDateTime.now());
    }
    
    /**
     * Get health trend analysis
     */
    public Map<String, Object> getHealthTrendAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        
        if (healthHistory.size() < 2) {
            analysis.put("status", "insufficient_data");
            analysis.put("message", "Need at least 2 health snapshots for trend analysis");
            return analysis;
        }
        
        // Analyze trends over the last 10 snapshots
        int analysisWindow = Math.min(10, healthHistory.size());
        List<HealthSnapshot> recentHistory = healthHistory.subList(healthHistory.size() - analysisWindow, healthHistory.size());
        
        // Calculate trend direction
        Map<String, String> metricTrends = new HashMap<>();
        for (String metricName : currentMetrics.keySet()) {
            List<Double> values = recentHistory.stream()
                .filter(snapshot -> snapshot.getMetrics().containsKey(metricName))
                .map(snapshot -> snapshot.getMetrics().get(metricName).getValue())
                .collect(Collectors.toList());
            
            if (values.size() >= 2) {
                double firstValue = values.get(0);
                double lastValue = values.get(values.size() - 1);
                
                if (lastValue > firstValue + 2.0) {
                    metricTrends.put(metricName, "improving");
                } else if (lastValue < firstValue - 2.0) {
                    metricTrends.put(metricName, "degrading");
                } else {
                    metricTrends.put(metricName, "stable");
                }
            }
        }
        
        analysis.put("status", "success");
        analysis.put("metricTrends", metricTrends);
        analysis.put("analysisWindow", analysisWindow);
        analysis.put("totalSnapshots", healthHistory.size());
        
        return analysis;
    }
}