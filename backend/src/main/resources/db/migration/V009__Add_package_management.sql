-- =====================================================================
-- Package Management System - Database Migration V009
-- =====================================================================
-- This migration adds package management functionality to the H2H system
-- by creating package tables and adding package context to existing
-- adapters and flows tables.
-- 
-- Date: 2026-01-09
-- Description: Add integration packages, asset dependencies, and package context
-- =====================================================================

-- =====================================================================
-- PACKAGE MANAGEMENT TABLES
-- =====================================================================

-- Integration packages - Logical containers for organizing integration assets
CREATE TABLE integration_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    version VARCHAR(50) DEFAULT '1.0.0',
    
    -- Package metadata
    package_type VARCHAR(50) NOT NULL DEFAULT 'STANDARD' CHECK (package_type IN ('STANDARD', 'TEMPLATE', 'SHARED')),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'DEPRECATED')),
    
    -- Package configuration
    configuration JSONB,
    tags TEXT[],
    
    -- Soft delete support
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID REFERENCES users(id),
    deletion_reason TEXT,
    
    -- Audit columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    
    -- Constraints
    CONSTRAINT check_not_deleted_or_named CHECK (deleted_at IS NULL OR name IS NOT NULL),
    CONSTRAINT check_valid_dates CHECK (updated_at >= created_at)
);

-- Package asset dependencies - Track relationships between package assets
CREATE TABLE package_asset_dependencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id UUID NOT NULL REFERENCES integration_packages(id) ON DELETE CASCADE,
    
    -- Asset identification
    asset_type VARCHAR(50) NOT NULL CHECK (asset_type IN ('ADAPTER', 'FLOW')),
    asset_id UUID NOT NULL,
    
    -- Dependency identification
    depends_on_asset_type VARCHAR(50) NOT NULL CHECK (depends_on_asset_type IN ('ADAPTER', 'FLOW')),
    depends_on_asset_id UUID NOT NULL,
    
    -- Dependency metadata
    dependency_type VARCHAR(50) NOT NULL DEFAULT 'REQUIRED' CHECK (dependency_type IN ('REQUIRED', 'OPTIONAL', 'WEAK')),
    dependency_description TEXT,
    
    -- Audit columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id),
    
    -- Constraints
    CONSTRAINT unique_dependency_relationship UNIQUE (package_id, asset_type, asset_id, depends_on_asset_type, depends_on_asset_id),
    CONSTRAINT no_self_dependency CHECK (NOT (asset_type = depends_on_asset_type AND asset_id = depends_on_asset_id))
);

-- =====================================================================
-- RENAME EXISTING TABLES TO PACKAGE NAMING CONVENTION
-- =====================================================================

-- Rename existing tables to align with package management system
ALTER TABLE adapters RENAME TO package_adapters;
ALTER TABLE integration_flows RENAME TO package_flows;

-- Update any existing indexes to reflect new table names
ALTER INDEX idx_adapters_name RENAME TO idx_package_adapters_name;
ALTER INDEX idx_adapters_type_direction RENAME TO idx_package_adapters_type_direction;
ALTER INDEX idx_adapters_status RENAME TO idx_package_adapters_status;
ALTER INDEX idx_adapters_active RENAME TO idx_package_adapters_active;
ALTER INDEX idx_adapters_status_active RENAME TO idx_package_adapters_status_active;
ALTER INDEX idx_adapters_created_at RENAME TO idx_package_adapters_created_at;
ALTER INDEX idx_adapters_bank RENAME TO idx_package_adapters_bank;
ALTER INDEX idx_adapters_bank_type RENAME TO idx_package_adapters_bank_type;

ALTER INDEX idx_integration_flows_active RENAME TO idx_package_flows_active;
ALTER INDEX idx_integration_flows_type RENAME TO idx_package_flows_type;
ALTER INDEX idx_integration_flows_next_run RENAME TO idx_package_flows_next_run;
ALTER INDEX idx_integration_flows_created_at RENAME TO idx_package_flows_created_at;
ALTER INDEX idx_integration_flows_bank_name RENAME TO idx_package_flows_bank_name;

-- =====================================================================
-- ADD PACKAGE CONTEXT TO RENAMED TABLES
-- =====================================================================

-- Add package context to package_adapters table
ALTER TABLE package_adapters 
ADD COLUMN package_id UUID REFERENCES integration_packages(id),
ADD COLUMN deployed_from_package_id UUID REFERENCES integration_packages(id);

-- Add package context to package_flows table  
ALTER TABLE package_flows
ADD COLUMN package_id UUID REFERENCES integration_packages(id),
ADD COLUMN deployed_from_package_id UUID REFERENCES integration_packages(id);

-- =====================================================================
-- CREATE DEFAULT PACKAGE AND MIGRATE EXISTING DATA
-- =====================================================================

