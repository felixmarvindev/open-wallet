-- Create ledger_entries table
CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    wallet_id BIGINT REFERENCES wallets(id),
    account_type VARCHAR(50) NOT NULL, -- WALLET_{walletId}, CASH_ACCOUNT, FEE_ACCOUNT
    entry_type VARCHAR(10) NOT NULL, -- DEBIT, CREDIT
    amount DECIMAL(19, 2) NOT NULL CHECK (amount > 0),
    balance_after DECIMAL(19, 2) NOT NULL, -- Running balance after this entry
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT'))
);

-- Create indexes for ledger_entries table
CREATE INDEX idx_ledger_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_wallet_id ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_account_type ON ledger_entries(account_type);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at DESC);

-- Add comment to table
COMMENT ON TABLE ledger_entries IS 'Double-entry ledger records for all financial transactions';

