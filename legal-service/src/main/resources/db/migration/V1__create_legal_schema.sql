-- legal-service schema (spec sections 9, 10, 4a, 16.8).

-- ---------------------------------------------------------------- documents
-- ONE model for both legal document types, each versioned independently.
-- DRAFT = editable; PUBLISHED = frozen forever (a new version is a new row).
CREATE TABLE legal_documents (
    id                      BIGSERIAL PRIMARY KEY,
    document_type           VARCHAR(30)  NOT NULL CHECK (document_type IN ('TERMS_AND_CONDITIONS', 'PRIVACY_POLICY')),
    version_label           VARCHAR(20)  NOT NULL,                 -- 'v1', 'v2', ...
    status                  VARCHAR(10)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    is_current              BOOLEAN      NOT NULL DEFAULT false,
    title                   VARCHAR(200) NOT NULL,
    full_text               TEXT         NOT NULL,
    -- Terms only: the three gated Policy Review cards (spec 9).
    cancellation_matrix     TEXT,
    force_majeure_weather   TEXT,
    identity_liability      TEXT,
    change_summary          TEXT,                                  -- "what changed" note for the admin history
    content_sha256          VARCHAR(64),                           -- fingerprint of exactly what was published
    effective_from          TIMESTAMPTZ,
    published_at            TIMESTAMPTZ,
    published_by            BIGINT,
    created_by              BIGINT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                 BIGINT       NOT NULL DEFAULT 0,       -- optimistic locking for concurrent draft edits

    CONSTRAINT uq_legal_doc_label UNIQUE (document_type, version_label),
    CONSTRAINT ck_current_is_published CHECK (NOT is_current OR status = 'PUBLISHED'),
    CONSTRAINT ck_published_has_hash CHECK (status = 'DRAFT' OR content_sha256 IS NOT NULL),
    -- A published Terms version MUST carry all three gated card sections.
    CONSTRAINT ck_terms_sections CHECK (
        document_type <> 'TERMS_AND_CONDITIONS' OR status = 'DRAFT'
        OR (cancellation_matrix IS NOT NULL AND force_majeure_weather IS NOT NULL AND identity_liability IS NOT NULL))
);

-- "Exactly one current version per type" — enforced by Postgres, not just Java.
CREATE UNIQUE INDEX uq_legal_doc_one_current ON legal_documents (document_type) WHERE is_current;

-- Published text is immutable. Only is_current may flip (when a newer version
-- is published). Even a bug, a rogue admin tool or a manual SQL session can't
-- silently rewrite terms a customer already agreed to.
CREATE FUNCTION legal_documents_guard() RETURNS trigger AS $$
BEGIN
    IF OLD.status = 'PUBLISHED' AND (
           NEW.status                IS DISTINCT FROM OLD.status
        OR NEW.version_label         IS DISTINCT FROM OLD.version_label
        OR NEW.title                 IS DISTINCT FROM OLD.title
        OR NEW.full_text             IS DISTINCT FROM OLD.full_text
        OR NEW.cancellation_matrix   IS DISTINCT FROM OLD.cancellation_matrix
        OR NEW.force_majeure_weather IS DISTINCT FROM OLD.force_majeure_weather
        OR NEW.identity_liability    IS DISTINCT FROM OLD.identity_liability
        OR NEW.content_sha256        IS DISTINCT FROM OLD.content_sha256
        OR NEW.published_at          IS DISTINCT FROM OLD.published_at) THEN
        RAISE EXCEPTION 'Published legal document % is immutable; publish a new version instead', OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    IF TG_OP = 'DELETE' AND OLD.status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'Published legal document % cannot be deleted', OLD.id USING ERRCODE = 'check_violation';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_legal_documents_guard
    BEFORE UPDATE OR DELETE ON legal_documents
    FOR EACH ROW EXECUTE FUNCTION legal_documents_guard();

-- ---------------------------------------------------------------- consent
-- Evidence that THIS customer agreed to THIS exact Terms text for THIS booking.
CREATE TABLE consent_records (
    id                          BIGSERIAL PRIMARY KEY,
    customer_id                 BIGINT       NOT NULL,
    booking_id                  BIGINT       NOT NULL,
    legal_document_id           BIGINT       NOT NULL REFERENCES legal_documents(id),
    version_label               VARCHAR(20)  NOT NULL,          -- copied for readability in audits
    content_sha256              VARCHAR(64)  NOT NULL,          -- proves which exact text was shown
    cancellation_expanded_at    TIMESTAMPTZ  NOT NULL,
    cancellation_checked_at     TIMESTAMPTZ  NOT NULL,
    force_majeure_expanded_at   TIMESTAMPTZ  NOT NULL,
    force_majeure_checked_at    TIMESTAMPTZ  NOT NULL,
    identity_expanded_at        TIMESTAMPTZ  NOT NULL,
    identity_checked_at         TIMESTAMPTZ  NOT NULL,
    consented_at                TIMESTAMPTZ  NOT NULL,          -- server time of the "Pay" tap
    client_ip                   VARCHAR(64),
    user_agent                  VARCHAR(500),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_consent_booking_version UNIQUE (booking_id, legal_document_id),
    -- A card can only be ticked after it was opened, and before the final tap.
    CONSTRAINT ck_cancellation_order CHECK (cancellation_checked_at >= cancellation_expanded_at AND consented_at >= cancellation_checked_at),
    CONSTRAINT ck_force_majeure_order CHECK (force_majeure_checked_at >= force_majeure_expanded_at AND consented_at >= force_majeure_checked_at),
    CONSTRAINT ck_identity_order CHECK (identity_checked_at >= identity_expanded_at AND consented_at >= identity_checked_at)
);
CREATE INDEX idx_consent_booking ON consent_records (booking_id);
CREATE INDEX idx_consent_customer ON consent_records (customer_id);

-- Consent is evidence: append-only, like a ledger. No UPDATE, no DELETE.
CREATE FUNCTION consent_records_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'consent_records is append-only (% not allowed)', TG_OP USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_consent_records_append_only
    BEFORE UPDATE OR DELETE ON consent_records
    FOR EACH ROW EXECUTE FUNCTION consent_records_append_only();

-- ---------------------------------------------------------------- site content
-- Onboarding carousel + company profile (spec 4a, 16.8): admin-editable JSON,
-- versioned the same draft -> publish way, one current version per key.
CREATE TABLE site_content (
    id              BIGSERIAL PRIMARY KEY,
    content_key     VARCHAR(40) NOT NULL CHECK (content_key IN ('ONBOARDING', 'COMPANY_PROFILE')),
    version_no      INT         NOT NULL,
    status          VARCHAR(10) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    is_current      BOOLEAN     NOT NULL DEFAULT false,
    payload         JSONB       NOT NULL,
    change_summary  TEXT,
    published_at    TIMESTAMPTZ,
    published_by    BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_site_content_version UNIQUE (content_key, version_no),
    CONSTRAINT ck_site_current_published CHECK (NOT is_current OR status = 'PUBLISHED')
);
CREATE UNIQUE INDEX uq_site_content_one_current ON site_content (content_key) WHERE is_current;

-- ---------------------------------------------------------------- audit
CREATE TABLE legal_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    admin_user_id   BIGINT,
    action_type     VARCHAR(50) NOT NULL,
    subject_type    VARCHAR(30) NOT NULL,
    subject_id      BIGINT,
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_legal_audit_subject ON legal_audit_log (subject_type, subject_id);
