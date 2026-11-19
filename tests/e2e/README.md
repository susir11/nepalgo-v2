# End-to-end suites

Python scripts that drive real business flows through the running stack (gateway + services + Postgres + RustFS + Mailpit) and assert on API responses **and** on the resulting database rows, audit entries and e-mails.

> **Local development stack only.** The suites create bookings, bills, credits and throwaway users, and some steps edit the dev database directly (`docker exec nepalgo-postgres psql ...`), e.g. to move a trip into the past. Never point them at a shared or production environment.

## Prerequisites

- The full stack running locally (see the root README), Python 3.11+, Docker.
- A test **customer** account (Firebase e-mail/password) and the dev **SUPER_ADMIN** with an authenticator app.

```powershell
$env:FB_KEY  = "<Firebase web API key>"     # same value as FIREBASE_API_KEY
$env:C_EMAIL = "<test customer e-mail>"; $env:C_PASS = "<password>"
$env:A_EMAIL = "<dev admin e-mail>";     $env:A_PASS = "<password>"

python tests/e2e/ng.py customer-login
python tests/e2e/ng.py admin-login
python tests/e2e/ng.py admin-mfa 123456      # code from the authenticator app
```

Tokens are cached in `~/.nepalgo-e2e/tokens.json` (outside the repository; override with `NG_TOKEN_STORE`) and refreshed automatically. Staff sessions expire after 30 idle minutes; log in again when a suite reports `401`.

## Suites

| Script | Covers |
|---|---|
| `e2e_phase4.py` | Itemised bills, FX freeze, approve & issue, invoice numbering, amendments vs corrections, PDF, receipts, verification |
| `e2e_credits.py` | Travel credits: reserve, accept, reject, redeem, release, ledger, cancellation money split |
| `e2e_legal.py` | Versioned Terms/Privacy, immutability trigger, consent gate, tamper detection |
| `e2e_admin_backend.py` | Customer search/suspend/reactivate, queue filters, stats, timeline, notes, feature flags |
| `e2e_phase8.py` | Notification outbox, push/e-mail delivery, invoice attachment (Mailpit), support flag gate |
| `e2e_mfa.py` | SUPER_ADMIN must enrol TOTP before getting tokens; re-enrol refused |
| `e2e_delete.py` | In-app account deletion across services (needs `FB_KEY`, `A_PASS`; creates throwaway users) |
| `e2e_reviews.py` | Verified-traveller reviews, moderation state machine, public view without ids, audit |
| `e2e_payment_window.py` | Payment window from Billing Settings drives expiry without restarts |
| `e2e_manual_itinerary.py` | Manual bookings with a custom (ad-hoc) itinerary, validation, DEFAULT fallback |
| `e2e_due_presign.py` | "Pay by" deadline on bookings; presigned receipt links open |
| `e2e_photos.py` | Add photo by URL (SSRF guard: internal ports, metadata IP, private ranges, loopback, file://, non-images), gateway-served sizes, remove + stored files deleted. Adds and removes a CC-licensed test photo. |

Each script prints `PASS`/`FAIL` per check and a final `n/m passed`. Run them from the repository root, e.g. `python tests/e2e/e2e_reviews.py`.
