"""E2E: Create Booking Manually with a custom (ad-hoc) itinerary, spec 8b."""
import os, sys, subprocess, random, datetime, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # helpers live next to this file
from e2e_lib import *  # noqa
import ng

G = "http://localhost:8080"
cust_id = int(ng.claims(tok("customer"))["sub"])


def sql(q):
    r = subprocess.run(["docker", "exec", "-e", "PGPASSWORD=booking_dev_pw", "nepalgo-postgres", "psql", "-U", "booking_svc",
                        "-d", "nepalgo", "-tAc", q], capture_output=True, text=True)
    assert r.returncode == 0, r.stderr
    return r.stdout.strip()


def manual(extra, days_out=None):
    d = days_out or random.randint(1600, 3000)
    for _ in range(20):
        body = {"customerId": cust_id, "destinationId": 1, "partySize": 2, "guideRequested": True, "porterRequested": False,
                "arrivalDate": (datetime.date.today() + datetime.timedelta(days=d)).isoformat(),
                "internalNotes": "Agreed on WhatsApp (e2e)", **extra}
        s, r = call("admin", "POST", f"{G}/api/admin/bookings/manual", body)
        if not (s == 409 and r.get("code") == "DESTINATION_UNAVAILABLE"):
            return s, r
        d += random.randint(5, 9)
    return s, r


CUSTOM = [
    {"narrative": "Arrive Kathmandu, private transfer, briefing over dinner.", "hotelName": "Dwarika's", "hotelLat": 27.7096, "hotelLng": 85.3315},
    {"narrative": "Flight to Pokhara, sunset boat on Phewa Lake."},
    {"narrative": "Sunrise at Sarangkot, return flight.", "hotelName": "  "},
]

s, r = manual({"customItinerary": CUSTOM})
check("manual booking with 3 custom days -> AWAITING_PAYMENT", s == 200 and r.get("status") == "AWAITING_PAYMENT", (s, r))
bid = r.get("id")
row = sql(f"SELECT duration_days, coalesce(itinerary_version_id::text,'NULL'), origin, itinerary_snapshot::text FROM booking.bookings WHERE id={bid}").split("|", 3)
snap = json.loads(row[3])
check("duration = 3 custom days, no catalogue version, origin ops_created",
      row[0] == "3" and row[1] == "NULL" and row[2] == "ops_created", row[:3])
check("snapshot tier CUSTOM, days numbered 1..3 in order", snap["tier"] == "CUSTOM" and [d["dayNumber"] for d in snap["days"]] == [1, 2, 3]
      and snap["days"][1]["narrative"].startswith("Flight to Pokhara"), snap)
check("blank hotel name stored as null, coords kept", snap["days"][2]["hotelName"] is None and snap["days"][0]["hotelLat"] == 27.7096, snap["days"])

s, b = call("customer", "GET", f"{G}/api/bookings/{bid}")
check("customer sees the custom days", s == 200 and len(json.loads(b["itinerarySnapshot"])["days"]) == 3, (s, b))
s, a = call("admin", "GET", f"{G}/api/admin/bookings/{bid}")
check("admin detail has the custom snapshot", s == 200 and json.loads(a["itinerarySnapshot"])["tier"] == "CUSTOM", s)
aud = sql(f"SELECT details::text FROM booking.booking_audit_log WHERE booking_id={bid} AND action_type='BOOKING_CREATED_MANUALLY'")
check("audit says custom itinerary, 3 days", "custom itinerary, 3 days" in aud, aud)

s, r = manual({"customItinerary": [{"narrative": "ok day"}, {"narrative": "   "}]})
check("blank narrative -> 400", s == 400, (s, r))
s, r = manual({"customItinerary": [{"narrative": f"day {i}"} for i in range(61)]})
check("61 days -> 400", s == 400, (s, r))
s, r = manual({"customItinerary": [{"narrative": "x", "hotelLat": 123}]})
check("latitude out of range -> 400", s == 400, (s, r))
s, r = manual({"customItinerary": []})
check("empty custom list -> 400", s == 400, (s, r))

s, r = manual({})
check("no custom itinerary -> DEFAULT path still works", s == 200 and r.get("status") == "AWAITING_PAYMENT", (s, r))
if s == 200:
    row = sql(f"SELECT coalesce(itinerary_version_id::text,'NULL'), itinerary_snapshot->>'tier' FROM booking.bookings WHERE id={r['id']}").split("|")
    check("DEFAULT path: catalogue version id + tier DEFAULT", row[0] != "NULL" and row[1] == "DEFAULT", row)

s, r = call("customer", "POST", f"{G}/api/admin/bookings/manual", {"customerId": cust_id})
check("customer token cannot create manual bookings", s in (401, 403), s)

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed; custom booking {bid}")
