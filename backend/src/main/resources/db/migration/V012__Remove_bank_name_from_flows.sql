-- Remove bank_name column from package_flows table
-- This field is no longer needed as flows are associated with packages

ALTER TABLE package_flows DROP COLUMN IF EXISTS bank_name;
