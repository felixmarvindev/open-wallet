-- Create notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL, -- Phone or email
    notification_type VARCHAR(50) NOT NULL, -- TRANSACTION_COMPLETED, KYC_VERIFIED, etc.
    channel VARCHAR(20) NOT NULL, -- SMS, EMAIL
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SENT, FAILED
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for notifications table
CREATE INDEX idx_notifications_recipient ON notifications(recipient);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);

-- Add comment to table
COMMENT ON TABLE notifications IS 'Notification history for audit and tracking';


