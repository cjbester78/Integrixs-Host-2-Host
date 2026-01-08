-- =================================================================
-- V008: Add INTEGRATOR system user and system integration configuration
-- =================================================================
-- This migration adds the missing INTEGRATOR system user and configuration
-- that is required for automated flow execution

-- Add INTEGRATOR system user for automated flow execution
INSERT INTO users (
    id, 
    username, 
    email, 
    password_hash, 
    full_name, 
    role, 
    timezone,
    enabled,
    account_non_expired,
    account_non_locked,
    credentials_non_expired,
    created_at,
    created_by
) VALUES (
    gen_random_uuid(),
    'SystemIntegrator',
    'integrator@integrixlab.com',
    '$2a$12$disabled.system.integration.account', -- Disabled account hash
    'System Integration Service Account',
    'INTEGRATOR',
    'UTC',
    TRUE, -- Enabled for system operations
    TRUE,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (username) DO UPDATE SET
    email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

-- Add system integration configuration
INSERT INTO system_configuration (
    config_key, 
    config_value, 
    config_type, 
    description, 
    category, 
    default_value, 
    created_by
) VALUES (
    'system.integration.username', 
    'SystemIntegrator', 
    'STRING', 
    'Username for system integration operations and automated flow execution', 
    'SYSTEM', 
    'SystemIntegrator', 
    (SELECT id FROM users WHERE username = 'Administrator')
) ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- Drop the existing step type constraint
ALTER TABLE flow_execution_steps DROP CONSTRAINT IF EXISTS flow_execution_steps_step_type_check;

-- Add the new constraint with ADAPTER_SENDER and other missing types included
ALTER TABLE flow_execution_steps ADD CONSTRAINT flow_execution_steps_step_type_check CHECK (step_type IN (
    'ADAPTER_INBOUND', 'ADAPTER_OUTBOUND', 'ADAPTER_SENDER', 'ADAPTER_RECEIVER',
    'UTILITY', 'DECISION', 'SPLIT', 'MERGE',
    'WAIT', 'NOTIFICATION', 'TRANSFORMATION', 'VALIDATION'
));

-- Add audit log entry for this migration
INSERT INTO system_audit_log (
    id, 
    event_type, 
    event_category, 
    event_description, 
    user_id,
    username,
    resource_type,
    success, 
    created_at
) VALUES (
    gen_random_uuid(), 
    'SCHEMA_UPDATE', 
    'SYSTEM', 
    'Added INTEGRATOR system user, system.integration.username config, and updated step type constraints', 
    (SELECT id FROM users WHERE username = 'Administrator'),
    'Administrator',
    'DATABASE_SCHEMA',
    TRUE, 
    CURRENT_TIMESTAMP
);