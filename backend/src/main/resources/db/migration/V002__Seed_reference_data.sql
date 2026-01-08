-- =================================================================
-- H2H File Transfer System - Reference Data Seed File
-- =================================================================
-- This file contains initial/reference data for non-transactional tables
-- Includes: users, system_configuration, and other lookup/reference tables
-- Excludes: logs, executions, processed_files (transactional data)
-- =================================================================

-- =================================================================
-- USERS TABLE - Default admin user and system users
-- =================================================================

-- Clear existing users (except during initial setup)
-- DELETE FROM users WHERE username != 'Administrator';

-- Insert default administrator user
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
    'Administrator',
    'admin@integrixlab.com',
    '$2a$12$fylwVEhE/Jn4r41BqK/a3.QRzcoSUhlkP5kkgfOjKpJCr1188U5qK', -- Int3grix@01
    'System Administrator',
    'ADMINISTRATOR',
    'UTC',
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (username) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    updated_at = CURRENT_TIMESTAMP;

-- Insert system service user for internal operations
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
    'SystemService',
    'system@integrixlab.com',
    '$2a$12$disabled.account.no.login.allowed', -- Disabled account hash
    'System Service Account',
    'ADMINISTRATOR',
    'UTC',
    FALSE, -- Disabled for login
    TRUE,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    'system'
) ON CONFLICT (username) DO UPDATE SET
    email = EXCLUDED.email,
    full_name = EXCLUDED.full_name,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

-- =================================================================
-- SYSTEM CONFIGURATION - Application settings
-- =================================================================

-- Dashboard Configuration
INSERT INTO system_configuration (config_key, config_value, config_type, description, category, default_value, created_by) VALUES
('dashboard.refresh.health_interval', '30000', 'INTEGER', 'Dashboard health status refresh interval in milliseconds', 'DASHBOARD', '30000', (SELECT id FROM users WHERE username = 'Administrator')),
('dashboard.refresh.adapter_stats_interval', '60000', 'INTEGER', 'Dashboard adapter statistics refresh interval in milliseconds', 'DASHBOARD', '60000', (SELECT id FROM users WHERE username = 'Administrator')),
('dashboard.refresh.recent_executions_interval', '15000', 'INTEGER', 'Dashboard recent executions refresh interval in milliseconds', 'DASHBOARD', '15000', (SELECT id FROM users WHERE username = 'Administrator')),
('dashboard.pagination.default_page_size', '20', 'INTEGER', 'Default number of items per page in dashboard tables', 'DASHBOARD', '20', (SELECT id FROM users WHERE username = 'Administrator')),
('dashboard.chart.data_retention_days', '30', 'INTEGER', 'Number of days to retain dashboard chart data', 'DASHBOARD', '30', (SELECT id FROM users WHERE username = 'Administrator')),

-- Security Configuration
('security.session.timeout_minutes', '1440', 'INTEGER', 'User session timeout in minutes (24 hours default)', 'SECURITY', '1440', (SELECT id FROM users WHERE username = 'Administrator')),
('security.password.min_length', '8', 'INTEGER', 'Minimum password length requirement', 'SECURITY', '8', (SELECT id FROM users WHERE username = 'Administrator')),
('security.password.require_uppercase', 'false', 'BOOLEAN', 'Require uppercase letters in passwords', 'SECURITY', 'false', (SELECT id FROM users WHERE username = 'Administrator')),
('security.password.require_lowercase', 'false', 'BOOLEAN', 'Require lowercase letters in passwords', 'SECURITY', 'false', (SELECT id FROM users WHERE username = 'Administrator')),
('security.password.require_numbers', 'false', 'BOOLEAN', 'Require numbers in passwords', 'SECURITY', 'false', (SELECT id FROM users WHERE username = 'Administrator')),
('security.password.require_special_chars', 'false', 'BOOLEAN', 'Require special characters in passwords', 'SECURITY', 'false', (SELECT id FROM users WHERE username = 'Administrator')),
('security.login.max_attempts', '5', 'INTEGER', 'Maximum login attempts before account lockout', 'SECURITY', '5', (SELECT id FROM users WHERE username = 'Administrator')),
('security.login.lockout_duration_minutes', '30', 'INTEGER', 'Account lockout duration in minutes after max attempts', 'SECURITY', '30', (SELECT id FROM users WHERE username = 'Administrator')),
('security.jwt.access_token_expiry_hours', '24', 'INTEGER', 'JWT access token expiry time in hours', 'SECURITY', '24', (SELECT id FROM users WHERE username = 'Administrator')),
('security.jwt.refresh_token_expiry_days', '7', 'INTEGER', 'JWT refresh token expiry time in days', 'SECURITY', '7', (SELECT id FROM users WHERE username = 'Administrator')),
('security.jwt.secret', 'JmQajlkHx5P3Ln+UcOwe2YY92HMJk3zAm6epso/iF1k408dQ56xLrYNfFixtwVnK', 'STRING', 'JWT signing secret key (minimum 64 characters for HS512)', 'SECURITY', 'JmQajlkHx5P3Ln+UcOwe2YY92HMJk3zAm6epso/iF1k408dQ56xLrYNfFixtwVnK', (SELECT id FROM users WHERE username = 'Administrator')),

