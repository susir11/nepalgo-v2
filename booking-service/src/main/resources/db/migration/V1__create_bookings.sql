-- Bookings: the core record for the entire 4-step lifecycle. Many fields
-- here are placeholders for payment-service (Phase 4) and legal-service
-- (Phase 5) content that doesn't have its own service yet — see the
-- comments on each.
CREATE TABLE bookings (
                          id                      BIGSERIAL PRIMARY KEY,
                          voucher_code            VARCHAR(16) NOT NULL UNIQUE,   -- "NEPALGO-A1B2C3"
                          status                  VARCHAR(30) NOT NULL,
                          origin                  VARCHAR(20) NOT NULL CHECK (origin IN ('self_served', 'ops_created')),

    -- Who this booking belongs to. Deliberately just the numeric customer
    -- id (from auth-service's customers table) — NOT a foreign key, since
    -- that table lives in a different schema/service entirely. Same
    -- "services never share tables" principle as Phase 1's schema isolation.
                          customer_id             BIGINT NOT NULL,

    -- Snapshot of what was booked, captured once at Step 1 and never
    -- re-fetched from catalog-service afterward — if the destination's
    -- price or itinerary changes later, an already-placed booking must
    -- still reflect what the customer actually saw and agreed to.
                          destination_id          BIGINT NOT NULL,
                          destination_name_snapshot   VARCHAR(255) NOT NULL,
                          itinerary_version_id    BIGINT NOT NULL,
                          itinerary_snapshot       JSONB NOT NULL,   -- the full day-by-day content, frozen at booking time

                          party_size              INT NOT NULL,
                          arrival_date             DATE NOT NULL,
                          duration_days            INT NOT NULL,      -- from the snapshotted itinerary's day-count; arrival_date + duration_days defines the hold's date range (D4)
                          guide_requested          BOOLEAN NOT NULL DEFAULT false,
                          porter_requested         BOOLEAN NOT NULL DEFAULT false,
                          special_request_text     TEXT,

    -- Step 2 / identity capture (section 8, Step 2 sub-screen 2)
                          lead_booker_legal_name   VARCHAR(255),
                          lead_booker_passport_number VARCHAR(50),
                          lead_booker_email        VARCHAR(255),

    -- --- Placeholder fields: payment-service's real domain, Phase 4 ---
                          payoneer_link             VARCHAR(500),
                          itemized_bill_json        JSONB,            -- simple placeholder; real billing model (section 21) arrives in Phase 4
                          invoice_number            VARCHAR(50),
                          payment_receipt_image_url VARCHAR(500),
                          payment_receipt_code      VARCHAR(100),      -- uniqueness-across-bookings check deferred to Phase 4

    -- --- Placeholder field: legal-service's real domain, Phase 5 ---
                          consent_confirmed         BOOLEAN NOT NULL DEFAULT false,

    -- Admin workflow fields
                          rejection_reason          TEXT,              -- required whenever status moves to REJECTED or PAYMENT_REJECTED
                          internal_notes            TEXT,              -- admin-only, used by "Create Booking Manually" (8b)

    -- Cancellation (8.7) — tier/percentage computed and stored at
    -- cancellation time; actual refund/credit EXECUTION is Phase 4.
                          cancellation_tier          VARCHAR(20),       -- e.g. "60_PLUS_DAYS", "30_59_DAYS", "15_29_DAYS", "0_14_DAYS", "FORCE_MAJEURE"
                          cancellation_refund_percent NUMERIC(5, 2),

                          created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Double-booking prevention (8.8) queries this directly — destination +
-- date range + active-status bookings. This index makes that lookup fast
-- rather than scanning every booking ever made.
CREATE INDEX idx_bookings_destination_arrival ON bookings (destination_id, arrival_date);
CREATE INDEX idx_bookings_customer_id ON bookings (customer_id);
CREATE INDEX idx_bookings_status ON bookings (status);

-- Booking-service's own audit log, per section 15.1 — booking-status
-- actions only. Same shape as auth-service's admin_audit_log from Phase 1,
-- deliberately NOT shared across services (each service logs its own
-- domain; the admin frontend merges them at render time, not the database).
CREATE TABLE booking_audit_log (
                                   id              BIGSERIAL PRIMARY KEY,
                                   admin_user_id   BIGINT,          -- NULL for customer-initiated actions (e.g. withdraw, cancel)
                                   action_type     VARCHAR(50) NOT NULL,
                                   booking_id      BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
                                   customer_id     BIGINT,
                                   details         JSONB,
                                   created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_audit_log_booking_id ON booking_audit_log (booking_id);