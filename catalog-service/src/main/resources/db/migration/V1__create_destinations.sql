-- Destinations: the catalog's core entity. permit_notes is a plain editable
-- text field, NOT versioned like Terms/Privacy — it's informational
-- guidance admin edits directly when something changes (spec section 6).
CREATE TABLE destinations (
                              id              BIGSERIAL PRIMARY KEY,
                              name            VARCHAR(255) NOT NULL,
                              region          VARCHAR(255) NOT NULL,
                              type            VARCHAR(100) NOT NULL,   -- e.g. Trek, Day Tour, Cultural, Adventure.
    -- NOT an enum: category chips are populated
    -- from whatever distinct values exist here,
    -- so a new type just needs a value, not a migration.
                              description     TEXT NOT NULL,
                              best_season     VARCHAR(255),
                              base_price_usd  NUMERIC(10, 2) NOT NULL,
                              permit_notes    TEXT,
                              created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                              updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Generated column: Postgres keeps this in sync automatically on every
    -- insert/update, so application code never has to remember to update
    -- it separately. Weighted 'A' (name) > 'B' (region) > 'C' (description)
    -- so a name match ranks higher than a description match in results.
                              search_vector   tsvector GENERATED ALWAYS AS (
                                  setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
                                  setweight(to_tsvector('english', coalesce(region, '')), 'B') ||
                                  setweight(to_tsvector('english', coalesce(description, '')), 'C')
                                  ) STORED
);

-- GIN index is the standard choice for tsvector columns — without it,
-- every search does a full table scan re-computing nothing but still
-- checking every row.
CREATE INDEX idx_destinations_search_vector ON destinations USING GIN (search_vector);
CREATE INDEX idx_destinations_type ON destinations (type);
CREATE INDEX idx_destinations_region ON destinations (region);

-- One row per uploaded image. display_order controls gallery sequence.
-- Populated by Step 7's upload pipeline — empty for now.
CREATE TABLE destination_images (
                                    id              BIGSERIAL PRIMARY KEY,
                                    destination_id  BIGINT NOT NULL REFERENCES destinations(id) ON DELETE CASCADE,
                                    display_order   INT NOT NULL DEFAULT 0,
                                    thumbnail_url   VARCHAR(500) NOT NULL,
                                    medium_url      VARCHAR(500) NOT NULL,
                                    full_url        VARCHAR(500) NOT NULL,
                                    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_destination_images_destination_id ON destination_images (destination_id);

-- Tiered itinerary content. Only DEFAULT is authored/shown in v1, but the
-- schema already supports TEAHOUSE/COMFORT/PREMIUM so a future tier
-- selector ships without a migration (spec section 6).
CREATE TABLE itinerary_versions (
                                    id              BIGSERIAL PRIMARY KEY,
                                    destination_id  BIGINT NOT NULL REFERENCES destinations(id) ON DELETE CASCADE,
                                    tier            VARCHAR(20) NOT NULL CHECK (tier IN ('DEFAULT', 'TEAHOUSE', 'COMFORT', 'PREMIUM')),
                                    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- One version per tier per destination — prevents an admin mistake
    -- from creating two competing DEFAULT versions for the same place.
                                    CONSTRAINT uq_itinerary_versions_destination_tier UNIQUE (destination_id, tier)
);

CREATE INDEX idx_itinerary_versions_destination_id ON itinerary_versions (destination_id);

-- Each ItineraryVersion's day-by-day content, fully independent per tier
-- (spec section 6) — editing TEAHOUSE's day 3 never touches DEFAULT's day 3,
-- even for the same destination.
CREATE TABLE itinerary_days (
                                id                   BIGSERIAL PRIMARY KEY,
                                itinerary_version_id BIGINT NOT NULL REFERENCES itinerary_versions(id) ON DELETE CASCADE,
                                day_number           INT NOT NULL,
                                narrative            TEXT NOT NULL,
                                hotel_name           VARCHAR(255),   -- NULL = no curated hotel that day
                                hotel_lat            NUMERIC(9, 6),
                                hotel_lng            NUMERIC(9, 6),

                                CONSTRAINT uq_itinerary_days_version_day UNIQUE (itinerary_version_id, day_number)
);

CREATE INDEX idx_itinerary_days_version_id ON itinerary_days (itinerary_version_id);