"""E2E: destination reviews (booking-service V6) through the real stack.
Uses the shared helpers e2e_lib.py / ng.py in this folder."""
import os, sys, subprocess, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # helpers live next to this file
from e2e_lib import *  # noqa

G = "http://localhost:8080"


def sql(q):
    # Test-only data shift: move a booking's dates into the past so the trip counts as finished.
    r = subprocess.run(["docker", "exec", "-e", "PGPASSWORD=booking_dev_pw", "nepalgo-postgres", "psql", "-U", "booking_svc",
                        "-d", "nepalgo", "-tAc", q], capture_output=True, text=True)
    assert r.returncode == 0, r.stderr
    return r.stdout.strip()


# --- setup: one confirmed booking (future) ---
bid = new_booking()
create_bill(bid)
pay_and_confirm(bid)

# 1. eligibility before the trip
s, r = call("customer", "GET", f"{G}/api/bookings/{bid}/review")
check("GET my review via gateway (future trip) -> not eligible TRIP_NOT_FINISHED",
      s == 200 and r["eligible"] is False and r["ineligibleReason"] == "TRIP_NOT_FINISHED" and r["review"] is None, (s, r))
s, r = call("customer", "PUT", f"{G}/api/bookings/{bid}/review", {"rating": 5, "body": "Too early to say much here."})
check("PUT before trip end -> 409 TRIP_NOT_FINISHED", s == 409 and r.get("code") == "TRIP_NOT_FINISHED", (s, r))

# 2. trip finished (shift dates into the past)
sql(f"UPDATE booking.bookings SET arrival_date = current_date - 30 WHERE id = {bid}")
s, r = call("customer", "GET", f"{G}/api/bookings/{bid}/review")
check("after trip -> eligible", s == 200 and r["eligible"] is True, (s, r))

s, r = call("customer", "PUT", f"{G}/api/bookings/{bid}/review", {"rating": 6, "body": "x"})
check("validation: rating 6 + short body -> 400", s == 400, (s, r))

body = f"Run {random.randint(1, 10**9)}: guide was brilliant, teahouses warm, great planning."
s, r = call("customer", "PUT", f"{G}/api/bookings/{bid}/review", {"rating": 4, "title": "Great trek", "body": body})
check("submit review -> PENDING", s == 200 and r["status"] == "PENDING" and r["rating"] == 4, (s, r))
rid = r.get("id")

s, pub = call(None, "GET", f"{G}/api/reviews/destinations/1?size=50")
check("public list (no token) does NOT show pending review", s == 200 and all(i["id"] != rid for i in pub["items"]), (s, pub))
check("public summary has all 5 buckets", s == 200 and sorted(pub["summary"]["ratingCounts"].keys()) == ["1", "2", "3", "4", "5"], pub.get("summary"))
before_count = pub["summary"]["reviewCount"]

s, r = call(None, "POST", f"{G}/api/reviews/destinations/1", {})
check("public chain: non-GET refused", s in (401, 403), (s, r))

# 3. admin queue + moderation
s, q = call("admin", "GET", f"{G}/api/admin/reviews?status=PENDING&size=100")
mine = [i for i in q.get("items", []) if i["id"] == rid] if s == 200 else []
check("admin PENDING queue contains review with voucher + destination", s == 200 and mine and mine[0]["voucherCode"]
      and mine[0]["destinationName"] and q["pendingCount"] >= 1, (s, q))
check("author name is shortened (Test T.)", mine and mine[0]["authorName"] == "Test T.", mine)

s, r = call("customer", "GET", f"{G}/api/admin/reviews")
check("customer token on admin endpoint -> 401/403", s in (401, 403), s)

s, r = call("admin", "POST", f"{G}/api/admin/reviews/{rid}/moderate", {"action": "REJECT"})
check("reject without note -> 400 NOTE_REQUIRED", s == 400 and r.get("code") == "NOTE_REQUIRED", (s, r))
s, r = call("admin", "POST", f"{G}/api/admin/reviews/{rid}/moderate", {"action": "HIDE", "note": "x"})
check("hide a PENDING review -> 409 INVALID_REVIEW_STATE", s == 409 and r.get("code") == "INVALID_REVIEW_STATE", (s, r))

s, r = call("admin", "POST", f"{G}/api/admin/reviews/{rid}/moderate", {"action": "REJECT", "note": "Please remove the phone number."})
check("reject with note -> REJECTED", s == 200 and r["status"] == "REJECTED", (s, r))
s, r = call("customer", "GET", f"{G}/api/bookings/{bid}/review")
check("customer sees REJECTED + reason", s == 200 and r["review"]["status"] == "REJECTED"
      and r["review"]["moderationNote"] == "Please remove the phone number.", (s, r))

s, r = call("customer", "PUT", f"{G}/api/bookings/{bid}/review", {"rating": 5, "title": "Great trek", "body": body + " Edited."})
check("customer edits -> back to PENDING, note cleared", s == 200 and r["status"] == "PENDING" and r["moderationNote"] is None, (s, r))

s, r = call("admin", "POST", f"{G}/api/admin/reviews/{rid}/moderate", {"action": "PUBLISH"})
check("publish -> PUBLISHED", s == 200 and r["status"] == "PUBLISHED" and r["moderatedBy"], (s, r))

s, pub = call(None, "GET", f"{G}/api/reviews/destinations/1?size=50")
item = [i for i in pub["items"] if i["id"] == rid]
check("public list shows published review, no customer/booking ids", item and item[0]["rating"] == 5
      and "customerId" not in item[0] and "bookingId" not in item[0] and item[0]["authorName"] == "Test T.", item)
check("public count +1 and average in 1..5", pub["summary"]["reviewCount"] == before_count + 1
      and 1 <= pub["summary"]["averageRating"] <= 5, pub["summary"])

s, r = call("admin", "POST", f"{G}/api/admin/reviews/{rid}/moderate", {"action": "HIDE", "note": "Test take-down"})
check("hide published -> HIDDEN", s == 200 and r["status"] == "HIDDEN", (s, r))
s, pub = call(None, "GET", f"{G}/api/reviews/destinations/1?size=50")
check("hidden review gone from public list", all(i["id"] != rid for i in pub["items"]), pub["items"][:3])
s, r = call("customer", "GET", f"{G}/api/bookings/{bid}/review")
check("HIDE note stays internal (customer sees no note)", s == 200 and r["review"]["moderationNote"] is None, (s, r))

# 4. audit trail
n = sql(f"SELECT count(*) FROM booking.booking_audit_log WHERE booking_id = {bid} AND action_type LIKE 'REVIEW_%'")
check("audit log has review events (submit, reject, edit, publish, hide = 5)", n == "5", n)

# 5. someone else's booking -> 404 (other booking ids aren't the customer's)
other = sql(f"SELECT id FROM booking.bookings WHERE customer_id <> (SELECT customer_id FROM booking.bookings WHERE id = {bid}) LIMIT 1")
if other:
    s, r = call("customer", "GET", f"{G}/api/bookings/{other}/review")
    check("another customer's booking -> 404", s == 404, (s, r))

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed")
