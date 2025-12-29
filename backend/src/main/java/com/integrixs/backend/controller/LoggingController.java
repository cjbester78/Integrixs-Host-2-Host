package com.integrixs.backend.controller;

import com.integrixs.core.service.DynamicLoggingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for managing dynamic logging configuration
 * Allows administrators to view and modify logging settings at runtime
 */
@RestController
@RequestMapping("/api/admin/logging")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class LoggingController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingController.class);
    private final DynamicLoggingService dynamicLoggingService;
    
    @Autowired
    public LoggingController(DynamicLoggingService dynamicLoggingService) {
        this.dynamicLoggingService = dynamicLoggingService;
    }
    
    /**
     * Get current logging configuration
     */
    @GetMapping("/configuration")
    public ResponseEntity<Map<String, String>> getLoggingConfiguration() {
        try {
            Map<String, String> config = dynamicLoggingService.getCurrentLoggingConfiguration();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            logger.error("Failed to get logging configuration", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get logging configuration summary for monitoring
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getLoggingConfigurationSummary() {
        try {
            Map<String, Object> summary = dynamicLoggingService.getLoggingConfigurationSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Failed to get logging configuration summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get current effective logging level for a specific logger
     */
    @GetMapping("/loggers/{loggerName}")
    public ResponseEntity<Map<String, String>> getLoggerLevel(@PathVariable String loggerName) {
        try {
            String level = dynamicLoggingService.getLoggerLevel(loggerName);
            return ResponseEntity.ok(Map.of(
                "loggerName", loggerName,
                "effectiveLevel", level
            ));
        } catch (Exception e) {
            logger.error("Failed to get logger level for '{}'", loggerName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Set logging level for a specific logger
     */
    @PutMapping("/loggers/{loggerName}")
    public ResponseEntity<Map<String, String>> setLoggerLevel(
            @PathVariable String loggerName, 
            @RequestBody Map<String, String> request) {
        
        try {
            String level = request.get("level");
            if (level == null || level.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            // Validate log level
            if (!isValidLogLevel(level)) {
                return ResponseEntity.badRequest().build();
            }
            
            dynamicLoggingService.setLoggerLevel(loggerName, level.toUpperCase());
            
            String newLevel = dynamicLoggingService.getLoggerLevel(loggerName);
            
            logger.info("Updated logger '{}' to level '{}' via API", loggerName, level);
            
            return ResponseEntity.ok(Map.of(
                "loggerName", loggerName,
                "previousLevel", request.getOrDefault("previousLevel", "UNKNOWN"),
                "newLevel", newLevel,
                "effectiveLevel", newLevel
            ));
            
        } catch (Exception e) {
            logger.error("Failed to set logger level for '{}' to '{}'", loggerName, request.get("level"), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Reload logging configuration from database
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadConfiguration() {
        try {
            long startTime = System.currentTimeMillis();
            
            dynamicLoggingService.reloadConfiguration();
            
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("Logging configuration reloaded via API in {}ms", duration);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Logging configuration reloaded successfully",
                "reloadTimeMs", duration,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to reload logging configuration", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Validate if the provided string is a valid log level
     */
    private boolean isValidLogLevel(String level) {
        if (level == null) {
            return false;
        }
        
        try {
            String upperLevel = level.toUpperCase();
            return upperLevel.equals("TRACE") || 
                   upperLevel.equals("DEBUG") || 
                   upperLevel.equals("INFO") || 
                   upperLevel.equals("WARN") || 
                   upperLevel.equals("ERROR") ||
                   upperLevel.equals("OFF");
        } catch (Exception e) {
            return false;
        }
    }
}