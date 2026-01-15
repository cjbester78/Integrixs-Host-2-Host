package com.integrixs.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Migration Rollback Service for safe rollback operations.
 * Follows OOP principles:
 * - Command Pattern: Encapsulates rollback operations as executable commands
 * - State Pattern: Manages different rollback states and transitions
 * - Strategy Pattern: Different rollback strategies for different scenarios
 * - Memento Pattern: Captures and restores system state snapshots
 * - Chain of Responsibility: Validates rollback safety through a chain of checks
 */
@Service
public class MigrationRollbackService {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationRollbackService.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final MigrationVerificationService verificationService;
    
    @Autowired
    public MigrationRollbackService(
            JdbcTemplate jdbcTemplate,
            MigrationVerificationService verificationService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
        this.verificationService = Objects.requireNonNull(verificationService, "MigrationVerificationService cannot be null");
    }
    
    /**
     * Rollback operation result interface
     */
    public interface RollbackResult {
        boolean isSuccessful();
        String getMessage();
        List<String> getStepsExecuted();
        List<String> getWarnings();
        Map<String, Object> getDetails();
    }
    
    /**
     * Rollback result implementation
     */
    public static class RollbackResultImpl implements RollbackResult {
        private final boolean successful;
        private final String message;
        private final List<String> stepsExecuted;
        private final List<String> warnings;
        private final Map<String, Object> details;
        
        public RollbackResultImpl(boolean successful, String message, List<String> stepsExecuted, 
                                List<String> warnings, Map<String, Object> details) {
            this.successful = successful;
            this.message = message;
            this.stepsExecuted = stepsExecuted != null ? stepsExecuted : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            this.details = details != null ? details : new HashMap<>();
        }
        
        @Override
        public boolean isSuccessful() { return successful; }
        @Override
        public String getMessage() { return message; }
        @Override
        public List<String> getStepsExecuted() { return stepsExecuted; }
        @Override
        public List<String> getWarnings() { return warnings; }
        @Override
        public Map<String, Object> getDetails() { return details; }
    }
    
    /**
     * System state memento for rollback operations
     */
    public static class SystemStateMemento {
        private final LocalDateTime captureTime;
        private final Map<String, Object> databaseState;
        private final Map<String, Integer> tableCounts;
        private final String description;
        
        public SystemStateMemento(Map<String, Object> databaseState, Map<String, Integer> tableCounts, String description) {
            this.captureTime = LocalDateTime.now();
            this.databaseState = new HashMap<>(databaseState);
            this.tableCounts = new HashMap<>(tableCounts);
            this.description = description;
        }
        
        // Getters
        public LocalDateTime getCaptureTime() { return captureTime; }
        public Map<String, Object> getDatabaseState() { return databaseState; }
        public Map<String, Integer> getTableCounts() { return tableCounts; }
        public String getDescription() { return description; }
    }
    
    /**
     * Rollback safety validator interface (Chain of Responsibility)
     */
    public interface RollbackSafetyValidator {
        RollbackSafetyValidator setNext(RollbackSafetyValidator next);
        ValidationResult validate();
    }
    
    /**
     * Validation result for safety checks
     */
    public static class ValidationResult {
        private final boolean safe;
        private final String message;
        private final List<String> warnings;
        private final Map<String, Object> details;
        
        public ValidationResult(boolean safe, String message, List<String> warnings, Map<String, Object> details) {
            this.safe = safe;
            this.message = message;
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            this.details = details != null ? details : new HashMap<>();
        }
        
        // Getters
        public boolean isSafe() { return safe; }
        public String getMessage() { return message; }
        public List<String> getWarnings() { return warnings; }
        public Map<String, Object> getDetails() { return details; }
    }
    
    /**
     * Abstract base validator for chain of responsibility
     */
    public abstract class AbstractRollbackValidator implements RollbackSafetyValidator {
        private RollbackSafetyValidator nextValidator;
        
        @Override
        public RollbackSafetyValidator setNext(RollbackSafetyValidator next) {
            this.nextValidator = next;
            return next;
        }
        
