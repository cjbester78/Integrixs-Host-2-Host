-- =====================================================================
-- H2H File Transfer System - Complete Database Schema
-- =====================================================================
-- This file contains the complete database schema for the H2H File
-- Transfer system, consolidated from all Flyway migration files V001-V030.
-- 
-- Version: 1.0 (Consolidated)
-- Date: 2025-12-23
-- Description: Complete schema with all tables, indexes, functions, triggers
-- =====================================================================

-- =====================================================================
-- EXTENSIONS AND SETUP
-- =====================================================================

-- Enable UUID generation functions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================================
-- CORE USER AND AUTHENTICATION TABLES
-- =====================================================================

-- Users table - System users and authentication
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('ADMINISTRATOR', 'VIEWER')),
    timezone VARCHAR(100) DEFAULT 'UTC',
    enabled BOOLEAN DEFAULT TRUE,
    account_non_expired BOOLEAN DEFAULT TRUE,
    account_non_locked BOOLEAN DEFAULT TRUE,
    credentials_non_expired BOOLEAN DEFAULT TRUE,
    failed_login_attempts INTEGER DEFAULT 0,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system'
);

-- User sessions for login tracking and security
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    ip_address INET NOT NULL,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    login_log_id UUID, -- Links to transaction_logs
    
    CONSTRAINT fk_user_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- User management error tracking for security monitoring
CREATE TABLE user_management_errors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    error_type VARCHAR(50) NOT NULL, -- AUTHENTICATION, AUTHORIZATION, SESSION_MANAGEMENT
    error_code VARCHAR(50) NOT NULL, -- LOGIN_FAILED, INVALID_CREDENTIALS, BRUTE_FORCE, SESSION_HIJACK
    action VARCHAR(100) NOT NULL, -- LOGIN, CREATE_USER, UPDATE_USER, DELETE_USER
    error_message TEXT NOT NULL,
    username VARCHAR(255),
    ip_address INET NOT NULL,
    user_agent TEXT,
    threat_level VARCHAR(20) DEFAULT 'LOW', -- LOW, MEDIUM, HIGH, CRITICAL
    transaction_log_id UUID, -- Links to transaction_logs for correlation
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_resolved BOOLEAN DEFAULT false,
    resolution_notes TEXT
);

-- =====================================================================
-- SYSTEM CONFIGURATION TABLES
-- =====================================================================

-- System configuration for dynamic application settings
CREATE TABLE system_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    config_type VARCHAR(50) NOT NULL DEFAULT 'STRING', -- STRING, INTEGER, BOOLEAN, JSON
    description TEXT,
    category VARCHAR(50) NOT NULL DEFAULT 'GENERAL', -- DASHBOARD, SECURITY, NOTIFICATIONS, etc.
    is_encrypted BOOLEAN DEFAULT FALSE,
    is_readonly BOOLEAN DEFAULT FALSE,
    default_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    
    CONSTRAINT unique_config_key UNIQUE (config_key)
);

-- =====================================================================
-- SECURITY AND ENCRYPTION TABLES
-- =====================================================================

-- SSH keys for secure file transfer authentication
CREATE TABLE ssh_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    private_key TEXT NOT NULL,
    public_key TEXT NOT NULL,
    key_type VARCHAR(50) NOT NULL DEFAULT 'RSA',
    key_size INTEGER NOT NULL DEFAULT 2048,
    fingerprint VARCHAR(255) UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id)
);

-- PGP keys for file encryption and digital signatures
CREATE TABLE pgp_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_name VARCHAR(100) NOT NULL,
    description TEXT,
    key_type VARCHAR(20) NOT NULL DEFAULT 'RSA', -- RSA, ECC, DSA
    key_size INTEGER NOT NULL DEFAULT 2048, -- Key size in bits
    user_id VARCHAR(255) NOT NULL, -- PGP User ID (email/name)
    fingerprint VARCHAR(40) NOT NULL UNIQUE, -- Key fingerprint for identification
    key_id VARCHAR(16) NOT NULL, -- Short key ID (last 8 bytes of fingerprint)
    
    -- Key material (encrypted)
    public_key TEXT NOT NULL, -- ASCII armored public key
    private_key TEXT, -- ASCII armored private key (null for imported public keys only)
    
    -- Key metadata
    algorithm VARCHAR(50), -- Encryption algorithm details
    expires_at TIMESTAMP, -- Key expiration date
    revoked_at TIMESTAMP, -- Revocation timestamp
    revocation_reason VARCHAR(255), -- Reason for revocation
    
    -- Usage flags
    can_encrypt BOOLEAN DEFAULT true,
    can_sign BOOLEAN DEFAULT true,
    can_certify BOOLEAN DEFAULT false,
    can_authenticate BOOLEAN DEFAULT false,
    
    -- Import/Export metadata
    imported_from VARCHAR(255), -- Original source of imported keys
    exported_count INTEGER DEFAULT 0, -- Number of times key was exported
    last_used_at TIMESTAMP, -- Last time key was used for encryption/decryption
    
    -- Standard audit columns
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    
    -- Constraints
    CONSTRAINT unique_key_name UNIQUE (key_name),
    CONSTRAINT valid_key_type CHECK (key_type IN ('RSA', 'ECC', 'DSA')),
    CONSTRAINT valid_key_size CHECK (key_size >= 1024 AND key_size <= 4096),
    CONSTRAINT valid_dates CHECK (expires_at IS NULL OR expires_at > created_at)
);

