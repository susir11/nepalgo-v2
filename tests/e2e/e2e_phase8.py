"""E2E Phase 8: notification-service (intake, idempotency, outbox -> Mailpit email + PDF, inbox) and support-service flag gate."""
import os, sys, json, time, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ng
from e2e_lib import *  # noqa

N, SUP, GW, MAIL = "http://localhost:8086", "http://localhost:8087", "http://localhost:8080", "http://localhost:8025"
KEY = "nepalgo-local-dev-internal-key"
cust = int(ng.claims(tok("customer"))["sub"])


def internal(path, body):
    req = urllib.request.Request(N + path, data=json.dumps(body).encode(), method="POST",
                                 headers={"Content-Type": "application/json", "X-Internal-Api-Key": KEY})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def mailpit(query):
    with urllib.request.urlopen(f"{MAIL}/api/v1/search?query={urllib.parse.quote(query)}") as r:
        return json.loads(r.read())


def wait_mail(query, secs=60):
    for _ in range(secs):
        m = mailpit(query)
        if m.get("messages_count", 0) > 0:
            return m["messages"][0]
        time.sleep(1)
    return None

import urllib.parse

# ---------------- security of the event intake
s, _ = ng.http("POST", f"{N}/internal/notifications/events", {"type": "BILL_ISSUED", "idempotencyKey": "x"})
check("event intake without internal key -> 401", s == 401, s)
s, _ = ng.http("GET", f"{GW}/internal/notifications/events")
check("gateway does not expose /internal", s == 404, s)

# ---------------- PAYMENT_VERIFIED for a confirmed booking: push + email with invoice PDF
s, trips = call("customer", "GET", f"{B}/api/bookings?size=50")
confirmed = next(b for b in trips["content"] if b["status"] == "CONFIRMED" and b["invoiceNumber"])
key = f"E2E-PAYMENT_VERIFIED:{confirmed['id']}:{int(time.time())}"
ev = {"type": "PAYMENT_VERIFIED", "idempotencyKey": key, "customerId": cust, "bookingId": confirmed["id"],
      "data": {"destinationName": confirmed["destinationNameSnapshot"], "voucherCode": confirmed["voucherCode"], "invoiceNumber": confirmed["invoiceNumber"]}}
s1, r1 = internal("/internal/notifications/events", ev)
s2, r2 = internal("/internal/notifications/events", ev)
check("event accepted (202)", s1 == 202, (s1, r1))
check("same idempotency key -> same notification (no duplicate)", s2 == 202 and r1["notificationId"] == r2["notificationId"], (r1, r2))
s, r = internal("/internal/notifications/events", {"type": "NOPE", "idempotencyKey": "bad-" + key})
check("unknown event type -> 400", s == 400, (s, r))

msg = wait_mail(f'subject:"{confirmed["destinationNameSnapshot"]}" to:traveller@example.com {confirmed["voucherCode"]}')  # this run's mail only
check("confirmation e-mail delivered (Mailpit) to lead-booker address", msg is not None, msg)
if msg:
    with urllib.request.urlopen(f"{MAIL}/api/v1/message/{msg['ID']}") as r:
        full = json.loads(r.read())
    att = [a for a in full.get("Attachments", []) if a["ContentType"] == "application/pdf"]
    check("e-mail carries the invoice PDF attachment", len(att) == 1 and att[0]["Size"] > 1000, full.get("Attachments"))
    check("e-mail body mentions the voucher code", confirmed["voucherCode"] in full.get("Text", ""), full.get("Text", "")[:200])

s, inbox = call("customer", "GET", f"{GW}/api/notifications")
mine = [n for n in inbox["content"] if n["id"] == r1["notificationId"]]
check("customer in-app inbox shows it, unread", s == 200 and mine and not mine[0]["read"] and inbox["unread"] >= 1, inbox)
s, _ = call("customer", "POST", f"{GW}/api/notifications/{r1['notificationId']}/read")
s, inbox2 = call("customer", "GET", f"{GW}/api/notifications")
check("mark as read", [n for n in inbox2["content"] if n["id"] == r1["notificationId"]][0]["read"], inbox2)
s, _ = call("customer", "POST", f"{GW}/api/notifications/devices", {"token": f"e2e-fake-fcm-token-{int(time.time())}", "platform": "WEB"})
check("device token registration", s == 204, s)
s, _ = call("admin", "GET", f"{GW}/api/notifications")
check("staff token can't read customer inbox", s == 401, s)

# ---------------- REAL producer path: customer uploads a receipt -> booking-service publishes -> ops e-mail
bid = new_booking()
create_bill(bid, approve=False) if False else None   # (needs admin; skipped)
awaiting = [b for b in trips["content"] if b["status"] == "AWAITING_PAYMENT" and b["invoiceNumber"]]
if awaiting:
    b = awaiting[0]
    consent(b["id"])
    code = f"PYN-E2E8-{int(time.time())}"
    s, r = ng.multipart(f"{B}/api/bookings/{b['id']}/payment-receipt", {"paymentReceiptCode": code}, png(int(time.time()) % 99999), "r.png", tok("customer"))
    check("receipt upload accepted", s == 200 and r.get("status") == "VERIFYING_PAYMENT", (s, r))
    m = wait_mail(f'to:ops@nepalgo.local subject:"{b["voucherCode"]}"')
    check("booking-service published RECEIPT_SUBMITTED after commit -> ops alert e-mail", m is not None and code in json.dumps(m), m)
else:
    print("   (skip: no AWAITING_PAYMENT booking with an issued bill to pay)")
call("customer", "POST", f"{B}/api/bookings/{bid}/withdraw")

# ---------------- support-service while flag is OFF
s, st = ng.http("GET", f"{GW}/api/support-status")
check("support status public: in-app chat disabled", s == 200 and st == {"inAppChatEnabled": False}, (s, st))
s, _ = call("customer", "GET", f"{GW}/api/support/threads")
check("chat routes unreachable while OFF (404 even with valid token)", s == 404, s)
s, _ = ng.http("GET", f"{SUP}/api/support/threads")
check("…and 404 without a token too (flag gate runs before security)", s == 404, s)
s, _ = ng.http("POST", f"{SUP}/api/admin/support/threads/1/close")
check("admin inbox routes also 404 while OFF", s == 404, s)

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed")
