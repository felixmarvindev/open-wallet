-- Create customer_user_mapping table for fast userId -> customerId lookups
CREATE TABLE
IF NOT EXISTS customer_user_mapping
(
    user_id VARCHAR
(255) NOT NULL PRIMARY KEY, -- Keycloak user ID
    customer_id BIGINT NOT NULL UNIQUE, -- Internal customer ID
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mapping_customer FOREIGN KEY
(customer_id) REFERENCES customers
(id) ON
DELETE CASCADE
);

-- Create index on customer_id for reverse lookups
CREATE INDEX idx_mapping_customer_id ON customer_user_mapping(customer_id);

-- Add comment
COMMENT ON TABLE customer_user_mapping IS 'Mapping table for fast userId to customerId resolution across services';

