-- Payment receipts (spec section 8, Steps 3-4). The image lives in a PRIVATE
-- RustFS bucket; this row is the index + fraud-check data.
CREATE TABLE payment_receipts (
    id                      BIGSERIAL PRIMARY KEY,
    booking_id              BIGINT NOT NULL,
    customer_id             BIGINT NOT NULL,
    bill_id                 BIGINT NOT NULL REFERENCES bills(id),
    receipt_code            VARCHAR(100) NOT NULL,          -- as typed by the customer
    receipt_code_normalized VARCHAR(100) NOT NULL,          -- upper-case, no spaces/dashes: "pyn-12 34" == "PYN1234"
    object_key              VARCHAR(300) NOT NULL UNIQUE,   -- key inside the private bucket
    content_type            VARCHAR(50)  NOT NULL,
    size_bytes              BIGINT       NOT NULL,
    sha256                  CHAR(64)     NOT NULL,          -- same screenshot re-used on another booking = red flag
    status                  VARCHAR(10)  NOT NULL CHECK (status IN ('SUBMITTED', 'ACCEPTED')),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The double-claim check ("has this code/image been used on a DIFFERENT
-- booking?") must stay fast as receipts accumulate.
CREATE INDEX idx_receipts_code ON payment_receipts (receipt_code_normalized);
CREATE INDEX idx_receipts_sha ON payment_receipts (sha256);
CREATE INDEX idx_receipts_booking ON payment_receipts (booking_id, created_at DESC);
