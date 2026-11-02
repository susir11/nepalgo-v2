-- When the booking entered AWAITING_PAYMENT. The expiry job measures the
-- payment window from this moment, NOT from updated_at (which moves on
-- every edit, e.g. consent confirmation, and would keep resetting the clock).
ALTER TABLE bookings ADD COLUMN awaiting_payment_since TIMESTAMPTZ;

-- Backfill any rows already sitting in AWAITING_PAYMENT
UPDATE bookings SET awaiting_payment_since = updated_at WHERE status = 'AWAITING_PAYMENT';