-- =====================================================================
-- ADAPTER CONFIGURATION TABLES
-- =====================================================================

-- Adapters for file transfer operations (SFTP, FILE, EMAIL)
CREATE TABLE adapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    adapter_type VARCHAR(20) NOT NULL CHECK (adapter_type IN ('SFTP', 'FILE', 'EMAIL')),
    direction VARCHAR(20) NOT NULL CHECK (direction IN ('SENDER', 'RECEIVER')),
    
    -- JSON configuration specific to adapter type
    configuration JSONB NOT NULL,
    
    -- Connection and testing
    connection_validated BOOLEAN DEFAULT false,
    last_test_at TIMESTAMP WITH TIME ZONE,
    test_result TEXT,
    
    -- Status and control
    status VARCHAR(20) NOT NULL DEFAULT 'STOPPED' CHECK (status IN ('STARTED', 'STOPPED')),
    active BOOLEAN NOT NULL DEFAULT true,
    
    -- Performance tracking
    average_execution_time_ms BIGINT DEFAULT 0,
    success_rate_percent DECIMAL(5,2) DEFAULT 100.0,
    
    -- Bank association
    bank VARCHAR(255) NOT NULL,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID REFERENCES users(id)
);

-- =====================================================================
-- FLOW ARCHITECTURE TABLES
-- =====================================================================

-- Flow utilities for data processing operations
CREATE TABLE flow_utilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    utility_type VARCHAR(50) NOT NULL CHECK (utility_type IN (
        'PGP_ENCRYPT', 'PGP_DECRYPT', 
        'ZIP_COMPRESS', 'ZIP_EXTRACT',
        'FILE_SPLIT', 'FILE_MERGE',
        'DATA_TRANSFORM', 'FILE_VALIDATE',
        'CUSTOM_SCRIPT'
    )),
    description TEXT NOT NULL,
    
    -- Configuration schema for this utility type
    configuration_schema JSONB NOT NULL,
    
    -- Default configuration values
    default_configuration JSONB,
    
    -- Processing capabilities
    supports_parallel BOOLEAN DEFAULT false,
    max_file_size_mb INTEGER DEFAULT 0, -- 0 = unlimited
    supported_formats TEXT[], -- Array of file extensions/types
    
    -- Status
    enabled BOOLEAN NOT NULL DEFAULT true,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id)
);

-- Integration flows for defining data processing workflows
CREATE TABLE integration_flows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    
    -- Flow definition in JSON format
    flow_definition JSONB NOT NULL,
    
    -- Flow metadata
    flow_version INTEGER NOT NULL DEFAULT 1,
    flow_type VARCHAR(50) NOT NULL DEFAULT 'STANDARD', -- STANDARD, PARALLEL, CONDITIONAL
    
    -- Execution settings
    max_parallel_executions INTEGER DEFAULT 1,
    timeout_minutes INTEGER DEFAULT 60,
    retry_policy JSONB, -- Retry configuration
    
    -- Performance tracking
    total_executions BIGINT DEFAULT 0,
    successful_executions BIGINT DEFAULT 0,
    failed_executions BIGINT DEFAULT 0,
    average_execution_time_ms BIGINT DEFAULT 0,
    
    -- Scheduling (if applicable)
    schedule_enabled BOOLEAN DEFAULT false,
    schedule_cron VARCHAR(100),
    next_scheduled_run TIMESTAMP WITH TIME ZONE,
    
    -- Status and control
    active BOOLEAN NOT NULL DEFAULT true,
    
    -- Bank association
    bank_name VARCHAR(255),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID REFERENCES users(id)
);

-- Flow node connections for defining flow structure
CREATE TABLE flow_node_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES integration_flows(id) ON DELETE CASCADE,
    
    -- Connection definition
    source_node_id VARCHAR(100) NOT NULL,
    target_node_id VARCHAR(100) NOT NULL,
    
    -- Connection type and conditions
    connection_type VARCHAR(20) DEFAULT 'DEFAULT' CHECK (connection_type IN (
        'DEFAULT', 'SUCCESS', 'FAILURE', 'CONDITIONAL'
    )),
    condition_expression TEXT, -- For conditional connections
    
    -- Connection metadata
    connection_label VARCHAR(255),
    connection_order INTEGER DEFAULT 0,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Flow notifications configuration
