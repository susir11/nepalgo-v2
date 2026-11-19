"""E2E: SUPER_ADMIN MFA is mandatory server-side; re-enrollment takeover is blocked."""
import os, sys, time, hmac, hashlib, struct, base64, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ng
from e2e_lib import check, results

A, P = "http://localhost:8081", "http://localhost:8084"
EMAIL = f"e2e-superadmin-{int(time.time())}@nepalgo.local"


def psql(sql):
    r = subprocess.run(["docker", "exec", "-i", "nepalgo-postgres", "psql", "-U", "auth_svc", "-d", "nepalgo", "-tA", "-c", sql],
                       capture_output=True, text=True)
    return (r.stdout + r.stderr).strip()


def totp(secret_b32, t=None):
    key = base64.b32decode(secret_b32.upper() + "=" * (-len(secret_b32) % 8))
    counter = int((t or time.time()) // 30)
    h = hmac.new(key, struct.pack(">Q", counter), hashlib.sha1).digest()
    o = h[-1] & 0x0F
    return f"{(struct.unpack('>I', h[o:o + 4])[0] & 0x7FFFFFFF) % 1_000_000:06d}"


# 1) existing MFA-enabled admin: normal 2-step flow, and NO re-enrollment possible
s, r = ng.http("POST", f"{A}/api/v1/admin/auth/login", {"email": "admin@nepalgo.local", "password": "Asdf@asdf12"})
check("MFA-enabled admin: login -> mfaRequired, no tokens", s == 200 and r["mfaRequired"] and not r["mfaEnrollmentRequired"] and r["tokens"] is None, r)
s, r2 = ng.http("POST", f"{A}/api/v1/admin/auth/mfa/enroll/start", {"pendingToken": r["pendingToken"]})
check("re-enrollment of an MFA-enabled account is REFUSED (takeover path closed)", s == 409 and r2.get("code") == "AUTH_MFA_ALREADY_ENABLED", (s, r2))

# 2) a SUPER_ADMIN without MFA (created directly in the DB with the same password hash)
psql(f"INSERT INTO staff_users (email, password_hash, role, mfa_enabled, must_change_password) "
     f"SELECT '{EMAIL}', password_hash, 'SUPER_ADMIN', false, false FROM staff_users WHERE email='admin@nepalgo.local'")
s, r = ng.http("POST", f"{A}/api/v1/admin/auth/login", {"email": EMAIL, "password": "Asdf@asdf12"})
check("SUPER_ADMIN without MFA: login gives NO access token, only enrollment", s == 200 and r["mfaEnrollmentRequired"] and r["tokens"] is None and r["pendingToken"], r)
pending = r["pendingToken"]
s, _ = ng.http("GET", f"{P}/api/admin/fx-rates", None, pending)
check("that pending token is useless on money endpoints (401)", s == 401, s)
s, e = ng.http("POST", f"{A}/api/v1/admin/auth/mfa/enroll/start", {"pendingToken": pending})
check("enroll/start with pending token returns a TOTP secret", s == 200 and len(e.get("secret", "")) >= 16, (s, e))
s, bad = ng.http("POST", f"{A}/api/v1/admin/auth/mfa/enroll/confirm", {"pendingToken": pending, "code": "000000"})
check("wrong first code rejected", s == 401, (s, bad))
s, tok = ng.http("POST", f"{A}/api/v1/admin/auth/mfa/enroll/confirm", {"pendingToken": pending, "code": totp(e["secret"])})
check("correct authenticator code -> real tokens", s == 200 and tok.get("accessToken") and tok.get("role") == "SUPER_ADMIN", (s, tok))
s, _ = ng.http("GET", f"{P}/api/admin/fx-rates", None, tok.get("accessToken"))
check("…and those tokens work on money endpoints", s == 200, s)
s, r = ng.http("POST", f"{A}/api/v1/admin/auth/login", {"email": EMAIL, "password": "Asdf@asdf12"})
check("next login now requires the 6-digit code (normal MFA)", s == 200 and r["mfaRequired"] and not r["mfaEnrollmentRequired"], r)

# cleanup: deactivate the throwaway account (rows stay for the audit trail)
print("cleanup:", psql(f"UPDATE staff_users SET active=false WHERE email='{EMAIL}'"))
print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed")
