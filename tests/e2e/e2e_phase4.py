import tempfile
"""End-to-end Phase 4 money path: booking -> bill -> issue -> pay -> reject (D1) -> pay -> verify/lock -> correction."""
import os, sys, json, random, datetime, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ng

B, P, C = "http://localhost:8083", "http://localhost:8084", "http://localhost:8082"
results = []


def tok(who):
    d = ng.load()
    if who == "admin" and ng.exp_of(d.get("admin", "x.e30.x")) < __import__("time").time() + 30:
        ng.admin_refresh(); d = ng.load()
    if who == "customer" and ng.exp_of(d.get("customer", "x.e30.x")) < __import__("time").time() + 30:
        ng.customer_refresh(); d = ng.load()
    return d.get(who)


def call(who, method, url, body=None):
    return ng.http(method, url, body, tok(who) if who in ("admin", "customer") else None)


def check(name, cond, info=""):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {info}"))


s, dest = call(None, "GET", f"{C}/api/destinations/1")
itin = dest["defaultItinerary"]["id"]
# random far-future date so we don't overlap earlier test bookings
arrival = (datetime.date.today() + datetime.timedelta(days=random.randint(400, 1500))).isoformat()

s, bk = call("customer", "POST", f"{B}/api/bookings", {"destinationId": 1, "itineraryVersionId": itin, "partySize": 2,
             "arrivalDate": arrival, "guideRequested": True, "porterRequested": False, "specialRequestText": "Anniversary"})
check("customer creates booking", s == 200 and bk.get("status") == "PENDING_APPROVAL", (s, bk))
bid = bk["id"]

# --- approve with NO bill -> payment says BILL_NOT_FOUND, booking must not move
s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/approve")
check("approve without bill is refused (fail-closed)", s == 404 and r.get("code") == "BILL_NOT_FOUND", (s, r))

# --- create bill without Payoneer link
lines = [
    {"description": "Kathmandu hotel, 2 nights", "category": "ACCOMMODATION", "itineraryDay": 1, "quantity": 2, "unitPriceNpr": 5000},
    {"description": "Private car Kathmandu to Lukla airport", "category": "TRANSPORT", "itineraryDay": 2, "quantity": 1, "unitPriceNpr": 12500},
    {"description": "Licensed trekking guide (requested)", "category": "GUIDE", "quantity": 3, "unitPriceNpr": 2500},
]
s, bill = call("admin", "POST", f"{P}/api/admin/bills", {"bookingId": bid, "lineItems": lines})
check("admin creates DRAFT bill", s == 200 and bill.get("status") == "DRAFT" and bill.get("invoiceNumber") is None, (s, bill))
billId = bill["id"]
t = bill["totals"]
check("bill math: subtotal 30000, service 3000, grand NPR 33000",
      float(t["subtotalNpr"]) == 30000 and float(t["serviceChargeNpr"]) == 3000 and float(t["grandTotalNpr"]) == 33000, t)
_, _rates = call("admin", "GET", f"{P}/api/admin/fx-rates")
RATE = next(float(r["rate"]) for r in _rates if r["status"] == "APPROVED" and r["currencyPair"] == "USD/NPR")
check("fx frozen from current approved rate", float(bill["fxRate"]) == RATE, (bill["fxRate"], RATE))
check("amount in words", bill["grandTotalNprInWords"] == "Rupees Thirty Three Thousand Only", bill["grandTotalNprInWords"])
check("title is 'Invoice' when not VAT-registered", bill["documentTitle"] == "Invoice", bill["documentTitle"])

s, r = call("admin", "POST", f"{P}/api/admin/bills", {"bookingId": bid, "lineItems": lines})
check("second bill for same booking -> 409", s == 409 and r.get("code") == "BILL_ALREADY_EXISTS", (s, r))

s, r = call("customer", "GET", f"{P}/api/bills/booking/{bid}")
check("customer cannot see un-issued bill", s == 404, (s, r))

s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/approve")
check("approve without Payoneer link -> payment reason passed through", s == 409 and "Payoneer" in r.get("detail", ""), (s, r))

# --- amend: add link + porter line (DRAFT edit => amendments logged)
lines2 = lines + [{"description": "Porter, 3 days", "category": "GUIDE", "quantity": 3, "unitPriceNpr": 1800}]
s, bill = call("admin", "PUT", f"{P}/api/admin/bills/{billId}",
               {"payoneerLink": "https://link.payoneer.com/Token?t=TEST123", "lineItems": lines2})
check("DRAFT edit applied in place (same bill id)", s == 200 and bill["id"] == billId and len(bill["lineItems"]) == 4, (s, bill))