CREATE TABLE flow_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES integration_flows(id) ON DELETE CASCADE,
    
    -- Notification settings
    notification_type VARCHAR(20) NOT NULL CHECK (notification_type IN (
        'EMAIL', 'WEBHOOK', 'SLACK', 'SMS'
    )),
    
    -- Trigger events
    on_success BOOLEAN DEFAULT false,
    on_failure BOOLEAN DEFAULT true,
    on_timeout BOOLEAN DEFAULT true,
    on_retry BOOLEAN DEFAULT false,
    
    -- Notification configuration
    configuration JSONB NOT NULL, -- Email addresses, webhook URLs, etc.
    
    -- Status
    enabled BOOLEAN DEFAULT true,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id)
);

-- =====================================================================
-- FLOW DEPLOYMENT TABLES
-- =====================================================================

-- Deployed flows registry for production flow instances
CREATE TABLE deployed_flows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES integration_flows(id) ON DELETE CASCADE,
    flow_name VARCHAR(255) NOT NULL, -- Snapshot at deployment time
    flow_version INTEGER NOT NULL, -- Version at deployment time
    
    -- Deployment status and metadata
    deployment_status VARCHAR(20) NOT NULL DEFAULT 'DEPLOYED' 
        CHECK (deployment_status IN ('DEPLOYED', 'UNDEPLOYED', 'DEPLOYING', 'UNDEPLOYING', 'FAILED', 'SUSPENDED')),
    deployment_environment VARCHAR(50) NOT NULL DEFAULT 'production',
    
    -- Adapter links (using sender/receiver terminology)
    sender_adapter_id UUID NOT NULL REFERENCES adapters(id),
    sender_adapter_name VARCHAR(255) NOT NULL,
    receiver_adapter_id UUID REFERENCES adapters(id),
    receiver_adapter_name VARCHAR(255),
    
    -- Runtime execution control
    execution_enabled BOOLEAN NOT NULL DEFAULT true,
    runtime_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' 
        CHECK (runtime_status IN ('ACTIVE', 'INACTIVE', 'ERROR', 'STARTING', 'STOPPING', 'MAINTENANCE')),
    
    -- Execution constraints
    max_concurrent_executions INTEGER DEFAULT 1,
    execution_timeout_minutes INTEGER DEFAULT 60,
    retry_policy JSONB,
    
    -- Performance tracking for deployed instance
    total_executions BIGINT DEFAULT 0,
    successful_executions BIGINT DEFAULT 0,
    failed_executions BIGINT DEFAULT 0,
    last_execution_at TIMESTAMP WITH TIME ZONE,
    last_execution_status VARCHAR(20),
    last_execution_duration_ms BIGINT,
    average_execution_time_ms BIGINT DEFAULT 0,
    
    -- Error tracking
    last_error_at TIMESTAMP WITH TIME ZONE,
    last_error_message TEXT,
    consecutive_failures INTEGER DEFAULT 0,
    
    -- Configuration snapshots (using sender/receiver terminology)
    flow_configuration JSONB NOT NULL,
    sender_adapter_config JSONB NOT NULL,
    receiver_adapter_config JSONB,
    
    -- Deployment audit
    deployed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deployed_by UUID NOT NULL REFERENCES users(id),
    undeployed_at TIMESTAMP WITH TIME ZONE,
    undeployed_by UUID REFERENCES users(id),
    deployment_notes TEXT,
    
    -- Health monitoring
    health_check_enabled BOOLEAN DEFAULT true,
    last_health_check_at TIMESTAMP WITH TIME ZONE,
    health_check_status VARCHAR(20) DEFAULT 'UNKNOWN',
    health_check_message TEXT,
    
    -- Ensure only one deployment per flow
    CONSTRAINT unique_flow_deployment UNIQUE(flow_id, deployment_environment)
);

-- Flow deployment operations log
CREATE TABLE flow_deployment_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deployed_flow_id UUID REFERENCES deployed_flows(id) ON DELETE CASCADE,
    flow_id UUID NOT NULL REFERENCES integration_flows(id) ON DELETE CASCADE,
    
    -- Operation details
    operation VARCHAR(20) NOT NULL CHECK (operation IN ('DEPLOY', 'UNDEPLOY', 'SUSPEND', 'RESUME', 'UPDATE', 'REDEPLOY')),
    operation_status VARCHAR(20) NOT NULL CHECK (operation_status IN ('SUCCESS', 'FAILED', 'IN_PROGRESS', 'CANCELLED')),
    
    -- Timing
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    
    -- Operation context
    operation_reason TEXT,
    error_message TEXT,
    error_details JSONB,
    
    -- Configuration snapshots
    flow_config_before JSONB,
    flow_config_after JSONB,
    
    -- Audit
    performed_by UUID NOT NULL REFERENCES users(id),
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    
    -- Additional metadata
    deployment_environment VARCHAR(50) NOT NULL DEFAULT 'production',
    operation_metadata JSONB
);

