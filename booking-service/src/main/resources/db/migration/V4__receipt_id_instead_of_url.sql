-- Receipt images now live in payment-service's private bucket. The booking
-- keeps only a reference (receipt id) plus the typed code for quick display.
-- A raw image URL on the booking would have been either public (bad: payment
-- details) or expired (useless), so we store the id and ask for a fresh
-- presigned link when an admin actually opens it.
ALTER TABLE bookings DROP COLUMN payment_receipt_image_url;
ALTER TABLE bookings ADD COLUMN payment_receipt_id BIGINT;
