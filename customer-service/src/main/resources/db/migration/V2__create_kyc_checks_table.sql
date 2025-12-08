-- Create kyc_checks table
CREATE TABLE IF NOT EXISTS kyc_checks (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, IN_PROGRESS, VERIFIED, REJECTED
    provider_reference VARCHAR(255),
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,
    rejection_reason TEXT,
    documents JSONB, -- Store document metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for kyc_checks table
CREATE INDEX idx_kyc_customer_id ON kyc_checks(customer_id);
CREATE INDEX idx_kyc_status ON kyc_checks(status);
CREATE UNIQUE INDEX idx_kyc_customer_latest ON kyc_checks(customer_id, created_at DESC);

-- Add comment to table
COMMENT ON TABLE kyc_checks IS 'KYC verification records for customers';