-- Flow deployment log for tracking deployment history (using sender/receiver terminology)
CREATE TABLE flow_deployment_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES integration_flows(id) ON DELETE CASCADE,
    
    -- Deployment operation details
    operation VARCHAR(20) NOT NULL CHECK (operation IN ('DEPLOY', 'UNDEPLOY', 'REDEPLOY')),
    operation_status VARCHAR(20) NOT NULL CHECK (operation_status IN ('SUCCESS', 'FAILED', 'IN_PROGRESS', 'CANCELLED')),
    
    -- Timing
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    
    -- Operation details
    deployment_config JSONB, -- Configuration snapshot at deployment time
    error_message TEXT,
    error_details JSONB,
    
    -- Validation results
    validation_passed BOOLEAN DEFAULT false,
    validation_errors TEXT[],
    
    -- Adapter information (using sender/receiver terminology)
    sender_adapter_id UUID,
    sender_adapter_config JSONB,
    receiver_adapter_id UUID,
    receiver_adapter_config JSONB,
    
    -- Audit fields
    deployed_by UUID NOT NULL REFERENCES users(id),
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid()
);

-- =====================================================================
-- FLOW EXECUTION TABLES (TRANSACTIONAL)
-- =====================================================================

-- Flow executions for runtime tracking
CREATE TABLE flow_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES integration_flows(id) ON DELETE CASCADE,
    flow_name VARCHAR(255) NOT NULL, -- Snapshot of flow name
    
    -- Execution context
    execution_status VARCHAR(20) NOT NULL CHECK (execution_status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 
        'CANCELLED', 'TIMEOUT', 'RETRY_PENDING'
    )) DEFAULT 'PENDING',
    
    -- Trigger information
    trigger_type VARCHAR(20) NOT NULL CHECK (trigger_type IN (
        'MANUAL', 'SCHEDULED', 'API', 'RETRY', 'WEBHOOK'
    )),
    triggered_by UUID REFERENCES users(id),
    
    -- Timing
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    timeout_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT DEFAULT 0,
    
    -- Execution data
    payload JSONB, -- Input data for retry/debugging
    execution_context JSONB, -- Runtime variables, file paths, etc.
    
    -- Results summary
    total_files_processed INTEGER DEFAULT 0,
    files_successful INTEGER DEFAULT 0,
    files_failed INTEGER DEFAULT 0,
    total_bytes_processed BIGINT DEFAULT 0,
    
    -- Error information
    error_message TEXT,
    error_details JSONB,
    error_step_id VARCHAR(100), -- Which step failed
    
    -- Retry management
    retry_attempt INTEGER DEFAULT 0,
    max_retry_attempts INTEGER DEFAULT 3,
    
    -- Correlation for tracking across systems
    correlation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    parent_execution_id UUID REFERENCES flow_executions(id), -- For retry chains
    
    -- Priority and scheduling
    priority INTEGER DEFAULT 5, -- 1-10 scale
    scheduled_for TIMESTAMP WITH TIME ZONE
);

-- Flow execution steps for detailed step tracking
CREATE TABLE flow_execution_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL REFERENCES flow_executions(id) ON DELETE CASCADE,
    
    -- Step identification
    step_id VARCHAR(100) NOT NULL, -- From flow definition
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL CHECK (step_type IN (
        'ADAPTER_INBOUND', 'ADAPTER_OUTBOUND', 
        'UTILITY', 'DECISION', 'SPLIT', 'MERGE',
        'WAIT', 'NOTIFICATION'
    )),
    step_order INTEGER NOT NULL, -- Execution sequence
    
    -- Step configuration (snapshot at execution time)
    step_configuration JSONB,
    
    -- Execution status
    step_status VARCHAR(20) NOT NULL CHECK (step_status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 
        'SKIPPED', 'CANCELLED', 'TIMEOUT'
    )) DEFAULT 'PENDING',
    
    -- Timing
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT DEFAULT 0,
    
    -- Step data
    input_data JSONB, -- Data received by this step
    output_data JSONB, -- Data produced by this step
    
    -- File tracking for this step
    input_files TEXT[], -- Array of file paths processed
    output_files TEXT[], -- Array of files produced
    files_count INTEGER DEFAULT 0,
    bytes_processed BIGINT DEFAULT 0,
    
    -- Error information
    error_message TEXT,
    error_details JSONB,
    exit_code INTEGER, -- For utility steps
    
    -- Performance metrics
    cpu_usage_percent DECIMAL(5,2),
    memory_usage_mb INTEGER,
    
    -- Correlation
    correlation_id UUID NOT NULL
);

-- =====================================================================
-- FILE PROCESSING TABLES (TRANSACTIONAL)
-- =====================================================================

