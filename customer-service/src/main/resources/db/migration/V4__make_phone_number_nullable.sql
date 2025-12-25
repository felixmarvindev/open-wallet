-- Migration: Make phone_number nullable to support partial customer data during onboarding
-- This allows customers to be created without a phone number, which will be set later
-- during profile completion.

-- Step 1: Update existing placeholder phone numbers to NULL
-- This handles any customers that were created with the placeholder "+254000000000"
UPDATE customers 
SET phone_number = NULL 
WHERE phone_number = '+254000000000';

-- Step 2: Alter the column to allow NULL values
-- PostgreSQL UNIQUE constraint allows multiple NULL values by default, so this is safe
ALTER TABLE customers 
ALTER COLUMN phone_number DROP NOT NULL;

-- Add comment explaining the change
COMMENT ON COLUMN customers.phone_number IS 'Customer phone number. NULL allowed during initial registration, required when user completes profile.';

