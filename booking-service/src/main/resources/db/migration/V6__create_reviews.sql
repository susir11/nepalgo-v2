-- Destination reviews (spec 3 / 6: "reviews" on Destination Detail).
--
-- Design: a review hangs off ONE completed booking, like a merchant rating that
-- only a cardholder with a settled transaction can leave. That makes every review
-- a "verified traveller" review, and eligibility is a local check in this service
-- (no cross-service call that could fail).
--
-- Moderation: nothing is public until staff publish it (Apple guideline 1.2 asks
-- apps with user-generated content to filter it). Any customer edit sends the
-- review back to PENDING, so an approved text can't be swapped afterwards.
CREATE TABLE reviews (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT       NOT NULL UNIQUE REFERENCES bookings (id),  -- one review per trip
    customer_id         BIGINT       NOT NULL,
    destination_id      BIGINT       NOT NULL,
    rating              SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title               VARCHAR(80),
    body                TEXT         NOT NULL CHECK (char_length(body) BETWEEN 10 AND 2000),
    -- Snapshot like "Susir P." — public pages never show full names or ids.
    author_display_name VARCHAR(60)  NOT NULL,
    trip_month          DATE         NOT NULL,  -- first day of the arrival month ("Travelled Oct 2026")
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'HIDDEN')),
    moderation_note     TEXT,                   -- shown to the customer when REJECTED
    moderated_by        BIGINT,
    moderated_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Public read path: published reviews of one destination, newest first.
CREATE INDEX ix_reviews_destination_published ON reviews (destination_id, created_at DESC) WHERE status = 'PUBLISHED';
-- Admin moderation queue.
CREATE INDEX ix_reviews_status_created ON reviews (status, created_at);
CREATE INDEX ix_reviews_customer ON reviews (customer_id);
