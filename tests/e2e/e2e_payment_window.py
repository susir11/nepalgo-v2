"""E2E: payment_window_hours lives in Billing Settings and drives booking expiry."""
import os, sys, subprocess, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # helpers live next to this file
from e2e_lib import *  # noqa

G = "http://localhost:8080"


def sql(q):
    r = subprocess.run(["docker", "exec", "-e", "PGPASSWORD=booking_dev_pw", "nepalgo-postgres", "psql", "-U", "booking_svc",
                        "-d", "nepalgo", "-tAc", q], capture_output=True, text=True)
    assert r.returncode == 0, r.stderr
    return r.stdout.strip()


s, st = call("admin", "GET", f"{G}/api/admin/billing-settings")
check("settings expose paymentWindowHours (default 48)", s == 200 and st.get("paymentWindowHours") == 48, (s, st))
orig = dict(st)
body = {k: st[k] for k in ("vatRegistered", "vatPercent", "serviceChargePercent", "sellerLegalName", "sellerPanVatNumber", "sellerAddress")}

s, r = call("admin", "PUT", f"{G}/api/admin/billing-settings", {**body, "paymentWindowHours": 0})
check("window 0 -> 400", s == 400, (s, r))
s, r = call("admin", "PUT", f"{G}/api/admin/billing-settings", {**body, "paymentWindowHours": 337})
check("window 337 -> 400", s == 400, (s, r))
s, r = call("admin", "PUT", f"{G}/api/admin/billing-settings", body)
check("old client without the field keeps the value", s == 200 and r["paymentWindowHours"] == 48, (s, r))

s, r = call(None, "GET", f"{G}/internal/settings/payment-window")
check("/internal not reachable through gateway", s in (401, 403, 404), s)

# Two AWAITING_PAYMENT bookings, both "issued" 3 hours ago.
a, b = new_booking(), new_booking()
for bid in (a, b):
    create_bill(bid, approve=True)
sql(f"UPDATE booking.bookings SET awaiting_payment_since = now() - interval '3 hours' WHERE id IN ({a},{b})")

# Window 48 h: nothing expires after an expiry run (job runs every 60 s).
time.sleep(70)
check("window 48h: 3h-old bookings stay AWAITING_PAYMENT",
      sql(f"SELECT string_agg(status, ',') FROM booking.bookings WHERE id IN ({a},{b})") == "AWAITING_PAYMENT,AWAITING_PAYMENT")

# Window 2 h: both expire on the next run, without restarting anything.
s, r = call("admin", "PUT", f"{G}/api/admin/billing-settings", {**body, "paymentWindowHours": 2})
check("set window to 2h", s == 200 and r["paymentWindowHours"] == 2, (s, r))
deadline = time.time() + 130
while time.time() < deadline:
    if sql(f"SELECT count(*) FROM booking.bookings WHERE id IN ({a},{b}) AND status='EXPIRED'") == "2":
        break
    time.sleep(5)
check("window 2h: both expire on the next run (no restart)",
      sql(f"SELECT count(*) FROM booking.bookings WHERE id IN ({a},{b}) AND status='EXPIRED'") == "2")

n = subprocess.run(["docker", "exec", "-e", "PGPASSWORD=payment_dev_pw", "nepalgo-postgres", "psql", "-U", "payment_svc", "-d", "nepalgo",
                    "-tAc", "SELECT count(*) FROM payment.payment_audit_log WHERE action_type='BILLING_SETTINGS_UPDATED' "
                    "AND details::text LIKE '%paymentWindowHours=2%'"], capture_output=True, text=True).stdout.strip()
check("settings change audited with the new window", n not in ("", "0"), n)

s, r = call("admin", "PUT", f"{G}/api/admin/billing-settings", {**body, "paymentWindowHours": 48})
check("restore 48h", s == 200 and r["paymentWindowHours"] == 48, (s, r))

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed; bookings {a},{b}")
