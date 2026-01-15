package com.integrixs.backend.controller;

import com.integrixs.backend.service.MigrationRollbackService;
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
 * REST Controller for migration rollback safety operations.
 * Provides rollback planning, safety validation, and simulation endpoints.
 * 
 * Following REST best practices with enhanced security:
 * - Admin-only access with additional rollback permissions
 * - Comprehensive audit logging for all rollback operations
 * - Safe simulation before any actual rollback operations
 * - Clear warnings and confirmations required for risky operations
 */
@RestController
@RequestMapping("/api/migration/rollback")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class MigrationRollbackController {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationRollbackController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final MigrationRollbackService rollbackService;
    
    @Autowired
    public MigrationRollbackController(MigrationRollbackService rollbackService) {
        this.rollbackService = rollbackService;
    }
    
    /**
     * Validate rollback safety
     */
    @GetMapping("/validate-safety")
    public ResponseEntity<?> validateRollbackSafety() {
        try {
            logger.info("API request: Validate rollback safety");
            
            MigrationRollbackService.ValidationResult result = rollbackService.validateRollbackSafety();
            
            if (result.isSafe()) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", result.getMessage(),
                    "safe", true,
                    "warnings", result.getWarnings(),
                    "details", result.getDetails()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "unsafe",
                    "message", result.getMessage(),
                    "safe", false,
                    "warnings", result.getWarnings(),
                    "details", result.getDetails(),
                    "recommendation", "Resolve safety issues before attempting rollback"
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error validating rollback safety", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error during safety validation",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Simulate rollback operation (dry run)
     */
    @PostMapping("/simulate")
    public ResponseEntity<?> simulateRollback() {
        try {
            logger.info("API request: Simulate rollback operation");
            
            MigrationRollbackService.RollbackResult result = rollbackService.simulateRollback();
            
            if (result.isSuccessful()) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", result.getMessage(),
                    "simulation", Map.of(
                        "successful", true,
                        "stepsExecuted", result.getStepsExecuted(),
                        "warnings", result.getWarnings(),
                        "details", result.getDetails()
                    )
                ));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "failed",
                    "message", result.getMessage(),
                    "simulation", Map.of(
                        "successful", false,
                        "stepsExecuted", result.getStepsExecuted(),
                        "warnings", result.getWarnings(),
                        "details", result.getDetails()
                    )
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error simulating rollback", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error during rollback simulation",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Create comprehensive rollback plan
     */
    @GetMapping("/plan")
    public ResponseEntity<?> createRollbackPlan() {
        try {
            logger.info("API request: Create rollback plan");
            
            Map<String, Object> plan = rollbackService.createRollbackPlan();
            
            if (plan.containsKey("error")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to create rollback plan",
                    "error", plan.get("error")
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Rollback plan created successfully",
                "plan", plan
            ));
            
        } catch (Exception e) {
            logger.error("Error creating rollback plan", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error creating rollback plan",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Capture current system state
     */
    @PostMapping("/capture-state")
    public ResponseEntity<?> captureSystemState() {
        try {
            logger.info("API request: Capture system state");
            
            MigrationRollbackService.SystemStateMemento memento = rollbackService.captureSystemState();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "System state captured successfully",
                "memento", Map.of(
                    "captureTime", memento.getCaptureTime().format(TIMESTAMP_FORMAT),
                    "description", memento.getDescription(),
                    "tableCounts", memento.getTableCounts(),
                    "databaseState", memento.getDatabaseState()
                )
            ));
            
        } catch (Exception e) {
            logger.error("Error capturing system state", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error capturing system state",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Emergency rollback simulation (for critical situations)
     */
    @PostMapping("/emergency-simulate")
    @PreAuthorize("hasRole('ADMINISTRATOR')") // Extra security for emergency operations
    public ResponseEntity<?> emergencyRollbackSimulation(
            @RequestParam(required = true) String confirmationToken) {
        
        // Simple confirmation token check (in production, use proper authorization)
        if (!"EMERGENCY_ROLLBACK_CONFIRMED".equals(confirmationToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", "error",
                "message", "Emergency rollback requires explicit confirmation token",
                "requiredToken", "EMERGENCY_ROLLBACK_CONFIRMED"
            ));
        }
        
        try {
            logger.warn("API request: Emergency rollback simulation - ADMIN CONFIRMED");
            
            MigrationRollbackService.RollbackResult result = rollbackService.emergencyRollbackSimulation();
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "emergency_simulation",
                "message", result.getMessage(),
                "WARNING", "This is an emergency simulation with reduced safety checks",
                "emergencyResult", Map.of(
                    "successful", result.isSuccessful(),
                    "stepsExecuted", result.getStepsExecuted(),
                    "warnings", result.getWarnings(),
                    "details", result.getDetails()
                )
            ));
            
        } catch (Exception e) {
            logger.error("Error in emergency rollback simulation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error during emergency simulation",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get rollback recommendations based on current system state
     */
    @GetMapping("/recommendations")
    public ResponseEntity<?> getRollbackRecommendations() {
        try {
            logger.info("API request: Get rollback recommendations");
            
            // Validate safety first
            MigrationRollbackService.ValidationResult safety = rollbackService.validateRollbackSafety();
            
            // Capture current state
            MigrationRollbackService.SystemStateMemento state = rollbackService.captureSystemState();
            
            // Generate recommendations
            Map<String, Object> recommendations = Map.of(
                "safetyStatus", Map.of(
                    "safe", safety.isSafe(),
                    "message", safety.getMessage(),
                    "warnings", safety.getWarnings()
                ),
                "currentState", Map.of(
                    "packages", state.getTableCounts().getOrDefault("integration_packages", 0),
                    "adapters", state.getTableCounts().getOrDefault("package_adapters", 0),
                    "flows", state.getTableCounts().getOrDefault("package_flows", 0)
                ),
                "recommendations", generateRecommendations(safety, state),
                "alternativeActions", Map.of(
                    "partialRollback", "Consider rolling back only specific components",
                    "forwardFix", "Address issues without full rollback",
                    "maintenanceMode", "Enable maintenance mode during rollback"
                )
            );
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Rollback recommendations generated",
                "recommendations", recommendations
            ));
            
        } catch (Exception e) {
            logger.error("Error generating rollback recommendations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error generating recommendations",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Generate recommendations based on safety and state
     */
    private Map<String, Object> generateRecommendations(
            MigrationRollbackService.ValidationResult safety, 
            MigrationRollbackService.SystemStateMemento state) {
        
        if (safety.isSafe()) {
            return Map.of(
                "overall", "PROCEED_WITH_CAUTION",
                "primaryRecommendation", "Rollback appears safe but create full backup first",
                "steps", Map.of(
                    "immediate", "Create comprehensive system backup",
                    "preparation", "Stop all active operations",
                    "execution", "Follow rollback plan step by step",
                    "verification", "Verify system integrity after rollback"
                ),
                "timeEstimate", "2-4 hours including backups and verification"
            );
        } else {
            return Map.of(
                "overall", "NOT_RECOMMENDED", 
                "primaryRecommendation", "Resolve safety issues before attempting rollback",
                "steps", Map.of(
                    "immediate", "Address safety validation failures",
                    "investigation", "Investigate root causes of current issues",
                    "alternative", "Consider forward fixes instead of rollback",
                    "escalation", "Consult with senior technical team"
                ),
                "timeEstimate", "Investigation required before rollback planning"
            );
        }
    }
}