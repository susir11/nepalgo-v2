"""E2E: travel credits (manual issue, apply, accept/reject, redeem), cancellation tiers, refund queue, gateway routes."""
import os, sys, random, datetime, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ng
from e2e_lib import *   # noqa  (call, tok, check, results, png, new_paid_booking helpers)

GW = "http://localhost:8080"

# ---------- gateway routes reach every service; /internal is NOT exposed
s, _ = call(None, "GET", f"{GW}/api/destinations?page=0&size=1");            check("gateway -> catalog", s == 200, s)
s, _ = call("customer", "GET", f"{GW}/api/bookings?page=0&size=1");          check("gateway -> booking", s == 200, s)
s, _ = call("customer", "GET", f"{GW}/api/store-credits");                   check("gateway -> payment (wallet)", s == 200, s)
s, _ = call("admin", "GET", f"{GW}/api/admin/fx-rates/current?pair=USD%2FNPR"); check("gateway -> payment (admin)", s == 200, s)
s, _ = ng.http("GET", f"{GW}/internal/bookings/1");                          check("gateway does NOT expose /internal", s == 404, s)

# ---------- SUPER_ADMIN issues goodwill credits to the test customer (id from token)
cust = int(ng.claims(tok("customer"))["sub"])
s, c45 = call("admin", "POST", f"{P}/api/admin/store-credits", {"customerId": cust, "amountUsd": 45, "note": "goodwill e2e"})
check("admin issues $45 goodwill credit", s == 200 and c45["code"].startswith("NG-CR-") and c45["status"] == "ACTIVE", (s, c45))
s, c30 = call("admin", "POST", f"{P}/api/admin/store-credits", {"customerId": cust, "amountUsd": 30, "note": "goodwill e2e 2"})
s, c99 = call("admin", "POST", f"{P}/api/admin/store-credits", {"customerId": cust, "amountUsd": 99})
s, r = call("admin", "PUT", f"{P}/api/admin/store-credits/{c99['id']}", {"amountUsd": 20})
check("admin edits credit amount (logged)", s == 200 and float(r["amountUsd"]) == 20, (s, r))
s, r = call("admin", "POST", f"{P}/api/admin/store-credits/{c99['id']}/void", {"reason": "issued in error"})
check("admin voids credit", s == 200 and r["status"] == "VOID", (s, r))
s, d = call("admin", "GET", f"{P}/api/admin/store-credits/{c99['id']}")
check("credit detail shows edit + void history", s == 200 and [h["fieldChanged"] for h in d["history"]] == ["amountUsd", "status"], d)
s, w = call("customer", "GET", f"{P}/api/store-credits")
codes = {x["code"]: x for x in w}
check("wallet shows active credits in USD, hides void", c45["code"] in codes and c99["code"] not in codes and "amountUsd" in codes[c45["code"]], w)

# ---------- booking with credits applied at trip-request time
bid = new_booking()
s, r = call("customer", "POST", f"{P}/api/store-credits/apply", {"bookingId": bid, "codes": [c45["code"], c30["code"].lower()]})
check("customer applies 2 credits to trip request", s == 200 and all(x["status"] == "RESERVED" for x in r if x["code"] in (c45["code"], c30["code"])), (s, r))
s, r = call("customer", "POST", f"{P}/api/store-credits/apply", {"bookingId": bid, "codes": [c99["code"]]})
check("void credit cannot be applied", s == 400, (s, r))

bill = create_bill(bid)
check("admin bill view shows applied credits as PENDING_DECISION",
      {c["code"]: c["decision"] for c in bill["appliedCredits"]} == {c45["code"]: "PENDING_DECISION", c30["code"]: "PENDING_DECISION"}, bill["appliedCredits"])
s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid}/approve")
check("issue blocked while credits undecided", s == 409 and "travel credit" in r.get("detail", ""), (s, r))
s, _ = call("admin", "POST", f"{P}/api/admin/bills/{bill['id']}/credits/{c45['id']}/accept")
check("admin accepts $45 credit", s == 200, s)
s, r = call("admin", "POST", f"{P}/api/admin/bills/{bill['id']}/credits/{c30['id']}/reject", {"reason": ""})
check("reject without reason -> 400", s == 400, (s, r))
s, _ = call("admin", "POST", f"{P}/api/admin/bills/{bill['id']}/credits/{c30['id']}/reject", {"reason": "Only one credit per trip this season"})
check("admin rejects $30 credit with reason", s == 200, s)
s, b = call("admin", "GET", f"{P}/api/admin/bills/{bill['id']}")
t = b["totals"]
check("bill nets accepted credit: payable = grand - 45", abs(float(t["payableUsd"]) - (float(t["grandTotalUsd"]) - 45)) < 0.001 and float(t["appliedCreditUsd"]) == 45, t)
s, w = call("customer", "GET", f"{P}/api/store-credits")
check("rejected credit is back in the wallet (ACTIVE)", {x["code"]: x["status"] for x in w}[c30["code"]] == "ACTIVE", w)

