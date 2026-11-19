import { useState } from "react";
import { Link, useParams } from "react-router";
import { api, download } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { date, dateTime, label, npr, usd } from "../lib/format";
import BillEditor from "../components/BillEditor";
import { Badge, Button, Card, Empty, ErrorBox, KeyValue, Loading, PageHeader, StatusBadge, Table, Td, Textarea, useConfirm, useToast } from "../components/ui";

// The shared booking detail view (spec 15.2): availability review, requests,
// applied credits, the itemised bill editor, receipt + code side by side,
// consent evidence, internal notes, and ONE merged activity timeline.
export default function BookingDetail() {
  const id = Number(useParams().id);
  const confirm = useConfirm();
  const toast = useToast();
  const invalidate = useInvalidate();
  const [editing, setEditing] = useState(false);

  const booking = useGet<any>(`/api/admin/bookings/${id}`);
  const bill = useGet<any>(`/api/admin/bills?bookingId=${id}`, { allow404: true });
  const receipts = useGet<any[]>(`/api/admin/receipts?bookingId=${id}`);
  const consents = useGet<any[]>(`/api/admin/legal/consents?bookingId=${id}`);
  const bTimeline = useGet<any[]>(`/api/admin/bookings/${id}/timeline`);
  const billTimeline = useGet<any[]>(`/api/admin/bills/timeline?bookingId=${id}`, { allow404: true });
  const b = booking.data;
  const customer = useGet<any>(b ? `/api/v1/admin/customers/${b.customerId}` : null);

  const refresh = () => invalidate(`/api/admin/bookings`, `/api/admin/bills`, `/api/admin/receipts`, `/api/admin/legal`);

  if (booking.isLoading) return <Loading />;
  if (booking.error || !b) return <ErrorBox error={booking.error ?? new Error("Booking not found")} />;

  const bl = bill.data;
  const t = bl?.totals;
  let days: any[] = [];
  let tier = "DEFAULT";
  try { const snap = JSON.parse(b.itinerarySnapshot); days = snap?.days ?? []; tier = snap?.tier ?? tier; } catch { /* snapshot is always JSON; be defensive */ }
  const latestReceipt = receipts.data?.[0];

  // ---------------------------------------------------------------- actions (each confirm-before-commit)
  const act = (path: string, body?: any) => api(path, { body: body ?? {} }).then(() => { refresh(); });

  const approve = () => confirm({
    title: "Approve & issue bill",
    summary: [["Booking", b.voucherCode], ["Customer", customer.data?.displayName ?? `#${b.customerId}`],
      ["Total to pay", t ? `${usd(t.payableUsd)} (${npr(t.grandTotalNpr)})` : "—"], ["Payoneer link", bl?.payoneerLink ?? "missing"]],
    consequence: "Assigns the bill's permanent invoice number, moves the booking to Awaiting Payment and starts the payment window. The customer sees the bill in USD.",
    confirmLabel: "Approve & issue",
    run: () => act(`/api/admin/bookings/${id}/approve`).then(() => toast("Bill issued, booking approved")),
  });
  const reject = () => confirm({
    title: "Reject trip request", danger: true,
    summary: [["Booking", b.voucherCode], ["Destination", b.destinationNameSnapshot], ["Trip start", date(b.arrivalDate)]],
    consequence: "The customer is told the request can't be fulfilled, with your reason. The date hold is released.",
    confirmLabel: "Reject request", reason: { label: "Reason shown to the customer", required: true },
    run: (reason) => act(`/api/admin/bookings/${id}/reject`, { rejectionReason: reason }),
  });
  const issueManual = () => confirm({
    title: "Issue bill (manual booking)",
    summary: [["Booking", b.voucherCode], ["Total to pay", t ? usd(t.payableUsd) : "—"], ["Payoneer link", bl?.payoneerLink ?? "missing"]],
    consequence: "Assigns the invoice number; the customer can now pay. The payment window starts now.",
    confirmLabel: "Issue bill",
    run: () => act(`/api/admin/bookings/${id}/issue-bill`).then(() => toast("Bill issued")),
  });
  const verify = () => confirm({
    title: "Payment verified — issue tickets",
    summary: [["Booking", b.voucherCode], ["Amount expected", t ? usd(t.payableUsd) : "—"],
      ["Receipt code", latestReceipt?.receiptCode ?? b.paymentReceiptCode ?? "—"], ["Invoice", bl?.invoiceNumber ?? "—"]],
    consequence: "Only confirm after you saw this payment in the Payoneer dashboard. The booking becomes CONFIRMED (voucher + QR), the bill locks for good, and accepted travel credits are consumed.",
    confirmLabel: "Confirm payment",
    run: () => act(`/api/admin/bookings/${id}/verify-payment`).then(() => toast("Payment verified, booking confirmed")),
  });
  const rejectPayment = () => confirm({
    title: "Kick back payment", danger: true,
    summary: [["Booking", b.voucherCode], ["Receipt code", latestReceipt?.receiptCode ?? "—"]],
    consequence: "The booking returns to Awaiting Payment with a fresh payment window. The customer sees your reason and can upload a new receipt.",
    confirmLabel: "Reject payment", reason: { label: "Reason shown to the customer", required: true },
    run: (reason) => act(`/api/admin/bookings/${id}/reject-payment`, { rejectionReason: reason }),
  });
  const forceMajeure = () => confirm({
    title: "Force-majeure cancellation", danger: true,
    summary: [["Booking", b.voucherCode], ["Cash refund", t ? usd(t.payableUsd) + " (100%, no fee)" : "—"],
      ["Credit returned", t && Number(t.appliedCreditUsd) > 0 ? usd(t.appliedCreditUsd) + " as new travel credit" : "—"]],
    consequence: "Bypasses the cancellation tiers entirely (spec 8.7). A refund request is queued for you to pay via Payoneer.",
    confirmLabel: "Cancel with full refund", reason: { label: "Force-majeure reason (e.g. landslide closed the route)", required: true },
    run: (reason) => act(`/api/admin/bookings/${id}/force-majeure-cancel`, { reason }),
  });
  const credit = (c: any, accept: boolean) => confirm({
    title: accept ? "Accept travel credit" : "Reject travel credit", danger: !accept,
    summary: [["Credit", c.code], ["Value", usd(c.amountUsd)], ["Expires", date(c.expiresOn)]],
    consequence: accept ? "The credit is netted against this bill's USD total (consumed only once the booking is paid)." : "The credit goes back to the customer's wallet.",
    confirmLabel: accept ? "Accept" : "Reject", reason: accept ? undefined : { label: "Reason", required: true },
    run: (reason) => act(`/api/admin/bills/${bl.id}/credits/${c.creditId}/${accept ? "accept" : "reject"}`, accept ? {} : { reason }),
  });

  // ---------------------------------------------------------------- merged timeline (spec 15.1)
  const timeline = [
    ...(bTimeline.data ?? []).map((e) => ({ at: e.createdAt, who: e.adminUserId ? `Staff #${e.adminUserId}` : "Customer/system", what: label(e.actionType), detail: parseDetail(e.details), src: "booking" })),
    ...(billTimeline.data ?? []).map((e) => ({ at: e.at, who: e.adminUserId ? `Staff #${e.adminUserId}` : "System", src: "bill",
      what: label(e.type) + (e.invoiceNumber ? ` · ${e.invoiceNumber}` : ""),
      detail: e.type === "BILL_CORRECTED" ? `Corrects ${e.linkedInvoiceNumber}: ${e.reason}` : e.fieldChanged ? `${e.fieldChanged}: ${short(e.oldValue)} → ${short(e.newValue)}` : "" })),
    ...(consents.data ?? []).map((c) => ({ at: c.consentedAt, who: "Customer", src: "legal", what: `Agreed to Terms ${c.versionLabel}`, detail: `all 3 Policy Review cards opened + ticked` })),
  ].sort((a, b) => (a.at < b.at ? -1 : 1));

  const editableStatus = ["PENDING_APPROVAL", "AWAITING_PAYMENT"].includes(b.status);

  return (
    <>
      <PageHeader title={b.voucherCode}
        subtitle={<span className="flex flex-wrap items-center gap-2"><StatusBadge status={b.status} />
          {b.origin === "ops_created" ? <Badge tone="mustard">Ops-created</Badge> : <Badge>Self-served</Badge>}
          {b.destinationNameSnapshot} · {date(b.arrivalDate)} · {b.durationDays} days · party of {b.partySize}</span>}
        actions={<>
          {b.status === "PENDING_APPROVAL" && <><Button variant="danger" onClick={reject}>Reject</Button><Button onClick={approve} disabled={!bl}>Approve & issue bill</Button></>}
          {b.status === "AWAITING_PAYMENT" && b.origin === "ops_created" && !b.invoiceNumber && <Button onClick={issueManual} disabled={!bl}>Issue bill</Button>}
          {b.status === "VERIFYING_PAYMENT" && <><Button variant="danger" onClick={rejectPayment}>Reject payment</Button><Button onClick={verify}>Payment verified</Button></>}
          {b.status === "CONFIRMED" && <Button variant="danger" onClick={forceMajeure}>Force-majeure cancel</Button>}
        </>} />

      {b.rejectionReason && <div className="mb-4 rounded-[14px] bg-[#fdf0e1] px-4 py-3 text-sm text-warning">Last reason shown to customer: {b.rejectionReason}</div>}

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.4fr_1fr]">
        <div className="space-y-5">
          <Card title="Itemised bill" actions={bl && <>
            {bl.issued && <Button variant="secondary" onClick={() => download(`/api/admin/bills/${bl.id}/pdf`, `${bl.invoiceNumber}.pdf`)}>PDF</Button>}
            {!editing && (editableStatus || bl.status === "LOCKED") && <Button variant="secondary" onClick={() => setEditing(true)}>Edit bill</Button>}
          </>}>
            {bill.isLoading ? <Loading /> : editing || (!bl && editableStatus && editing) ? (
              <BillEditor bookingId={id} bill={bl ?? undefined} onCancel={() => setEditing(false)}
                onSaved={(saved) => { setEditing(false); refresh(); toast(saved.correctsBillId ? `Correction ${saved.invoiceNumber} created` : "Bill saved"); }} />
            ) : !bl ? (
              editableStatus ? <div className="py-4"><p className="mb-3 text-sm text-muted">No bill yet. Price each service in NPR; the customer will see USD at the locked rate.</p>
                <Button onClick={() => setEditing(true)}>Build bill</Button></div> : <Empty>No bill for this booking.</Empty>
            ) : (
              <BillView bill={bl} onCredit={credit} />
            )}
          </Card>

          <Card title="Payment receipt">
            {!latestReceipt ? <Empty>No receipt submitted yet.</Empty> : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <a href={latestReceipt.viewUrl} target="_blank" rel="noreferrer" className="block overflow-hidden rounded-[14px] bg-background">
                  <img src={latestReceipt.viewUrl} alt="Customer's payment screenshot" className="max-h-80 w-full object-contain" />
                </a>
                <div className="space-y-3">
                  <KeyValue items={[["Receipt / transaction code", <span className="mono text-base font-semibold">{latestReceipt.receiptCode}</span>],
                    ["Submitted", dateTime(latestReceipt.submittedAt)], ["Status", <StatusBadge status={latestReceipt.status} />],
                    ["Expected amount", t ? usd(t.payableUsd) : "—"]]} />
                  {(latestReceipt.duplicateCodeOnBookings.length > 0 || latestReceipt.duplicateImageOnBookings.length > 0) && (
                    <div role="alert" className="rounded-[14px] bg-[#fbe6e6] px-4 py-3 text-sm text-error">
                      <b>Possible double claim.</b> {latestReceipt.duplicateCodeOnBookings.length > 0 && <>Same code used on booking(s) {latestReceipt.duplicateCodeOnBookings.map((x: number) => <Link key={x} className="underline" to={`/bookings/${x}`}>#{x} </Link>)}. </>}
                      {latestReceipt.duplicateImageOnBookings.length > 0 && <>Same screenshot used on booking(s) {latestReceipt.duplicateImageOnBookings.map((x: number) => <Link key={x} className="underline" to={`/bookings/${x}`}>#{x} </Link>)}.</>}
                      {" "}Verification is blocked.
                    </div>
                  )}
                  <p className="text-xs text-muted">Image link is private and expires in {latestReceipt.viewUrlExpiresIn}. Earlier attempts: {Math.max(0, (receipts.data?.length ?? 1) - 1)}</p>
                </div>
              </div>
            )}
          </Card>

          <Card title="Activity timeline">
            {timeline.length === 0 ? <Empty>No activity yet.</Empty> : (
              <ol className="space-y-3">
                {timeline.map((e, i) => (
                  <li key={i} className="grid grid-cols-[110px_1fr] gap-3 text-sm">
                    <span className="text-xs text-muted">{dateTime(e.at)}</span>
                    <div>
                      <div className="font-medium">{e.what} <Badge tone={e.src === "bill" ? "brand" : e.src === "legal" ? "mustard" : "neutral"}>{e.src}</Badge></div>
                      <div className="text-xs text-muted">{e.who}{e.detail ? ` · ${e.detail}` : ""}</div>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>

        <div className="space-y-5">
          <Card title="Customer">
            <KeyValue items={[["Name", customer.data?.displayName], ["NepalGo ID", customer.data?.nepalgoIdDisplay],
              ["Email", customer.data?.email], ["Account", customer.data && <StatusBadge status={customer.data.status} />]]} />
            <Link className="mt-3 inline-block text-sm font-semibold text-primary" to={`/customers/${b.customerId}`}>Open customer →</Link>
          </Card>
          {b.paymentDueAt && (
            <Card title="Payment window">
              {/* Same deadline the customer sees; expires automatically after it (Billing Settings > Payment window). */}
              <KeyValue items={[["Customer must pay by", <b>{dateTime(b.paymentDueAt)}</b>],
                ["Status", new Date(b.paymentDueAt).getTime() < Date.now() ? <Badge tone="error">Overdue: expires on the next check</Badge> : <Badge tone="warning">Running</Badge>]]} />
            </Card>
          )}
          <Card title="Requests to review">
            <KeyValue items={[["Guide", b.guideRequested ? <Badge tone="brand">Requested</Badge> : "No"], ["Porter", b.porterRequested ? <Badge tone="brand">Requested</Badge> : "No"],
              ["Special request", b.specialRequestText || "—"]]} />
          </Card>
          <Card title="Lead booker (identity)">
            <KeyValue items={[["Legal name", b.leadBookerLegalName], ["Passport", b.leadBookerPassportNumber], ["Email", b.leadBookerEmail]]} />
            <p className="mt-2 text-xs text-muted">Self-declared (Terms §5). Not verified against a physical passport.</p>
          </Card>
          <Card title="Terms consent">
            {consents.data?.length ? consents.data.map((c) => (
              <KeyValue key={c.id} items={[["Version", `${c.versionLabel} (doc #${c.termsDocumentId})`], ["Agreed", dateTime(c.consentedAt)],
                ["Content hash", <span className="mono text-xs">{c.contentSha256.slice(0, 16)}…</span>]]} />
            )) : <Empty>No consent recorded yet.</Empty>}
          </Card>
          <Notes booking={b} onSaved={refresh} />
          <Card title={tier === "CUSTOM" ? "Itinerary (custom, agreed on WhatsApp)" : "Itinerary (snapshot at booking time)"}>
            <ol className="space-y-2 text-sm">
              {days.map((d) => <li key={d.dayNumber}><b>Day {d.dayNumber}.</b> {d.narrative}{d.hotelName ? <span className="text-muted"> — {d.hotelName}</span> : null}</li>)}
            </ol>
          </Card>
        </div>
      </div>
    </>
  );
}

function BillView({ bill, onCredit }: { bill: any; onCredit: (c: any, accept: boolean) => void }) {
  const t = bill.totals;
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <StatusBadge status={bill.status} />
        <span className="mono">{bill.invoiceNumber ?? "not issued yet"}</span>
        <span className="text-muted">· {bill.documentTitle} · FX {bill.fxRate} {bill.currencyPair} (frozen)</span>
        {bill.correctsBillId && <Badge tone="mustard">Correction of bill #{bill.correctsBillId}</Badge>}
      </div>
      <Table head={["#", "Description", "Qty", "Unit (NPR)", "Total (NPR)", "Total (USD)"]}>
        {bill.lineItems.map((l: any) => (
          <tr key={l.id}><Td>{l.position}</Td><Td>{l.itineraryDay ? `Day ${l.itineraryDay} — ` : ""}{l.description} <Badge>{label(l.category)}</Badge></Td>
            <Td>{l.quantity}</Td><Td className="whitespace-nowrap">{npr(l.unitPriceNpr)}</Td>
            <Td className="whitespace-nowrap">{npr(l.lineTotalNpr)}</Td><Td className="whitespace-nowrap">{usd(l.lineTotalUsd)}</Td></tr>
        ))}
      </Table>
      <div className="ml-auto max-w-sm">
        <KeyValue items={[
          ["Subtotal", npr(t.subtotalNpr)], [`Service charge ${bill.serviceChargePercent}%`, npr(t.serviceChargeNpr)],
          ...(bill.vatRegistered ? [[`VAT ${bill.vatPercent}%`, npr(t.vatNpr)] as [string, string]] : []),
          ["Grand total", <b>{npr(t.grandTotalNpr)}</b>], ["", <span className="text-xs text-muted">{bill.grandTotalNprInWords}</span>],
          ["Grand total (USD)", usd(t.grandTotalUsd)],
          ...(Number(t.appliedCreditUsd) > 0 ? [["Travel credit", `− ${usd(t.appliedCreditUsd)}`] as [string, string]] : []),
          ["Customer pays", <b className="text-primary">{usd(t.payableUsd)}</b>],
        ]} />
      </div>
      {bill.appliedCredits?.length > 0 && (
        <div className="rounded-[14px] bg-primary-tint p-4">
          <div className="mb-2 text-sm font-semibold text-primary">Travel credits applied by the customer</div>
          {bill.appliedCredits.map((c: any) => (
            <div key={c.creditId} className="flex flex-wrap items-center justify-between gap-2 py-1 text-sm">
              <span>Customer applied a {usd(c.amountUsd)} Travel Credit (<span className="mono">{c.code}</span>), expires {date(c.expiresOn)}
                {c.expired && <Badge tone="error">expired</Badge>} <StatusBadge status={c.decision} /></span>
              {!bill.issued && c.decision === "PENDING_DECISION" && (
                <span className="flex gap-2"><Button variant="ghost" onClick={() => onCredit(c, false)}>Reject</Button>
                  <Button variant="secondary" disabled={c.expired} onClick={() => onCredit(c, true)}>Accept</Button></span>
              )}
            </div>
          ))}
        </div>
      )}
      <p className="text-xs text-muted">Payoneer link: {bill.payoneerLink ? <a className="text-primary underline" href={bill.payoneerLink} target="_blank" rel="noreferrer">{bill.payoneerLink}</a> : <b className="text-error">missing</b>}</p>
    </div>
  );
}

function Notes({ booking, onSaved }: { booking: any; onSaved: () => void }) {
  const [text, setText] = useState(booking.internalNotes ?? "");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<unknown>(null);
  const dirty = (booking.internalNotes ?? "") !== text;
  return (
    <Card title="Internal notes" actions={dirty && <Button busy={busy} onClick={async () => {
      setBusy(true); setErr(null);
      try { await api(`/api/admin/bookings/${booking.id}/internal-notes`, { method: "PUT", body: { internalNotes: text } }); onSaved(); }
      catch (e) { setErr(e); } finally { setBusy(false); }
    }}>Save</Button>}>
      <Textarea rows={4} value={text} onChange={(e) => setText(e.target.value)} placeholder="Only staff see this. Changes are logged on the timeline." />
      <ErrorBox error={err} />
    </Card>
  );
}

function parseDetail(d?: string | null) {
  if (!d) return "";
  try { return JSON.parse(d).detail ?? d; } catch { return d; }
}
const short = (s?: string | null) => (!s ? "—" : s.length > 60 ? s.slice(0, 57) + "…" : s);
