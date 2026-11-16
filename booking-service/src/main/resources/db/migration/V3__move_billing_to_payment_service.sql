-- Phase 4: payment-service now owns the bill (itemized lines, Payoneer link,
-- invoice). The Phase 3 placeholder columns are dropped here.
-- invoice_number STAYS as a read-only copy so My Trips / admin lists can show
-- it without calling payment-service for every row (denormalised reference;
-- payment-service remains the source of truth).
ALTER TABLE bookings DROP COLUMN payoneer_link;
ALTER TABLE bookings DROP COLUMN itemized_bill_json;

-- Decision D1: a rejected payment returns the booking to AWAITING_PAYMENT
-- (window restarts) instead of a dead-end PAYMENT_REJECTED status.
-- Safety net for any row still holding the retired status:
UPDATE bookings SET status = 'AWAITING_PAYMENT', awaiting_payment_since = now()
WHERE status = 'PAYMENT_REJECTED';