-- Email/Notification Configuration
('notifications.email.enabled', 'false', 'BOOLEAN', 'Enable email notifications', 'NOTIFICATIONS', 'false', (SELECT id FROM users WHERE username = 'Administrator')),
('notifications.email.smtp_host', '', 'STRING', 'SMTP server hostname for email notifications', 'NOTIFICATIONS', '', (SELECT id FROM users WHERE username = 'Administrator')),
('notifications.email.smtp_port', '587', 'INTEGER', 'SMTP server port', 'NOTIFICATIONS', '587', (SELECT id FROM users WHERE username = 'Administrator')),
('notifications.email.smtp_username', '', 'STRING', 'SMTP authentication username', 'NOTIFICATIONS', '', (SELECT id FROM users WHERE username = 'Administrator')),
('notifications.email.smtp_password', '', 'STRING', 'SMTP authentication password (encrypted)', 'NOTIFICATIONS', '', (SELECT id FROM users WHERE username = 'Administrator')),
('notifications.email.from_address', 'noreply@integrixlab.com', 'STRING', 'Default from address for system notifications', 'NOTIFICATIONS', 'noreply@integrixlab.com', (SELECT id FROM users WHERE username = 'Administrator')),
('notifications.email.admin_addresses', 'admin@integrixlab.com', 'STRING', 'Comma-separated list of admin email addresses', 'NOTIFICATIONS', 'admin@integrixlab.com', (SELECT id FROM users WHERE username = 'Administrator')),

-- File Processing Configuration
('file.processing.max_file_size_mb', '100', 'INTEGER', 'Maximum file size for processing in MB', 'FILE_PROCESSING', '100', (SELECT id FROM users WHERE username = 'Administrator')),
('file.processing.archive_retention_days', '90', 'INTEGER', 'Number of days to retain archived files', 'FILE_PROCESSING', '90', (SELECT id FROM users WHERE username = 'Administrator')),
('file.processing.batch_size', '100', 'INTEGER', 'Default batch size for file processing operations', 'FILE_PROCESSING', '100', (SELECT id FROM users WHERE username = 'Administrator')),
('file.processing.modification_check_delay_ms', '2000', 'INTEGER', 'Milliseconds to wait before modification check to ensure file is fully written', 'FILE_PROCESSING', '2000', (SELECT id FROM users WHERE username = 'Administrator')),
('file.processing.poll_interval_seconds', '60', 'INTEGER', 'Default polling interval in seconds for file adapters', 'FILE_PROCESSING', '60', (SELECT id FROM users WHERE username = 'Administrator')),
('file.processing.retry_interval_seconds', '60', 'INTEGER', 'Default retry interval in seconds for file adapters', 'FILE_PROCESSING', '60', (SELECT id FROM users WHERE username = 'Administrator')),
('file.processing.max_retry_attempts', '3', 'INTEGER', 'Maximum retry attempts for failed file operations', 'FILE_PROCESSING', '3', (SELECT id FROM users WHERE username = 'Administrator')),
('file.processing.temp_directory', '/tmp/h2h-processing', 'STRING', 'Temporary directory for file processing operations', 'FILE_PROCESSING', '/tmp/h2h-processing', (SELECT id FROM users WHERE username = 'Administrator')),
('file.processing.allowed_extensions', '.xml,.csv,.txt,.json,.zip,.pgp', 'STRING', 'Comma-separated list of allowed file extensions', 'FILE_PROCESSING', '.xml,.csv,.txt,.json,.zip,.pgp', (SELECT id FROM users WHERE username = 'Administrator')),