-- Processed files audit trail
CREATE TABLE processed_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID NOT NULL, -- References adapter or flow execution
    
    -- File details
    file_name VARCHAR(500) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT,
    file_hash VARCHAR(128), -- SHA-256 hash for integrity
    
    -- Processing details
    status VARCHAR(50) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    processing_start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processing_end_time TIMESTAMP,
    processing_duration_ms BIGINT,
    
    -- File operations performed
    operations_performed TEXT, -- JSON array of operations (copy, move, encrypt, etc.)
    
    -- Error details (if failed)
    error_message TEXT,
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- AUDIT AND LOGGING TABLES
-- =====================================================================

-- System audit log for security and compliance
CREATE TABLE system_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Event details
    event_type VARCHAR(100) NOT NULL, -- LOGIN, LOGOUT, USER_CREATE, INTERFACE_CREATE, etc.
    event_description TEXT NOT NULL,
    event_category VARCHAR(50) NOT NULL CHECK (event_category IN ('SECURITY', 'USER_MANAGEMENT', 'INTERFACE_MANAGEMENT', 'SYSTEM', 'FILE_OPERATIONS')),
    
    -- User context
    user_id UUID,
    username VARCHAR(100),
    user_ip VARCHAR(45), -- IPv6 compatible
    user_agent TEXT,
    
    -- Resource context
    resource_type VARCHAR(100), -- user, interface, file, etc.
    resource_id UUID,
    resource_name VARCHAR(255),
    
    -- Additional context (JSON)
    additional_data TEXT, -- JSON string for extra context
    
    -- Result
    success BOOLEAN NOT NULL,
    error_message TEXT,
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- System logs for application logging
CREATE TABLE system_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Log metadata
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    log_level VARCHAR(20) NOT NULL, -- TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    log_category VARCHAR(50) NOT NULL DEFAULT 'SYSTEM', -- SYSTEM, ADAPTER_EXECUTION, FLOW_EXECUTION, AUTHENTICATION, DATABASE, SCHEDULER, API
    logger_name VARCHAR(255) NOT NULL, -- Logger class name
    thread_name VARCHAR(100),
    
    -- Log content
    message TEXT NOT NULL,
    formatted_message TEXT,
    
    -- Context information
    correlation_id UUID,
    session_id VARCHAR(255),
    user_id UUID,
    
    -- Execution context
    adapter_id UUID, -- For adapter execution logs
    adapter_name VARCHAR(255), -- For easier querying
    flow_id UUID, -- For flow execution logs  
    flow_name VARCHAR(255), -- For easier querying
    execution_id UUID, -- Links to flow_executions or adapter execution records
    
    -- Request context
    request_id VARCHAR(255),
    request_method VARCHAR(10), -- GET, POST, PUT, DELETE, etc.
    request_uri VARCHAR(500),
    remote_address INET,
    user_agent VARCHAR(500),
    
    -- Application context
    application_name VARCHAR(100) DEFAULT 'h2h-backend',
    environment VARCHAR(50), -- dev, test, prod
    server_hostname VARCHAR(100),
    
    -- Exception details
    exception_class VARCHAR(255),
    exception_message TEXT,
    stack_trace TEXT,
    
    -- Additional metadata
    mdc_data JSONB, -- Mapped Diagnostic Context data
    marker VARCHAR(100), -- Logback markers for categorization
    
    -- Performance tracking
    execution_time_ms BIGINT, -- For performance-related logs
    
    -- Indexing timestamp for performance
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Transaction logs for business event tracking
CREATE TABLE transaction_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    level VARCHAR(10) NOT NULL DEFAULT 'INFO' CHECK (level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL')),
    category VARCHAR(50) NOT NULL CHECK (category IN ('AUTHENTICATION', 'FILE_PROCESSING', 'ADAPTER_EXECUTION', 'FLOW_EXECUTION', 'SYSTEM_OPERATION', 'USER_MANAGEMENT', 'CONFIGURATION_CHANGE', 'SECURITY')),
    component VARCHAR(50) NOT NULL, 
    source VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    
    -- Authentication context
    username VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    session_id VARCHAR(100),
    
    -- File processing context
    correlation_id VARCHAR(100),
    adapter_id UUID,
    execution_id UUID,
    file_name VARCHAR(255),
    
    -- Flexible additional data
    details JSONB,
    
    -- Performance tracking
    execution_time_ms BIGINT,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- =====================================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================================

-- Users table indexes
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_enabled ON users(enabled);
CREATE INDEX idx_users_created_at ON users(created_at);

-- User sessions indexes
CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_session_id ON user_sessions(session_id);
CREATE INDEX idx_user_sessions_active ON user_sessions(is_active);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);

-- User management errors indexes
CREATE INDEX idx_user_errors_error_type ON user_management_errors(error_type);
CREATE INDEX idx_user_errors_error_code ON user_management_errors(error_code);
CREATE INDEX idx_user_errors_ip_address ON user_management_errors(ip_address);
CREATE INDEX idx_user_errors_username ON user_management_errors(username);
CREATE INDEX idx_user_errors_threat_level ON user_management_errors(threat_level);
CREATE INDEX idx_user_errors_occurred_at ON user_management_errors(occurred_at);