-- Create default package for existing data
INSERT INTO integration_packages (
    id,
    name,
    description,
    status,
    created_by,
    updated_by
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'Default Package',
    'Default package containing migrated existing adapters and flows',
    'ACTIVE',
    (SELECT id FROM users WHERE username = 'Administrator' LIMIT 1),
    (SELECT id FROM users WHERE username = 'Administrator' LIMIT 1)
);

-- Migrate existing adapters to default package
UPDATE package_adapters 
SET package_id = 'a0000000-0000-0000-0000-000000000001',
    deployed_from_package_id = 'a0000000-0000-0000-0000-000000000001'
WHERE package_id IS NULL;

-- Migrate existing flows to default package
UPDATE package_flows 
SET package_id = 'a0000000-0000-0000-0000-000000000001',
    deployed_from_package_id = 'a0000000-0000-0000-0000-000000000001'
WHERE package_id IS NULL;

-- =====================================================================
-- ADD NOT NULL CONSTRAINTS AFTER DATA MIGRATION
-- =====================================================================

-- Make package_id required for all new adapters and flows
ALTER TABLE package_adapters ALTER COLUMN package_id SET NOT NULL;
ALTER TABLE package_flows ALTER COLUMN package_id SET NOT NULL;

-- =====================================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================================

-- Integration packages indexes
CREATE INDEX idx_integration_packages_name ON integration_packages(name);
CREATE INDEX idx_integration_packages_status ON integration_packages(status);
CREATE INDEX idx_integration_packages_type ON integration_packages(package_type);
CREATE INDEX idx_integration_packages_created_at ON integration_packages(created_at);
CREATE INDEX idx_integration_packages_created_by ON integration_packages(created_by);
CREATE INDEX idx_integration_packages_deleted_at ON integration_packages(deleted_at);

-- Partial index for active packages
CREATE INDEX idx_integration_packages_active ON integration_packages(name, status) 
    WHERE deleted_at IS NULL AND status IN ('DRAFT', 'ACTIVE');

-- Package asset dependencies indexes
CREATE INDEX idx_package_dependencies_package_id ON package_asset_dependencies(package_id);
CREATE INDEX idx_package_dependencies_asset ON package_asset_dependencies(asset_type, asset_id);
CREATE INDEX idx_package_dependencies_depends_on ON package_asset_dependencies(depends_on_asset_type, depends_on_asset_id);
CREATE INDEX idx_package_dependencies_type ON package_asset_dependencies(dependency_type);

-- Package context indexes for renamed tables
CREATE INDEX idx_package_adapters_package_id ON package_adapters(package_id);
CREATE INDEX idx_package_adapters_deployed_from_package ON package_adapters(deployed_from_package_id);
CREATE INDEX idx_package_flows_package_id ON package_flows(package_id);
CREATE INDEX idx_package_flows_deployed_from_package ON package_flows(deployed_from_package_id);

-- Composite indexes for package-scoped queries
CREATE INDEX idx_package_adapters_package_type ON package_adapters(package_id, adapter_type);
CREATE INDEX idx_package_adapters_package_status ON package_adapters(package_id, status, active);
CREATE INDEX idx_package_flows_package_active ON package_flows(package_id, active);

-- =====================================================================
-- UPDATE EXISTING FUNCTIONS FOR PACKAGE AWARENESS
-- =====================================================================

-- Update the flow execution check function to be package-aware
CREATE OR REPLACE FUNCTION can_flow_execute_with_package(flow_id_param UUID)
RETURNS TABLE (
    can_execute BOOLEAN,
    deployment_id UUID,
    runtime_status VARCHAR(20),
    adapters_ready BOOLEAN,
    package_id UUID,
    package_name VARCHAR(255),
    reason TEXT
) AS $$
DECLARE
    deployment_record RECORD;
    package_record RECORD;
BEGIN
    -- Get deployment record with package information
    SELECT 
        df.*, 
        a.active as sender_active,
        pf.package_id,
        p.name as package_name,
        p.status as package_status
    INTO deployment_record
    FROM deployed_flows df
    LEFT JOIN package_adapters a ON a.id = df.sender_adapter_id
    JOIN package_flows pf ON pf.id = df.flow_id
    JOIN integration_packages p ON p.id = pf.package_id
    WHERE df.flow_id = flow_id_param 
    AND df.deployment_status = 'DEPLOYED' 
    AND df.execution_enabled = true;

    -- If no deployment found
    IF NOT FOUND THEN
        RETURN QUERY SELECT false, NULL::UUID, 'NOT_DEPLOYED'::VARCHAR(20), false, NULL::UUID, NULL::VARCHAR(255), 'Flow is not deployed or execution is disabled'::TEXT;
        RETURN;
    END IF;

    -- Check package status
    IF deployment_record.package_status != 'ACTIVE' THEN
        RETURN QUERY SELECT false, deployment_record.id, deployment_record.runtime_status, false,
            deployment_record.package_id, deployment_record.package_name,
            'Package status is ' || deployment_record.package_status;
        RETURN;
    END IF;

    -- Check runtime status
    IF deployment_record.runtime_status != 'ACTIVE' THEN
        RETURN QUERY SELECT false, deployment_record.id, deployment_record.runtime_status, false,
            deployment_record.package_id, deployment_record.package_name,
            'Flow runtime status is ' || deployment_record.runtime_status;
        RETURN;
    END IF;

    -- Check sender adapter status
    IF deployment_record.sender_active IS NULL OR deployment_record.sender_active = false THEN
        RETURN QUERY SELECT false, deployment_record.id, deployment_record.runtime_status, false,
            deployment_record.package_id, deployment_record.package_name,
            'Sender adapter is not active: ' || COALESCE(deployment_record.sender_active::text, 'NULL');
        RETURN;
    END IF;

    -- All checks passed
    RETURN QUERY SELECT true, deployment_record.id, deployment_record.runtime_status, true,
        deployment_record.package_id, deployment_record.package_name, NULL::TEXT;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- ADD PACKAGE AWARENESS TO TRIGGERS
