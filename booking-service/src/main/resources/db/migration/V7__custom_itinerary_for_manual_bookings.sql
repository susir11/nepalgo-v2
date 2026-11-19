-- Spec 8b: "Create Booking Manually" can carry an ad-hoc itinerary agreed on
-- WhatsApp, scoped to that one booking and never saved as a catalogue
-- template. Such a booking has no catalogue itinerary version, so the column
-- becomes optional (NULL = custom). The itinerary_snapshot jsonb remains the
-- single source of truth for what the customer agreed to, in both cases.
ALTER TABLE bookings ALTER COLUMN itinerary_version_id DROP NOT NULL;
