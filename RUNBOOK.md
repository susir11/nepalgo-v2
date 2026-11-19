# NepalGo — Runbook

Plain-language operating manual. If you are new: read **1**, then **2**. If something is on fire: jump to **8**.

---

## 1. What runs where

```
 Flutter app ─┐                         ┌─ auth-service ........ :8081  staff + customer login, NepalGo ID
 Admin (nginx)┴─► api-gateway :8080 ────┼─ catalog-service ..... :8082  destinations, itineraries, images, weather
                  (only public entry)   ├─ booking-service ..... :8083  booking state machine
                                        ├─ payment-service ..... :8084  bills, invoices, receipts, credits, refunds
        eureka-server :8761             ├─ legal-service ....... :8085  Terms/Privacy versions, consent
        (service registry)              ├─ notification-service  :8086  push, e-mail, in-app inbox (outbox + retries)
                                        └─ support-service ..... :8087  in-app chat (feature flag OFF)
 PostgreSQL 17 (one schema + one role per service) · S3 object storage (RustFS locally) · SMTP · Firebase (auth + push)
```

- Services call each other **directly** via Eureka on `/internal/**` with the `X-Internal-Api-Key` header. The gateway **never** routes `/internal/**`.
- Every service owns its tables (Flyway migrations run automatically at startup; Hibernate only validates).
- Money flows are **fail-closed**: if a dependency is down, the action is refused (HTTP 503) and nothing changes. Retrying is always safe (idempotent).

## 2. Running it

### Local development (IntelliJ)
1. Docker Desktop running → `docker compose up -d` (Postgres, pgAdmin, RustFS, Mailpit).
2. Start services from IntelliJ run configurations (env from `.env` via EnvFile). Start **eureka-server first**, gateway last.
3. After restarting a service wait ~30 s before cross-service calls (Eureka cache), otherwise you get a safe 503.
4. Admin portal: `cd admin-frontend; npm run dev` → http://localhost:5173.
5. App: see `mobile-app/README.md`. Dev e-mail inbox: http://localhost:8025 (Mailpit).

### Production-like, all in containers
```powershell
.\build-all.ps1                                   # ALWAYS rebuild JARs first — images copy target/*.jar
docker compose --profile app up -d --build
```
Gateway http://localhost:8080 · Admin http://localhost:8090 · Eureka http://localhost:8761.
Needs ~5 GB RAM free. Every container uses the **prod** profile: missing configuration stops it with
`FATAL: missing required environment variable(s): ...` (exit 64) — fix the listed variables.

## 3. Environment variables (prod)

