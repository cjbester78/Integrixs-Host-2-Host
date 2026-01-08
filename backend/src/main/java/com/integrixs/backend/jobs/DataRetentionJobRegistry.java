package com.integrixs.backend.jobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Registry for all available data retention job implementations
 * 
 * Automatically discovers and registers all DataRetentionJob implementations
 * for use in the dynamic scheduling system.
 */
@Service
public class DataRetentionJobRegistry {
    
    private static final Logger log = Logger.getLogger(DataRetentionJobRegistry.class.getName());
    
    @Autowired
    private List<DataRetentionJob> availableJobs;
    
    private Map<String, DataRetentionJob> jobRegistry = new HashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Data Retention Job Registry...");
        
        for (DataRetentionJob job : availableJobs) {
            String jobId = job.getJobIdentifier();
            jobRegistry.put(jobId, job);
            log.info("Registered job: " + jobId + " - " + job.getDisplayName());
        }
        
        log.info("Job registry initialized with " + jobRegistry.size() + " jobs");
    }
    
    /**
     * Get a job by its identifier
     */
    public DataRetentionJob getJob(String jobIdentifier) {
        return jobRegistry.get(jobIdentifier);
    }
    
    /**
     * Get all available jobs for UI dropdown
     */
    public Map<String, DataRetentionJob> getAllJobs() {
        return new HashMap<>(jobRegistry);
    }
    
    /**
     * Check if a job identifier is valid
     */
    public boolean isValidJobIdentifier(String jobIdentifier) {
        return jobRegistry.containsKey(jobIdentifier);
    }
    
    /**
     * Execute a job with the given configuration
     */
    public int executeJob(String jobIdentifier, com.integrixs.shared.model.DataRetentionConfig config) {
        DataRetentionJob job = jobRegistry.get(jobIdentifier);
        if (job == null) {
            throw new IllegalArgumentException("Unknown job identifier: " + jobIdentifier);
        }
        
        if (!job.validateConfiguration(config)) {
            throw new IllegalArgumentException("Invalid configuration for job: " + jobIdentifier);
        }
        
        return job.execute(config);
    }
}