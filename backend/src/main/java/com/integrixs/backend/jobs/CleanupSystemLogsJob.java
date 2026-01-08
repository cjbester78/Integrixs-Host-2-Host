package com.integrixs.backend.jobs;

import com.integrixs.shared.model.DataRetentionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Database Cleanup: System Logs
 * 
 * Deletes system log records from the DATABASE table that are older 
 * than the specified retention period. This is a DATABASE operation, not file system.
 */
@Component
public class CleanupSystemLogsJob implements DataRetentionJob {
    
    private static final Logger log = Logger.getLogger(CleanupSystemLogsJob.class.getName());
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public int execute(DataRetentionConfig config) {
        log.info("Starting cleanup of system logs older than " + config.getRetentionDays() + " days");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(config.getRetentionDays());
        
        try {
            String sql = "DELETE FROM system_logs WHERE created_at < ?";
            int deletedRecords = jdbcTemplate.update(sql, cutoffDate);
            log.info("Cleanup system logs completed: " + deletedRecords + " records deleted");
            return deletedRecords;
        } catch (Exception e) {
            log.severe("Error during system logs cleanup: " + e.getMessage());
            throw new RuntimeException("Failed to cleanup system logs", e);
        }
    }
    
    @Override
    public String getDisplayName() {
        return "Database: Cleanup System Logs";
    }
    
    @Override
    public String getDescription() {
        return "Delete system log records from the DATABASE table (not files) that are older than the retention period";
    }
    
    @Override
    public String getJobIdentifier() {
        return "CleanupSystemLogsJob";
    }
    
    @Override
    public boolean validateConfiguration(DataRetentionConfig config) {
        return config.getRetentionDays() != null && config.getRetentionDays() > 0;
    }
}