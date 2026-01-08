package com.integrixs.backend.jobs;

import com.integrixs.shared.model.DataRetentionConfig;

/**
 * Interface for data retention job implementations
 * 
 * All data retention executors must implement this interface to be
 * selectable in the UI and executable by the retention scheduler.
 */
public interface DataRetentionJob {
    
    /**
     * Execute the data retention job with the given configuration
     * 
     * @param config The retention configuration containing parameters
     * @return Number of items processed (files deleted, records cleaned, etc.)
     */
    int execute(DataRetentionConfig config);
    
    /**
     * Get a human-readable name for this job
     * 
     * @return Display name for the UI dropdown
     */
    String getDisplayName();
    
    /**
     * Get a detailed description of what this job does
     * 
     * @return Description for the UI
     */
    String getDescription();
    
    /**
     * Get the unique identifier for this job class
     * 
     * @return Class name or identifier
     */
    String getJobIdentifier();
    
    /**
     * Validate if the given configuration is valid for this job
     * 
     * @param config The configuration to validate
     * @return true if valid, false otherwise
     */
    boolean validateConfiguration(DataRetentionConfig config);
}