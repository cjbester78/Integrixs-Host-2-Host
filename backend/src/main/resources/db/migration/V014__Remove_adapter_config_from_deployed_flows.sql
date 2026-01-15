-- V014: Remove adapter config columns from deployed_flows
-- Adapter configurations should be read from adapters table during execution, not stored as snapshots

-- Drop dependent view first
DROP VIEW IF EXISTS active_deployed_flows CASCADE;

-- Drop the columns
ALTER TABLE deployed_flows DROP COLUMN IF EXISTS sender_adapter_config;
ALTER TABLE deployed_flows DROP COLUMN IF EXISTS receiver_adapter_config;

-- Recreate the view without adapter config columns
CREATE OR REPLACE VIEW active_deployed_flows AS
SELECT
    df.id,
    df.flow_id,
    df.flow_name,
    df.flow_version,
    df.deployment_status,
    df.deployment_environment,
    df.sender_adapter_id,
    df.sender_adapter_name,
    df.receiver_adapter_id,
    df.receiver_adapter_name,
    df.execution_enabled,
    df.runtime_status,
    df.max_concurrent_executions,
    df.execution_timeout_minutes,
    df.total_executions,
    df.successful_executions,
    df.failed_executions,
    df.last_execution_at,
    df.last_execution_status,
    df.average_execution_time_ms,
    df.consecutive_failures,
    df.deployed_at,
    df.deployed_by,
    df.health_check_enabled,
    df.health_check_status
FROM deployed_flows df
WHERE df.deployment_status = 'DEPLOYED'
  AND df.execution_enabled = true;
