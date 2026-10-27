-- Runs ONCE, as superuser, against the "nepalgo" database.
-- DEV-ONLY passwords. Production roles are provisioned separately (Phase 9).
-- This is a .sql file on purpose: a .sh script saved with Windows CRLF line endings
-- fails inside the Linux container.

-- One login role per service (like one settlement account per correspondent:
-- each service can post only to its own ledger).
CREATE ROLE auth_svc         LOGIN PASSWORD 'auth_dev_pw';
CREATE ROLE catalog_svc      LOGIN PASSWORD 'catalog_dev_pw';
CREATE ROLE booking_svc      LOGIN PASSWORD 'booking_dev_pw';
CREATE ROLE payment_svc      LOGIN PASSWORD 'payment_dev_pw';
CREATE ROLE legal_svc        LOGIN PASSWORD 'legal_dev_pw';
CREATE ROLE notification_svc LOGIN PASSWORD 'notification_dev_pw';
CREATE ROLE support_svc      LOGIN PASSWORD 'support_dev_pw';

-- One schema per service, OWNED by that service's role.
-- Ownership is what lets Flyway create tables there and nowhere else.
CREATE SCHEMA auth         AUTHORIZATION auth_svc;
CREATE SCHEMA catalog      AUTHORIZATION catalog_svc;
CREATE SCHEMA booking      AUTHORIZATION booking_svc;
CREATE SCHEMA payment      AUTHORIZATION payment_svc;
CREATE SCHEMA legal        AUTHORIZATION legal_svc;
CREATE SCHEMA notification AUTHORIZATION notification_svc;
CREATE SCHEMA support      AUTHORIZATION support_svc;

-- Each role's default schema, so unqualified table names resolve to its own schema.
ALTER ROLE auth_svc         SET search_path = auth;
ALTER ROLE catalog_svc      SET search_path = catalog;
ALTER ROLE booking_svc      SET search_path = booking;
ALTER ROLE payment_svc      SET search_path = payment;
ALTER ROLE legal_svc        SET search_path = legal;
ALTER ROLE notification_svc SET search_path = notification;
ALTER ROLE support_svc      SET search_path = support;

-- Lock everything else down: no implicit access for anybody.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE nepalgo FROM PUBLIC;
GRANT CONNECT ON DATABASE nepalgo TO auth_svc, catalog_svc, booking_svc,
    payment_svc, legal_svc, notification_svc, support_svc;
