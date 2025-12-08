-- Create transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_type VARCHAR(20) NOT NULL, -- DEPOSIT, WITHDRAWAL, TRANSFER
    amount DECIMAL(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    from_wallet_id BIGINT REFERENCES wallets(id),
    to_wallet_id BIGINT REFERENCES wallets(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, FAILED, CANCELLED
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    failure_reason TEXT,
    idempotency_key VARCHAR(255) UNIQUE, -- For idempotent operations
    metadata JSONB, -- Additional transaction metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_transfer_wallets CHECK (
        (transaction_type = 'TRANSFER' AND from_wallet_id IS NOT NULL AND to_wallet_id IS NOT NULL) OR
        (transaction_type = 'DEPOSIT' AND to_wallet_id IS NOT NULL AND from_wallet_id IS NULL) OR
        (transaction_type = 'WITHDRAWAL' AND from_wallet_id IS NOT NULL AND to_wallet_id IS NULL)
    )
);

-- Create indexes for transactions table
CREATE INDEX idx_transactions_from_wallet ON transactions(from_wallet_id);
CREATE INDEX idx_transactions_to_wallet ON transactions(to_wallet_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_initiated_at ON transactions(initiated_at DESC);
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);
CREATE INDEX idx_transactions_customer_lookup ON transactions(from_wallet_id, to_wallet_id, initiated_at DESC);

-- Add comment to table
COMMENT ON TABLE transactions IS 'Transaction records for deposits, withdrawals, and transfers';