-- System Configuration
('system.logging.level', 'INFO', 'STRING', 'Application logging level', 'SYSTEM', 'INFO', (SELECT id FROM users WHERE username = 'Administrator')),
('system.backup.enabled', 'true', 'BOOLEAN', 'Enable automatic system backups', 'SYSTEM', 'true', (SELECT id FROM users WHERE username = 'Administrator')),
('system.backup.schedule_cron', '0 2 * * *', 'STRING', 'Cron expression for backup schedule (daily at 2 AM)', 'SYSTEM', '0 2 * * *', (SELECT id FROM users WHERE username = 'Administrator')),
('system.backup.retention_days', '30', 'INTEGER', 'Number of days to retain system backups', 'SYSTEM', '30', (SELECT id FROM users WHERE username = 'Administrator')),
('system.maintenance.window_start', '02:00', 'STRING', 'Daily maintenance window start time (24h format)', 'SYSTEM', '02:00', (SELECT id FROM users WHERE username = 'Administrator')),
('system.maintenance.window_end', '04:00', 'STRING', 'Daily maintenance window end time (24h format)', 'SYSTEM', '04:00', (SELECT id FROM users WHERE username = 'Administrator')),
('system.cleanup.enabled', 'true', 'BOOLEAN', 'Enable automatic system cleanup tasks', 'SYSTEM', 'true', (SELECT id FROM users WHERE username = 'Administrator')),
('system.cleanup.schedule_cron', '0 3 * * *', 'STRING', 'Cron expression for cleanup schedule (daily at 3 AM)', 'SYSTEM', '0 3 * * *', (SELECT id FROM users WHERE username = 'Administrator')),

-- Logging Configuration
('logging.database.enabled', 'true', 'BOOLEAN', 'Enable logging to database', 'LOGGING', 'true', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.database.batch_size', '100', 'INTEGER', 'Batch size for database log inserts', 'LOGGING', '100', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.database.async_enabled', 'true', 'BOOLEAN', 'Enable asynchronous database logging', 'LOGGING', 'true', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.level.root', 'INFO', 'STRING', 'Root logging level', 'LOGGING', 'INFO', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.level.system', 'INFO', 'STRING', 'System logging level', 'LOGGING', 'INFO', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.level.adapter_execution', 'DEBUG', 'STRING', 'Adapter execution logging level', 'LOGGING', 'DEBUG', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.level.flow_execution', 'DEBUG', 'STRING', 'Flow execution logging level', 'LOGGING', 'DEBUG', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.level.authentication', 'INFO', 'STRING', 'Authentication and security logging level', 'LOGGING', 'INFO', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.file.max_size_mb', '100', 'INTEGER', 'Maximum log file size in MB before rotation', 'LOGGING', '100', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.file.max_history_days', '30', 'INTEGER', 'Number of days to keep log files', 'LOGGING', '30', (SELECT id FROM users WHERE username = 'Administrator')),
('logging.retention.database_days', '90', 'INTEGER', 'Number of days to retain logs in database before cleanup', 'LOGGING', '90', (SELECT id FROM users WHERE username = 'Administrator')),

-- Adapter Configuration Defaults
('adapter.default.connection_timeout_ms', '30000', 'INTEGER', 'Default connection timeout for adapters in milliseconds', 'ADAPTER', '30000', (SELECT id FROM users WHERE username = 'Administrator')),
('adapter.default.read_timeout_ms', '60000', 'INTEGER', 'Default read timeout for adapters in milliseconds', 'ADAPTER', '60000', (SELECT id FROM users WHERE username = 'Administrator')),
('adapter.sftp.session_timeout_ms', '60000', 'INTEGER', 'Default SFTP session timeout in milliseconds', 'ADAPTER', '60000', (SELECT id FROM users WHERE username = 'Administrator')),
('adapter.sftp.channel_timeout_ms', '60000', 'INTEGER', 'Default SFTP channel timeout in milliseconds', 'ADAPTER', '60000', (SELECT id FROM users WHERE username = 'Administrator')),
('adapter.email.smtp_timeout_ms', '30000', 'INTEGER', 'Default SMTP timeout in milliseconds', 'ADAPTER', '30000', (SELECT id FROM users WHERE username = 'Administrator')),
('adapter.email.imap_timeout_ms', '30000', 'INTEGER', 'Default IMAP timeout in milliseconds', 'ADAPTER', '30000', (SELECT id FROM users WHERE username = 'Administrator'))

ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    config_type = EXCLUDED.config_type,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    default_value = EXCLUDED.default_value,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = EXCLUDED.updated_by;

-- =================================================================
-- AUDIT LOG ENTRY - Record this seed operation
-- =================================================================

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
    'SEED_DATA_APPLIED', 
    'SYSTEM', 
    'Reference data seed file applied - users and system configuration initialized', 
    (SELECT id FROM users WHERE username = 'Administrator'),
    'Administrator',
    'SYSTEM_SEED',
    TRUE, 
    CURRENT_TIMESTAMP
);

-- =================================================================
-- END OF SEED FILE
-- =================================================================