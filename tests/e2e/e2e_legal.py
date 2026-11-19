"""E2E Phase 5: legal content, gated consent, server-side enforcement, versioning, immutability."""
import os, sys, json, random, datetime, subprocess, hashlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ng
from e2e_lib import *  # noqa

GW = "http://localhost:8080"

# ---------------- public content (no login), direct and through the gateway
s, terms = call(None, "GET", f"{L}/api/legal/terms/current")
check("public: current Terms v1 with 3 card sections", s == 200 and terms["status"] == "PUBLISHED"
      and all(terms[k] for k in ("cancellationMatrix", "forceMajeureWeather", "identityLiability")), (s, terms))
check("Terms text is the spec v1 text", "60+ days before your trip: a cash refund of 90%" in terms["fullText"], terms["fullText"][:200])
s, priv = call(None, "GET", f"{L}/api/legal/privacy/current")
check("public: current Privacy Policy, no card sections", s == 200 and priv["cancellationMatrix"] is None, (s, priv))
s, pr = call(None, "GET", f"{GW}/api/legal/policy-review")
check("gateway: policy-review = 3 gated cards + version id", s == 200 and [c["key"] for c in pr["cards"]] ==
      ["CANCELLATION_MATRIX", "FORCE_MAJEURE_WEATHER", "IDENTITY_LIABILITY"] and pr["termsDocumentId"], (s, pr))
s, ob = call(None, "GET", f"{GW}/api/content/onboarding")
check("gateway: onboarding carousel content", s == 200 and len(ob["payload"]["slides"]) >= 1, (s, ob))
s, cp = call(None, "GET", f"{GW}/api/content/company-profile")
check("gateway: company profile content", s == 200 and "legalName" in cp["payload"], (s, cp))
s, _ = call(None, "GET", f"{L}/api/legal/nonsense/current")
check("unknown document type -> 404", s == 404, s)
s, _ = call(None, "POST", f"{L}/api/legal/terms/current")
check("public area is read-only (POST denied)", s in (401, 403), s)

# ---------------- auth boundaries
s, _ = call(None, "POST", f"{L}/api/legal/consents", {})
check("consent without login -> 401", s == 401, s)
s, _ = call("admin", "POST", f"{L}/api/legal/consents", {})
check("staff token cannot post customer consent -> 401", s == 401, s)
s, _ = call("customer", "GET", f"{L}/api/admin/legal/terms/versions")
check("customer token on admin legal API -> 401", s == 401, s)
s, _ = ng.http("GET", f"{L}/internal/consents/check?bookingId=1&customerId=1")
check("internal consent check without key -> 401", s == 401, s)

# ---------------- server-side enforcement in booking-service
bid = new_booking()
create_bill(bid, approve=True)
s, r = ng.multipart(f"{B}/api/bookings/{bid}/payment-receipt", {"paymentReceiptCode": f"PYN-{bid}-X"}, png(bid), "r.png", tok("customer"))
check("receipt WITHOUT consent -> 409 CONSENT_REQUIRED / NO_CONSENT", s == 409 and r.get("code") == "CONSENT_REQUIRED"
      and r.get("reason") == "NO_CONSENT", (s, r))

doc = pr["termsDocumentId"]
body = consent_body(bid, doc)
bad = json.loads(json.dumps(body)); bad["identityLiability"] = None
s, r = call("customer", "POST", f"{L}/api/legal/consents", bad)
check("consent with an un-ticked card -> 400", s == 400, (s, r))
bad = json.loads(json.dumps(body)); bad["forceMajeureWeather"] = {"expandedAt": body["forceMajeureWeather"]["checkedAt"], "checkedAt": body["forceMajeureWeather"]["expandedAt"]}
s, r = call("customer", "POST", f"{L}/api/legal/consents", bad)
check("ticked before opened -> 400 SECTION_ORDER", s == 400 and r.get("code") == "SECTION_ORDER", (s, r))
s, r = call("customer", "POST", f"{L}/api/legal/consents", consent_body(bid, doc, skew=3600))
check("timestamps in the future -> 400", s == 400 and r.get("code") == "SECTION_IN_FUTURE", (s, r))
s, r = call("customer", "POST", f"{L}/api/legal/consents", consent_body(bid, doc, skew=-26 * 3600))
check("stale review (>24h) -> 400", s == 400 and r.get("code") == "SECTION_STALE", (s, r))
s, r = call("customer", "POST", f"{L}/api/legal/consents", consent_body(bid, doc + 9999))
check("consent to a version that isn't current -> 409 TERMS_VERSION_CHANGED", s == 409 and r.get("code") == "TERMS_VERSION_CHANGED"
      and r.get("currentTermsDocumentId") == doc, (s, r))
bid_pending = new_booking()
s, r = call("customer", "POST", f"{L}/api/legal/consents", consent_body(bid_pending, doc))
check("consent before bill issued (PENDING_APPROVAL) -> 409", s == 409 and r.get("code") == "INVALID_BOOKING_STATE", (s, r))
call("customer", "POST", f"{B}/api/bookings/{bid_pending}/withdraw")

s, c1 = call("customer", "POST", f"{L}/api/legal/consents", body)
check("valid consent -> 201 with version + content hash", s == 201 and c1["versionLabel"] == terms["versionLabel"]
      and c1["contentSha256"] == terms["contentSha256"], (s, c1))
s, c2 = call("customer", "POST", f"{L}/api/legal/consents", consent_body(bid, doc))
check("double tap is idempotent (same record)", s == 201 and c2["id"] == c1["id"], (s, c2))
s, r = ng.multipart(f"{B}/api/bookings/{bid}/payment-receipt", {"paymentReceiptCode": f"PYN-{bid}-X"}, png(bid), "r.png", tok("customer"))
check("receipt WITH consent -> VERIFYING_PAYMENT", s == 200 and r.get("status") == "VERIFYING_PAYMENT", (s, r))
s, ac = call("admin", "GET", f"{L}/api/admin/legal/consents?bookingId={bid}")
check("admin sees consent with per-card timestamps", s == 200 and ac[0]["identityLiability"]["checkedAt"], (s, ac))

