import { Link, useParams } from "react-router";
import { api } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { date, dateTime, label, usd } from "../lib/format";
import { Badge, Button, Card, Empty, ErrorBox, KeyValue, Loading, PageHeader, StatusBadge, Table, Td, useConfirm } from "../components/ui";

// Customer profile (spec 15.2): bookings, wallet cards, account history, and
// the suspend/reactivate toggle (confirm + reason, logged).
export default function CustomerDetail() {
  const id = Number(useParams().id);
  const confirm = useConfirm();
  const invalidate = useInvalidate();
  const c = useGet<any>(`/api/v1/admin/customers/${id}`);
  const bookings = useGet<any>(`/api/admin/bookings?customerId=${id}&sort=newest&size=50`);
  const credits = useGet<any>(`/api/admin/store-credits?customerId=${id}&size=50`);
  const history = useGet<any[]>(`/api/v1/admin/customers/${id}/history`);

  if (c.isLoading) return <Loading />;
  if (!c.data) return <ErrorBox error={c.error ?? new Error("Customer not found")} />;
  const cu = c.data;
  const suspended = cu.status === "SUSPENDED";

  const toggle = () => confirm({
    title: suspended ? "Reactivate account" : "Suspend account", danger: !suspended,
    summary: [["Customer", cu.displayName ?? cu.email], ["NepalGo ID", cu.nepalgoIdDisplay],
      ["Active bookings", String(bookings.data?.content?.filter((b: any) => ["PENDING_APPROVAL", "AWAITING_PAYMENT", "VERIFYING_PAYMENT", "CONFIRMED"].includes(b.status)).length ?? 0)]],
    consequence: suspended ? "Lifts the block: they can request trips and submit payments again. Nothing about their history changes."
      : "Blocks NEW trip requests and payment submissions. Confirmed trips are untouched; they can still view bookings and reach support.",
    confirmLabel: suspended ? "Reactivate" : "Suspend",
    reason: { label: "Reason (kept in the account history)", required: true },
    run: (reason) => api(`/api/v1/admin/customers/${id}/${suspended ? "reactivate" : "suspend"}`, { body: { reason } })
      .then(() => invalidate("/api/v1/admin/customers")),
  });

  return (
    <>
      <PageHeader title={cu.displayName ?? cu.email ?? `Customer #${id}`}
        subtitle={<span className="flex items-center gap-2"><span className="mono">{cu.nepalgoIdDisplay}</span><StatusBadge status={cu.status} /></span>}
        actions={<Button variant={suspended ? "primary" : "danger"} onClick={toggle}>{suspended ? "Reactivate" : "Suspend"}</Button>} />
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1fr_1.6fr]">
        <div className="space-y-5">
          <Card title="Profile">
            <KeyValue items={[["Email", cu.email], ["Name", cu.displayName], ["Signed up", date(cu.createdAt)],
              ...(suspended ? [["Suspended", `${dateTime(cu.suspendedAt)} — ${cu.suspensionReason}`] as [string, string]] : [])]} />
          </Card>
          <Card title="Account history">
            {!history.data?.length ? <Empty>No admin actions on this account.</Empty> : (
              <ol className="space-y-2 text-sm">{history.data.map((h) => (
                <li key={h.id}><b>{label(h.action)}</b> <span className="text-muted">· staff #{h.actorStaffId} · {dateTime(h.createdAt)}</span><br />{h.details}</li>
              ))}</ol>
            )}
          </Card>
        </div>
        <div className="space-y-5">
          <Card title="Bookings">
            {!bookings.data?.content?.length ? <Empty>No bookings yet.</Empty> : (
              <Table head={["Voucher", "Status", "Destination", "Trip start", "Invoice"]}>
                {bookings.data.content.map((b: any) => (
                  <tr key={b.id}><Td><Link className="font-semibold text-primary" to={`/bookings/${b.id}`}>{b.voucherCode}</Link></Td>
                    <Td><StatusBadge status={b.status} /></Td><Td>{b.destinationNameSnapshot}</Td><Td>{date(b.arrivalDate)}</Td><Td className="mono">{b.invoiceNumber ?? "—"}</Td></tr>
                ))}
              </Table>
            )}
          </Card>
          <Card title="Wallet cards" actions={<Link to={`/credits?customerId=${id}`} className="text-sm font-semibold text-primary">Manage credits →</Link>}>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {bookings.data?.content?.filter((b: any) => b.status === "CONFIRMED").map((b: any) => (
                <div key={b.id} className="rounded-[24px] bg-nav p-4 text-white">
                  <div className="text-xs text-white/60">Booking confirmation</div>
                  <div className="mono mt-1 text-lg font-semibold">{b.voucherCode}</div>
                  <div className="text-sm">{b.destinationNameSnapshot} · {date(b.arrivalDate)}</div>
                </div>
              ))}
              {credits.data?.content?.map((cr: any) => (
                <div key={cr.id} className="rounded-[24px] bg-primary p-4 text-white">
                  <div className="flex justify-between text-xs text-white/70"><span>NepalGo Travel Credit</span><Badge tone="mustard">{label(cr.status)}</Badge></div>
                  <div className="mt-1 text-2xl font-bold">{usd(cr.amountUsd)}</div>
                  <div className="mono text-sm">{cr.code} · expires {date(cr.expiresOn)}</div>
                </div>
              ))}
            </div>
            {!credits.data?.content?.length && !bookings.data?.content?.some((b: any) => b.status === "CONFIRMED") && <Empty>No wallet cards.</Empty>}
          </Card>
        </div>
      </div>
    </>
  );
}
