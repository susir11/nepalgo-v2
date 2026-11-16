-- Phase 5: consent is a real ConsentRecord in legal-service (exact Terms version,
-- per-card expand/check timestamps, append-only). The Phase 3 boolean placeholder
-- is dropped; booking-service now asks legal-service before accepting payment.
ALTER TABLE bookings DROP COLUMN consent_confirmed;
