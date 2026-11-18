-- support-service (spec 12 + 20): dormant, feature-flagged OFF by default.

-- Shared admin inbox: any available staff member picks a thread up (no routing engine in v1).
CREATE TABLE chat_threads (
    id               BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT       NOT NULL,
    booking_id       BIGINT,                         -- optional: not every question is about a booking
    subject          VARCHAR(200) NOT NULL,
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    assigned_staff_id BIGINT,                        -- who picked it up (NULL = unassigned, in the shared queue)
    last_message_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0  -- two staff can't both "pick up" the same thread
);
CREATE INDEX idx_threads_customer ON chat_threads (customer_id, last_message_at DESC);
CREATE INDEX idx_threads_inbox ON chat_threads (status, last_message_at DESC);

CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    thread_id    BIGINT      NOT NULL REFERENCES chat_threads(id),
    sender_type  VARCHAR(10) NOT NULL CHECK (sender_type IN ('CUSTOMER', 'STAFF')),
    sender_id    BIGINT      NOT NULL,
    body         TEXT        NOT NULL CHECK (length(body) BETWEEN 1 AND 4000),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_thread ON chat_messages (thread_id, id);

-- Spec 12 data model, exactly: id, customer_id, booking_id (nullable), phone_number, preferred_time, status.
CREATE TABLE callback_requests (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT       NOT NULL,
    booking_id      BIGINT,
    phone_number    VARCHAR(30)  NOT NULL,
    preferred_time  VARCHAR(100) NOT NULL,           -- free text: "weekday evenings Nepal time"
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
    completed_by    BIGINT,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_callbacks_status ON callback_requests (status, created_at);
