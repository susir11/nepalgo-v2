"""Shared helpers for the NepalGo e2e scripts."""
import os, sys, random, datetime, time, struct, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ng

B, P, C = "http://localhost:8083", "http://localhost:8084", "http://localhost:8082"
results = []

LINES = [
    {"description": "Kathmandu hotel, 2 nights", "category": "ACCOMMODATION", "itineraryDay": 1, "quantity": 2, "unitPriceNpr": 5000},
    {"description": "Private car Kathmandu to Lukla airport", "category": "TRANSPORT", "itineraryDay": 2, "quantity": 1, "unitPriceNpr": 12500},
    {"description": "Licensed trekking guide", "category": "GUIDE", "quantity": 3, "unitPriceNpr": 2500},
]


def tok(who):
    d = ng.load()
    if who == "admin" and ng.exp_of(d.get("admin", "x.e30.x")) < time.time() + 30:
        ng.admin_refresh(); d = ng.load()
    if who == "customer" and ng.exp_of(d.get("customer", "x.e30.x")) < time.time() + 30:
        ng.customer_refresh(); d = ng.load()
    return d.get(who)


def call(who, method, url, body=None):
    return ng.http(method, url, body, tok(who) if who in ("admin", "customer") else None)


def check(name, cond, info=""):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(info)[:600]}"))


def png(seed):
    raw = b"\x00" + bytes([seed % 256, (seed * 7) % 256, (seed * 13) % 256])
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw + struct.pack(">I", seed))) + chunk(b"IEND", b""))


def new_booking(days_out=None):
    s, dest = call(None, "GET", f"{C}/api/destinations/1")
    days = days_out if days_out is not None else random.randint(400, 1500)
    for _ in range(30):   # retry on date overlap with earlier test bookings
        arrival = (datetime.date.today() + datetime.timedelta(days=days)).isoformat()
        s, bk = call("customer", "POST", f"{B}/api/bookings", {"destinationId": 1, "itineraryVersionId": dest["defaultItinerary"]["id"],
                     "partySize": 2, "arrivalDate": arrival, "guideRequested": True, "porterRequested": False})
        if s == 200:
            return bk["id"]
        days += 3 if days_out is not None and days < days_out + 9 else random.randint(1, 3)
    raise SystemExit(f"could not create booking: {s} {bk}")


def create_bill(bid, approve=False):
    s, bill = call("admin", "POST", f"{P}/api/admin/bills",
                   {"bookingId": bid, "payoneerLink": "https://link.payoneer.com/Token?t=E2E", "lineItems": LINES})
    if s != 200:
        raise SystemExit(f"bill create failed {s} {bill}")
    if approve:
        s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/approve")
        if s != 200:
            raise SystemExit(f"approve failed {s} {r}")
    return bill


L = "http://localhost:8085"


def iso(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def consent_body(bid, doc_id, skew=0):
    """What the app sends when the Pay button is tapped: per-card open/tick times."""
    now = datetime.datetime.now(datetime.timezone.utc)
    sec = lambda a, b: {"expandedAt": iso(now - datetime.timedelta(seconds=a) + datetime.timedelta(seconds=skew)),
                        "checkedAt": iso(now - datetime.timedelta(seconds=b) + datetime.timedelta(seconds=skew))}
    return {"bookingId": bid, "termsDocumentId": doc_id,
            "cancellationMatrix": sec(90, 80), "forceMajeureWeather": sec(70, 60), "identityLiability": sec(50, 40)}


def consent(bid):
    s, pr = call(None, "GET", f"{L}/api/legal/policy-review")
    s, r = call("customer", "POST", f"{L}/api/legal/consents", consent_body(bid, pr["termsDocumentId"]))
    assert s in (200, 201), (s, r)
    return r


def pay_and_confirm(bid):
    s, b = call("customer", "GET", f"{B}/api/bookings/{bid}")
    if b["status"] == "PENDING_APPROVAL":
        s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/approve")
        assert s == 200, (s, r)
    call("customer", "POST", f"{B}/api/bookings/{bid}/identity", {"legalName": "Test Traveller", "passportNumber": "PA1234567", "email": "traveller@example.com"})
    consent(bid)
    s, r = ng.multipart(f"{B}/api/bookings/{bid}/payment-receipt", {"paymentReceiptCode": f"PYN-{bid}-{random.randint(1, 10**9)}"},
                        png(random.randint(1, 10**9)), "r.png", tok("customer"))
    assert s == 200, (s, r)
    s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/verify-payment")
    assert s == 200 and r["status"] == "CONFIRMED", (s, r)
