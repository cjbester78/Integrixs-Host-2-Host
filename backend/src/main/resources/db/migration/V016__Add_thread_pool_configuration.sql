-- =====================================================
-- Migration: V016__Add_thread_pool_configuration.sql
-- Description: Add configurable thread pool settings for adapter, flow, and monitoring executors
-- Author: System
-- Date: 2026-01-12
-- =====================================================

-- Add thread pool configuration for adapter executor
-- These settings control concurrent adapter execution
INSERT INTO system_configuration (config_key, config_value, config_type, description, category, is_encrypted, is_readonly, default_value) VALUES
('thread.pool.adapter.core.size', '20', 'INTEGER', 'Adapter thread pool core size - minimum threads always alive', 'PERFORMANCE', false, false, '20'),
('thread.pool.adapter.max.size', '50', 'INTEGER', 'Adapter thread pool maximum size - maximum concurrent adapter executions', 'PERFORMANCE', false, false, '50'),
('thread.pool.adapter.queue.capacity', '200', 'INTEGER', 'Adapter thread pool queue capacity - pending tasks before rejection', 'PERFORMANCE', false, false, '200')
ON CONFLICT (config_key) DO NOTHING;

-- Add thread pool configuration for flow executor
-- These settings control concurrent flow execution
INSERT INTO system_configuration (config_key, config_value, config_type, description, category, is_encrypted, is_readonly, default_value) VALUES
('thread.pool.flow.core.size', '15', 'INTEGER', 'Flow execution thread pool core size', 'PERFORMANCE', false, false, '15'),
('thread.pool.flow.max.size', '30', 'INTEGER', 'Flow execution thread pool maximum size - maximum concurrent flow executions', 'PERFORMANCE', false, false, '30'),
('thread.pool.flow.queue.capacity', '150', 'INTEGER', 'Flow execution thread pool queue capacity', 'PERFORMANCE', false, false, '150')
ON CONFLICT (config_key) DO NOTHING;

-- Add thread pool configuration for primary executor
-- These settings control general async operations
INSERT INTO system_configuration (config_key, config_value, config_type, description, category, is_encrypted, is_readonly, default_value) VALUES
('thread.pool.primary.core.size', '10', 'INTEGER', 'Primary thread pool core size', 'PERFORMANCE', false, false, '10'),
('thread.pool.primary.max.size', '25', 'INTEGER', 'Primary thread pool maximum size', 'PERFORMANCE', false, false, '25'),
('thread.pool.primary.queue.capacity', '100', 'INTEGER', 'Primary thread pool queue capacity', 'PERFORMANCE', false, false, '100')
ON CONFLICT (config_key) DO NOTHING;

-- Add thread pool configuration for monitoring executor
-- These settings control monitoring and health check operations
INSERT INTO system_configuration (config_key, config_value, config_type, description, category, is_encrypted, is_readonly, default_value) VALUES
('thread.pool.monitoring.core.size', '5', 'INTEGER', 'Monitoring thread pool core size', 'PERFORMANCE', false, false, '5'),
('thread.pool.monitoring.max.size', '10', 'INTEGER', 'Monitoring thread pool maximum size', 'PERFORMANCE', false, false, '10'),
('thread.pool.monitoring.queue.capacity', '50', 'INTEGER', 'Monitoring thread pool queue capacity', 'PERFORMANCE', false, false, '50')
ON CONFLICT (config_key) DO NOTHING;

-- Add comment explaining thread pool sizing
COMMENT ON TABLE system_configuration IS 'System-wide configuration settings. Thread pool settings control concurrent execution and system performance.';