-- System configuration indexes
CREATE INDEX idx_system_configuration_category ON system_configuration(category);
CREATE INDEX idx_system_configuration_key ON system_configuration(config_key);

-- SSH keys indexes
CREATE INDEX idx_ssh_keys_name ON ssh_keys(name);
CREATE INDEX idx_ssh_keys_active ON ssh_keys(active);
CREATE INDEX idx_ssh_keys_key_type ON ssh_keys(key_type);
CREATE INDEX idx_ssh_keys_created_at ON ssh_keys(created_at);
CREATE INDEX idx_ssh_keys_fingerprint ON ssh_keys(fingerprint);

-- PGP keys indexes
CREATE INDEX idx_pgp_keys_fingerprint ON pgp_keys(fingerprint);
CREATE INDEX idx_pgp_keys_key_id ON pgp_keys(key_id);
CREATE INDEX idx_pgp_keys_user_id ON pgp_keys(user_id);
CREATE INDEX idx_pgp_keys_created_by ON pgp_keys(created_by);
CREATE INDEX idx_pgp_keys_expires_at ON pgp_keys(expires_at);
CREATE INDEX idx_pgp_keys_last_used ON pgp_keys(last_used_at);

-- Adapters indexes
CREATE INDEX idx_adapters_name ON adapters(name);
CREATE INDEX idx_adapters_type_direction ON adapters(adapter_type, direction);
CREATE INDEX idx_adapters_status ON adapters(status);
CREATE INDEX idx_adapters_active ON adapters(active);
CREATE INDEX idx_adapters_status_active ON adapters(status, active);
CREATE INDEX idx_adapters_created_at ON adapters(created_at);
CREATE INDEX idx_adapters_bank ON adapters(bank);
CREATE INDEX idx_adapters_bank_type ON adapters(bank, adapter_type);

-- Flow utilities indexes
CREATE INDEX idx_flow_utilities_type ON flow_utilities(utility_type);
CREATE INDEX idx_flow_utilities_enabled ON flow_utilities(enabled);

-- Integration flows indexes
CREATE INDEX idx_integration_flows_active ON integration_flows(active);
CREATE INDEX idx_integration_flows_type ON integration_flows(flow_type);
CREATE INDEX idx_integration_flows_next_run ON integration_flows(next_scheduled_run);
CREATE INDEX idx_integration_flows_created_at ON integration_flows(created_at);
CREATE INDEX idx_integration_flows_bank_name ON integration_flows(bank_name);

-- Flow node connections indexes
CREATE INDEX idx_flow_node_connections_flow_id ON flow_node_connections(flow_id);
CREATE INDEX idx_flow_node_connections_source ON flow_node_connections(source_node_id);
CREATE INDEX idx_flow_node_connections_target ON flow_node_connections(target_node_id);

-- Flow notifications indexes
CREATE INDEX idx_flow_notifications_flow_id ON flow_notifications(flow_id);
CREATE INDEX idx_flow_notifications_type ON flow_notifications(notification_type);
CREATE INDEX idx_flow_notifications_enabled ON flow_notifications(enabled);

-- Deployed flows indexes
CREATE INDEX idx_deployed_flows_flow_id ON deployed_flows(flow_id);
CREATE INDEX idx_deployed_flows_sender_adapter ON deployed_flows(sender_adapter_id);
CREATE INDEX idx_deployed_flows_receiver_adapter ON deployed_flows(receiver_adapter_id);
CREATE INDEX idx_deployed_flows_deployment_status ON deployed_flows(deployment_status);
CREATE INDEX idx_deployed_flows_runtime_status ON deployed_flows(runtime_status);
CREATE INDEX idx_deployed_flows_execution_enabled ON deployed_flows(execution_enabled);
CREATE INDEX idx_deployed_flows_environment ON deployed_flows(deployment_environment);
CREATE INDEX idx_deployed_flows_deployed_at ON deployed_flows(deployed_at);
CREATE INDEX idx_deployed_flows_last_execution_at ON deployed_flows(last_execution_at);

-- Flow deployment operations indexes
CREATE INDEX idx_flow_deployment_ops_deployed_flow ON flow_deployment_operations(deployed_flow_id);
CREATE INDEX idx_flow_deployment_ops_flow_id ON flow_deployment_operations(flow_id);
CREATE INDEX idx_flow_deployment_ops_operation ON flow_deployment_operations(operation);
CREATE INDEX idx_flow_deployment_ops_status ON flow_deployment_operations(operation_status);
CREATE INDEX idx_flow_deployment_ops_started_at ON flow_deployment_operations(started_at);
CREATE INDEX idx_flow_deployment_ops_performed_by ON flow_deployment_operations(performed_by);

