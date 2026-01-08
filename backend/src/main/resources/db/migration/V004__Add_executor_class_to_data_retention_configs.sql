-- Add executor_class column to data_retention_configs table
-- This allows users to select which specific job implementation will execute the retention policy

ALTER TABLE data_retention_configs 
ADD COLUMN executor_class VARCHAR(255);

-- Add constraint to ensure non-null executor_class for non-schedule types
ALTER TABLE data_retention_configs 
ADD CONSTRAINT chk_executor_class CHECK (
    (data_type = 'SCHEDULE' AND executor_class IS NOT NULL) OR
    (data_type != 'SCHEDULE' AND executor_class IS NOT NULL)
);

-- Update existing records with appropriate executor classes
UPDATE data_retention_configs 
SET executor_class = CASE 
    WHEN data_type = 'LOG_FILES' THEN 'CleanupLogFilesJob'
    WHEN data_type = 'SYSTEM_LOGS' THEN 'CleanupSystemLogsJob'
    WHEN data_type = 'TRANSACTION_LOGS' THEN 'CleanupTransactionLogsJob'
    WHEN data_type = 'SCHEDULE' THEN 'ScheduleRetentionJob'
    ELSE 'CleanupSystemLogsJob' -- Default fallback
END;

-- Add index for performance
CREATE INDEX idx_data_retention_configs_executor_class ON data_retention_configs(executor_class);