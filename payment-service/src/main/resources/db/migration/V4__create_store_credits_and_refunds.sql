-- NepalGo Travel Credit (spec 6b, 6c, 8.7, 15 "Wallet & Card Management").
-- Amounts are USD: credit is a promise in the currency the customer paid.
CREATE TABLE store_credits (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(20)  NOT NULL UNIQUE,                 -- e.g. NG-CR-7K2M9Q (shown on the wallet card)
    customer_id           BIGINT       NOT NULL,
    amount_usd            NUMERIC(12,2) NOT NULL CHECK (amount_usd > 0),
    expires_on            DATE         NOT NULL,
    -- ACTIVE: spendable | RESERVED: applied to a trip request, waiting for that
    -- booking to be paid | REDEEMED: used | VOID: cancelled by admin.
    -- "Expired" is not stored: it is ACTIVE + expires_on in the past (no cron needed).
    status                VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE','RESERVED','REDEEMED','VOID')),
    source                VARCHAR(20)  NOT NULL CHECK (source IN ('CANCELLATION','MANUAL')),
    source_booking_id     BIGINT,                                       -- the cancelled booking that produced it
    reserved_booking_id   BIGINT,                                       -- set while RESERVED / REDEEMED
    -- Admin decision while pricing the bill (spec 21): NULL = not decided yet.
    accepted              BOOLEAN,
    rejection_reason      TEXT,
    redeemed_at           TIMESTAMPTZ,
    void_reason           TEXT,
    note                  TEXT,
    issued_by             BIGINT,                                       -- admin id; NULL when system-issued
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_store_credits_customer ON store_credits (customer_id, status);
CREATE INDEX idx_store_credits_reserved ON store_credits (reserved_booking_id);
-- A cancelled booking produces at most one credit (makes the cancellation call idempotent).
CREATE UNIQUE INDEX uq_store_credits_cancellation ON store_credits (source_booking_id) WHERE source = 'CANCELLATION';

-- Edit log for credits (same amend-log principle as bills).
CREATE TABLE store_credit_amendments (
    id              BIGSERIAL PRIMARY KEY,
    credit_id       BIGINT NOT NULL REFERENCES store_credits(id),
    admin_user_id   BIGINT NOT NULL,
    field_changed   VARCHAR(50) NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_amendments_credit ON store_credit_amendments (credit_id);

-- Cash refunds (8.7 60+ days tier, force majeure). Paid manually by admin in the
-- Payoneer dashboard, then marked COMPLETED here (no webhook, by design).
CREATE TABLE refund_requests (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT NOT NULL UNIQUE,           -- one refund per booking => idempotent cancellation
    customer_id       BIGINT NOT NULL,
    bill_id           BIGINT NOT NULL REFERENCES bills(id),
    tier              VARCHAR(20) NOT NULL,             -- 60_PLUS_DAYS / FORCE_MAJEURE
    refund_percent    NUMERIC(5,2) NOT NULL,
    amount_usd        NUMERIC(12,2) NOT NULL CHECK (amount_usd >= 0),
    status            VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','COMPLETED')),
    payout_reference  VARCHAR(100),                     -- Payoneer transaction id typed by admin
    completed_by      BIGINT,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refund_requests_status ON refund_requests (status, created_at);