# ---------------- Policy Content Manager: draft -> preview -> publish; old consent becomes outdated
bid2 = new_booking()
create_bill(bid2, approve=True)
consent(bid2)                                                  # agreed to the CURRENT version...
s, draft = call("admin", "POST", f"{L}/api/admin/legal/terms/drafts",
                {"changeSummary": "e2e: clarify clause 14 contact", "fullText": terms["fullText"].replace("[support\nemail/contact]", "[support email/contact]") + "\n\n(e2e revision)"})
check("admin creates Terms draft (unchanged cards copied from current)", s == 201 and draft["status"] == "DRAFT"
      and draft["cancellationMatrix"] == terms["cancellationMatrix"], (s, draft))
s, upd = call("admin", "PUT", f"{L}/api/admin/legal/documents/{draft['id']}", {"changeSummary": "e2e: edited draft"})
check("draft is editable", s == 200 and upd["changeSummary"] == "e2e: edited draft", (s, upd))
s, r = call(None, "GET", f"{L}/api/legal/documents/{draft['id']}")
check("drafts are NOT publicly readable", s == 404, s)
s, pub = call("admin", "POST", f"{L}/api/admin/legal/documents/{draft['id']}/publish")
check("publish -> current, hashed", s == 200 and pub["current"] and len(pub["contentSha256"]) == 64, (s, pub))
s, old = call(None, "GET", f"{L}/api/legal/documents/{doc}")
check("old version kept for history, no longer current", s == 200 and old["status"] == "PUBLISHED" and not old["current"], (s, old))
s, r = call("admin", "PUT", f"{L}/api/admin/legal/documents/{pub['id']}", {"title": "sneaky edit"})
check("published version can't be edited via API -> 409", s == 409 and r.get("code") == "ALREADY_PUBLISHED", (s, r))

s, r = ng.multipart(f"{B}/api/bookings/{bid2}/payment-receipt", {"paymentReceiptCode": f"PYN-{bid2}-X"}, png(bid2), "r.png", tok("customer"))
check("...but Terms changed before paying -> 409 CONSENT_FOR_OUTDATED_TERMS", s == 409 and r.get("reason") == "CONSENT_FOR_OUTDATED_TERMS"
      and r.get("currentTermsDocumentId") == pub["id"], (s, r))
s, r = call("customer", "POST", f"{L}/api/legal/consents", consent_body(bid2, doc))
check("app still sending old version id -> 409 TERMS_VERSION_CHANGED", s == 409, (s, r))
consent(bid2)
s, r = ng.multipart(f"{B}/api/bookings/{bid2}/payment-receipt", {"paymentReceiptCode": f"PYN-{bid2}-X"}, png(bid2 + 7), "r.png", tok("customer"))
check("re-agreed to new version -> receipt accepted", s == 200 and r.get("status") == "VERIFYING_PAYMENT", (s, r))
s, hist = call("admin", "GET", f"{L}/api/admin/legal/consents?bookingId={bid2}")
check("booking keeps BOTH consent records (old + new version)", s == 200 and len(hist) == 2 and len({h['termsDocumentId'] for h in hist}) == 2, hist)

# ---------------- Privacy has no card sections
s, r = call("admin", "POST", f"{L}/api/admin/legal/privacy/drafts", {"cancellationMatrix": "nope"})
check("privacy draft with card sections -> 400 NOT_SECTIONED", s == 400 and r.get("code") == "NOT_SECTIONED", (s, r))

# ---------------- onboarding content draft/publish
s, r = call("admin", "POST", f"{L}/api/admin/content/onboarding/drafts", {"payload": {"slides": []}})
check("invalid onboarding (no slides) rejected", s == 400, (s, r))
new_slides = ob["payload"]; new_slides["slides"][0]["headline"] = "Your companion for Nepal. (e2e)"
s, d = call("admin", "POST", f"{L}/api/admin/content/onboarding/drafts", {"payload": new_slides, "changeSummary": "e2e"})
s, p = call("admin", "POST", f"{L}/api/admin/content/versions/{d['id']}/publish")
s, live = call(None, "GET", f"{L}/api/content/onboarding")
check("published onboarding version is live", s == 200 and live["versionNo"] == p["versionNo"]
      and live["payload"]["slides"][0]["headline"].endswith("(e2e)"), (s, live))

# ---------------- database-level guarantees (bypass the app entirely)
def psql(sql):
    out = subprocess.run(["docker", "exec", "-i", "nepalgo-postgres", "psql", "-U", "legal_svc", "-d", "nepalgo", "-tA", "-c", sql],
                         capture_output=True, text=True)
    return out.returncode, (out.stdout + out.stderr).strip()

rc, out = psql(f"UPDATE legal.legal_documents SET full_text = 'tampered' WHERE id = {doc}")
check("DB trigger blocks editing published Terms even via raw SQL", rc != 0 and "immutable" in out, out)
rc, out = psql(f"DELETE FROM legal.consent_records WHERE booking_id = {bid}")
check("DB trigger blocks deleting consent records (append-only)", rc != 0 and "append-only" in out, out)
rc, out = psql("UPDATE legal.legal_documents SET is_current = true WHERE document_type='TERMS_AND_CONDITIONS' AND NOT is_current AND status='PUBLISHED'")
check("DB allows only ONE current Terms version", rc != 0 and "uq_legal_doc_one_current" in out, out)

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed")
