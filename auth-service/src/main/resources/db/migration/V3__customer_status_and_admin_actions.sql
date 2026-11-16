-- Phase 7: Customer Management (spec 15.2). Suspension blocks NEW activity
-- (new trip requests, new payment submissions) but never touches confirmed
-- trips or history; reactivation simply lifts the block.
ALTER TABLE customers ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'SUSPENDED'));
ALTER TABLE customers ADD COLUMN suspended_at TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN suspension_reason TEXT;

-- Admin actions on customers are audited like actions on staff.
ALTER TABLE admin_audit_log ADD COLUMN target_customer_id BIGINT REFERENCES customers(id);
CREATE INDEX idx_admin_audit_target_customer ON admin_audit_log (target_customer_id);

-- Case-insensitive search by email / name in the customer list.
CREATE INDEX idx_customers_email_lower ON customers (lower(email));
CREATE INDEX idx_customers_name_lower ON customers (lower(display_name));
