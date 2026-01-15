package com.integrixs.backend.controller;

import com.integrixs.backend.service.MigrationVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST Controller for migration verification operations.
 * Provides endpoints to verify Phase 7 cleanup and migration integrity.
 * 
 * Following REST best practices:
 * - Proper HTTP status codes
 * - JSON response format
 * - Admin-only access with security annotations
 * - Comprehensive error handling
 */
@RestController
@RequestMapping("/api/migration")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class MigrationVerificationController {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationVerificationController.class);
    
    private final MigrationVerificationService migrationVerificationService;
    
    @Autowired
    public MigrationVerificationController(MigrationVerificationService migrationVerificationService) {
        this.migrationVerificationService = migrationVerificationService;
    }
    
    /**
     * Verify adapter-package associations
     */
    @GetMapping("/verify/adapter-packages")
    public ResponseEntity<?> verifyAdapterPackages() {
        try {
            logger.info("API request: Verify adapter-package associations");
            
            MigrationVerificationService.VerificationResult result = 
                migrationVerificationService.verifyAdapterPackageAssociations();
            
            if (result.isSuccessful()) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", result.getMessage(),
                    "details", result.getDetails()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "failed",
                    "message", result.getMessage(),
                    "issues", result.getIssues(),
                    "details", result.getDetails()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error verifying adapter-package associations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error during verification",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Verify flow-package associations
     */
    @GetMapping("/verify/flow-packages")
    public ResponseEntity<?> verifyFlowPackages() {
        try {
            logger.info("API request: Verify flow-package associations");
            
            MigrationVerificationService.VerificationResult result = 
                migrationVerificationService.verifyFlowPackageAssociations();
            
            if (result.isSuccessful()) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", result.getMessage(),
                    "details", result.getDetails()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "failed",
                    "message", result.getMessage(),
                    "issues", result.getIssues(),
                    "details", result.getDetails()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error verifying flow-package associations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error during verification",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Verify data integrity
     */
    @GetMapping("/verify/data-integrity")
    public ResponseEntity<?> verifyDataIntegrity() {
        try {
            logger.info("API request: Verify data integrity");
            
            MigrationVerificationService.VerificationResult result = 
                migrationVerificationService.verifyDataIntegrity();
            
            if (result.isSuccessful()) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", result.getMessage(),
                    "details", result.getDetails()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "failed",
                    "message", result.getMessage(),
                    "issues", result.getIssues(),
                    "details", result.getDetails()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error verifying data integrity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error during verification",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Run comprehensive verification of all migration aspects
     */
    @GetMapping("/verify/comprehensive")
    public ResponseEntity<?> runComprehensiveVerification() {
        try {
            logger.info("API request: Run comprehensive migration verification");
            
            Map<String, Object> summary = migrationVerificationService.getVerificationSummary();
            
            String overallStatus = (String) summary.get("overallStatus");
            
            if ("PASSED".equals(overallStatus)) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "All migration verification checks passed",
                    "summary", summary
                ));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "failed", 
                    "message", "One or more migration verification checks failed",
                    "summary", summary
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error running comprehensive verification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error during comprehensive verification",
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get verification status for dashboard/monitoring
     */
    @GetMapping("/status")
    public ResponseEntity<?> getVerificationStatus() {
        try {
            logger.info("API request: Get migration verification status");
            
            Map<String, Object> summary = migrationVerificationService.getVerificationSummary();
            
            // Return just the summary for quick status checks
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "verificationSummary", Map.of(
                    "overallStatus", summary.get("overallStatus"),
                    "timestamp", summary.get("timestamp"),
                    "totalChecks", summary.get("totalChecks"),
                    "passedChecks", summary.get("passedChecks"),
                    "failedChecks", summary.get("failedChecks"),
                    "issueCount", ((java.util.List<?>) summary.get("issues")).size()
                )
            ));
            
        } catch (Exception e) {
            logger.error("Error getting verification status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Internal server error getting verification status",
                "error", e.getMessage()
            ));
        }
    }
}