pay_and_confirm(bid)
s, w = call("customer", "GET", f"{P}/api/store-credits")
check("accepted credit REDEEMED once payment verified", {x["code"]: x["status"] for x in w}[c45["code"]] == "REDEEMED", w)

# ---------- cancel >= 60 days out: 90% of CASH paid as cash, credit part comes back as credit (never cash)
s, cr = call("customer", "POST", f"{B}/api/bookings/{bid}/cancel")
cash_paid = float(t["payableUsd"])
check("cancel 60+ days -> CANCELLED_TIERED, 90% of cash queued", s == 200 and cr["cancellationTier"] == "60_PLUS_DAYS"
      and abs(float(cr["cashRefundUsd"]) - round(cash_paid * 0.9, 2)) < 0.011, (s, cr))
check("credit-paid part ($45) returns as a NEW credit 90% = $40.50, not cash", cr["travelCreditCode"] and float(cr["travelCreditUsd"]) == 40.5, cr)
s, q = call("admin", "GET", f"{P}/api/admin/refund-requests?status=PENDING")
mine = [x for x in q if x["bookingId"] == bid]
check("refund appears in PENDING queue", s == 200 and len(mine) == 1, q)
s, r = call("admin", "POST", f"{P}/api/admin/refund-requests/{mine[0]['id']}/complete", {"payoutReference": "PYN-REFUND-" + str(bid)})
check("admin marks refund as sent", s == 200 and r["status"] == "COMPLETED", (s, r))
s, r = call("admin", "POST", f"{P}/api/admin/refund-requests/{mine[0]['id']}/complete", {"payoutReference": "again"})
check("cannot complete a refund twice", s == 409, (s, r))

# ---------- idempotency: replaying the internal cancellation returns the same outcome, no 2nd refund/credit
import json, urllib.request
key = os.environ.get("NG_INTERNAL_KEY", "nepalgo-local-dev-internal-key")
req = urllib.request.Request(f"{P}/internal/cancellations", method="POST",
      data=json.dumps({"bookingId": bid, "tier": "60_PLUS_DAYS", "refundPercent": 90, "forceMajeure": False}).encode(),
      headers={"Content-Type": "application/json", "X-Internal-Api-Key": key})
again = json.loads(urllib.request.urlopen(req).read())
check("internal cancellation replay is idempotent (same refund id + same credit code)",
      again["refundRequestId"] == mine[0]["id"] and again["creditCode"] == cr["travelCreditCode"], again)

# ---------- cancel 30-59 tier => all value as credit (needs arrival 30-59 days out)
bid2 = new_booking(days_out=45)
create_bill(bid2, approve=True)
pay_and_confirm(bid2)
s, r = call("customer", "POST", f"{B}/api/bookings/{bid2}/cancel")
s2, b2 = call("admin", "GET", f"{P}/api/admin/bills?bookingId={bid2}")
check("30-59 days -> no cash, 70% of total as travel credit", s == 200 and r["cancellationTier"] == "30_59_DAYS"
      and float(r["cashRefundUsd"]) == 0 and abs(float(r["travelCreditUsd"]) - round(float(b2["totals"]["grandTotalUsd"]) * 0.7, 2)) < 0.011, (s, r))

# ---------- force majeure (admin) => 100% cash
bid3 = new_booking(days_out=20)
create_bill(bid3, approve=True)
pay_and_confirm(bid3)
s, r = call("admin", "POST", f"{B}/api/admin/bookings/{bid3}/force-majeure-cancel", {"reason": "Lukla airport closed by landslide"})
s3, b3 = call("admin", "GET", f"{P}/api/admin/bills?bookingId={bid3}")
check("force majeure -> 100% cash refund", s == 200 and float(r["cashRefundUsd"]) == float(b3["totals"]["payableUsd"]), (s, r))

# ---------- CSV export
today = datetime.date.today().isoformat()
req = urllib.request.Request(f"{P}/api/admin/bills/export?from={today}&to={today}",
                             headers={"Authorization": "Bearer " + tok("admin")})
with urllib.request.urlopen(req) as resp:
    csv = resp.read().decode()
check("CSV export has header + rows for today's invoices", csv.startswith("invoice_number,issue_date") and csv.count("\n") > 3, csv[:200])

print(f"\n{sum(ok for _, ok in results)}/{len(results)} passed")