-- Flow deployment log indexes
CREATE INDEX idx_flow_deployment_log_flow_id ON flow_deployment_log(flow_id);
CREATE INDEX idx_flow_deployment_log_operation ON flow_deployment_log(operation);
CREATE INDEX idx_flow_deployment_log_status ON flow_deployment_log(operation_status);
CREATE INDEX idx_flow_deployment_log_started_at ON flow_deployment_log(started_at);
CREATE INDEX idx_flow_deployment_log_deployed_by ON flow_deployment_log(deployed_by);

-- Flow executions indexes
CREATE INDEX idx_flow_executions_flow_id ON flow_executions(flow_id);
CREATE INDEX idx_flow_executions_status ON flow_executions(execution_status);
CREATE INDEX idx_flow_executions_started_at ON flow_executions(started_at);
CREATE INDEX idx_flow_executions_correlation_id ON flow_executions(correlation_id);
CREATE INDEX idx_flow_executions_trigger_type ON flow_executions(trigger_type);
CREATE INDEX idx_flow_executions_priority ON flow_executions(priority);
CREATE INDEX idx_flow_executions_scheduled_for ON flow_executions(scheduled_for);

-- Flow execution steps indexes
CREATE INDEX idx_flow_execution_steps_execution_id ON flow_execution_steps(execution_id);
CREATE INDEX idx_flow_execution_steps_step_id ON flow_execution_steps(step_id);
CREATE INDEX idx_flow_execution_steps_status ON flow_execution_steps(step_status);
CREATE INDEX idx_flow_execution_steps_started_at ON flow_execution_steps(started_at);
CREATE INDEX idx_flow_execution_steps_correlation_id ON flow_execution_steps(correlation_id);

-- Processed files indexes
CREATE INDEX idx_processed_files_execution_id ON processed_files(execution_id);
CREATE INDEX idx_processed_files_file_name ON processed_files(file_name);
CREATE INDEX idx_processed_files_status ON processed_files(status);
CREATE INDEX idx_processed_files_created_at ON processed_files(created_at);
CREATE INDEX idx_processed_files_file_hash ON processed_files(file_hash);

-- System audit log indexes
CREATE INDEX idx_audit_log_event_type ON system_audit_log(event_type);
CREATE INDEX idx_audit_log_event_category ON system_audit_log(event_category);
CREATE INDEX idx_audit_log_user_id ON system_audit_log(user_id);
CREATE INDEX idx_audit_log_username ON system_audit_log(username);
CREATE INDEX idx_audit_log_created_at ON system_audit_log(created_at);
CREATE INDEX idx_audit_log_success ON system_audit_log(success);
CREATE INDEX idx_audit_log_resource_type ON system_audit_log(resource_type);

-- System logs indexes
CREATE INDEX idx_system_logs_timestamp ON system_logs(timestamp);
CREATE INDEX idx_system_logs_level ON system_logs(log_level);
CREATE INDEX idx_system_logs_category ON system_logs(log_category);
CREATE INDEX idx_system_logs_logger ON system_logs(logger_name);
CREATE INDEX idx_system_logs_correlation_id ON system_logs(correlation_id);
CREATE INDEX idx_system_logs_user_id ON system_logs(user_id);
CREATE INDEX idx_system_logs_adapter_id ON system_logs(adapter_id);
CREATE INDEX idx_system_logs_flow_id ON system_logs(flow_id);
CREATE INDEX idx_system_logs_execution_id ON system_logs(execution_id);
CREATE INDEX idx_system_logs_marker ON system_logs(marker);
CREATE INDEX idx_system_logs_environment ON system_logs(environment);

-- Composite indexes for system logs
CREATE INDEX idx_system_logs_level_category ON system_logs(log_level, log_category);
CREATE INDEX idx_system_logs_timestamp_level ON system_logs(timestamp, log_level);
CREATE INDEX idx_system_logs_adapter_timestamp ON system_logs(adapter_id, timestamp);
CREATE INDEX idx_system_logs_flow_timestamp ON system_logs(flow_id, timestamp);

-- Partial indexes for system logs (performance optimization)
CREATE INDEX idx_system_logs_errors_only ON system_logs(timestamp, logger_name) 
    WHERE log_level IN ('ERROR', 'FATAL');
CREATE INDEX idx_system_logs_with_exceptions ON system_logs(timestamp, exception_class) 
    WHERE exception_class IS NOT NULL;

-- Transaction logs indexes
CREATE INDEX idx_transaction_logs_timestamp ON transaction_logs(timestamp);
CREATE INDEX idx_transaction_logs_level ON transaction_logs(level);
CREATE INDEX idx_transaction_logs_category ON transaction_logs(category);
CREATE INDEX idx_transaction_logs_component ON transaction_logs(component);
CREATE INDEX idx_transaction_logs_source ON transaction_logs(source);
CREATE INDEX idx_transaction_logs_username ON transaction_logs(username);
CREATE INDEX idx_transaction_logs_ip_address ON transaction_logs(ip_address);
CREATE INDEX idx_transaction_logs_correlation_id ON transaction_logs(correlation_id);
CREATE INDEX idx_transaction_logs_adapter_id ON transaction_logs(adapter_id);
CREATE INDEX idx_transaction_logs_execution_id ON transaction_logs(execution_id);

