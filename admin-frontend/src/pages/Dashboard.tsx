import { Link } from "react-router";
import { useGet } from "../lib/hooks";
import { Badge, Card, ErrorBox, Loading, PageHeader, StatusBadge, Table, Td } from "../components/ui";
import { date, usd } from "../lib/format";

// Spec 15.1: on login, show what needs a human RIGHT NOW so nothing sits
// unnoticed in a queue nobody happened to open today.
export default function Dashboard() {
  const stats = useGet<Record<string, number>>("/api/admin/bookings/stats");
  const refunds = useGet<any[]>("/api/admin/refund-requests?status=PENDING");
  const urgent = useGet<any>("/api/admin/bookings?sort=urgency&size=8&page=0");
  const s = stats.data ?? {};

  const tiles = [
    { label: "Pending availability review", value: s.PENDING_APPROVAL, to: "/bookings?status=PENDING_APPROVAL", hint: "Price the bill, then Approve & Issue" },
    { label: "Receipts awaiting verification", value: s.VERIFYING_PAYMENT, to: "/bookings?status=VERIFYING_PAYMENT", hint: "Check Payoneer, then verify or reject" },
    { label: "Bills awaiting payment", value: s.AWAITING_PAYMENT, to: "/bookings?status=AWAITING_PAYMENT", hint: "Customer has the Payoneer link" },
    { label: "Manual bookings without a bill", value: s.MANUAL_AWAITING_BILL, to: "/bookings?status=AWAITING_PAYMENT&origin=ops_created", hint: "Build + issue the bill" },
    { label: "Open refund requests", value: refunds.data?.length, to: "/refunds", hint: "Send via Payoneer, then mark as sent" },
  ];

  return (
    <>
      <PageHeader title="Today" subtitle="What needs attention now" />
      <ErrorBox error={stats.error || refunds.error} />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {tiles.map((t) => (
          <Link key={t.label} to={t.to} className="rounded-[24px] bg-card p-5 transition hover:ring-2 hover:ring-primary/30">
            <div className="text-sm text-muted">{t.label}</div>
            <div className={`mt-2 text-[34px] font-bold tracking-[-0.5px] ${t.value ? "text-ink" : "text-muted"}`}>{t.value ?? "…"}</div>
            <div className="mt-1 text-xs text-muted">{t.hint}</div>
          </Link>
        ))}
      </div>

      <Card className="mt-6" title="Most urgent bookings" actions={<Link className="text-sm font-semibold text-primary" to="/bookings">Open queue →</Link>}>
        {urgent.isLoading ? <Loading /> : (
          <Table head={["Voucher", "Status", "Destination", "Arrival", "Origin", "Created"]}>
            {urgent.data?.content?.map((b: any) => (
              <tr key={b.id} className="hover:bg-background/40">
                <Td><Link className="font-semibold text-primary" to={`/bookings/${b.id}`}>{b.voucherCode}</Link></Td>
                <Td><StatusBadge status={b.status} /></Td>
                <Td>{b.destinationNameSnapshot}</Td>
                <Td>{date(b.arrivalDate)}</Td>
                <Td>{b.origin === "ops_created" ? <Badge tone="mustard">Ops-created</Badge> : <Badge>Self-served</Badge>}</Td>
                <Td>{date(b.createdAt)}</Td>
              </tr>
            ))}
          </Table>
        )}
        {refunds.data && refunds.data.length > 0 && (
          <p className="mt-4 text-sm text-muted">Refunds owed: {usd(refunds.data.reduce((a: number, r: any) => a + Number(r.amountUsd), 0))}</p>
        )}
      </Card>
    </>
  );
}
