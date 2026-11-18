# NepalGo v2 — project memory (read first, keep this file short)

Owner: Susir (IT & Ops Officer, aiming to relocate to Germany). Portfolio project; he must be able to explain everything in interviews.

## How to work with Susir
- Brief like a senior engineer: checklist first, then step-by-step "your task: do X, then reply Y".
- Comment code for learning/interview prep; use fintech/remittance analogies for concepts.
- For any Spring Initializr instruction spell out: group, artifact, package, packaging, Java, full dependency list.
- Windows: use `.\mvnw.cmd` in PowerShell, Docker Desktop must be running, watch `.txt` suffix on extensionless files, YAML nesting, rebuild JARs before `docker compose up --build`.
- Save credits: read `docs/SPEC-INDEX.md` and open only the needed spec range. Don't re-read whole files. Run `/clear` between phases.

## Stack
Spring Boot 4.1.x, Spring Cloud 2025.1.3, Java 21, Maven wrapper. Postgres 17 (host port 5433, schema+role per service), Flyway owns schema, Hibernate `ddl-auto: validate`. RustFS (S3, path-style) replaces MinIO. Flutter app + React admin come later.
Ports: eureka 8761, gateway 8080, auth 8081, catalog 8082, booking 8083, payment 8084, pgAdmin 5050, RustFS 9000/9001.
Services run from the repo root as working dir, so key paths have no `../` (e.g. `secrets/staff-public.pem`). Env vars come from `.env` via IntelliJ EnvFile plugin.

## SECRETS — never read, print, or commit
`.env`, `secrets/` (PEMs, Firebase JSON), Postman environment file (holds dev passwords).

## Security model
RS256 JWT, separate customer/staff keys; each service validates with the public key only. Audiences: `nepalgo-customer`, `nepalgo-admin`, `nepalgo-admin-pending` (MFA step; same staff key, no `role` claim). Staff TOTP MFA, BCrypt 12, lockout 5 fails/15 min, Bucket4j rate limits. Customer login via Firebase. Staff roles: SUPER_ADMIN, OPS. Separate security chains via `securityMatcher` + `@Order`.
Service-to-service: `@LoadBalanced` RestClient (Eureka), no gateway; `/internal/**` protected by `INTERNAL_API_KEY` (Phase 4).

## Pitfalls already solved (don't repeat)
- Every main class sets `TimeZone.setDefault(UTC)` (Windows `Asia/Katmandu` breaks Postgres).
- tsvector/jsonb: `columnDefinition`, `@JdbcTypeCode(SqlTypes.JSON)`. jsonb needs valid JSON: audit helper wraps details as `{"detail": "..."}`.
- Native queries: `CAST(:p AS type)`, never `:p::type`. No JPQL `date_add`; use native interval SQL.
- `@Transactional` rollback swallows writes → `noRollbackFor`.
- Lombok doesn't copy `@Qualifier` to generated constructors → write explicit constructor. Keep a `@Primary` plain `RestClient.Builder` beside the `@LoadBalanced` one.
- Lombok needs `annotationProcessorPaths` in pom.
- IntelliJ: add each service as a Maven project; set JVM target 21.

## Booking state machine
PENDING_APPROVAL → AWAITING_PAYMENT → VERIFYING_PAYMENT → CONFIRMED; plus REJECTED, EXPIRED, WITHDRAWN, CANCELLED_TIERED, CANCELLED_FORCE_MAJEURE. D1 applied: reject-payment returns to AWAITING_PAYMENT and restarts the window.

## Pitfalls (Phase 4)
- `open-in-view: false` → build response DTOs INSIDE the @Transactional service method (lazy lineItems after commit = LazyInitializationException = 500 after the write already committed).
- Spring Data ignores repository interfaces nested in another class → top-level files.

## Status (update as you go)
- Phases 1 (eureka, gateway, auth), 2 (catalog), 3 (booking): built and tested via Postman. Phase 3 commit/push NOT confirmed — check `git status`.
- Phase 4 progress (2026-11-16, all verified live by e2e script, 37/37):
  - Step 2 FX maker-checker + billing settings: done, tested.
  - Step 3 bills (payment V2): Bill/LineItem/Amendment/Correction, BillCalculator (unit-tested), AmountInWords (lakh/crore), one "Edit" PUT = amendment if DRAFT / correction if LOCKED, invoice `NG-YYYY-NNNNNN` per AD year via UPSERT counter, customer view USD-only.
  - Integration: booking approve = "Approve & Issue Bill" (calls payment `/internal/bills/issue` first, fail-closed, idempotent); verify-payment locks bill; D1 reject-payment → AWAITING_PAYMENT + fresh window; receipt requires issued bill; manual bookings use `POST /api/admin/bookings/{id}/issue-bill`; expiry job skips un-issued bills. Booking V3 dropped payoneer_link/itemized_bill_json; PAYMENT_REJECTED enum removed.
  - Audience checks added to payment + booking decoders. `/internal/**` uses `X-Internal-Api-Key` (`INTERNAL_API_KEY`, dev fallback in yaml — remove in Phase 9). Catalog decoder still lacks audience check (TODO).