-- =====================================================================
-- FUNCTIONS AND TRIGGERS
-- =====================================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Function to check if a flow can execute (using sender/receiver terminology)
CREATE OR REPLACE FUNCTION can_flow_execute(flow_id_param UUID)
RETURNS TABLE (
    can_execute BOOLEAN,
    deployment_id UUID,
    runtime_status VARCHAR(20),
    adapters_ready BOOLEAN,
    reason TEXT
) AS $$
DECLARE
    deployment_record RECORD;
    adapter_enabled BOOLEAN;
BEGIN
    -- Get deployment record using new column names
    SELECT df.*, a.active as sender_active
    INTO deployment_record
    FROM deployed_flows df
    LEFT JOIN adapters a ON a.id = df.sender_adapter_id
    WHERE df.flow_id = flow_id_param 
    AND df.deployment_status = 'DEPLOYED' 
    AND df.execution_enabled = true;

    -- If no deployment found
    IF NOT FOUND THEN
        RETURN QUERY SELECT false, NULL::UUID, 'NOT_DEPLOYED'::VARCHAR(20), false, 'Flow is not deployed or execution is disabled'::TEXT;
        RETURN;
    END IF;

    -- Check runtime status
    IF deployment_record.runtime_status != 'ACTIVE' THEN
        RETURN QUERY SELECT false, deployment_record.id, deployment_record.runtime_status, false,
            'Flow runtime status is ' || deployment_record.runtime_status;
        RETURN;
    END IF;

    -- Check sender adapter status (use active instead of enabled)
    IF deployment_record.sender_active IS NULL OR deployment_record.sender_active = false THEN
        RETURN QUERY SELECT false, deployment_record.id, deployment_record.runtime_status, false,
            'Sender adapter is not active: ' || COALESCE(deployment_record.sender_active::text, 'NULL');
        RETURN;
    END IF;

    -- All checks passed
    RETURN QUERY SELECT true, deployment_record.id, deployment_record.runtime_status, true, NULL::TEXT;
END;
$$ LANGUAGE plpgsql;

-- Apply timestamp triggers to all tables
CREATE TRIGGER trigger_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER trigger_adapters_updated_at BEFORE UPDATE ON adapters FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER trigger_system_configuration_updated_at BEFORE UPDATE ON system_configuration FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER trigger_ssh_keys_updated_at BEFORE UPDATE ON ssh_keys FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER trigger_pgp_keys_updated_at BEFORE UPDATE ON pgp_keys FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER trigger_integration_flows_updated_at BEFORE UPDATE ON integration_flows FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER trigger_deployed_flows_updated_at BEFORE UPDATE ON deployed_flows FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER trigger_user_sessions_updated_at BEFORE UPDATE ON user_sessions FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- =====================================================================
-- VIEWS FOR COMMON QUERIES
-- =====================================================================

-- View for active deployed flows with adapter status
CREATE VIEW active_deployed_flows AS
SELECT 
    df.*,
    sa.name AS sender_adapter_name_actual,
    sa.status AS sender_adapter_status,
    sa.active AS sender_adapter_active,
    ra.name AS receiver_adapter_name_actual,
    ra.status AS receiver_adapter_status,
    ra.active AS receiver_adapter_active
FROM deployed_flows df
JOIN adapters sa ON df.sender_adapter_id = sa.id
LEFT JOIN adapters ra ON df.receiver_adapter_id = ra.id
WHERE df.deployment_status = 'DEPLOYED'
  AND df.execution_enabled = true
  AND df.runtime_status = 'ACTIVE';

-- View for deployment status summary
CREATE VIEW deployment_status_summary AS
SELECT 
    deployment_status,
    deployment_environment,
    COUNT(*) AS flow_count,
    COUNT(CASE WHEN execution_enabled THEN 1 END) AS execution_enabled_count,
    COUNT(CASE WHEN runtime_status = 'ACTIVE' THEN 1 END) AS active_count
FROM deployed_flows
GROUP BY deployment_status, deployment_environment;

-- =====================================================================
-- GRANTS AND PERMISSIONS
-- =====================================================================

-- Grant permissions to application user
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO integrix;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO integrix;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO integrix;

-- =====================================================================
-- SCHEMA MIGRATION COMPLETE
-- =====================================================================
-- This consolidated migration creates the complete H2H File Transfer
-- system database schema with all tables, indexes, functions, triggers,
-- and views from the original migrations V001-V030.
-- 
-- Key features:
-- - Complete user and authentication system
-- - System configuration management
-- - SSH and PGP key management
-- - Adapter configuration and monitoring
-- - Flow-based data processing architecture
-- - Deployment management with sender/receiver terminology
-- - Comprehensive audit trails and logging
-- - Performance monitoring and optimization
-- =====================================================================