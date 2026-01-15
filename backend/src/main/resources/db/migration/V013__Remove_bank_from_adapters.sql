-- Remove bank column from package_adapters table
-- Bank is no longer a required field for adapters

ALTER TABLE package_adapters DROP COLUMN IF EXISTS bank;
