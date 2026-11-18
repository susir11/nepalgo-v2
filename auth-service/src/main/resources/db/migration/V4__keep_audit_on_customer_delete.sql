-- Account deletion (spec 15.2) removes the customer row. Admin audit entries
-- about that customer (e.g. "suspended for fraud check") are evidence and must
-- survive, so the reference is cleared instead of blocking the delete.
ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_target_customer_id_fkey;
ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_target_customer_id_fkey
    FOREIGN KEY (target_customer_id) REFERENCES customers(id) ON DELETE SET NULL;
