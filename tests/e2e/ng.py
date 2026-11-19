"""Tiny NepalGo API client for local testing. Tokens are cached in ~/.nepalgo-e2e/tokens.json
(outside the repository, override with NG_TOKEN_STORE) and never printed. Usage:
  ng.py customer-login            (env: FB_KEY, C_EMAIL, C_PASS)
  ng.py admin-login               (env: A_EMAIL, A_PASS)  -> stores pendingToken
  ng.py admin-mfa <code>
  ng.py admin-refresh
  ng.py call <who> <METHOD> <url> [json-body]   who = customer|admin|none
"""
import json, os, sys, urllib.request, urllib.error, base64, time

HERE = os.path.dirname(os.path.abspath(__file__))
STORE = os.environ.get("NG_TOKEN_STORE") or os.path.join(os.path.expanduser("~"), ".nepalgo-e2e", "tokens.json")
AUTH = "http://localhost:8081"


def load():
    try:
        return json.load(open(STORE))
    except Exception:
        return {}


def save(d):
    os.makedirs(os.path.dirname(STORE), exist_ok=True)
    json.dump(d, open(STORE, "w"))


def http(method, url, body=None, token=None):
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    req = urllib.request.Request(url, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def multipart(url, fields, file_bytes, filename, token):
    """POST multipart/form-data with text fields + one 'file' part."""
    boundary = "----ngboundary" + str(int(time.time() * 1000))
    body = b""
    for k, v in fields.items():
        body += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n").encode()
    body += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
             f"Content-Type: application/octet-stream\r\n\r\n").encode() + file_bytes + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def exp_of(tok):
    try:
        p = tok.split(".")[1]
        p += "=" * (-len(p) % 4)
        return json.loads(base64.urlsafe_b64decode(p))["exp"]
    except Exception:
        return 0


def claims(tok):
    p = tok.split(".")[1]
    p += "=" * (-len(p) % 4)
    c = json.loads(base64.urlsafe_b64decode(p))
    return {k: c.get(k) for k in ("sub", "aud", "role", "exp")}


def customer_login():
    s, j = http("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + os.environ["FB_KEY"],
                {"email": os.environ["C_EMAIL"], "password": os.environ["C_PASS"], "returnSecureToken": True})
    if s != 200:
        sys.exit(f"firebase {s}: {j}")
    s, j2 = http("POST", AUTH + "/api/v1/auth/firebase/exchange", {"idToken": j["idToken"]})
    if s != 200:
        sys.exit(f"exchange {s}: {j2}")
    d = load(); d["customer"] = j2["accessToken"]; d["customerRefresh"] = j2.get("refreshToken"); save(d)
    print("customer token ok", claims(d["customer"]))


def admin_login():
    s, j = http("POST", AUTH + "/api/v1/admin/auth/login", {"email": os.environ["A_EMAIL"], "password": os.environ["A_PASS"]})
    d = load()
    if s == 200 and j.get("pendingToken"):
        d["pending"] = j["pendingToken"]; save(d); print("pending token stored; MFA code needed")
    elif s == 200 and j.get("accessToken"):
        d["admin"] = j["accessToken"]; d["adminRefresh"] = j.get("refreshToken"); save(d); print("admin token ok", claims(d["admin"]))
    else:
        print(s, {k: v for k, v in (j.items() if isinstance(j, dict) else []) if "token" not in k.lower()} or j)


def admin_mfa(code):
    d = load()
    s, j = http("POST", AUTH + "/api/v1/admin/auth/mfa/verify", {"pendingToken": d["pending"], "code": code})
    if s == 200 and j.get("accessToken"):
        d["admin"] = j["accessToken"]; d["adminRefresh"] = j.get("refreshToken"); save(d); print("admin token ok", claims(d["admin"]))
    else:
        print(s, j)


def customer_refresh():
    d = load()
    s, j = http("POST", AUTH + "/api/v1/auth/refresh", {"refreshToken": d["customerRefresh"]})
    if s == 200 and j.get("accessToken"):
        d["customer"] = j["accessToken"]; d["customerRefresh"] = j.get("refreshToken", d["customerRefresh"]); save(d); print("customer refreshed")
    else:
        print("customer refresh failed", s, j)


def admin_refresh():
    d = load()
    s, j = http("POST", AUTH + "/api/v1/admin/auth/refresh", {"refreshToken": d["adminRefresh"]})
    if s == 200 and j.get("accessToken"):
        d["admin"] = j["accessToken"]; d["adminRefresh"] = j.get("refreshToken", d["adminRefresh"]); save(d); print("admin refreshed")
    else:
        print("refresh failed", s, j)


def call(who, method, url, body=None):
    tok = None
    if who == "admin":
        if exp_of(load().get("admin", "x.e30.x")) < time.time() + 30:
            admin_refresh()
        tok = load().get("admin")
    elif who == "customer":
        tok = load().get("customer")
    elif who.startswith("raw:"):
        tok = who[4:]
    s, j = http(method, url, None if body is None else json.loads(body), tok)
    print(s, json.dumps(j, indent=1) if not isinstance(j, str) else j[:2000])


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "customer-login": customer_login()
    elif cmd == "admin-login": admin_login()
    elif cmd == "admin-mfa": admin_mfa(sys.argv[2])
    elif cmd == "admin-refresh": admin_refresh()
    elif cmd == "pending-token": print(load()["pending"])  # for negative tests only, piped not shown
    elif cmd == "call": call(*sys.argv[2:])
