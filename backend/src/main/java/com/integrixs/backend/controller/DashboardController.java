package com.integrixs.backend.controller;

import com.integrixs.backend.model.User;
import com.integrixs.backend.service.UserService;
import com.integrixs.backend.service.SystemConfigurationService;
import com.integrixs.core.service.AdapterManagementService;
import com.integrixs.core.service.FlowExecutionService;
import com.integrixs.core.service.TransactionLogService;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.FlowExecution;
import com.integrixs.shared.model.TransactionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard controller for monitoring and system status
 * Provides real-time information for both administrators and viewers
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final UserService userService;
    private final SystemConfigurationService configService;
    private final AdapterManagementService adapterManagementService;
    private final FlowExecutionService flowExecutionService;
    private final TransactionLogService transactionLogService;

    public DashboardController(UserService userService, SystemConfigurationService configService, 
                             AdapterManagementService adapterManagementService, FlowExecutionService flowExecutionService,
                             TransactionLogService transactionLogService) {
        this.userService = userService;
        this.configService = configService;
        this.adapterManagementService = adapterManagementService;
        this.flowExecutionService = flowExecutionService;
        this.transactionLogService = transactionLogService;
    }

    /**
     * Get dashboard overview
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<?> getDashboardOverview() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();
            
            Map<String, Object> overview = new HashMap<>();
            
            // System status
            overview.put("systemStatus", "RUNNING");
            overview.put("uptime", getSystemUptime());
            overview.put("timestamp", LocalDateTime.now());
            
            // User info
            overview.put("currentUser", Map.of(
                "username", currentUser.getUsername(),
                "role", currentUser.getRole(),
                "fullName", currentUser.getFullName()
            ));
            
            // Basic statistics (everyone can see these)
            overview.put("statistics", Map.of(
                "totalInterfaces", 0, // Will be implemented when we have interfaces
                "activeInterfaces", 0,
                "todaysExecutions", 0,
                "successfulExecutions", 0
            ));
            
            // Admin-only statistics
            if (currentUser.hasFullAccess()) {
                Map<String, Long> userStats = userService.getUserStatistics();
                overview.put("userStatistics", userStats);
                
                // Recent activities
                List<User> recentUsers = userService.findUsersCreatedAfter(
                    LocalDateTime.now().minusDays(7)
                );
                overview.put("recentUserCount", recentUsers.size());
                
                List<User> neverLoggedIn = userService.findUsersWhoNeverLoggedIn();
                overview.put("usersNeverLoggedIn", neverLoggedIn.size());
            }
            
            // Recent activity summary
            overview.put("recentActivity", getRecentActivity(currentUser));
            
            return ResponseEntity.ok(overview);

        } catch (Exception e) {
            logger.error("Error getting dashboard overview: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to load dashboard"));
        }
    }

    /**
     * Get system health status
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<?> getSystemHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // Status matching frontend enum
            health.put("status", "UP");
            health.put("uptime", getSystemUptime());
            health.put("javaVersion", System.getProperty("java.version"));
            health.put("timestamp", LocalDateTime.now().toString());
            
            // Memory information
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double usagePercent = Math.round((double) usedMemory / totalMemory * 100);
            
            health.put("memoryUsage", usagePercent + "%");
            health.put("cpuUsage", "N/A"); // CPU usage would require additional libraries
            health.put("diskUsage", "N/A"); // Disk usage would require additional implementation
            
            return ResponseEntity.ok(health);

        } catch (Exception e) {
            logger.error("Error getting system health: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of(
                        "status", "DOWN",
                        "error", "Health check failed",
                        "uptime", "0s",
                        "javaVersion", "Unknown",
                        "memoryUsage", "0%",
                        "cpuUsage", "0%",
                        "diskUsage", "0%",
                        "timestamp", LocalDateTime.now().toString()
                    ));
        }
    }

    /**
     * Get adapter statistics
     */
    @GetMapping("/adapter-stats")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<?> getAdapterStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // Get actual adapter statistics from the service
            List<Adapter> allAdapters = adapterManagementService.getAllAdapters();
            
            // Get execution statistics from flow execution service
            Map<String, Object> execStats = flowExecutionService.getExecutionStatistics();
            
            // Calculate statistics matching frontend expectations
            Long totalOperationsLong = (Long) execStats.get("total_executions");
            Long completedOperationsLong = (Long) execStats.get("completed_executions");
            Long failedOperationsLong = (Long) execStats.get("failed_executions");
            Long totalFilesProcessedLong = (Long) execStats.get("total_files_processed");
            
            int operationsToday = totalOperationsLong != null ? totalOperationsLong.intValue() : 0;
            int successfulOperations = completedOperationsLong != null ? completedOperationsLong.intValue() : 0;
            int errorsToday = failedOperationsLong != null ? failedOperationsLong.intValue() : 0;
            int filesProcessed = totalFilesProcessedLong != null ? totalFilesProcessedLong.intValue() : 0;
            
            // Get files processed in last hour (approximation using recent executions)
            List<FlowExecution> recentExecutions = flowExecutionService.getRecentExecutions(20);
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            int filesLastHour = recentExecutions.stream()
                .filter(exec -> exec.getStartedAt() != null && exec.getStartedAt().isAfter(oneHourAgo))
                .mapToInt(FlowExecution::getTotalFilesProcessed)
                .sum();
            
            // Calculate success rate
            int totalOperations = successfulOperations + errorsToday;
            double successRate = totalOperations > 0 ? ((double) successfulOperations / totalOperations) * 100 : 100.0;
            
            stats.put("operationsToday", successfulOperations);
            stats.put("operationsTrend", totalOperations > 0 ? 
                String.format("Success rate: %.1f%%", successRate) : "No operations today");
            stats.put("filesProcessed", filesProcessed);
            stats.put("filesLastHour", filesLastHour);
            stats.put("successRate", Math.round(successRate));
            stats.put("errorsToday", errorsToday);
            
            // Transform adapters to frontend format
            List<Map<String, Object>> adapterInfos = allAdapters.stream()
                .map(adapter -> {
                    Map<String, Object> adapterInfo = new HashMap<>();
                    adapterInfo.put("id", adapter.getId().toString());
                    adapterInfo.put("name", adapter.getName());
                    adapterInfo.put("type", adapter.getAdapterType());
                    
                    // Map adapter status to frontend status
                    String status;
                    if (adapter.isActive()) {
                        // If adapter is active (enabled), show as ACTIVE regardless of whether it's running or stopped
                        status = "ACTIVE";
                    } else {
                        // If adapter is not active (disabled), show as INACTIVE
                        status = "INACTIVE";
                    }
                    adapterInfo.put("status", status);
                    
                    // Use last test time as last run indicator
                    adapterInfo.put("lastRun", adapter.getLastTestAt() != null ? 
                        adapter.getLastTestAt().toString() : null);
                    
                    // Calculate files processed today for this adapter
                    LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
                    int adapterFilesToday = recentExecutions.stream()
                        .filter(exec -> exec.getFlowName() != null && exec.getFlowName().equals(adapter.getName()))
                        .filter(exec -> exec.getStartedAt() != null && exec.getStartedAt().isAfter(startOfDay))
                        .mapToInt(FlowExecution::getTotalFilesProcessed)
                        .sum();
                    
                    adapterInfo.put("filesToday", adapterFilesToday);
                    adapterInfo.put("configuration", adapter.getConfiguration());
                    
                    return adapterInfo;
                })
                .toList();
            
            stats.put("adapters", adapterInfos);
            
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Error getting adapter statistics: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of(
                        "operationsToday", 0,
                        "operationsTrend", "Error loading data",
                        "filesProcessed", 0,
                        "filesLastHour", 0,
                        "successRate", 0,
                        "errorsToday", 0,
                        "adapters", List.of(),
                        "error", "Failed to retrieve adapter statistics"
                    ));
        }
    }

    /**
     * Get recent executions from flow execution service
     */
    @GetMapping("/recent-executions")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<?> getRecentExecutions(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        try {
            // Get actual execution data from flow execution service
            List<FlowExecution> recentExecutions = flowExecutionService.getRecentExecutions(limit);
            
            List<Map<String, Object>> executions = recentExecutions.stream()
                .map(execution -> {
                    Map<String, Object> exec = new HashMap<>();
                    exec.put("id", execution.getId().toString());
                    exec.put("adapterName", execution.getFlowName());
                    exec.put("operation", execution.getTriggerType().name());
                    
                    // Map execution status to frontend RecentExecution status enum
                    String status = switch (execution.getExecutionStatus()) {
                        case COMPLETED -> "SUCCESS";
                        case FAILED -> "FAILED";
                        case RUNNING -> "RUNNING";
                        case PENDING -> "IN_PROGRESS";
                        case CANCELLED -> "FAILED";
                        case TIMEOUT -> "FAILED";
                        case RETRY_PENDING -> "IN_PROGRESS";
                    };
                    exec.put("status", status);
                    
                    exec.put("message", execution.getErrorMessage() != null ? 
                        execution.getErrorMessage() : "Execution " + execution.getExecutionStatus().name().toLowerCase());
                    exec.put("startTime", execution.getStartedAt() != null ? 
                        execution.getStartedAt().toString() : null);
                    exec.put("endTime", execution.getCompletedAt() != null ? 
                        execution.getCompletedAt().toString() : null);
                    exec.put("duration", execution.getDurationMs());
                    exec.put("filesProcessed", execution.getTotalFilesProcessed());
                    exec.put("errorCount", execution.getFilesFailed());
                    
                    return exec;
                })
                .toList();
            
            return ResponseEntity.ok(executions);

        } catch (Exception e) {
            logger.error("Error getting recent executions: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(List.of());
        }
    }

    /**
     * Get system metrics
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<?> getSystemMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // JVM metrics
            Runtime runtime = Runtime.getRuntime();
            metrics.put("jvm", Map.of(
                "version", System.getProperty("java.version"),
                "vendor", System.getProperty("java.vendor"),
                "availableProcessors", runtime.availableProcessors(),
                "maxMemory", runtime.maxMemory(),
                "totalMemory", runtime.totalMemory(),
                "freeMemory", runtime.freeMemory()
            ));
            
            // System properties
            metrics.put("system", Map.of(
                "osName", System.getProperty("os.name"),
                "osVersion", System.getProperty("os.version"),
                "osArch", System.getProperty("os.arch"),
                "userDir", System.getProperty("user.dir"),
                "userHome", System.getProperty("user.home")
            ));
            
            // Application metrics
            Map<String, Long> userStats = userService.getUserStatistics();
            metrics.put("application", Map.of(
                "userStatistics", userStats,
                "uptime", getSystemUptime(),
                "startTime", getSystemStartTime()
            ));
            
            metrics.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(metrics);

        } catch (Exception e) {
            logger.error("Error getting system metrics: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve metrics"));
        }
    }

    /**
     * Get alerts and notifications
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'VIEWER')")
    public ResponseEntity<?> getAlerts() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) auth.getPrincipal();
            
            Map<String, Object> alerts = new HashMap<>();
            
            // System alerts
            List<Map<String, Object>> systemAlerts = List.of(
                Map.of(
                    "id", "welcome",
                    "type", "INFO",
                    "title", "Welcome to Integrixs Host 2 Host",
                    "message", "System is ready for configuration",
                    "timestamp", LocalDateTime.now(),
                    "read", false
                )
            );
            
            // User-specific alerts
            if (currentUser.hasFullAccess()) {
                // Admin alerts
                List<User> neverLoggedIn = userService.findUsersWhoNeverLoggedIn();
                if (neverLoggedIn.size() > 1) { // More than just the current admin
                    systemAlerts = new java.util.ArrayList<>(systemAlerts);
                    systemAlerts.add(Map.of(
                        "id", "inactive-users",
                        "type", "WARNING",
                        "title", "Inactive Users",
                        "message", (neverLoggedIn.size() - 1) + " users have never logged in",
                        "timestamp", LocalDateTime.now(),
                        "read", false
                    ));
                }
            }
            
            alerts.put("alerts", systemAlerts);
            alerts.put("unreadCount", systemAlerts.size());
            alerts.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(alerts);

        } catch (Exception e) {
            logger.error("Error getting alerts: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to retrieve alerts"));
        }
    }

    // Helper methods
    private String checkUserServiceHealth() {
        try {
            userService.getUserStatistics();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String getSystemUptime() {
        long uptimeMs = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptimeMs / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, secs);
        } else {
            return String.format("%d seconds", secs);
        }
    }

    private LocalDateTime getSystemStartTime() {
        long startTime = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(startTime),
            java.time.ZoneId.systemDefault()
        );
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private List<Map<String, Object>> getRecentActivity(User currentUser) {
        try {
            // Get recent transaction logs for activity feed
            List<TransactionLog> recentLogs = transactionLogService.getRecentTransactionLogs(10);
            
            List<Map<String, Object>> activities = recentLogs.stream()
                .map(this::convertTransactionLogToActivity)
                .collect(java.util.stream.Collectors.toList());
            
            // Add current user login if not already present
            boolean hasCurrentUserLogin = activities.stream()
                .anyMatch(activity -> 
                    "AUTH".equals(activity.get("type")) && 
                    currentUser.getUsername().equals(activity.get("user"))
                );
            
            if (!hasCurrentUserLogin) {
                activities.add(0, Map.of(
                    "id", "user-login-" + currentUser.getUsername(),
                    "type", "AUTH",
                    "description", "User " + currentUser.getUsername() + " logged in",
                    "timestamp", currentUser.getLastLogin() != null ? currentUser.getLastLogin() : LocalDateTime.now(),
                    "user", currentUser.getUsername(),
                    "status", "SUCCESS"
                ));
            }
            
            // Add system start event if activities list is sparse
            if (activities.size() < 3) {
                activities.add(Map.of(
                    "id", "system-start",
                    "type", "SYSTEM",
                    "description", "System started successfully",
                    "timestamp", getSystemStartTime(),
                    "user", "System",
                    "status", "SUCCESS"
                ));
            }
            
            // Sort by timestamp descending and limit to 10
            return activities.stream()
                .sorted((a, b) -> {
                    LocalDateTime timestampA = (LocalDateTime) a.get("timestamp");
                    LocalDateTime timestampB = (LocalDateTime) b.get("timestamp");
                    return timestampB.compareTo(timestampA);
                })
                .limit(10)
                .collect(java.util.stream.Collectors.toList());
                
        } catch (Exception e) {
            logger.warn("Failed to load recent activity from transaction logs, using fallback: {}", e.getMessage());
            
            // Fallback to basic activity
            return List.of(
                Map.of(
                    "id", "system-start",
                    "type", "SYSTEM",
                    "description", "System started successfully",
                    "timestamp", getSystemStartTime(),
                    "user", "System",
                    "status", "SUCCESS"
                ),
                Map.of(
                    "id", "user-login",
                    "type", "AUTH",
                    "description", "User " + currentUser.getUsername() + " logged in",
                    "timestamp", currentUser.getLastLogin() != null ? currentUser.getLastLogin() : LocalDateTime.now(),
                    "user", currentUser.getUsername(),
                    "status", "SUCCESS"
                )
            );
        }
    }
    
    private Map<String, Object> convertTransactionLogToActivity(TransactionLog log) {
        Map<String, Object> activity = new HashMap<>();
        
        activity.put("id", "log-" + log.getId());
        activity.put("type", mapCategoryToActivityType(log.getCategory()));
        activity.put("description", log.getMessage());
        activity.put("timestamp", log.getTimestamp());
        activity.put("user", log.getUsername() != null ? log.getUsername() : "System");
        
        // Determine status from category and message
        String status = "INFO";
        if (log.getCategory() != null) {
            if (log.getCategory().contains("ERROR") || log.getCategory().contains("FAILED")) {
                status = "ERROR";
            } else if (log.getCategory().contains("SUCCESS") || log.getCategory().contains("COMPLETED")) {
                status = "SUCCESS";
            } else if (log.getCategory().contains("WARN")) {
                status = "WARNING";
            }
        }
        activity.put("status", status);
        
        return activity;
    }
    
    private String mapCategoryToActivityType(String category) {
        if (category == null) return "SYSTEM";
        
        switch (category.toUpperCase()) {
            case "AUTHENTICATION":
            case "AUTH":
            case "LOGIN":
            case "LOGOUT":
                return "AUTH";
            case "FILE_TRANSFER":
            case "SFTP":
            case "EMAIL":
                return "TRANSFER";
            case "ADAPTER":
            case "ADAPTER_ERROR":
            case "ADAPTER_START":
            case "ADAPTER_STOP":
                return "ADAPTER";
            case "FLOW":
            case "FLOW_EXECUTION":
                return "FLOW";
            case "CONFIGURATION":
            case "CONFIG":
                return "CONFIG";
            case "USER_MANAGEMENT":
            case "USER":
                return "USER";
            default:
                return "SYSTEM";
        }
    }
}