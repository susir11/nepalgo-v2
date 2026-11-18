-- notification-service (spec 19).

-- One row per business event per recipient. Also the customer's / admin's
-- in-app inbox (read_at). idempotency_key makes event intake safe to retry:
-- booking-service can resend "PAYMENT_VERIFIED:booking:15" and we notify once.
CREATE TABLE notifications (
    id               BIGSERIAL PRIMARY KEY,
    idempotency_key  VARCHAR(150) NOT NULL UNIQUE,
    event_type       VARCHAR(40)  NOT NULL,
    audience         VARCHAR(10)  NOT NULL CHECK (audience IN ('CUSTOMER', 'ADMIN')),
    customer_id      BIGINT,
    booking_id       BIGINT,
    title            VARCHAR(200) NOT NULL,
    body             TEXT         NOT NULL,
    data             JSONB,
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_customer ON notifications (customer_id, created_at DESC);
CREATE INDEX idx_notifications_admin ON notifications (audience, created_at DESC);

-- Delivery OUTBOX: one row per channel per notification. A scheduler works
-- this table with retries + exponential backoff, so a down mail server or FCM
-- outage delays a message instead of losing it.
CREATE TABLE deliveries (
    id               BIGSERIAL PRIMARY KEY,
    notification_id  BIGINT       NOT NULL REFERENCES notifications(id),
    channel          VARCHAR(10)  NOT NULL CHECK (channel IN ('PUSH', 'EMAIL')),
    recipient        VARCHAR(500),                -- e-mail address; NULL for push (resolved to device tokens at send time)
    attach_invoice   BOOLEAN      NOT NULL DEFAULT false,
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    attempts         INT          NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error       TEXT,
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_deliveries_due ON deliveries (status, next_attempt_at);

-- Customer devices for FCM push. A token belongs to one customer at a time
-- (a shared phone that switches accounts moves the token).
CREATE TABLE device_tokens (
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT       NOT NULL,
    token         VARCHAR(500) NOT NULL UNIQUE,
    platform      VARCHAR(10)  NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_tokens_customer ON device_tokens (customer_id);
