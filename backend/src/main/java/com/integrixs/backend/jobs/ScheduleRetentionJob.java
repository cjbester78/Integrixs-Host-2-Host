package com.integrixs.backend.jobs;

import com.integrixs.shared.model.DataRetentionConfig;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * Schedule Retention Job
 * 
 * This is a special job type that defines WHEN other retention jobs should run.
 * It doesn't process data itself, but provides scheduling information for the
 * dynamic task scheduler.
 */
@Component
public class ScheduleRetentionJob implements DataRetentionJob {
    
    private static final Logger log = Logger.getLogger(ScheduleRetentionJob.class.getName());
    
    @Override
    public int execute(DataRetentionConfig config) {
        // Schedule jobs don't execute directly - they provide timing information
        // The actual execution is handled by the dynamic scheduler
        log.info("Schedule configuration '" + config.getName() + "' defines timing: " + config.getScheduleCron());
        return 0;
    }
    
    @Override
    public String getDisplayName() {
        return "Schedule Retention Jobs";
    }
    
    @Override
    public String getDescription() {
        return "Define when other retention jobs should run using cron expressions (does not process data itself)";
    }
    
    @Override
    public String getJobIdentifier() {
        return "ScheduleRetentionJob";
    }
    
    @Override
    public boolean validateConfiguration(DataRetentionConfig config) {
        // Schedule jobs must have a valid cron expression
        return config.getScheduleCron() != null && !config.getScheduleCron().trim().isEmpty();
    }
}