-- =====================================================================
-- Remove Package Type, Tags, and Soft Delete Columns - Migration V010
-- =====================================================================
-- This migration removes package_type, tags columns and soft delete
-- functionality from integration_packages table as requested by user.
-- All packages will be treated as integration packages with hard deletes.
-- 
-- Date: 2026-01-09
-- Description: Remove package_type, tags, and soft delete columns
-- =====================================================================

-- =====================================================================
-- REMOVE PACKAGE TYPE AND TAGS COLUMNS
-- =====================================================================

-- Remove package_type column and associated constraints
ALTER TABLE integration_packages DROP COLUMN IF EXISTS package_type CASCADE;

-- Remove tags column 
ALTER TABLE integration_packages DROP COLUMN IF EXISTS tags CASCADE;

-- =====================================================================
-- REMOVE SOFT DELETE COLUMNS AND FUNCTIONALITY
-- =====================================================================

-- Remove soft delete columns
ALTER TABLE integration_packages DROP COLUMN IF EXISTS deleted_at CASCADE;
ALTER TABLE integration_packages DROP COLUMN IF EXISTS deleted_by CASCADE;
ALTER TABLE integration_packages DROP COLUMN IF EXISTS deletion_reason CASCADE;

-- =====================================================================
-- UPDATE INDEXES TO REMOVE REFERENCES TO DELETED COLUMNS
-- =====================================================================

-- Drop indexes that reference removed columns
DROP INDEX IF EXISTS idx_integration_packages_type;
DROP INDEX IF EXISTS idx_integration_packages_deleted_at;
DROP INDEX IF EXISTS idx_integration_packages_active; -- This had deleted_at filter

-- Recreate active packages index without soft delete filter
CREATE INDEX idx_integration_packages_active ON integration_packages(name, status) 
    WHERE status IN ('DRAFT', 'ACTIVE');

-- =====================================================================
-- UPDATE VIEWS TO REMOVE REFERENCES TO DELETED COLUMNS
-- =====================================================================

-- Update package_summary view to remove references to deleted columns
DROP VIEW IF EXISTS package_summary CASCADE;

-- Recreate package_summary view without package_type and deleted_at references
CREATE VIEW package_summary AS
SELECT 
    p.id,
    p.name,
    p.description,
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
GROUP BY p.id, p.name, p.description, p.status, p.created_at, p.created_by, u.username;

-- =====================================================================
-- UPDATE FUNCTIONS TO REMOVE SOFT DELETE CHECKS
-- =====================================================================

-- Update the flow execution check function to remove package soft delete checks
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
    -- Get deployment record with package information (no soft delete checks)
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

    -- Check package status (removed soft delete check)
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
-- CLEANUP CONSTRAINTS THAT REFERENCE DELETED COLUMNS
-- =====================================================================

-- Remove constraints that might reference deleted columns
ALTER TABLE integration_packages DROP CONSTRAINT IF EXISTS check_not_deleted_or_named;

-- =====================================================================
-- MIGRATION COMPLETE
-- =====================================================================
-- This migration successfully removes package management complexity:
-- 
-- 1. Removed package_type column - all packages are now integration packages
-- 2. Removed tags column - no more tagging system
-- 3. Removed soft delete functionality (deleted_at, deleted_by, deletion_reason)
-- 4. Updated indexes to remove references to deleted columns
-- 5. Updated views to work without removed columns
-- 6. Updated functions to remove soft delete checks
-- 7. Cleaned up constraints that referenced deleted columns
-- 
-- All packages now use hard deletes and are treated as integration packages.
-- This simplifies the package management system as requested.
-- =====================================================================