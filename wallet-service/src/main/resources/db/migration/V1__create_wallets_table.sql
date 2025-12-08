-- Create wallets table
CREATE TABLE IF NOT EXISTS wallets (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES', -- ISO currency code
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, CLOSED
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    daily_limit DECIMAL(19, 2) NOT NULL DEFAULT 100000.00,
    monthly_limit DECIMAL(19, 2) NOT NULL DEFAULT 1000000.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_customer_currency UNIQUE (customer_id, currency)
);

-- Create indexes for wallets table
CREATE INDEX idx_wallets_customer_id ON wallets(customer_id);
CREATE INDEX idx_wallets_status ON wallets(status);
CREATE INDEX idx_wallets_currency ON wallets(currency);

-- Add comment to table
COMMENT ON TABLE wallets IS 'Customer wallets with balance and transaction limits';

