-- Staff: a completely separate identity system from customers (section 5b).
-- No firebase_uid here — staff log in with email+password directly against
-- OUR database, never through Firebase.
CREATE TABLE staff_users (
                             id                  BIGSERIAL PRIMARY KEY,
                             email               VARCHAR(255) NOT NULL UNIQUE,
                             password_hash       VARCHAR(255) NOT NULL,
                             role                VARCHAR(20) NOT NULL CHECK (role IN ('SUPER_ADMIN', 'OPS')),
                             totp_secret_encrypted VARCHAR(255),        -- NULL until MFA enrollment completes
                             mfa_enabled         BOOLEAN NOT NULL DEFAULT false,
                             must_change_password BOOLEAN NOT NULL DEFAULT true,
                             failed_login_count  INT NOT NULL DEFAULT 0,
                             locked_until        TIMESTAMPTZ,
                             active              BOOLEAN NOT NULL DEFAULT true,
                             created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                             updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Same rotation/reuse-detection design as customer_refresh_tokens, but a
-- separate table — staff sessions must never share storage with customer
-- sessions, even though the schema looks identical. Keeping them apart
-- means a bug in one token family's logic can't cross-contaminate the other.
CREATE TABLE staff_refresh_tokens (
                                      id              BIGSERIAL PRIMARY KEY,
                                      staff_user_id   BIGINT NOT NULL REFERENCES staff_users(id) ON DELETE CASCADE,
                                      token_hash      VARCHAR(64) NOT NULL UNIQUE,
                                      token_family    UUID NOT NULL,
                                      issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                                      expires_at      TIMESTAMPTZ NOT NULL,
                                      revoked_at      TIMESTAMPTZ,
                                      replaced_by_id  BIGINT REFERENCES staff_refresh_tokens(id)
);

CREATE INDEX idx_staff_refresh_tokens_staff_user_id ON staff_refresh_tokens(staff_user_id);
CREATE INDEX idx_staff_refresh_tokens_token_family ON staff_refresh_tokens(token_family);

-- Audit log, section 15.1 shape: who did what, to whom, when. Append-only —
-- nothing in this build ever UPDATEs or DELETEs a row here.
CREATE TABLE admin_audit_log (
                                 id              BIGSERIAL PRIMARY KEY,
                                 actor_staff_id  BIGINT REFERENCES staff_users(id),
                                 action          VARCHAR(50) NOT NULL,
                                 target_staff_id BIGINT REFERENCES staff_users(id),
                                 details         TEXT,
                                 created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_log_actor ON admin_audit_log(actor_staff_id);
CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log(created_at);