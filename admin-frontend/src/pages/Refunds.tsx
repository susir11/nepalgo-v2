import { useState } from "react";
import { Link } from "react-router";
import { api } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { dateTime, label, usd } from "../lib/format";
import { Button, Card, Empty, ErrorBox, Loading, PageHeader, StatusBadge, Table, Td, useConfirm } from "../components/ui";

// Refund-Request Queue (spec 21): send the money manually in Payoneer, then
// "Mark as sent" with the Payoneer transaction reference. No webhook by design.
export default function Refunds() {
  const [tab, setTab] = useState<"PENDING" | "COMPLETED">("PENDING");
  const list = useGet<any[]>(`/api/admin/refund-requests?status=${tab}`);
  const confirm = useConfirm();
  const invalidate = useInvalidate();

  const markSent = (r: any) => confirm({
    title: "Mark refund as sent",
    summary: [["Booking", <Link className="text-primary" to={`/bookings/${r.bookingId}`}>#{r.bookingId}</Link>], ["Amount", usd(r.amountUsd)],
      ["Tier", label(r.tier) + ` (${r.refundPercent}%)`]],
    consequence: "Only after you sent this amount from your Payoneer dashboard. The customer is notified that the refund is on its way.",
    confirmLabel: "Mark as sent",
    reason: { label: "Payoneer transaction reference", required: true },
    run: (ref) => api(`/api/admin/refund-requests/${r.id}/complete`, { body: { payoutReference: ref } }).then(() => invalidate("/api/admin/refund-requests")),
  });

  return (
    <>
      <PageHeader title="Refund requests" subtitle="Cash refunds owed after cancellations (60+ days tier, force majeure)" />
      <Card>
        <div className="mb-4 flex gap-2">
          {(["PENDING", "COMPLETED"] as const).map((t) => (
            <button key={t} onClick={() => setTab(t)} className={`rounded-full px-4 py-1.5 text-sm ${tab === t ? "bg-primary text-white" : "bg-background"}`}>{label(t)}</button>
          ))}
        </div>
        <ErrorBox error={list.error} />
        {list.isLoading ? <Loading /> : !list.data?.length ? <Empty>{tab === "PENDING" ? "Nothing to pay right now." : "No completed refunds yet."}</Empty> : (
          <Table head={["Booking", "Amount", "Tier", "Requested", "Status", tab === "PENDING" ? "" : "Payout ref"]}>
            {list.data.map((r) => (
              <tr key={r.id}>
                <Td><Link className="font-semibold text-primary" to={`/bookings/${r.bookingId}`}>#{r.bookingId}</Link></Td>
                <Td className="font-semibold">{usd(r.amountUsd)}</Td><Td>{label(r.tier)} · {r.refundPercent}%</Td>
                <Td>{dateTime(r.createdAt)}</Td><Td><StatusBadge status={r.status} /></Td>
                <Td>{tab === "PENDING" ? <Button onClick={() => markSent(r)}>Mark as sent</Button> : <span className="mono">{r.payoutReference}</span>}</Td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </>
  );
}
