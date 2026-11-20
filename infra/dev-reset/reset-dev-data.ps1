# Resets the LOCAL development database to a clean "day one" state, like a fresh production
# launch: no bookings, bills, receipts, credits, refunds, consents, reviews, notifications or
# support chats; three curated destinations; staff = the seeded admin + ops only.
#
# KEEPS: customer accounts (and their active sessions + push devices), retired NepalGo IDs
# (must never be reused), legal documents + onboarding/company content, billing settings,
# FX rates and their audit trail.
#
# SAFETY: only talks to the local Docker container 'nepalgo-postgres', refuses to run without
# -Yes, and always writes a full backup first (infra\backup\backup.ps1). Restore = pg_restore
# of that dump. NEVER point this at a shared or production database.
#
#   powershell -File infra\dev-reset\reset-dev-data.ps1 -Yes
param([switch]$Yes)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..\..')

if (-not $Yes) { Write-Error "This DELETES all bookings, bills and notifications in the local dev DB. Re-run with -Yes." }
$running = docker ps --filter "name=^nepalgo-postgres$" --format '{{.Names}}'
if ($running -ne 'nepalgo-postgres') { Write-Error "Local container nepalgo-postgres is not running." }

Write-Host "1/3 Backup..."
powershell -NoProfile -File infra\backup\backup.ps1
if ($LASTEXITCODE -ne 0) { Write-Error "Backup failed - nothing was changed." }

# Each block runs as the schema's OWN role in one transaction (-1): all or nothing per service.
# TRUNCATE (not DELETE) also bypasses the row-level append-only triggers on consent records,
# which is exactly what a full reset needs; published legal documents are not touched.
$steps = [ordered]@{
  'booking' = @"
TRUNCATE booking.reviews, booking.booking_audit_log, booking.bookings RESTART IDENTITY;
"@
  'payment' = @"
TRUNCATE payment.bill_amendments, payment.bill_corrections, payment.bill_line_items, payment.bills,
         payment.payment_receipts, payment.refund_requests, payment.store_credit_amendments,
         payment.store_credits RESTART IDENTITY CASCADE;
DELETE FROM payment.invoice_sequences;                       -- invoice numbers start again at NG-YYYY-000001
DELETE FROM payment.payment_audit_log WHERE subject_type NOT IN ('FX_RATE', 'BILLING_SETTINGS');
"@
  'legal' = @"
TRUNCATE legal.consent_records RESTART IDENTITY;
"@
  'notification' = @"
TRUNCATE notification.deliveries, notification.notifications RESTART IDENTITY CASCADE;
DELETE FROM notification.device_tokens WHERE customer_id IS NULL;
"@
  'support' = @"
TRUNCATE support.chat_messages, support.chat_threads, support.callback_requests RESTART IDENTITY CASCADE;
"@
  'auth' = @"
-- test staff created by the e2e suites (e2e-*@...): their audit rows first (FK), then the accounts
DELETE FROM auth.admin_audit_log WHERE actor_staff_id IN (SELECT id FROM auth.staff_users WHERE email LIKE 'e2e-%')
                                    OR target_staff_id IN (SELECT id FROM auth.staff_users WHERE email LIKE 'e2e-%');
DELETE FROM auth.staff_users WHERE email LIKE 'e2e-%';          -- their refresh tokens cascade
-- expired / revoked sessions (active ones stay, so nobody is logged out)
DELETE FROM auth.customer_refresh_tokens WHERE revoked_at IS NOT NULL OR expires_at < now();
DELETE FROM auth.staff_refresh_tokens    WHERE revoked_at IS NOT NULL OR expires_at < now();
"@
  'catalog' = (Get-Content (Join-Path $PSScriptRoot 'seed-catalogue.sql') -Raw)
}

Write-Host "2/3 Reset..."
foreach ($svc in $steps.Keys) {
  $out = $steps[$svc] | docker exec -i -e "PGPASSWORD=${svc}_dev_pw" nepalgo-postgres psql -U "${svc}_svc" -d nepalgo -v ON_ERROR_STOP=1 -1 -q 2>&1
  if ($LASTEXITCODE -ne 0) { Write-Error "Reset of '$svc' failed (that schema rolled back): $out" }
  Write-Host "   $svc ok"
}

Write-Host "3/3 Result:"
foreach ($q in @(@('booking', 'SELECT count(*) FROM booking.bookings'), @('catalog', 'SELECT string_agg(name, '', '' ORDER BY id) FROM catalog.destinations'))) {
  $r = $q[1] | docker exec -i -e "PGPASSWORD=$($q[0])_dev_pw" nepalgo-postgres psql -U "$($q[0])_svc" -d nepalgo -tA
  Write-Host "   $($q[0]): $r"
}
Write-Host "Done. Restart nothing: services read fresh data on the next request."
