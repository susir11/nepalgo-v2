-- FX rates with maker-checker (spec section 21).
CREATE TABLE fx_rates (
                          id              BIGSERIAL PRIMARY KEY,
                          currency_pair   VARCHAR(10) NOT NULL,             -- e.g. 'USD/NPR'
                          rate            NUMERIC(14, 6) NOT NULL CHECK (rate > 0),
                          proposed_by     BIGINT NOT NULL,                   -- the maker (staff id from auth-service; not an FK, different schema)
                          approved_by     BIGINT,                            -- the checker; NULL while pending
                          status          VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'RETIRED')),
                          effective_from  TIMESTAMPTZ,                       -- set when approved
                          created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- THE database-level guarantee: at most one APPROVED row per currency pair.
-- A partial unique index only indexes rows matching the WHERE clause, so any
-- number of PENDING/RETIRED rows are fine, but a second APPROVED row for the
-- same pair is rejected by Postgres itself, even if application code has a bug
-- or two approvals race. Same idea as a unique guard on a UTR.
CREATE UNIQUE INDEX uq_fx_rates_one_approved_per_pair
    ON fx_rates (currency_pair) WHERE status = 'APPROVED';

CREATE INDEX idx_fx_rates_pair_created ON fx_rates (currency_pair, created_at DESC);

-- Billing & Company Settings: exactly one row (id is pinned to 1 by CHECK).
CREATE TABLE billing_settings (
                                  id                      INTEGER PRIMARY KEY CHECK (id = 1),
                                  vat_registered          BOOLEAN NOT NULL DEFAULT false,
                                  vat_percent             NUMERIC(5, 2) NOT NULL DEFAULT 13.00 CHECK (vat_percent BETWEEN 0 AND 100),
                                  service_charge_percent  NUMERIC(5, 2) NOT NULL DEFAULT 10.00 CHECK (service_charge_percent BETWEEN 0 AND 100),
                                  seller_legal_name       VARCHAR(255) NOT NULL DEFAULT 'NepalGo (PLACEHOLDER - set real legal name)',
                                  seller_pan_vat_number   VARCHAR(50)  NOT NULL DEFAULT 'PLACEHOLDER',
                                  seller_address          TEXT NOT NULL DEFAULT 'Kathmandu, Nepal (PLACEHOLDER)',
                                  updated_by              BIGINT,
                                  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO billing_settings (id) VALUES (1);

-- payment-service's own audit log (each service logs its own domain, spec 15.1).
CREATE TABLE payment_audit_log (
                                   id              BIGSERIAL PRIMARY KEY,
                                   admin_user_id   BIGINT,
                                   action_type     VARCHAR(50) NOT NULL,
                                   subject_type    VARCHAR(30) NOT NULL,     -- 'FX_RATE', 'BILLING_SETTINGS', later 'BILL', ...
                                   subject_id      BIGINT,
                                   details         JSONB,
                                   created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_audit_subject ON payment_audit_log (subject_type, subject_id);