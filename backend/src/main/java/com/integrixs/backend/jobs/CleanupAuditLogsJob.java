package com.integrixs.backend.jobs;

import com.integrixs.shared.model.DataRetentionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Database Cleanup: Audit Logs
 * 
 * Deletes audit log records from the DATABASE table that are older 
 * than the specified retention period. This is a DATABASE operation, not file system.
 */
@Component
public class CleanupAuditLogsJob implements DataRetentionJob {
    
    private static final Logger log = Logger.getLogger(CleanupAuditLogsJob.class.getName());
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public int execute(DataRetentionConfig config) {
        log.info("Starting cleanup of audit logs older than " + config.getRetentionDays() + " days");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(config.getRetentionDays());
        
        try {
            String sql = "DELETE FROM audit_logs WHERE created_at < ?";
            int deletedRecords = jdbcTemplate.update(sql, cutoffDate);
            log.info("Cleanup audit logs completed: " + deletedRecords + " records deleted");
            return deletedRecords;
        } catch (Exception e) {
            log.severe("Error during audit logs cleanup: " + e.getMessage());
            throw new RuntimeException("Failed to cleanup audit logs", e);
        }
    }
    
    @Override
    public String getDisplayName() {
        return "Database: Cleanup Audit Logs";
    }
    
    @Override
    public String getDescription() {
        return "Delete audit log records from the DATABASE table (not files) that are older than the retention period";
    }
    
    @Override
    public String getJobIdentifier() {
        return "CleanupAuditLogsJob";
    }
    
    @Override
    public boolean validateConfiguration(DataRetentionConfig config) {
        return config.getRetentionDays() != null && config.getRetentionDays() > 0;
    }
}