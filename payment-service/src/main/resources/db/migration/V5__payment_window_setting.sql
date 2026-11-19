-- Spec 15 "Billing & Company Settings": payment_window_hours is a named,
-- staff-editable value, not a constant in a config file. Like a card
-- pre-authorisation hold: how long the customer's dates are held for them
-- before the hold lapses (AWAITING_PAYMENT -> EXPIRED in booking-service).
-- 1 hour .. 14 days; 48 h keeps today's behaviour.
ALTER TABLE billing_settings
    ADD COLUMN payment_window_hours INTEGER NOT NULL DEFAULT 48
        CHECK (payment_window_hours BETWEEN 1 AND 336);
