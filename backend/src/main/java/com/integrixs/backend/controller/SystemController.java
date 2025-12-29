package com.integrixs.backend.controller;

import com.integrixs.backend.service.SystemService;
import com.integrixs.core.repository.SystemLogRepository;
import com.integrixs.shared.dto.SystemHealth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for system monitoring and health checks
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired
    private SystemService systemService;

    @Autowired
    private SystemLogRepository systemLogRepository;

    /**
     * Get overall system health status
     */
    @GetMapping("/health")
    public ResponseEntity<SystemHealth> getSystemHealth() {
        SystemHealth health = systemService.getSystemHealth();
        return ResponseEntity.ok(health);
    }

    /**
     * Get application version and build information
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = systemService.getSystemInfo();
        return ResponseEntity.ok(info);
    }

    /**
     * Get system metrics (memory usage, disk space, etc.)
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        Map<String, Object> metrics = systemService.getSystemMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get recent operation logs from database
     */
    @GetMapping("/logs/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        
        List<Map<String, Object>> logs = systemLogRepository.getRecentLogsForApi(limit, level, category, search);
        return ResponseEntity.ok(logs);
    }

    /**
     * Export logs as CSV file
     */
    @GetMapping("/logs/export")
    public ResponseEntity<String> exportLogs(
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(required = false) String bankName,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "csv") String format) {
        
        String exportData = systemService.exportLogs(limit, bankName, level, format);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", 
            "system-logs-" + java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".csv");
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(exportData);
    }

    /**
     * Get operation statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getOperationStatistics(
            @RequestParam(required = false) String bankName,
            @RequestParam(defaultValue = "24") int hours) {
        
        Map<String, Object> stats = systemService.getOperationStatistics(bankName, hours);
        return ResponseEntity.ok(stats);
    }

    /**
     * Clear old log files
     */
    @PostMapping("/cleanup/logs")
    public ResponseEntity<Map<String, Object>> cleanupLogs(
            @RequestParam(defaultValue = "30") int retentionDays) {
        
        Map<String, Object> result = systemService.cleanupLogs(retentionDays);
        return ResponseEntity.ok(result);
    }

    /**
     * Test all configured bank connections
     */
    @PostMapping("/test-all-connections")
    public ResponseEntity<Map<String, Object>> testAllConnections() {
        Map<String, Object> results = systemService.testAllBankConnections();
        return ResponseEntity.ok(results);
    }
}