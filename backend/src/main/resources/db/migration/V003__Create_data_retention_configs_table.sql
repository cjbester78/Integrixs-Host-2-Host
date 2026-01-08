-- Migration: Create data_retention_configs table
-- Purpose: Store configuration settings for automated data retention policies
-- Version: V1.0.21
-- Date: 2025-12-29

CREATE TABLE IF NOT EXISTS data_retention_configs (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Configuration details
    data_type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    
    -- Retention settings
    retention_days INTEGER NOT NULL,
    archive_days INTEGER, -- Only for LOG_FILES type
    schedule_cron VARCHAR(100), -- Only for SCHEDULE type
    
    -- Status
    enabled BOOLEAN NOT NULL DEFAULT true,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    
    -- Execution tracking
    last_execution TIMESTAMP,
    last_execution_status VARCHAR(255),
    last_items_processed INTEGER,
    
    -- Constraints
    CONSTRAINT chk_data_type CHECK (data_type IN ('LOG_FILES', 'SYSTEM_LOGS', 'TRANSACTION_LOGS', 'SCHEDULE')),
    CONSTRAINT chk_retention_days_positive CHECK (retention_days > 0),
    CONSTRAINT chk_archive_days_positive CHECK (archive_days IS NULL OR archive_days > 0),
    CONSTRAINT chk_archive_after_retention CHECK (archive_days IS NULL OR archive_days >= retention_days),
    CONSTRAINT chk_log_files_archive CHECK (
        (data_type = 'LOG_FILES' AND archive_days IS NOT NULL) OR 
        (data_type != 'LOG_FILES' AND archive_days IS NULL)
    ),
    CONSTRAINT chk_schedule_cron CHECK (
        (data_type = 'SCHEDULE' AND schedule_cron IS NOT NULL AND LENGTH(TRIM(schedule_cron)) > 0) OR 
        (data_type != 'SCHEDULE' AND schedule_cron IS NULL)
    )
);

-- Create indexes
CREATE INDEX idx_data_retention_configs_data_type ON data_retention_configs(data_type);
CREATE INDEX idx_data_retention_configs_enabled ON data_retention_configs(enabled);
CREATE INDEX idx_data_retention_configs_name ON data_retention_configs(name);
CREATE INDEX idx_data_retention_configs_last_execution ON data_retention_configs(last_execution);

-- Create trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_data_retention_configs_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_data_retention_configs_updated_at
    BEFORE UPDATE ON data_retention_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_data_retention_configs_updated_at();

-- Insert default configurations
INSERT INTO data_retention_configs (
    data_type, name, description, retention_days, archive_days, enabled, created_by, updated_by
) VALUES 
(
    'LOG_FILES',
    'Application Log Files',
    'Archive application log files after 7 days, delete archived files after 30 days',
    7,
    30,
    true,
    'system',
    'system'
),
(
    'SYSTEM_LOGS',
    'System Database Logs',
    'Delete system_logs table records older than 90 days',
    90,
    NULL,
    true,
    'system',
    'system'
),
(
    'TRANSACTION_LOGS',
    'Transaction Database Logs',
    'Delete transaction_logs table records older than 180 days',
    180,
    NULL,
    true,
    'system',
    'system'
),
(
    'SCHEDULE',
    'Daily Cleanup Schedule',
    'Run data retention cleanup daily at 2:00 AM',
    0,
    NULL,
    true,
    'system',
    'system'
);

-- Update the SCHEDULE record with cron expression
UPDATE data_retention_configs 
SET schedule_cron = '0 0 2 * * ?'
WHERE data_type = 'SCHEDULE' AND name = 'Daily Cleanup Schedule';

-- Add comments to table and columns
COMMENT ON TABLE data_retention_configs IS 'Configuration settings for automated data retention policies';
COMMENT ON COLUMN data_retention_configs.data_type IS 'Type of data: LOG_FILES, SYSTEM_LOGS, TRANSACTION_LOGS, SCHEDULE';
COMMENT ON COLUMN data_retention_configs.retention_days IS 'Number of days to retain data before archiving/deletion';
COMMENT ON COLUMN data_retention_configs.archive_days IS 'Number of days to keep archived data (LOG_FILES only)';
COMMENT ON COLUMN data_retention_configs.schedule_cron IS 'Cron expression for scheduling (SCHEDULE type only)';
COMMENT ON COLUMN data_retention_configs.last_execution IS 'Timestamp of last retention execution';
COMMENT ON COLUMN data_retention_configs.last_execution_status IS 'Status message from last execution';
COMMENT ON COLUMN data_retention_configs.last_items_processed IS 'Number of items processed in last execution';