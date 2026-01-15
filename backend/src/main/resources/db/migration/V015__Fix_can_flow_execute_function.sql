-- V015: Fix can_flow_execute function to use package_adapters table instead of adapters

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
    -- Get deployment record using package_adapters table
    SELECT df.*, a.active as sender_active
    INTO deployment_record
    FROM deployed_flows df
    LEFT JOIN package_adapters a ON a.id = df.sender_adapter_id
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