| Variable | Used by | Notes |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | all except eureka/gateway | **Each service its own role.** `jdbc:postgresql://host:5432/nepalgo?sslmode=require` |
| `EUREKA_URL` | all except eureka | `http://eureka-server:8761/eureka` |
| `INTERNAL_API_KEY` | all except eureka/gateway | Shared service secret. 32+ random chars. Same value everywhere. |
| `JWT_STAFF_PUBLIC_KEY_PATH`, `JWT_CUSTOMER_PUBLIC_KEY_PATH` | all except eureka/gateway | PEM files mounted read-only |
| `JWT_STAFF_PRIVATE_KEY_PATH`, `JWT_CUSTOMER_PRIVATE_KEY_PATH` | auth | **Only auth-service may hold private keys.** |
| `TOTP_ENCRYPTION_KEY` | auth | Encrypts staff TOTP secrets at rest. Losing it = every admin re-enrols MFA. |
| `FIREBASE_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS` | auth, notification | Service-account JSON path (verify ID tokens / send push) |
| `ALLOWED_ORIGINS` | gateway | Comma list of real origins, e.g. `https://admin.nepalgo.com`. Never `*`. |
| `OPENWEATHERMAP_API_KEY` | catalog | |
| `RUSTFS_ACCESS_KEY`, `RUSTFS_SECRET_KEY`, `S3_ENDPOINT` | catalog, payment | Any S3-compatible store. Receipts bucket must be **private**. |
| `S3_PUBLIC_URL_BASE` | catalog | Base URL for destination photos: a CDN URL in front of the public images bucket, or `/media/destination-images` to serve them through the gateway (then set `MEDIA_ORIGIN`). Only keys are stored in the DB, so this can change any time. |
| `MEDIA_ORIGIN` | gateway | Image store origin for the `/media/destination-images/**` route (e.g. `http://rustfs:9000`). GET/HEAD only; the receipts bucket is never routed. |
| `S3_PUBLIC_ENDPOINT` | payment | Host put into presigned receipt links (what admins' browsers open, e.g. `https://files.example.com`). If a reverse proxy sits in front of the store it must pass the original `Host` header through, or the signature check fails. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM`, `OPS_ALERT_EMAIL` | notification | Transactional mail provider (SES, Postmark, …) |
| `IN_APP_SUPPORT_CHAT_ENABLED` | support | Feature flag, default `false` |

Never commit any of these. Locally they live in `.env` (git-ignored); in the cloud use the platform's secret manager.

## 4. Deploying to production

Recommended starting point (verify pricing first): **Google Cloud Run** or **AWS ECS Fargate** for the containers,
**Cloud SQL / RDS PostgreSQL 17** (automated backups + point-in-time recovery ON), a managed S3 bucket + CDN, an SMTP provider.

1. Create **two** environments: `staging` and `production` — separate databases, buckets, Firebase projects and secrets.
2. Database: create DB `nepalgo`, run `infra/postgres/init/01-schemas-and-roles.sql` once **with new strong passwords**.
3. CI (`.github/workflows/ci.yml`) builds and tests every push; on `master` it publishes images
   `ghcr.io/<owner>/nepalgo-<service>:<git-sha>`. Deploy by pointing each service at a SHA tag (never `latest`) → rollback = redeploy the previous SHA.
4. Deploy order: eureka → all services → gateway → admin frontend. Expose **only** the gateway and admin UI publicly (HTTPS).
5. Smoke test: `GET /actuator/health` on the gateway, log in to admin, open a destination in the app.

## 5. Secrets: rotation

| Secret | How to rotate | Impact |
|---|---|---|
| `INTERNAL_API_KEY` | Set new value on **all** services, redeploy together | Brief 401s between services during rollout |
| Customer/staff JWT keys | New key pair → auth (private) + all services (public) → redeploy | Everyone logs in again |
| DB passwords | `ALTER ROLE x_svc PASSWORD '...'` → update that service's secret → redeploy | That service only |
| Firebase service account | Create new key in Firebase console, replace file, delete old key | none |
| OpenWeatherMap key | **Rotate before launch** (dev key was shared during development) | none |

## 6. Database: backups and restore

- Cloud: provider automated backups + PITR (keep ≥ 14 days).
- Local / extra copy: `powershell -File infra\backup\backup.ps1` (pg_dump, keeps 14).
- **Monthly restore test (mandatory):** `powershell -File infra\backup\restore-test.ps1` restores the newest dump into a scratch
  database and compares row counts of every table. Last run 2026-11-18: **PASSED, 41 tables**.
- Legal text and consent are protected by DB triggers (published Terms immutable, consent append-only). A restore keeps them.
- Tax records (bills, invoice numbers) must be kept for the legally required period even if a customer deletes their account.

## 7. Monitoring and alerting

- Health: every service exposes `/actuator/health/liveness` and `/readiness` (used by the container platform + Docker `HEALTHCHECK`).
- Uptime check (e.g. UptimeRobot / Cloud Monitoring) on `https://api.<domain>/actuator/health` every minute → alert on 2 failures.
- Logs are JSON (ECS format) in prod → ship to the platform's log service. Alert on:
  - any `ERROR` from payment-service or booking-service,
  - `LEGAL INTEGRITY FAILURE` (legal-service startup — someone altered published Terms in the DB),
  - `Delivery ... FAILED permanently` (notification-service gave up after 6 attempts).
- Notification health query: `SELECT status, count(*) FROM notification.deliveries GROUP BY status;`

## 8. Incident playbook

| Symptom | Likely cause | Fix |
|---|---|---|
| Container exits with code 64 | Missing env var (message lists them) | Set them, redeploy |
| Admin action returns 503 "…unavailable, nothing was changed" | A dependency service down or just restarted | Check Eureka (`:8761`); wait 30 s after restarts; retry — safe |
| Customer says "no e-mail" | Check `GET /api/admin/notifications/{id}/deliveries` (status, attempts, last_error) | Fix SMTP; FAILED rows can be re-queued: `UPDATE notification.deliveries SET status='PENDING', attempts=0, next_attempt_at=now() WHERE id=…` |
| "Possible double claim" on a receipt | Same receipt code or same screenshot used on another booking | Check both bookings in Payoneer; never verify blindly |
| Admin lost their phone (MFA) | — | Another SUPER_ADMIN: Admin team → Reset 2-step. The admin re-enrols at next login (mandatory for SUPER_ADMIN). |
| Only one SUPER_ADMIN and they lost MFA | — | DB: `UPDATE auth.staff_users SET mfa_enabled=false, totp_secret_encrypted=NULL WHERE email='…';` then they enrol at next login. Log the reason. |
| Terms published by mistake | Published versions can't be edited/deleted | Publish a corrected new version; customers mid-checkout simply re-agree |
| Image/receipt links broken | S3 endpoint/credentials | Check `S3_ENDPOINT`; receipt links use `S3_PUBLIC_ENDPOINT` (browser-reachable host) and expire after 5 minutes by design. `SignatureDoesNotMatch` = proxy changed the Host header |

## 9. Before go-live checklist

- [ ] Legal review of Terms v1 + Privacy v1; fill `[Company Legal Name]` etc. by publishing v2 (Admin → Policy content).
- [ ] Nepali accountant confirms invoice format (VAT Rules 2053, Rule 17) and numbering.
- [ ] Billing settings: real legal name, PAN/VAT, address. Company profile content: real contacts.
- [ ] Remove "(e2e)" from onboarding slide 1 (Admin → Onboarding & company).
- [ ] Rotate OpenWeatherMap key; generate production JWT key pairs, `INTERNAL_API_KEY`, `TOTP_ENCRYPTION_KEY`.
- [ ] Firebase: production project; FlutterFire config files (`google-services.json`, `GoogleService-Info.plist`) for social login + push.
- [ ] Android build (Android Studio + SDK), iOS build (macOS + Xcode); store listings; privacy disclosures.
- [ ] `ALLOWED_ORIGINS` set to real domains; HTTPS everywhere; only gateway + admin UI public.
- [ ] Automated DB backups + PITR on; first restore test run on staging.
- [ ] Customer account deletion workflow (spec 15.2) — app currently routes requests to support.
