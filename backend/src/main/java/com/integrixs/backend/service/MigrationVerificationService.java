package com.integrixs.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Migration Verification Service for validating Phase 7 cleanup operations.
 * Follows OOP principles:
 * - Single Responsibility: Only handles migration verification
 * - Strategy Pattern: Different verification strategies for different entity types
 * - Command Pattern: Encapsulates verification operations
 * - Observer Pattern: Reports verification progress and results
 */
@Service
public class MigrationVerificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationVerificationService.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public MigrationVerificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    }
    
    /**
     * Verification result interface for consistency
     */
    public interface VerificationResult {
        boolean isSuccessful();
        String getMessage();
        Map<String, Object> getDetails();
        List<String> getIssues();
    }
    
    /**
     * Implementation of verification result
     */
    public static class VerificationResultImpl implements VerificationResult {
        private final boolean successful;
        private final String message;
        private final Map<String, Object> details;
        private final List<String> issues;
        
        public VerificationResultImpl(boolean successful, String message, Map<String, Object> details, List<String> issues) {
            this.successful = successful;
            this.message = message;
            this.details = details != null ? details : new HashMap<>();
            this.issues = issues != null ? issues : new ArrayList<>();
        }
        
        @Override
        public boolean isSuccessful() { return successful; }
        
        @Override
        public String getMessage() { return message; }
        
        @Override
        public Map<String, Object> getDetails() { return details; }
        
        @Override
        public List<String> getIssues() { return issues; }
    }
    
    /**
     * Verify adapter-package associations
     */
    public VerificationResult verifyAdapterPackageAssociations() {
        logger.info("Starting adapter-package association verification");
        
        try {
            Map<String, Object> details = new HashMap<>();
            List<String> issues = new ArrayList<>();
            
            // Check total adapter count
            String totalCountSql = "SELECT COUNT(*) FROM package_adapters";
            Integer totalAdapters = jdbcTemplate.queryForObject(totalCountSql, Integer.class);
            details.put("totalAdapters", totalAdapters);
            
            // Check adapters without package association
            String orphanedSql = "SELECT COUNT(*) FROM package_adapters WHERE package_id IS NULL";
            Integer orphanedAdapters = jdbcTemplate.queryForObject(orphanedSql, Integer.class);
            details.put("orphanedAdapters", orphanedAdapters);
            
            if (orphanedAdapters > 0) {
                issues.add(String.format("%d adapters found without package association", orphanedAdapters));
            }
            
            // Check adapters with invalid package references
            String invalidRefSql = """
                SELECT COUNT(*) FROM package_adapters pa 
                WHERE pa.package_id IS NOT NULL 
                AND NOT EXISTS (SELECT 1 FROM integration_packages ip WHERE ip.id = pa.package_id)
            """;
            Integer invalidReferences = jdbcTemplate.queryForObject(invalidRefSql, Integer.class);
            details.put("invalidPackageReferences", invalidReferences);
            
            if (invalidReferences > 0) {
                issues.add(String.format("%d adapters found with invalid package references", invalidReferences));
            }
            
            // Check package distribution
            String distributionSql = """
                SELECT ip.name, COUNT(pa.id) as adapter_count 
                FROM integration_packages ip 
                LEFT JOIN package_adapters pa ON ip.id = pa.package_id 
                GROUP BY ip.id, ip.name 
                ORDER BY adapter_count DESC
            """;
            
            List<Map<String, Object>> distribution = jdbcTemplate.queryForList(distributionSql);
            details.put("packageDistribution", distribution);
            
            boolean successful = orphanedAdapters == 0 && invalidReferences == 0;
            String message = successful ? 
                String.format("All %d adapters properly associated with packages", totalAdapters) :
                String.format("Found %d issues with adapter-package associations", issues.size());
            
            logger.info("Adapter-package verification completed: {} adapters, {} issues", totalAdapters, issues.size());
            return new VerificationResultImpl(successful, message, details, issues);
            
        } catch (Exception e) {
            logger.error("Error during adapter-package verification", e);
            return new VerificationResultImpl(false, "Verification failed: " + e.getMessage(), null, 
                Arrays.asList("Database error during verification: " + e.getMessage()));
        }
    }
    
    /**
     * Verify flow-package associations
     */
    public VerificationResult verifyFlowPackageAssociations() {
        logger.info("Starting flow-package association verification");
        
        try {
            Map<String, Object> details = new HashMap<>();
            List<String> issues = new ArrayList<>();
            
            // Check total flow count
            String totalCountSql = "SELECT COUNT(*) FROM package_flows";
            Integer totalFlows = jdbcTemplate.queryForObject(totalCountSql, Integer.class);
            details.put("totalFlows", totalFlows);
            
            // Check flows without package association
            String orphanedSql = "SELECT COUNT(*) FROM package_flows WHERE package_id IS NULL";
            Integer orphanedFlows = jdbcTemplate.queryForObject(orphanedSql, Integer.class);
            details.put("orphanedFlows", orphanedFlows);
            
            if (orphanedFlows > 0) {
                issues.add(String.format("%d flows found without package association", orphanedFlows));
            }
            
            // Check flows with invalid package references
            String invalidRefSql = """
                SELECT COUNT(*) FROM package_flows pf 
                WHERE pf.package_id IS NOT NULL 
                AND NOT EXISTS (SELECT 1 FROM integration_packages ip WHERE ip.id = pf.package_id)
            """;
            Integer invalidReferences = jdbcTemplate.queryForObject(invalidRefSql, Integer.class);
            details.put("invalidPackageReferences", invalidReferences);
            
            if (invalidReferences > 0) {
                issues.add(String.format("%d flows found with invalid package references", invalidReferences));
            }
            
            // Check package distribution
            String distributionSql = """
                SELECT ip.name, COUNT(pf.id) as flow_count 
                FROM integration_packages ip 
                LEFT JOIN package_flows pf ON ip.id = pf.package_id 
                GROUP BY ip.id, ip.name 
                ORDER BY flow_count DESC
            """;
            
            List<Map<String, Object>> distribution = jdbcTemplate.queryForList(distributionSql);
            details.put("packageDistribution", distribution);
            
            boolean successful = orphanedFlows == 0 && invalidReferences == 0;
            String message = successful ? 
                String.format("All %d flows properly associated with packages", totalFlows) :
                String.format("Found %d issues with flow-package associations", issues.size());
            
            logger.info("Flow-package verification completed: {} flows, {} issues", totalFlows, issues.size());
            return new VerificationResultImpl(successful, message, details, issues);
            
        } catch (Exception e) {
            logger.error("Error during flow-package verification", e);
            return new VerificationResultImpl(false, "Verification failed: " + e.getMessage(), null, 
                Arrays.asList("Database error during verification: " + e.getMessage()));
        }
    }
    
    /**
     * Verify data integrity after migration
     */
    public VerificationResult verifyDataIntegrity() {
        logger.info("Starting data integrity verification");
        
        try {
            Map<String, Object> details = new HashMap<>();
            List<String> issues = new ArrayList<>();
            
            // Verify all packages have valid data
            String packageCheckSql = """
                SELECT COUNT(*) FROM integration_packages 
                WHERE name IS NULL OR name = '' OR id IS NULL
            """;
            Integer invalidPackages = jdbcTemplate.queryForObject(packageCheckSql, Integer.class);
            details.put("invalidPackages", invalidPackages);
            
            if (invalidPackages > 0) {
                issues.add(String.format("%d packages found with invalid data", invalidPackages));
            }
            
            // Check for duplicate package names
            String duplicateNamesSql = """
                SELECT name, COUNT(*) as count
                FROM integration_packages
                GROUP BY name
                HAVING COUNT(*) > 1
            """;
            List<Map<String, Object>> duplicateNames = jdbcTemplate.queryForList(duplicateNamesSql);
            details.put("duplicatePackageNames", duplicateNames);
            
            if (!duplicateNames.isEmpty()) {
                issues.add(String.format("%d duplicate package names found", duplicateNames.size()));
            }
            
            // Verify audit trail data
            String missingAuditSql = """
                SELECT 
                    (SELECT COUNT(*) FROM package_adapters WHERE created_at IS NULL) as adapters_missing_created_at,
                    (SELECT COUNT(*) FROM package_flows WHERE created_at IS NULL) as flows_missing_created_at,
                    (SELECT COUNT(*) FROM integration_packages WHERE created_at IS NULL) as packages_missing_created_at
            """;
            Map<String, Object> auditCheck = jdbcTemplate.queryForMap(missingAuditSql);
            details.put("auditTrailCheck", auditCheck);
            
            // Check for any missing audit data
            auditCheck.values().forEach(value -> {
                if (value instanceof Number && ((Number) value).intValue() > 0) {
                    issues.add("Missing audit trail data found");
                }
            });
            
            boolean successful = issues.isEmpty();
            String message = successful ? 
                "All data integrity checks passed" :
                String.format("Found %d data integrity issues", issues.size());
            
            logger.info("Data integrity verification completed: {} issues found", issues.size());
            return new VerificationResultImpl(successful, message, details, issues);
            
        } catch (Exception e) {
            logger.error("Error during data integrity verification", e);
            return new VerificationResultImpl(false, "Verification failed: " + e.getMessage(), null, 
                Arrays.asList("Database error during verification: " + e.getMessage()));
        }
    }
    
    /**
     * Run comprehensive migration verification
     */
    public Map<String, VerificationResult> runComprehensiveVerification() {
        logger.info("Starting comprehensive migration verification");
        
        Map<String, VerificationResult> results = new HashMap<>();
        
        // Run all verifications
        results.put("adapterPackageAssociations", verifyAdapterPackageAssociations());
        results.put("flowPackageAssociations", verifyFlowPackageAssociations());
        results.put("dataIntegrity", verifyDataIntegrity());
        
        // Log summary
        long successfulChecks = results.values().stream()
            .mapToLong(result -> result.isSuccessful() ? 1 : 0)
            .sum();
        
        logger.info("Comprehensive verification completed: {}/{} checks passed", 
                   successfulChecks, results.size());
        
        return results;
    }
    
    /**
     * Get verification summary for reporting
     */
    public Map<String, Object> getVerificationSummary() {
        Map<String, VerificationResult> results = runComprehensiveVerification();
        Map<String, Object> summary = new HashMap<>();
        
        boolean allPassed = results.values().stream().allMatch(VerificationResult::isSuccessful);
        summary.put("overallStatus", allPassed ? "PASSED" : "FAILED");
        summary.put("timestamp", new Date());
        summary.put("totalChecks", results.size());
        
        long passedChecks = results.values().stream()
            .mapToLong(result -> result.isSuccessful() ? 1 : 0)
            .sum();
        summary.put("passedChecks", passedChecks);
        summary.put("failedChecks", results.size() - passedChecks);
        
        // Collect all issues
        List<String> allIssues = new ArrayList<>();
        results.values().forEach(result -> allIssues.addAll(result.getIssues()));
        summary.put("issues", allIssues);
        
        // Add detailed results
        summary.put("detailedResults", results);
        
        return summary;
    }
}