package com.integrixs.backend.controller;

import com.integrixs.backend.service.MigrationHealthMonitorService;
import com.integrixs.backend.service.MigrationHealthMonitorService.MigrationHealthReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * REST Controller for migration health monitoring operations.
 * Provides real-time health status, trend analysis, and alerting endpoints.
 * 
 * Following REST best practices:
 * - Proper HTTP status codes and response formats
 * - Admin-only access for sensitive operations
 * - Comprehensive error handling with user-friendly messages
 * - Consistent JSON response structure
 */
@RestController
@RequestMapping("/api/migration/health")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class MigrationHealthController {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationHealthController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final MigrationHealthMonitorService healthMonitorService;
    
    @Autowired
    public MigrationHealthController(MigrationHealthMonitorService healthMonitorService) {
        this.healthMonitorService = healthMonitorService;
    }
    
    /**
     * Get current migration health status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getCurrentHealthStatus() {
        try {
            logger.info("API request: Get current migration health status");
            
            MigrationHealthReport report = healthMonitorService.getCurrentHealthStatus();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "healthReport", Map.of(
                    "overallStatus", report.getOverallStatus().name(),
                    "overallStatusDescription", report.getOverallStatus().getDescription(),
                    "timestamp", report.getTimestamp().format(TIMESTAMP_FORMAT),
                    "metricsCount", report.getMetrics().size(),
                    "issuesCount", report.getIssues().size(),
                    "severity", report.getOverallStatus().getSeverity()
                ),
                "metrics", report.getMetrics(),
                "issues", report.getIssues()
            ));
            
        } catch (Exception e) {
            logger.error("Error getting current health status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error getting health status",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Perform immediate comprehensive health check
     */
    @PostMapping("/check")
    public ResponseEntity<?> performHealthCheck() {
        try {
            logger.info("API request: Perform immediate health check");
            
            MigrationHealthReport report = healthMonitorService.performComprehensiveHealthCheck();
            
            HttpStatus httpStatus = determineHttpStatusFromHealthReport(report);
            
            return ResponseEntity.status(httpStatus).body(Map.of(
                "status", httpStatus == HttpStatus.OK ? "success" : "warning",
                "message", String.format("Health check completed - Status: %s", report.getOverallStatus().name()),
                "healthReport", Map.of(
                    "overallStatus", report.getOverallStatus().name(),
                    "overallStatusDescription", report.getOverallStatus().getDescription(),
                    "timestamp", report.getTimestamp().format(TIMESTAMP_FORMAT),
                    "metricsCount", report.getMetrics().size(),
                    "issuesCount", report.getIssues().size(),
                    "severity", report.getOverallStatus().getSeverity()
                ),
                "metrics", report.getMetrics(),
                "issues", report.getIssues()
            ));
            
        } catch (Exception e) {
            logger.error("Error performing health check", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error during health check",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get health trend analysis
     */
    @GetMapping("/trends")
    public ResponseEntity<?> getHealthTrends() {
        try {
            logger.info("API request: Get health trend analysis");
            
            Map<String, Object> trendAnalysis = healthMonitorService.getHealthTrendAnalysis();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Health trend analysis completed",
                "trendAnalysis", trendAnalysis
            ));
            
        } catch (Exception e) {
            logger.error("Error getting health trends", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error getting health trends",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get detailed health metrics for specific metric
     */
    @GetMapping("/metrics/{metricName}")
    public ResponseEntity<?> getMetricDetails(@PathVariable String metricName) {
        try {
            logger.info("API request: Get details for metric: {}", metricName);
            
            MigrationHealthReport report = healthMonitorService.getCurrentHealthStatus();
            
            if (!report.getMetrics().containsKey(metricName)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "error",
                    "message", "Metric not found: " + metricName,
                    "availableMetrics", report.getMetrics().keySet()
                ));
            }
            
            MigrationHealthMonitorService.HealthMetric metric = report.getMetrics().get(metricName);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Metric details retrieved successfully",
                "metric", Map.of(
                    "name", metric.getName(),
                    "value", metric.getValue(),
                    "threshold", metric.getThreshold(),
                    "status", metric.getStatus().name(),
                    "statusDescription", metric.getStatus().getDescription(),
                    "message", metric.getMessage(),
                    "timestamp", metric.getTimestamp().format(TIMESTAMP_FORMAT),
                    "details", metric.getDetails(),
                    "severity", metric.getStatus().getSeverity()
                )
            ));
            
        } catch (Exception e) {
            logger.error("Error getting metric details for: {}", metricName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error getting metric details",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get health dashboard summary
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getHealthDashboard() {
        try {
            logger.info("API request: Get health dashboard summary");
            
            MigrationHealthReport report = healthMonitorService.getCurrentHealthStatus();
            Map<String, Object> trends = healthMonitorService.getHealthTrendAnalysis();
            
            // Calculate summary statistics
            long excellentCount = report.getMetrics().values().stream()
                .mapToLong(metric -> metric.getStatus() == MigrationHealthMonitorService.HealthStatus.EXCELLENT ? 1 : 0)
                .sum();
            
            long goodCount = report.getMetrics().values().stream()
                .mapToLong(metric -> metric.getStatus() == MigrationHealthMonitorService.HealthStatus.GOOD ? 1 : 0)
                .sum();
            
            long warningCount = report.getMetrics().values().stream()
                .mapToLong(metric -> metric.getStatus() == MigrationHealthMonitorService.HealthStatus.WARNING ? 1 : 0)
                .sum();
            
            long criticalCount = report.getMetrics().values().stream()
                .mapToLong(metric -> metric.getStatus() == MigrationHealthMonitorService.HealthStatus.CRITICAL ? 1 : 0)
                .sum();
            
            long failedCount = report.getMetrics().values().stream()
                .mapToLong(metric -> metric.getStatus() == MigrationHealthMonitorService.HealthStatus.FAILED ? 1 : 0)
                .sum();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "dashboard", Map.of(
                    "overallHealth", Map.of(
                        "status", report.getOverallStatus().name(),
                        "description", report.getOverallStatus().getDescription(),
                        "severity", report.getOverallStatus().getSeverity(),
                        "timestamp", report.getTimestamp().format(TIMESTAMP_FORMAT)
                    ),
                    "metricsSummary", Map.of(
                        "total", report.getMetrics().size(),
                        "excellent", excellentCount,
                        "good", goodCount,
                        "warning", warningCount,
                        "critical", criticalCount,
                        "failed", failedCount
                    ),
                    "issuesSummary", Map.of(
                        "total", report.getIssues().size(),
                        "recentIssues", report.getIssues()
                    ),
                    "trends", trends,
                    "quickActions", Map.of(
                        "runHealthCheck", "/api/migration/health/check",
                        "viewDetailedMetrics", "/api/migration/health/status",
                        "viewTrends", "/api/migration/health/trends"
                    )
                )
            ));
            
        } catch (Exception e) {
            logger.error("Error getting health dashboard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error getting health dashboard",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get available health metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getAvailableMetrics() {
        try {
            logger.info("API request: Get available health metrics");
            
            MigrationHealthReport report = healthMonitorService.getCurrentHealthStatus();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Available metrics retrieved successfully",
                "metrics", report.getMetrics().keySet(),
                "totalMetrics", report.getMetrics().size()
            ));
            
        } catch (Exception e) {
            logger.error("Error getting available metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error getting available metrics",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Determine appropriate HTTP status code based on health report
     */
    private HttpStatus determineHttpStatusFromHealthReport(MigrationHealthReport report) {
        switch (report.getOverallStatus()) {
            case EXCELLENT:
            case GOOD:
                return HttpStatus.OK;
            case WARNING:
                return HttpStatus.ACCEPTED; // 202 - Accepted but with warnings
            case CRITICAL:
                return HttpStatus.CONFLICT; // 409 - Conflict (issues that need resolution)
            case FAILED:
                return HttpStatus.SERVICE_UNAVAILABLE; // 503 - Service unavailable
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}