s, appr = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/approve")
check("Approve & Issue Bill -> AWAITING_PAYMENT", s == 200 and appr.get("status") == "AWAITING_PAYMENT", (s, appr))
s, bill = call("admin", "GET", f"{P}/api/admin/bills/{billId}")
inv = bill.get("invoiceNumber")
check("invoice number assigned NG-YYYY-NNNNNN", s == 200 and inv and inv.startswith(f"NG-{datetime.date.today().year}-"), bill)
s, mine = call("customer", "GET", f"{B}/api/bookings/{bid}")
check("booking carries invoice copy", mine.get("invoiceNumber") == inv, mine)

s, cv = call("customer", "GET", f"{P}/api/bills/booking/{bid}")
check("customer sees issued bill in USD only", s == 200 and cv["currency"] == "USD" and "Npr" not in json.dumps(cv), (s, cv))
# 30000+5400 = 35400 ; +10% = 38940 ; /137.25 = 283.715 -> 283.72
from decimal import Decimal, ROUND_HALF_UP
EXPECTED = float((Decimal("38940") / Decimal(str(RATE))).quantize(Decimal("0.01"), ROUND_HALF_UP))
check(f"customer payable USD = 38940/{RATE} = {EXPECTED}", float(cv["payableUsd"]) == EXPECTED, cv.get("payableUsd"))

# --- amend after issue (still unpaid): allowed, number unchanged
s, bill2 = call("admin", "PUT", f"{P}/api/admin/bills/{billId}",
                {"payoneerLink": "https://link.payoneer.com/Token?t=TEST124", "lineItems": lines2})
check("edit after issue but before payment keeps invoice number", s == 200 and bill2["invoiceNumber"] == inv, (s, bill2))

# --- customer pays
s, r = call("customer", "POST", f"{B}/api/bookings/{bid}/identity", {"legalName": "Test Traveller", "passportNumber": "PA1234567", "email": "traveller@example.com"})
check("customer submits identity", s == 200, (s, r))
import e2e_lib
r = e2e_lib.consent(bid)
check("customer agrees to Terms (legal-service ConsentRecord)", r.get("id"), r)
import struct, zlib
def png(seed):
    # tiny valid 1x1 PNG; seed changes the pixel so each image has a different SHA-256
    raw = b"\x00" + bytes([seed % 256, (seed * 7) % 256, 50])
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")

def upload(code, data, name="receipt.png"):
    return ng.multipart(f"{B}/api/bookings/{bid}/payment-receipt", {"paymentReceiptCode": code}, data, name, tok("customer"))

s, r = upload(f"PYN-{bid}-A", b"<html>not an image</html>", "receipt.png")
check("fake image (wrong bytes, .png name) rejected", s == 400 and "JPEG, PNG or WebP" in r.get("detail", ""), (s, r))
seed = random.randint(1, 10_000)
s, r = upload(f"PYN-{bid}-A", png(seed))
check("receipt image uploaded -> VERIFYING_PAYMENT", s == 200 and r.get("status") == "VERIFYING_PAYMENT", (s, r))
s, rec = call("admin", "GET", f"{P}/api/admin/receipts?bookingId={bid}")
check("admin sees receipt with presigned private link", s == 200 and "X-Amz-Signature" in rec[0]["viewUrl"], (s, rec))
img = urllib.request.urlopen(rec[0]["viewUrl"]).read()
check("presigned link returns the exact uploaded image", img == png(seed), len(img))
s, _ = ng.http("GET", rec[0]["viewUrl"].split("?")[0])
check("same object WITHOUT signature is refused (private bucket)", s in (401, 403), s)

s, r = call("admin", "PUT", f"{P}/api/admin/bills/{billId}", {"payoneerLink": "https://x.y/z", "lineItems": lines})
check("bill edit blocked while payment is being verified", s == 409, (s, r))

# --- D1 reject-payment
s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/reject-payment", {"rejectionReason": "Receipt screenshot unreadable"})
check("reject-payment -> back to AWAITING_PAYMENT (D1)", s == 200 and r.get("status") == "AWAITING_PAYMENT", (s, r))
s, mine = call("customer", "GET", f"{B}/api/bookings/{bid}")
check("customer sees rejection reason", mine.get("rejectionReason") == "Receipt screenshot unreadable", mine)

# resubmit using a code ALREADY used on another booking (previous e2e run) -> verify must be blocked
s, others = call("admin", "GET", f"{B}/api/admin/bookings?page=0&size=50&status=CONFIRMED")
dup_code = None
for b in others.get("content", []):
    if b["id"] == bid:
        continue
    s2, recs = call("admin", "GET", f"{P}/api/admin/receipts?bookingId={b['id']}")
    if s2 == 200 and recs:
        dup_code = recs[0]["receiptCode"]
        break
