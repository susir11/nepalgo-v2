"""Live: paymentDueAt on bookings + presigned receipt link host."""
import os, sys, datetime, random, time, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # helpers live next to this file
from e2e_lib import *  # noqa
import ng

G = "http://localhost:8080"


def iso(s):
    return datetime.datetime.fromisoformat(s.replace("Z", "+00:00"))


bid = new_booking()
s, b = call("customer", "GET", f"{G}/api/bookings/{bid}")
check("PENDING_APPROVAL: no paymentDueAt", s == 200 and b.get("paymentDueAt") is None and "paymentDueAt" in b, b)

create_bill(bid, approve=True)
# the window cache fills on the expiry job's next successful run (<= ~60 s after startup)
deadline = time.time() + 150
while time.time() < deadline:
    s, b = call("customer", "GET", f"{G}/api/bookings/{bid}")
    if b.get("paymentDueAt"): break
    time.sleep(5)
since = subprocess.run(["docker", "exec", "-e", "PGPASSWORD=booking_dev_pw", "nepalgo-postgres", "psql", "-U", "booking_svc", "-d", "nepalgo",
                        "-tAc", f"SELECT to_char(awaiting_payment_since AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') FROM booking.bookings WHERE id={bid}"],
                       capture_output=True, text=True).stdout.strip()
ok = b.get("paymentDueAt") and abs((iso(b["paymentDueAt"]) - iso(since)).total_seconds() - 48 * 3600) < 1
check("AWAITING_PAYMENT + issued bill: paymentDueAt = issued + 48 h", ok, (b.get("paymentDueAt"), since))
s, a = call("admin", "GET", f"{G}/api/admin/bookings/{bid}")
check("admin detail carries the same paymentDueAt", s == 200 and a.get("paymentDueAt") == b.get("paymentDueAt"), (a.get("paymentDueAt"), b.get("paymentDueAt")))

# receipt -> VERIFYING_PAYMENT: deadline stops; admin presigned link host (dev: localhost:9000)
call("customer", "POST", f"{B}/api/bookings/{bid}/identity", {"legalName": "Test Traveller", "passportNumber": "PA1234567", "email": "traveller@example.com"})
consent(bid)
s, r = ng.multipart(f"{B}/api/bookings/{bid}/payment-receipt", {"paymentReceiptCode": f"PYN-{bid}-{random.randint(1, 10**9)}"},
                    png(random.randint(1, 10**9)), "r.png", tok("customer"))
check("receipt uploaded -> VERIFYING_PAYMENT", s == 200 and r.get("status") == "VERIFYING_PAYMENT", (s, r))
s, b = call("customer", "GET", f"{G}/api/bookings/{bid}")
check("VERIFYING_PAYMENT: no deadline any more", b.get("paymentDueAt") is None, b.get("paymentDueAt"))
s, rec = call("admin", "GET", f"{G}/api/admin/receipts?bookingId={bid}")
first = rec[0] if isinstance(rec, list) and rec else (rec.get("content", [None])[0] if isinstance(rec, dict) else None)
url = next((v for v in (first or {}).values() if isinstance(v, str) and v.startswith("http")), "")
check("dev presigned link uses browser-reachable localhost:9000", url.startswith("http://localhost:9000/payment-receipts/"), (s, str(rec)[:300]))
if url:
    import urllib.request
    try:
        code = urllib.request.urlopen(url, timeout=10).status
    except Exception as e:
        code = getattr(e, "code", str(e))
    check("presigned link actually downloads (200)", code == 200, code)

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed; booking {bid}")
