"""E2E: Phase 7 backend additions (customers, suspension, queue search, stats, timeline, notes, flags)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ng
from e2e_lib import *  # noqa

GW = "http://localhost:8080"
A = "http://localhost:8081"
cust = int(ng.claims(tok("customer"))["sub"])

s, page = call("admin", "GET", f"{GW}/api/v1/admin/customers?q={os.environ['C_EMAIL'].split('@')[0]}&page=0&size=10")
check("customer search by name/email via gateway", s == 200 and any(c["id"] == cust for c in page["content"]), (s, page))
me = next(c for c in page["content"] if c["id"] == cust)
s, byid = call("admin", "GET", f"{A}/api/v1/admin/customers/by-nepalgo-id/{me['nepalgoIdDisplay'].replace(' ', '%20')}")
check("lookup by NepalGo ID (display format with space)", s == 200 and byid["id"] == cust, (s, byid))
s, r = call("customer", "GET", f"{A}/api/v1/admin/customers?q=x")
check("customer token can't use admin customer API", s == 401, s)

bid = new_booking()                      # created while ACTIVE
s, r = call("admin", "POST", f"{A}/api/v1/admin/customers/{cust}/suspend", {"reason": "e2e: fraud check"})
check("suspend customer", s == 200 and r["status"] == "SUSPENDED", (s, r))
s, r = call("admin", "POST", f"{A}/api/v1/admin/customers/{cust}/suspend", {"reason": "again"})
check("double suspend -> 409", s == 409, (s, r))
s, dest = call(None, "GET", f"{C}/api/destinations/1")
s, r = call("customer", "POST", f"{B}/api/bookings", {"destinationId": 1, "itineraryVersionId": dest["defaultItinerary"]["id"],
            "partySize": 1, "arrivalDate": "2030-01-05", "guideRequested": False, "porterRequested": False})
check("suspended customer can't create a trip request -> 403", s == 403 and r.get("code") == "CUSTOMER_SUSPENDED", (s, r))
s, r = call("customer", "GET", f"{B}/api/bookings/{bid}")
check("...but can still view existing bookings", s == 200, s)
s, r = call("admin", "POST", f"{A}/api/v1/admin/customers/{cust}/reactivate", {"reason": "e2e: cleared"})
check("reactivate customer", s == 200 and r["status"] == "ACTIVE", (s, r))
s, h = call("admin", "GET", f"{A}/api/v1/admin/customers/{cust}/history")
check("customer history shows suspend + reactivate", s == 200 and [x["action"] for x in h][-2:] == ["CUSTOMER_SUSPENDED", "CUSTOMER_REACTIVATED"], h)

s, q = call("admin", "GET", f"{GW}/api/admin/bookings?sort=urgency&size=50")
order = {"VERIFYING_PAYMENT": 0, "PENDING_APPROVAL": 1, "AWAITING_PAYMENT": 2}
ranks = [order.get(b["status"], 3) for b in q["content"]]
check("queue sorted by urgency", s == 200 and ranks == sorted(ranks), ranks)
s, q = call("admin", "GET", f"{GW}/api/admin/bookings?q=NG-2026-0000&size=5")
check("queue search by invoice number", s == 200 and q["content"] and all(b["invoiceNumber"] for b in q["content"]), q)
s, q = call("admin", "GET", f"{GW}/api/admin/bookings?origin=ops_created&size=50")
check("queue filter by origin", s == 200 and all(b["origin"] == "ops_created" for b in q["content"]), q)
s, st = call("admin", "GET", f"{GW}/api/admin/bookings/stats")
check("dashboard stats", s == 200 and "PENDING_APPROVAL" in st and "MANUAL_AWAITING_BILL" in st, st)
s, n = call("admin", "PUT", f"{B}/api/admin/bookings/{bid}/internal-notes", {"internalNotes": "Called hotel, waiting for reply"})
check("internal notes saved", s == 200 and n["internalNotes"] == "Called hotel, waiting for reply", (s, n))
s, tl = call("admin", "GET", f"{GW}/api/admin/bookings/{bid}/timeline")
check("booking timeline has creation + notes", s == 200 and [e["actionType"] for e in tl] == ["BOOKING_CREATED", "INTERNAL_NOTES_UPDATED"], tl)
s, f1 = call("admin", "GET", f"{GW}/api/admin/feature-flags/booking")
s2, f2 = call("admin", "GET", f"{GW}/api/admin/feature-flags/payment")
check("feature flags (booking + payment) readable, all OFF", s == 200 and s2 == 200 and not any(f["enabled"] for f in f1 + f2), (f1, f2))
s, r = ng.multipart(f"{B}/api/bookings/{bid}/documents", {}, b"x", "p.png", tok("customer"))
check("dormant document upload unreachable while flag OFF -> 404", s == 404, (s, r))
call("customer", "POST", f"{B}/api/bookings/{bid}/withdraw")

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed")
