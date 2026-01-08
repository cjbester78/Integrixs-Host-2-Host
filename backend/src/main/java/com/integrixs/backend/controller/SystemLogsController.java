package com.integrixs.backend.controller;

import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.backend.dto.ExecutionValidationResult;
import com.integrixs.backend.dto.request.AdminSystemRequest;
import com.integrixs.backend.service.AdministrativeRequestValidationService;
import com.integrixs.backend.service.ResponseStandardizationService;
import com.integrixs.shared.util.SecurityContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for system logs retrieval and management.
 * Provides endpoints for accessing application logs, errors, and system events.
 */
@RestController
@RequestMapping("/api/logs")
@PreAuthorize("hasAuthority('ADMINISTRATOR')")
public class SystemLogsController {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemLogsController.class);
    
    private final AdministrativeRequestValidationService validationService;
    private final ResponseStandardizationService responseService;
    
    @Autowired
    public SystemLogsController(AdministrativeRequestValidationService validationService,
                               ResponseStandardizationService responseService) {
        this.validationService = validationService;
        this.responseService = responseService;
    }
    
    /**
     * Get current user ID from security context.
     */
    private UUID getCurrentUserId() {
        return SecurityContextHelper.getCurrentUserId();
    }
    
    /**
     * Get recent system logs with filtering options.
     */
    @GetMapping("/system")
    public ResponseEntity<List<Map<String, Object>>> getSystemLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        UUID currentUserId = getCurrentUserId();
        logger.info("System logs requested by user: {} with limit={}, level={}, search={}", 
                   currentUserId, limit, level, search);
        
        // Create immutable request DTO
        AdminSystemRequest logsRequest = AdminSystemRequest.builder()
            .operation("system_logs")
            .search(search)
            .level(level)
            .requestedBy(currentUserId)
            .build();
        
        // Validate request
        ExecutionValidationResult validation = validationService.validateLogsRequest(
            Map.of(
                "limit", String.valueOf(limit),
                "level", level != null ? level : "",
                "search", search != null ? search : ""
            )
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid logs request: " + String.join(", ", validation.getErrors()));
        }
        
        try {
            List<Map<String, Object>> logs = readSystemLogs(limit, level, search);
            logger.info("Retrieved {} system logs for user: {}", logs.size(), currentUserId);
            return ResponseEntity.ok(logs);
            
        } catch (Exception e) {
            logger.error("Failed to get system logs for user: {}", currentUserId, e);
            throw new RuntimeException("Failed to retrieve system logs", e);
        }
    }
    
    /**
     * Read system logs from database table.
     */
    private List<Map<String, Object>> readSystemLogs(int limit, String level, String search) throws IOException {
        List<Map<String, Object>> logs = new ArrayList<>();
        
        try {
            // Build SQL query with filters
            StringBuilder sql = new StringBuilder("SELECT * FROM system_logs WHERE 1=1 ");
            List<Object> params = new ArrayList<>();
            int paramIndex = 1;
            
            // Add level filter
            if (level != null && !level.isEmpty() && !level.equals("ALL")) {
                sql.append(" AND log_level = ? ");
                params.add(level);
                paramIndex++;
            }
            
            // Add search filter
            if (search != null && !search.isEmpty()) {
                sql.append(" AND (message ILIKE ? OR logger_name ILIKE ? OR thread_name ILIKE ?) ");
                String searchPattern = "%" + search + "%";
                params.add(searchPattern);
                params.add(searchPattern);
                params.add(searchPattern);
                paramIndex += 3;
            }
            
            // Add ordering and limit
            sql.append(" ORDER BY timestamp DESC LIMIT ? ");
            params.add(limit);
            
            // Execute query using JDBC
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/h2h_dev", "h2h_user", "h2h_dev_password")) {
                
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }
                    
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> logEntry = new HashMap<>();
                            
                            logEntry.put("id", rs.getString("id"));
                            logEntry.put("timestamp", rs.getTimestamp("timestamp").toString());
                            logEntry.put("logLevel", rs.getString("log_level"));
                            logEntry.put("logCategory", rs.getString("log_category"));
                            logEntry.put("loggerName", rs.getString("logger_name"));
                            logEntry.put("threadName", rs.getString("thread_name"));
                            logEntry.put("message", rs.getString("message"));
                            logEntry.put("formattedMessage", rs.getString("formatted_message"));
                            logEntry.put("correlationId", rs.getString("correlation_id"));
                            logEntry.put("sessionId", rs.getString("session_id"));
                            logEntry.put("userId", rs.getString("user_id"));
                            logEntry.put("requestId", rs.getString("request_id"));
                            logEntry.put("requestMethod", rs.getString("request_method"));
                            logEntry.put("requestUri", rs.getString("request_uri"));
                            logEntry.put("remoteAddress", rs.getString("remote_address"));
                            logEntry.put("userAgent", rs.getString("user_agent"));
                            logEntry.put("applicationName", rs.getString("application_name"));
                            logEntry.put("environment", rs.getString("environment"));
                            logEntry.put("serverHostname", rs.getString("server_hostname"));
                            logEntry.put("exceptionClass", rs.getString("exception_class"));
                            logEntry.put("exceptionMessage", rs.getString("exception_message"));
                            logEntry.put("source", "database");
                            
                            logs.add(logEntry);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to read system logs from database", e);
            throw new IOException("Failed to read system logs from database", e);
        }
        
        return logs;
    }
    
}