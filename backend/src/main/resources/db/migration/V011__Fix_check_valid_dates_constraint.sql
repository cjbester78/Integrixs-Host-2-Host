-- Fix check_valid_dates constraint to allow NULL updated_at for new records
-- This properly implements audit logging best practices where updated_at is only set on updates

-- Drop the existing constraint
ALTER TABLE integration_packages DROP CONSTRAINT IF EXISTS check_valid_dates;

-- Fix the updated_at column: remove NOT NULL constraint and DEFAULT value
-- This ensures updated_at is only set on UPDATE operations, not INSERT
ALTER TABLE integration_packages ALTER COLUMN updated_at DROP NOT NULL;
ALTER TABLE integration_packages ALTER COLUMN updated_at DROP DEFAULT;

-- Add the corrected constraint that allows NULL updated_at (for INSERT operations)
-- or requires updated_at >= created_at (for UPDATE operations)
ALTER TABLE integration_packages ADD CONSTRAINT check_valid_dates
    CHECK (updated_at IS NULL OR updated_at >= created_at);

-- Add comment explaining the audit logging pattern
COMMENT ON CONSTRAINT check_valid_dates ON integration_packages IS
    'Ensures updated_at is either NULL (for new records) or >= created_at (for updated records). This follows proper audit logging practices.';