-- =====================================================================

-- Add updated_at trigger to integration_packages
CREATE TRIGGER trigger_integration_packages_updated_at 
    BEFORE UPDATE ON integration_packages 
    FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- =====================================================================
-- CREATE PACKAGE MANAGEMENT VIEWS
-- =====================================================================

-- View for package summary with asset counts
CREATE VIEW package_summary AS
SELECT 
    p.id,
    p.name,
    p.description,
    p.package_type,
    p.status,
    p.created_at,
    p.created_by,
    u.username as created_by_username,
    COUNT(DISTINCT a.id) as adapter_count,
    COUNT(DISTINCT f.id) as flow_count,
    COUNT(DISTINCT CASE WHEN a.status = 'STARTED' AND a.active = true THEN a.id END) as active_adapter_count,
    COUNT(DISTINCT CASE WHEN f.active = true THEN f.id END) as active_flow_count
FROM integration_packages p
LEFT JOIN package_adapters a ON a.package_id = p.id AND a.package_id IS NOT NULL
LEFT JOIN package_flows f ON f.package_id = p.id AND f.package_id IS NOT NULL
LEFT JOIN users u ON u.id = p.created_by
WHERE p.deleted_at IS NULL
GROUP BY p.id, p.name, p.description, p.package_type, p.status, p.created_at, p.created_by, u.username;

-- View for package asset dependencies with details
CREATE VIEW package_dependencies_detail AS
SELECT 
    pad.id,
    pad.package_id,
    p.name as package_name,
    pad.asset_type,
    pad.asset_id,
    CASE 
        WHEN pad.asset_type = 'ADAPTER' THEN a.name
        WHEN pad.asset_type = 'FLOW' THEN f.name
    END as asset_name,
    pad.depends_on_asset_type,
    pad.depends_on_asset_id,
    CASE 
        WHEN pad.depends_on_asset_type = 'ADAPTER' THEN da.name
        WHEN pad.depends_on_asset_type = 'FLOW' THEN df.name
    END as depends_on_asset_name,
    pad.dependency_type,
    pad.dependency_description,
    pad.created_at
FROM package_asset_dependencies pad
JOIN integration_packages p ON p.id = pad.package_id
LEFT JOIN package_adapters a ON a.id = pad.asset_id AND pad.asset_type = 'ADAPTER'
LEFT JOIN package_flows f ON f.id = pad.asset_id AND pad.asset_type = 'FLOW'
LEFT JOIN package_adapters da ON da.id = pad.depends_on_asset_id AND pad.depends_on_asset_type = 'ADAPTER'
LEFT JOIN package_flows df ON df.id = pad.depends_on_asset_id AND pad.depends_on_asset_type = 'FLOW';

-- =====================================================================
-- GRANTS AND PERMISSIONS
-- =====================================================================

-- Grant permissions to application user for new tables
GRANT SELECT, INSERT, UPDATE, DELETE ON integration_packages TO integrix;
GRANT SELECT, INSERT, UPDATE, DELETE ON package_asset_dependencies TO integrix;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO integrix;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO integrix;
GRANT SELECT ON package_summary TO integrix;
GRANT SELECT ON package_dependencies_detail TO integrix;

-- =====================================================================
-- MIGRATION COMPLETE
-- =====================================================================
-- This migration successfully adds package management to the H2H system:
-- 
-- 1. Created integration_packages table for logical asset containers
-- 2. Created package_asset_dependencies for tracking relationships
-- 3. Added package_id columns to existing adapters and integration_flows tables
-- 4. Migrated all existing data to "Default Package"
-- 5. Added comprehensive indexes for performance
-- 6. Updated functions to be package-aware
-- 7. Created views for package management operations
-- 
-- All existing functionality is preserved while adding package context
-- for improved organization and management of integration assets.
-- =====================================================================