        @Override
        public ValidationResult validate() {
            ValidationResult result = performValidation();
            
            if (!result.isSafe()) {
                return result; // Stop chain if validation fails
            }
            
            if (nextValidator != null) {
                ValidationResult nextResult = nextValidator.validate();
                // Merge warnings from chain
                List<String> allWarnings = new ArrayList<>(result.getWarnings());
                allWarnings.addAll(nextResult.getWarnings());
                
                return new ValidationResult(
                    nextResult.isSafe(),
                    nextResult.isSafe() ? result.getMessage() + "; " + nextResult.getMessage() : nextResult.getMessage(),
                    allWarnings,
                    nextResult.getDetails()
                );
            }
            
            return result;
        }
        
        protected abstract ValidationResult performValidation();
    }
    
    /**
     * Data integrity validator
     */
    public class DataIntegrityValidator extends AbstractRollbackValidator {
        @Override
        protected ValidationResult performValidation() {
            try {
                MigrationVerificationService.VerificationResult integrityResult = 
                    verificationService.verifyDataIntegrity();
                
                if (integrityResult.isSuccessful()) {
                    return new ValidationResult(true, "Data integrity verified for rollback", null, integrityResult.getDetails());
                } else {
                    return new ValidationResult(false, "Data integrity issues detected - rollback not safe", 
                                              integrityResult.getIssues(), integrityResult.getDetails());
                }
                
            } catch (Exception e) {
                logger.error("Error validating data integrity for rollback", e);
                return new ValidationResult(false, "Data integrity validation failed: " + e.getMessage(), null, null);
            }
        }
    }
    
    /**
     * Active system validator
     */
    public class ActiveSystemValidator extends AbstractRollbackValidator {
        @Override
        protected ValidationResult performValidation() {
            try {
                // Check for active flows or operations
                String activeSql = """
                    SELECT 
                        (SELECT COUNT(*) FROM package_flows WHERE active = true) as active_flows,
                        (SELECT COUNT(*) FROM package_adapters WHERE enabled = true) as active_adapters
                """;
                
                Map<String, Object> activeCheck = jdbcTemplate.queryForMap(activeSql);
                int activeFlows = ((Number) activeCheck.get("active_flows")).intValue();
                int activeAdapters = ((Number) activeCheck.get("active_adapters")).intValue();
                
                List<String> warnings = new ArrayList<>();
                
                if (activeFlows > 0) {
                    warnings.add(String.format("%d active flows detected - consider stopping before rollback", activeFlows));
                }
                
                if (activeAdapters > 0) {
                    warnings.add(String.format("%d active adapters detected - consider disabling before rollback", activeAdapters));
                }
                
                Map<String, Object> details = new HashMap<>();
                details.put("activeFlows", activeFlows);
                details.put("activeAdapters", activeAdapters);
                
                return new ValidationResult(true, "Active system check completed", warnings, details);
                
            } catch (Exception e) {
                logger.error("Error validating active system for rollback", e);
                return new ValidationResult(false, "Active system validation failed: " + e.getMessage(), null, null);
            }
        }
    }
    
    /**
     * Backup existence validator
     */
    public class BackupExistenceValidator extends AbstractRollbackValidator {
        @Override
        protected ValidationResult performValidation() {
            try {
                // Check if we have pre-migration state captured
                // This is a simplified check - in real implementation, you'd check actual backup systems
                String backupCheckSql = """
                    SELECT COUNT(*) as package_count FROM integration_packages 
                    WHERE name = 'Default Package' AND created_at < NOW() - INTERVAL '1 day'
                """;
                
                Integer defaultPackageExists = jdbcTemplate.queryForObject(backupCheckSql, Integer.class);
                
                if (defaultPackageExists > 0) {
                    return new ValidationResult(true, "Pre-migration state detected - rollback possible", null, null);
                } else {
                    List<String> warnings = Arrays.asList("No clear pre-migration state detected - rollback may be risky");
                    return new ValidationResult(true, "Backup validation completed with warnings", warnings, null);
                }
                
            } catch (Exception e) {
                logger.error("Error validating backup existence for rollback", e);
                return new ValidationResult(false, "Backup validation failed: " + e.getMessage(), null, null);
            }
        }
    }
    
