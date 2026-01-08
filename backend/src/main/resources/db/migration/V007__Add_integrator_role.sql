-- Add INTEGRATOR role to user role check constraint
-- This migration allows the INTEGRATOR role for automated flow execution

-- Drop the existing role check constraint
ALTER TABLE users DROP CONSTRAINT users_role_check;

-- Add the new constraint with INTEGRATOR role included
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMINISTRATOR', 'VIEWER', 'INTEGRATOR'));