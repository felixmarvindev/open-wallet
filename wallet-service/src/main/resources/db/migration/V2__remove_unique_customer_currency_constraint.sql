-- Remove unique constraint on (customer_id, currency) to allow multiple wallets with same currency
-- This enables customers to have multiple KES wallets (e.g., for different purposes)
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS unique_customer_currency;

-- Add comment explaining the change
COMMENT ON TABLE wallets IS 'Customer wallets with balance and transaction limits. Customers can have multiple wallets with the same currency.';