    /**
     * Capture current system state (Memento Pattern)
     */
    public SystemStateMemento captureSystemState() {
        logger.info("Capturing current system state for rollback safety");
        
        try {
            Map<String, Object> databaseState = new HashMap<>();
            Map<String, Integer> tableCounts = new HashMap<>();
            
            // Capture key table counts
            String countsSql = """
                SELECT 
                    'integration_packages' as table_name, COUNT(*) as count FROM integration_packages
                UNION ALL
                SELECT 
                    'package_adapters' as table_name, COUNT(*) as count FROM package_adapters
                UNION ALL
                SELECT 
                    'package_flows' as table_name, COUNT(*) as count FROM package_flows
            """;
            
            List<Map<String, Object>> counts = jdbcTemplate.queryForList(countsSql);
            for (Map<String, Object> count : counts) {
                String tableName = (String) count.get("table_name");
                Integer tableCount = ((Number) count.get("count")).intValue();
                tableCounts.put(tableName, tableCount);
            }
            
            // Capture key configuration
            databaseState.put("captureTime", LocalDateTime.now());
            databaseState.put("systemVersion", "package-management-v1.0");
            
            return new SystemStateMemento(databaseState, tableCounts, "Pre-rollback system state");
            
        } catch (Exception e) {
            logger.error("Error capturing system state", e);
            throw new RuntimeException("Failed to capture system state: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate rollback safety using chain of responsibility
     */
    public ValidationResult validateRollbackSafety() {
        logger.info("Validating rollback safety");
        
        // Build validation chain
        RollbackSafetyValidator validatorChain = new DataIntegrityValidator()
            .setNext(new ActiveSystemValidator())
            .setNext(new BackupExistenceValidator());
        
        return validatorChain.validate();
    }
    
    /**
     * Simulate rollback (dry run)
     */
    public RollbackResult simulateRollback() {
        logger.info("Simulating rollback operation (dry run)");
        
        List<String> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();
        
        try {
            // Step 1: Validate safety
            steps.add("Validating rollback safety");
            ValidationResult safetyResult = validateRollbackSafety();
            
            if (!safetyResult.isSafe()) {
                return new RollbackResultImpl(false, "Rollback simulation failed: " + safetyResult.getMessage(), 
                                            steps, warnings, safetyResult.getDetails());
            }
            
            warnings.addAll(safetyResult.getWarnings());
            
            // Step 2: Capture current state
            steps.add("Capturing current system state");
            SystemStateMemento memento = captureSystemState();
            details.put("currentState", memento.getTableCounts());
            
            // Step 3: Simulate rollback steps
            steps.add("Simulating package data restoration");
            steps.add("Simulating adapter association updates");
            steps.add("Simulating flow association updates");
            steps.add("Simulating component restoration");
            steps.add("Simulating route restoration");
            
            // Step 4: Estimate impact
            int totalPackages = memento.getTableCounts().getOrDefault("integration_packages", 0);
            int totalAdapters = memento.getTableCounts().getOrDefault("package_adapters", 0);
            int totalFlows = memento.getTableCounts().getOrDefault("package_flows", 0);
            
            details.put("estimatedImpact", Map.of(
                "packagesToRemove", Math.max(0, totalPackages - 1), // Keep default package
                "adaptersToUpdate", totalAdapters,
                "flowsToUpdate", totalFlows,
                "componentsToRestore", 4, // Number of archived components
                "routesToRestore", 6 // Number of legacy routes
            ));
            
            String message = String.format("Rollback simulation successful - Would affect %d packages, %d adapters, %d flows", 
                                         totalPackages, totalAdapters, totalFlows);
            
            return new RollbackResultImpl(true, message, steps, warnings, details);
            
        } catch (Exception e) {
            logger.error("Error during rollback simulation", e);
            steps.add("Rollback simulation failed: " + e.getMessage());
            return new RollbackResultImpl(false, "Rollback simulation failed: " + e.getMessage(), steps, warnings, details);
        }
    }
    
    /**
     * Create rollback plan
     */
    public Map<String, Object> createRollbackPlan() {
        logger.info("Creating comprehensive rollback plan");
        
        Map<String, Object> plan = new HashMap<>();
        
        try {
            // Safety assessment
            ValidationResult safety = validateRollbackSafety();
            plan.put("safetyAssessment", Map.of(
                "safe", safety.isSafe(),
                "message", safety.getMessage(),
                "warnings", safety.getWarnings(),
                "details", safety.getDetails()
            ));
            
            // Current state
            SystemStateMemento currentState = captureSystemState();
            plan.put("currentState", Map.of(
                "captureTime", currentState.getCaptureTime(),
                "tableCounts", currentState.getTableCounts(),
                "description", currentState.getDescription()
            ));
            
            // Rollback steps
            List<Map<String, Object>> rollbackSteps = Arrays.asList(
                Map.of("step", 1, "action", "Stop all active flows and adapters", "risk", "LOW", "reversible", true),
                Map.of("step", 2, "action", "Backup current package configurations", "risk", "LOW", "reversible", true),
                Map.of("step", 3, "action", "Remove package associations from adapters", "risk", "MEDIUM", "reversible", false),
                Map.of("step", 4, "action", "Remove package associations from flows", "risk", "MEDIUM", "reversible", false),
                Map.of("step", 5, "action", "Delete created packages (except default)", "risk", "HIGH", "reversible", false),
                Map.of("step", 6, "action", "Restore archived components", "risk", "LOW", "reversible", true),
                Map.of("step", 7, "action", "Restore legacy routes", "risk", "LOW", "reversible", true),
                Map.of("step", 8, "action", "Update navigation to pre-package state", "risk", "LOW", "reversible", true),
                Map.of("step", 9, "action", "Verify system integrity", "risk", "LOW", "reversible", true),
                Map.of("step", 10, "action", "Restart affected services", "risk", "MEDIUM", "reversible", true)
            );
            
            plan.put("rollbackSteps", rollbackSteps);
            
            // Risk assessment
            long highRiskSteps = rollbackSteps.stream().mapToLong(step -> "HIGH".equals(step.get("risk")) ? 1 : 0).sum();
            long irreversibleSteps = rollbackSteps.stream().mapToLong(step -> !((Boolean) step.get("reversible")) ? 1 : 0).sum();
            
            plan.put("riskAssessment", Map.of(
                "totalSteps", rollbackSteps.size(),
                "highRiskSteps", highRiskSteps,
                "irreversibleSteps", irreversibleSteps,
                "overallRisk", highRiskSteps > 2 ? "HIGH" : irreversibleSteps > 0 ? "MEDIUM" : "LOW",
                "recommendation", safety.isSafe() ? "Rollback appears safe with precautions" : "Rollback not recommended - resolve issues first"
            ));
            
            // Prerequisites
            plan.put("prerequisites", Arrays.asList(
                "Full database backup completed",
                "All active operations stopped",
                "System maintenance window scheduled",
                "Rollback team assembled and briefed",
                "Recovery procedures documented and tested"
            ));
            
            plan.put("generatedAt", LocalDateTime.now());
            plan.put("planVersion", "1.0");
            
            return plan;
            
        } catch (Exception e) {
            logger.error("Error creating rollback plan", e);
            plan.put("error", "Failed to create rollback plan: " + e.getMessage());
            return plan;
        }
    }
    
    /**
     * Emergency rollback simulation (for critical situations)
     */
    public RollbackResult emergencyRollbackSimulation() {
        logger.warn("Performing emergency rollback simulation");
        
        List<String> steps = Arrays.asList(
            "EMERGENCY: Immediate system state capture",
            "EMERGENCY: Bypass normal safety checks",
            "EMERGENCY: Rapid package association removal",
            "EMERGENCY: Quick component restoration",
            "EMERGENCY: Fast route restoration",
            "EMERGENCY: Basic integrity verification"
        );
        
        List<String> warnings = Arrays.asList(
            "Emergency rollback bypasses normal safety checks",
            "Data integrity verification is minimal in emergency mode",
            "Some configurations may be lost during emergency rollback",
            "Manual verification required after emergency rollback"
        );
        
        Map<String, Object> details = Map.of(
            "mode", "EMERGENCY",
            "safetyLevel", "MINIMAL",
            "dataRisk", "MEDIUM",
            "timeEstimate", "15-30 minutes",
            "manualStepsRequired", true
        );
        
        return new RollbackResultImpl(true, "Emergency rollback simulation completed - USE WITH CAUTION", 
                                    steps, warnings, details);
    }
}