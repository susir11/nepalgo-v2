-- Itemized bill model (spec section 21).
-- Money columns are NUMERIC, never float: 0.1 + 0.2 must equal 0.3 on a tax invoice.

CREATE TABLE bills (
    id                      BIGSERIAL PRIMARY KEY,
    booking_id              BIGINT NOT NULL,              -- booking-service id (different schema, so no FK)
    customer_id             BIGINT NOT NULL,              -- copied from the booking; drives customer read access

    -- Assigned exactly once, at "Approve & Issue Bill". NULL while the bill is
    -- still being priced. UNIQUE so the same number can never be handed out twice.
    invoice_number          VARCHAR(40) UNIQUE,
    status                  VARCHAR(10) NOT NULL CHECK (status IN ('DRAFT', 'LOCKED')),
    issued_at               TIMESTAMPTZ,
    issue_date              DATE,                         -- AD (Gregorian). BS dates deliberately out of scope for now.
    locked_at               TIMESTAMPTZ,

    -- Seller snapshot, copied from billing_settings when the bill is created.
    seller_legal_name       VARCHAR(255) NOT NULL,
    seller_pan_vat_number   VARCHAR(50)  NOT NULL,
    seller_address          TEXT         NOT NULL,

    -- Buyer: lead booker's name is only known after identity capture, so it is
    -- filled when the bill locks. PAN is optional (most buyers are foreign tourists).
    buyer_name              VARCHAR(255),
    buyer_pan               VARCHAR(50),

    -- Frozen copies, never live references (an FX or VAT change later must not
    -- rewrite an already-priced bill).
    currency_pair           VARCHAR(10)   NOT NULL,
    fx_rate                 NUMERIC(14,6) NOT NULL CHECK (fx_rate > 0),
    vat_registered          BOOLEAN       NOT NULL,
    vat_percent             NUMERIC(5,2)  NOT NULL,
    service_charge_percent  NUMERIC(5,2)  NOT NULL,

    applied_credit_amount   NUMERIC(12,2) CHECK (applied_credit_amount >= 0),   -- USD, from store credit (later step)
    payoneer_link           VARCHAR(1000),

    -- TRUE once a BillCorrection has replaced this bill. Superseded bills stay
    -- forever (tax trail); they are simply no longer the "current" bill.
    superseded              BOOLEAN NOT NULL DEFAULT false,

    created_by              BIGINT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0       -- optimistic locking (@Version)
);

-- At most ONE current bill per booking, enforced by Postgres (same partial-index
-- trick as the FX table). Originals that were corrected are superseded = true.
CREATE UNIQUE INDEX uq_bills_one_current_per_booking ON bills (booking_id) WHERE superseded = false;
CREATE INDEX idx_bills_customer ON bills (customer_id);

CREATE TABLE bill_line_items (
    id              BIGSERIAL PRIMARY KEY,
    bill_id         BIGINT NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    position        INT NOT NULL,                         -- display order on the invoice
    description     VARCHAR(500) NOT NULL,
    category        VARCHAR(20) NOT NULL CHECK (category IN ('TRANSPORT','ACCOMMODATION','FOOD','GUIDE','ACTIVITY','OTHER')),
    itinerary_day   INT CHECK (itinerary_day >= 1),       -- optional link to the itinerary day
    quantity        NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
    unit_price_npr  NUMERIC(14,2) NOT NULL CHECK (unit_price_npr >= 0),
    line_total_npr  NUMERIC(14,2) NOT NULL CHECK (line_total_npr >= 0)
);
CREATE INDEX idx_bill_line_items_bill ON bill_line_items (bill_id, position);

-- Amend log: one row per change made while the bill is DRAFT.
CREATE TABLE bill_amendments (
    id              BIGSERIAL PRIMARY KEY,
    bill_id         BIGINT NOT NULL REFERENCES bills(id),
    admin_user_id   BIGINT NOT NULL,
    field_changed   VARCHAR(50) NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bill_amendments_bill ON bill_amendments (bill_id, created_at);

-- Correction: a mistake found AFTER lock produces a brand-new bill (own invoice
-- number) linked to the original. The original row is never edited in place.
CREATE TABLE bill_corrections (
    id                  BIGSERIAL PRIMARY KEY,
    original_bill_id    BIGINT NOT NULL REFERENCES bills(id),
    new_bill_id         BIGINT NOT NULL UNIQUE REFERENCES bills(id),
    reason              TEXT NOT NULL,
    created_by          BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bill_corrections_original ON bill_corrections (original_bill_id);

-- Invoice numbers are sequential WITHIN a calendar year (AD), e.g. NG-2026-000001.
-- One row per year; incremented with INSERT ... ON CONFLICT DO UPDATE ... RETURNING,
-- which row-locks, so two admins issuing at the same moment never get the same number.
CREATE TABLE invoice_sequences (
    seq_year    INT PRIMARY KEY,
    last_value  BIGINT NOT NULL
);
