"""E2E: in-app account deletion (spec 15.2) with a THROWAWAY Firebase customer."""
import os, sys, time, datetime, random, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ng
from e2e_lib import check, results, LINES
import hmac, hashlib, struct, base64

A, B, P, C, N = "http://localhost:8081", "http://localhost:8083", "http://localhost:8084", "http://localhost:8082", "http://localhost:8086"
FB = "https://identitytoolkit.googleapis.com/v1/accounts:"
KEY = os.environ["FB_KEY"]
EMAIL, PASSWORD = f"e2e-delete-{int(time.time())}@example.com", "Throwaway#12345"


def sql(db_user, q):
    r = subprocess.run(["docker", "exec", "-i", "nepalgo-postgres", "psql", "-U", db_user, "-d", "nepalgo", "-tA", "-c", q],
                       capture_output=True, text=True)
    return (r.stdout + r.stderr).strip()


def totp(secret):
    key = base64.b32decode(secret.upper() + "=" * (-len(secret) % 8))
    h = hmac.new(key, struct.pack(">Q", int(time.time() // 30)), hashlib.sha1).digest()
    o = h[-1] & 0x0F
    return f"{(struct.unpack('>I', h[o:o + 4])[0] & 0x7FFFFFFF) % 1_000_000:06d}"


# --- a throwaway SUPER_ADMIN for the admin-side setup (deactivated at the end)
ADMIN_EMAIL = f"e2e-delete-admin-{int(time.time())}@nepalgo.local"
sql("auth_svc", f"INSERT INTO staff_users (email, password_hash, role, mfa_enabled, must_change_password) "
                f"SELECT '{ADMIN_EMAIL}', password_hash, 'SUPER_ADMIN', false, false FROM staff_users WHERE email='admin@nepalgo.local'")
_, lr = ng.http("POST", A + "/api/v1/admin/auth/login", {"email": ADMIN_EMAIL, "password": os.environ["A_PASS"]})
_, en = ng.http("POST", A + "/api/v1/admin/auth/mfa/enroll/start", {"pendingToken": lr["pendingToken"]})
_, at = ng.http("POST", A + "/api/v1/admin/auth/mfa/enroll/confirm", {"pendingToken": lr["pendingToken"], "code": totp(en["secret"])})
ADMIN = at["accessToken"]
def call(_who, method, url, body=None):
    return ng.http(method, url, body, ADMIN)


# --- a fresh customer: Firebase sign-up -> our token
s, fb = ng.http("POST", FB + "signUp?key=" + KEY, {"email": EMAIL, "password": PASSWORD, "returnSecureToken": True})
assert s == 200, (s, fb)
s, t = ng.http("POST", A + "/api/v1/auth/firebase/exchange", {"idToken": fb["idToken"]})
assert s == 200, (s, t)
TOKEN, REFRESH = t["accessToken"], t["refreshToken"]
s, me = ng.http("GET", A + "/api/v1/auth/me", None, TOKEN)
CID, NGID = int(ng.claims(TOKEN)["sub"]), me["nepalgoId"].replace(" ", "")
print("throwaway customer", CID, NGID)

# --- two bookings: one left PENDING, one approved (bill issued) with identity details
_, dest = ng.http("GET", C + "/api/destinations/1")
def book(text):
    arrival = (datetime.date.today() + datetime.timedelta(days=random.randint(400, 1500))).isoformat()
    s, b = ng.http("POST", B + "/api/bookings", {"destinationId": 1, "itineraryVersionId": dest["defaultItinerary"]["id"], "partySize": 2,
                   "arrivalDate": arrival, "guideRequested": False, "porterRequested": False, "specialRequestText": text}, TOKEN)
    assert s == 200, (s, b)
    return b["id"]
pending = book("Vegetarian meals please")
paid_path = book("Honeymoon")
s, bill = call("admin", "POST", f"{P}/api/admin/bills", {"bookingId": paid_path, "payoneerLink": "https://link.payoneer.com/Token?t=E2EDEL", "lineItems": LINES})
s2, ap = call("admin", "POST", f"{B}/api/admin/bookings/{paid_path}/approve")
s3, _ = ng.http("POST", f"{B}/api/bookings/{paid_path}/identity", {"legalName": "Throw Away", "passportNumber": "PX9999999", "email": EMAIL}, TOKEN)
check("setup: booking approved with bill + identity", s == 200 and s2 == 200 and s3 == 200, (s, s2, s3))
s, _ = ng.http("POST", N + "/api/notifications/devices", {"token": f"e2e-device-{CID}", "platform": "ANDROID"}, TOKEN)
check("setup: device token registered", s in (200, 201, 204), s)
s, _ = call("admin", "POST", f"{A}/api/v1/admin/customers/{CID}/suspend", {"reason": "e2e: audit row before deletion"})
call("admin", "POST", f"{A}/api/v1/admin/customers/{CID}/reactivate", {"reason": "e2e"})
check("setup: admin audit rows reference the customer", s == 200, s)

# --- guard rails
s, r = ng.http("DELETE", A + "/api/v1/auth/me", {"confirmation": "yes"}, TOKEN)
check("without typed DELETE confirmation -> 400, nothing deleted", s == 400, (s, r))
sql("booking_svc", f"UPDATE bookings SET status='VERIFYING_PAYMENT' WHERE id={paid_path}")
s, r = ng.http("DELETE", A + "/api/v1/auth/me", {"confirmation": "DELETE"}, TOKEN)
check("payment being verified -> 409 ACCOUNT_DELETION_BLOCKED", s == 409 and r.get("code") == "ACCOUNT_DELETION_BLOCKED", (s, r))
check("...and the account still exists", ng.http("GET", A + "/api/v1/auth/me", None, TOKEN)[0] == 200)
check("...and the pending request was NOT withdrawn (all-or-nothing)",
      sql("booking_svc", f"SELECT status FROM bookings WHERE id={pending}") == "PENDING_APPROVAL")
sql("booking_svc", f"UPDATE bookings SET status='AWAITING_PAYMENT' WHERE id={paid_path}")

# --- the real deletion
s, r = ng.http("DELETE", A + "/api/v1/auth/me", {"confirmation": "DELETE"}, TOKEN)
check("delete account -> 204", s == 204, (s, r))
check("customer row gone", sql("auth_svc", f"SELECT count(*) FROM customers WHERE id={CID}") == "0")
check("NepalGo ID retired forever", sql("auth_svc", f"SELECT count(*) FROM retired_nepalgo_ids WHERE nepalgo_id='{NGID}'") == "1")
check("admin audit rows kept (reference cleared)",
      sql("auth_svc", f"SELECT count(*) FROM admin_audit_log WHERE details LIKE '%audit row before deletion%' AND target_customer_id IS NULL") != "0")
rows = sql("booking_svc", f"SELECT id||':'||status||':'||coalesce(lead_booker_legal_name,'-')||':'||coalesce(lead_booker_passport_number,'-')"
                          f"||':'||coalesce(lead_booker_email,'-')||':'||coalesce(special_request_text,'-') FROM bookings WHERE customer_id={CID} ORDER BY id")
check("both open bookings withdrawn, name/passport/e-mail/request erased, rows kept",
      rows == f"{pending}:WITHDRAWN:-:-:-:-\n{paid_path}:WITHDRAWN:-:-:-:-", rows)
s, b = call("admin", "GET", f"{P}/api/admin/bills/{bill['id']}")
check("bill (tax record) untouched in payment-service", s == 200 and b.get("bookingId") == paid_path, (s, b.get("status")))
check("device tokens erased", sql("notification_svc", f"SELECT count(*) FROM device_tokens WHERE customer_id={CID}") == "0")
s, r = ng.http("POST", FB + "signInWithPassword?key=" + KEY, {"email": EMAIL, "password": PASSWORD, "returnSecureToken": True})
check("Firebase login deleted (can't sign in any more)", s == 400, (s, r.get("error", {}).get("message")))
s, _ = ng.http("POST", A + "/api/v1/auth/refresh", {"refreshToken": REFRESH})
check("refresh token dead", s == 401, s)
s, _ = ng.http("DELETE", A + "/api/v1/auth/me", {"confirmation": "DELETE"}, TOKEN)
check("retry is harmless (204)", s == 204, s)

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed")
