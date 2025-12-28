-- Test schema for wallets table (used in ledger service tests)
-- This allows ledger service tests to query wallet limits without creating a JPA entity
-- Note: Simplified for tests - no foreign key constraints, no unique constraints

CREATE TABLE IF NOT EXISTS wallets (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    daily_limit NUMERIC(19, 2) NOT NULL DEFAULT 100000.00,
    monthly_limit NUMERIC(19, 2) NOT NULL DEFAULT 1000000.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wallets_customer_id ON wallets(customer_id);
CREATE INDEX IF NOT EXISTS idx_wallets_status ON wallets(status);
CREATE INDEX IF NOT EXISTS idx_wallets_currency ON wallets(currency);

