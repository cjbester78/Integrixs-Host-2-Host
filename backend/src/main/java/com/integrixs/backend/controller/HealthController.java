package com.integrixs.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check controller for monitoring system availability
 * Public endpoint for basic health checks
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * Basic health check endpoint (public)
     */
    @GetMapping
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Integrixs Host 2 Host",
            "version", "1.0.0-SNAPSHOT",
            "timestamp", LocalDateTime.now(),
            "uptime", getUptime()
        ));
    }

    /**
     * Ready check endpoint (public)
     */
    @GetMapping("/ready")
    public ResponseEntity<?> readyCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "READY",
            "message", "Service is ready to accept requests",
            "timestamp", LocalDateTime.now()
        ));
    }

    /**
     * Live check endpoint (public)
     */
    @GetMapping("/live")
    public ResponseEntity<?> liveCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "ALIVE",
            "message", "Service is alive and responsive",
            "timestamp", LocalDateTime.now()
        ));
    }

    private String getUptime() {
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
}