print("   (duplicate-code scenario " + ("uses " + dup_code if dup_code else "skipped: no earlier stored receipt yet") + ")")
if dup_code:
    s, r = upload(dup_code.lower().replace("-", " "), png(seed + 1))   # same code, disguised formatting
    check("resubmit with a code used on another booking", s == 200, (s, r))
    s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/verify-payment")
    check("verify BLOCKED: duplicate receipt code (normalized match)", s == 409 and r.get("code") == "DUPLICATE_RECEIPT", (s, r))
    call("admin", "POST", f"{B}/api/admin/bookings/{bid}/reject-payment", {"rejectionReason": "Code belongs to another booking"})

s, r = upload(f"PYN-{bid}-B", png(seed + 2))
check("customer resubmits receipt", s == 200 and r.get("status") == "VERIFYING_PAYMENT", (s, r))

s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/verify-payment")
check("verify-payment -> CONFIRMED", s == 200 and r.get("status") == "CONFIRMED", (s, r))
s, bill = call("admin", "GET", f"{P}/api/admin/bills/{billId}")
check("bill LOCKED with buyer name from identity form", bill["status"] == "LOCKED" and bill["buyerName"] == "Test Traveller", bill)

# --- edit a LOCKED bill => correction
s, r = call("admin", "PUT", f"{P}/api/admin/bills/{billId}", {"payoneerLink": bill["payoneerLink"], "lineItems": lines})
check("locked edit without reason -> 400", s == 400 and r.get("code") == "BAD_BILL_REQUEST", (s, r))
s, fix = call("admin", "PUT", f"{P}/api/admin/bills/{billId}",
              {"payoneerLink": bill["payoneerLink"], "lineItems": lines, "reason": "Porter was not provided"})
check("locked edit => NEW bill, new number, links to original",
      s == 200 and fix["id"] != billId and fix["correctsBillId"] == billId and fix["invoiceNumber"] != inv
      and fix["status"] == "LOCKED", (s, fix))
s, orig = call("admin", "GET", f"{P}/api/admin/bills/{billId}")
check("original untouched, marked superseded", orig["superseded"] and orig["invoiceNumber"] == inv and len(orig["lineItems"]) == 4, orig)
s, r = call("admin", "PUT", f"{P}/api/admin/bills/{billId}", {"lineItems": lines, "reason": "again"})
check("editing a superseded bill -> 409", s == 409, (s, r))
s, cv = call("customer", "GET", f"{P}/api/bills/booking/{bid}")
check("customer now sees the corrected bill", s == 200 and cv["invoiceNumber"] == fix["invoiceNumber"], cv)

s, tl = call("admin", "GET", f"{P}/api/admin/bills/timeline?bookingId={bid}")
types = [e["type"] for e in tl]
check("timeline has created/amended/issued/locked/corrected", all(x in types for x in
      ["BILL_CREATED", "BILL_AMENDED", "BILL_ISSUED", "BILL_LOCKED", "BILL_CORRECTED"]), types)
corr = [e for e in tl if e["type"] == "BILL_CORRECTED"][0]
check("correction event links back to original invoice", corr["linkedInvoiceNumber"] == inv and corr["reason"], corr)

# --- PDF invoices
def get_bytes(url, who):
    req = urllib.request.Request(url, headers={"Authorization": "Bearer " + tok(who)})
    with urllib.request.urlopen(req) as r:
        return r.status, r.headers.get("Content-Type"), r.headers.get("Content-Disposition"), r.read()
st, ct, cd, pdf = get_bytes(f"{P}/api/admin/bills/{fix['id']}/pdf", "admin")
check("admin PDF of correction bill", st == 200 and ct == "application/pdf" and pdf[:5] == b"%PDF-" and fix["invoiceNumber"] in cd, (st, ct, cd))
open(os.path.join(tempfile.gettempdir(), f"{fix['invoiceNumber']}.pdf"), "wb").write(pdf)
st, ct, cd, pdf2 = get_bytes(f"{P}/api/admin/bills/{billId}/pdf", "admin")
open(os.path.join(tempfile.gettempdir(), f"{inv}.pdf"), "wb").write(pdf2)
st, ct, cd, pdf3 = get_bytes(f"{P}/api/bills/booking/{bid}/pdf", "customer")
check("customer downloads own current invoice PDF", st == 200 and pdf3[:5] == b"%PDF-" and fix["invoiceNumber"] in cd, (st, cd))

# --- security spot checks
s, _ = call("customer", "GET", f"{P}/api/admin/bills/{billId}")
check("customer token on admin bill API -> 401", s == 401, s)
s, _ = call("admin", "GET", f"{P}/api/bills/booking/{bid}")
check("staff token on customer bill API -> 401", s == 401, s)
s, _ = ng.http("GET", f"{B}/internal/bookings/{bid}")
check("internal API without key -> 401", s == 401, s)
s, _ = ng.http("GET", f"{P}/internal/bills/status?bookingId={bid}")
check("payment internal API without key -> 401", s == 401, s)

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed; booking {bid}, invoices {inv} -> {fix.get('invoiceNumber')}")
