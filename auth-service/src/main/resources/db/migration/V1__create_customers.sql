-- Customers: one row per Firebase-authenticated user. firebase_uid is Firebase's
-- own identifier; nepalgo_id is OUR permanent, randomly-generated 8-digit ID
-- (section 5a) — deliberately not sequential, so it can't leak signup volume,
-- and deliberately unrelated in format to booking/wallet codes.
CREATE TABLE customers (
                           id              BIGSERIAL PRIMARY KEY,
                           firebase_uid    VARCHAR(128) NOT NULL UNIQUE,
                           nepalgo_id      VARCHAR(8) NOT NULL UNIQUE,
                           email           VARCHAR(255),
                           display_name    VARCHAR(255),
                           created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                           updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Retired IDs: when an account is deleted (Phase 9), its nepalgo_id lands
-- here so the generator can never reissue it to someone else — the same
-- principle as never reusing a closed bank account number.
CREATE TABLE retired_nepalgo_ids (
                                     nepalgo_id      VARCHAR(8) PRIMARY KEY,
                                     retired_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Refresh tokens: we never store the raw token, only its SHA-256 hash — same
-- reason passwords are hashed, not stored plain. token_family lets us revoke
-- every descendant of a token at once if an old one gets reused (replay
-- detection, like a duplicate UETR catching a replayed payment message).
CREATE TABLE customer_refresh_tokens (
                                         id              BIGSERIAL PRIMARY KEY,
                                         customer_id     BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
                                         token_hash      VARCHAR(64) NOT NULL UNIQUE,
                                         token_family    UUID NOT NULL,
                                         issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                                         expires_at      TIMESTAMPTZ NOT NULL,
                                         revoked_at      TIMESTAMPTZ,
                                         replaced_by_id  BIGINT REFERENCES customer_refresh_tokens(id)
);

CREATE INDEX idx_customer_refresh_tokens_customer_id ON customer_refresh_tokens(customer_id);
CREATE INDEX idx_customer_refresh_tokens_token_family ON customer_refresh_tokens(token_family);