- DECISION (Susir): NO Bikram Sambat dates — AD only; add BS later if needed.
  - Also done: PDF invoice (OpenPDF 3, `org.openpdf.*`), CSV export, receipts (multipart → private bucket `payment-receipts`, magic-byte check, presigned 5-min links, double-claim block by normalized code OR image SHA-256), travel credits (reserve/accept/reject/redeem/release, ledger + amend log), cancellation money (cash only for cash paid, credit part back as credit), refund queue, gateway routes for all services (`/internal/**` unrouted), audience checks in catalog too, Postman payment folders.
- **PHASE 4 COMPLETE** — committed `ea14436` on branch `phase-4-payments` (not pushed, not merged). E2E: `e2e_phase4.py` 45/45, `e2e_credits.py` 29/29 (scratchpad, needs `e2e_lib.py`, `ng.py`).
- Known follow-ups: after restarting a service wait ~30 s for Eureka before cross-service calls (else fail-closed 503); `payment_window_hours` still in booking yaml (spec wants it in Billing Settings); notifications are TODO hooks for Phase 8; presigned URLs use localhost:9000 (Phase 9 needs public endpoint).
- **PHASE 5 COMPLETE** (branch `phase-5-legal`, off phase-4): legal-service :8085 (schema `legal`). LegalDocument (TERMS/PRIVACY, DRAFT→PUBLISHED, one current per type via partial unique index, SHA-256 fingerprint, DB trigger makes published rows immutable, startup integrity re-hash), ConsentRecord (exact version + hash + per-card expand/check times, append-only trigger, only CURRENT version accepted → 409 TERMS_VERSION_CHANGED), site_content ONBOARDING/COMPANY_PROFILE (versioned JSON). v1 Terms/Privacy seeded verbatim from spec (needs legal review; placeholders like [Company Legal Name]). booking payment-receipt asks legal `/internal/consents/check` (fail-closed); booking `/consent` endpoint + `consent_confirmed` column removed (V5). Gateway routes /api/legal, /api/content, /api/admin/legal, /api/admin/content. E2E `e2e_legal.py` 39/39; Phase 4 suites still green.
- legal-service has NO IntelliJ run config yet (needs: add `legal-service/pom.xml` as Maven project). Run it with `java -jar legal-service/target/legal-service-0.0.1-SNAPSHOT.jar` from repo root — no env needed (key paths default to `secrets/*-public.pem`).
- Jackson: Boot 4 MVC uses Jackson 3 (`tools.jackson.*`, inject `JsonMapper`) for request/response JSON types; Jackson 2 ObjectMapper still exists (used only for audit-string serialization).
- **PHASE 7 (branch `phase-7-admin`)**: backend done + e2e 17/17 (`e2e_admin_backend.py`): auth customer search/suspend/reactivate (V3, audited), booking suspension guard (fail-closed), queue filters + urgency sort, `/stats`, `/{id}/timeline`, internal notes, feature-flag endpoints (`/api/admin/feature-flags/{booking|payment}`), dormant doc upload gated (404). Frontend `admin-frontend/` (React 19 + Vite 7 + TS + Tailwind 4 tokens from 16.1 + TanStack Query + react-router 7 + react-hook-form + dnd-kit + Radix Dialog): all 15 screens of spec 15.2/15.3, confirm-before-commit dialog, merged cross-service timeline. `npm run dev` → http://localhost:5173 (Vite proxies /api → gateway :8080). Type-check + build pass; NOT yet clicked through in a real browser.
- Known gaps: SUPER_ADMIN without MFA enrolled can still log in (auth-service only "nudges") → enforce server-side; manual booking uses destination's DEFAULT itinerary (no ad-hoc itinerary yet); staff refresh token idle 30 min / absolute 8 h.
- **PHASE 6 (branch `phase-7-admin`, commit after 6a24c93)**: Flutter app `mobile-app/` (Flutter 3.50 at `C:\src\flutter\bin`, Material via `package:material_ui`, Riverpod 3, go_router 18). All customer screens of spec 3 incl. full checkout + gated Policy Review + receipt upload + QR + wallet cards + appearance. `flutter analyze` clean, `flutter build web` OK, runs in Chrome against the real stack (served on :5180). Gateway got dev-only CORS for `http://localhost:[*]`. Customer `BookingResponse` now includes `destinationId` + `itinerarySnapshot`.
- **PHASE 8 (branch `phase-7-admin`)**: notification-service :8086 (schema `notification`): `/internal/notifications/events` intake (idempotency key, spec-19 matrix in `EventCatalog`), delivery OUTBOX worked by `DeliveryWorker` (`FOR UPDATE SKIP LOCKED`, exponential backoff, max 6), channels FCM push (firebase-admin; tokens via `POST /api/notifications/devices`) + SMTP email (dev: **Mailpit** container, UI http://localhost:8025; PAYMENT_VERIFIED attaches invoice PDF from payment `/internal/bills/pdf`), customer inbox `/api/notifications`, admin alerts `/api/admin/notifications`. Producers: booking + payment `client/NotificationPublisher` (after-commit, virtual-thread async, best-effort). support-service :8087 (schema `support`): threads/messages/callbacks + shared admin inbox; flag `in_app_support_chat_enabled` OFF → gate filter (highest precedence) returns 404 for `/api/support/**` + `/api/admin/support/**`; `/api/support-status` public. E2E `e2e_phase8.py` 15/15 relevant + flag gate 404s + flag-ON customer chat 9/9. Admin: Alerts page (unread badge), Support inbox page, live support flag. Flutter: inbox bell + screen, chat/callback screens (shown only when flag ON). Admin-side support inbox not yet exercised against a live staff token.
- **PHASE 9 (deployment)**: `application-prod.yaml` per service (env-only, no fallbacks, ECS JSON logs, health probes; gateway prod hides `/actuator/gateway`, CORS from `ALLOWED_ORIGINS`). Dockerfile per service (layered jar, non-root, `REQUIRED_ENV` check in `docker-entrypoint.sh` → exit 64 naming missing vars), admin-frontend nginx image (security headers via snippet, `/api` proxy). `docker compose --profile app up -d --build` runs everything (run `build-all.ps1` first). Backups: `infra/backup/backup.ps1` + `restore-test.ps1` (PASSED 41 tables 2026-11-18). CI: `.github/workflows/ci.yml` (gitleaks, 9 services, admin, flutter, GHCR images on master). `RUNBOOK.md` = ops manual. SUPER_ADMIN MFA now enforced server-side (login → enrollment-only pending token); re-enroll of MFA-enabled account refused (409). E2E `e2e_mfa.py` 9/9.
- **PHASE 10 (branch `phase-10-polish`)**: 65 unit tests (tiers, refund/credit split, receipts, consent, notification matrix, Flutter format); admin ErrorBoundary; Flutter 404 page; Postman Phase 7-9 folders; `docs/APP-STORE-CHECKLIST.md` (blockers: in-app account deletion, Sign in with Apple, public privacy URL; app id still `com.nepalgo.nepalgo`). Full regression 2026-11-18: phase4 45/45, credits 29/29, legal 39/39, admin 17/17, phase8 16/16, mfa 9/9. e2e scripts must not hard-code the FX rate (read current APPROVED) and must search Mailpit by this run's voucher.
- **Android (2026-11-18)**: Android Studio + SDK installed (platforms 36+37, NDK 28.2 + 30, cmdline-tools). `flutter build apk --debug --dart-define=API_BASE=http://10.0.2.2:8080` OK (first build slow). Emulator AVD `Medium_Phone_API_37.0` (`emulator -avd Medium_Phone_API_37.0 -memory 2048`); install with `adb install -r`, app id `com.nepalgo.nepalgo`. Verified live onboarding on emulator. Gradle can't download SDK parts reliably here → Susir installs missing ones via Android Studio SDK Manager.
- **Account deletion (branch `account-deletion`)**: `DELETE /api/v1/auth/me` body `{"confirmation":"DELETE"}` → booking `/internal/bookings/customers/{id}/erase` (withdraw open, null name/passport/email/special request; 409 if VERIFYING_PAYMENT) → notification `/internal/notifications/customers/{id}/erase` → Firebase deleteUser → retire NepalGo ID + delete row (auth V4: audit FK ON DELETE SET NULL). Fail-closed, idempotent. Flutter Profile dialog (type DELETE). Bills/consents kept. E2E `e2e_delete.py` 17/17 (throwaway Firebase user + throwaway admin, needs FB_KEY, A_PASS env). Fixed: customer refresh with unknown token returned 500 → now 401.
- Low-RAM machine: run new services with `java -Xmx160m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -jar ...` from repo root (no IntelliJ run configs for legal/notification/support yet).
- Phase 6 gaps: social login (needs FlutterFire config files from Susir), account-deletion backend (button routes to WhatsApp), push notifications (Phase 8), Android build needs Android Studio + SDK, destination reviews (no backend yet). Onboarding slide 1 headline still says "(e2e)" from a test → fix in admin portal → Onboarding & company → Publish.
- Claude may stop/restart services itself: `taskkill` allowed in `.claude/settings.local.json`; relaunch via IntelliJ MCP run configs (they carry EnvFile). Test helper + e2e scripts live in the session scratchpad (`ng.py`, `e2e_phase4.py`).
- Phase 4 decisions approved: all defaults (D1–D7). Feature flag `maker-checker-segregation-enforced` default false; frozen `fx_rate` copy on each bill; partial unique index allows one APPROVED FX rate per pair.
- Deferred: rotate OpenWeatherMap key.
- Browser testing: Claude-in-Chrome tools don't load in this setup. Working method: launch visible Chrome with `--remote-debugging-port=9333 --user-data-dir=<scratch profile>`, then IntelliJ's `chrome-devtools` CLI (`mcp__idea__get_chrome_connect_cli`) `start --browserUrl http://127.0.0.1:9333` (its own launch mode is always headless). Wrap every CLI call in `timeout`.

## Testing
`postman/NepalGo.postman_collection.json` (auto token scripts; env vars authUrl/catalogUrl/bookingUrl/…). Payment-service folders still to be added. Import env separately; fill firebaseApiKey + customerPassword.
