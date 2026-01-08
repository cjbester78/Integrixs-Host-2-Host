-- Add original_flow_id column to track imported flows
ALTER TABLE integration_flows 
ADD COLUMN original_flow_id UUID;

-- Add index for faster lookup of imported flows
CREATE INDEX IF NOT EXISTS idx_integration_flows_original_flow_id 
ON integration_flows(original_flow_id);

-- Add comment for clarity
COMMENT ON COLUMN integration_flows.original_flow_id IS 'UUID of the original flow when this flow was imported from another system';