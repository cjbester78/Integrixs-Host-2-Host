package com.integrixs.backend.controller;

import com.integrixs.core.repository.TransactionLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for transaction logs management
 */
@RestController
@RequestMapping("/api/transaction-logs")
public class TransactionLogsController {

    @Autowired
    private TransactionLogRepository transactionLogRepository;

    /**
     * Get recent transaction logs from database
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentTransactionLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        
        List<Map<String, Object>> logs = transactionLogRepository.getRecentLogsForApi(limit, level, category, search);
        return ResponseEntity.ok(logs);
    }

    /**
     * Export transaction logs as CSV file
     */
    @GetMapping("/export")
    public ResponseEntity<String> exportTransactionLogs(
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "csv") String format) {
        
        List<Map<String, Object>> logs = transactionLogRepository.getRecentLogsForApi(limit, level, category, null);
        
        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder();
            
            // CSV header
            csv.append("Timestamp,Level,Category,Component,Source,Message,Username,IP Address,Session ID,Correlation ID,Execution Time (ms)\n");
            
            // CSV data
            for (Map<String, Object> log : logs) {
                csv.append(escapeCSV(getString(log, "timestamp"))).append(",");
                csv.append(escapeCSV(getString(log, "level"))).append(",");
                csv.append(escapeCSV(getString(log, "category"))).append(",");
                csv.append(escapeCSV(getString(log, "component"))).append(",");
                csv.append(escapeCSV(getString(log, "source"))).append(",");
                csv.append(escapeCSV(getString(log, "message"))).append(",");
                csv.append(escapeCSV(getString(log, "username"))).append(",");
                csv.append(escapeCSV(getString(log, "ipAddress"))).append(",");
                csv.append(escapeCSV(getString(log, "sessionId"))).append(",");
                csv.append(escapeCSV(getString(log, "correlationId"))).append(",");
                csv.append(escapeCSV(getString(log, "executionTimeMs"))).append("\n");
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", 
                "transaction-logs-" + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".csv");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(csv.toString());
        }
        
        // Default to JSON format if not CSV
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(logs.toString());
    }
    
    /**
     * Safely get string value from map
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }
    
    /**
     * Escape CSV values
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}