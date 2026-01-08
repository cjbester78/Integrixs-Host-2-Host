-- Add environment configuration settings to system_configuration table
-- This enables environment-based feature restrictions similar to Flow Bridge

INSERT INTO system_configuration (config_key, config_value, config_type, description, category, default_value, readonly, created_by) VALUES
('system.environment.type', 'DEVELOPMENT', 'STRING', 'System environment type (DEVELOPMENT, QUALITY_ASSURANCE, PRODUCTION)', 'SYSTEM', 'DEVELOPMENT', false, (SELECT id FROM users WHERE username = 'Administrator')),
('system.environment.enforce_restrictions', 'true', 'BOOLEAN', 'Enforce environment-based feature restrictions', 'SYSTEM', 'true', false, (SELECT id FROM users WHERE username = 'Administrator')),
('system.environment.restriction_message', 'This action is not allowed in %s environment', 'STRING', 'Custom message shown when actions are restricted by environment', 'SYSTEM', 'This action is not allowed in %s environment', false, (SELECT id FROM users WHERE username = 'Administrator'));

-- Add comment for clarity
COMMENT ON COLUMN system_configuration.config_key IS 'Unique configuration key. Environment settings: system.environment.type (DEVELOPMENT/QUALITY_ASSURANCE/PRODUCTION), system.environment.enforce_restrictions (true/false), system.environment.restriction_message (custom message template)';