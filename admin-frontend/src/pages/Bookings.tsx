import { Link, useSearchParams } from "react-router";
import { useGet, qs } from "../lib/hooks";
import { Badge, Button, Card, Empty, ErrorBox, Input, Loading, PageHeader, Pager, Select, StatusBadge, Table, Td } from "../components/ui";
import { date, label } from "../lib/format";

const STATUSES = ["PENDING_APPROVAL", "AWAITING_PAYMENT", "VERIFYING_PAYMENT", "CONFIRMED", "REJECTED", "EXPIRED", "WITHDRAWN", "CANCELLED_TIERED", "CANCELLED_FORCE_MAJEURE"];

// Booking Queue (spec 15.2). Filters live in the URL, so a filtered view can be
// bookmarked or pasted to a colleague ("look at these 3").
export default function Bookings() {
  const [params, setParams] = useSearchParams();
  const f = Object.fromEntries(params.entries());
  const page = Number(f.page ?? 0);
  const set = (k: string, v: string) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v); else next.delete(k);
    if (k !== "page") next.delete("page");
    setParams(next);
  };
  const list = useGet<any>(`/api/admin/bookings?${qs({ ...f, size: 20, sort: f.sort ?? "urgency" })}`, { refreshMs: 30_000 });
  const dests = useGet<any>("/api/destinations?size=100");

  return (
    <>
      <PageHeader title="Booking queue" subtitle="Sorted by urgency: receipts to verify first, then new requests, then unpaid bills"
        actions={<Link to="/bookings/new"><Button>+ Create booking manually</Button></Link>} />
      <Card>
        <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-3 xl:grid-cols-6">
          <Input placeholder="Voucher, invoice, lead booker…" defaultValue={f.q ?? ""} onKeyDown={(e) => e.key === "Enter" && set("q", (e.target as HTMLInputElement).value)} aria-label="Search" />
          <Select value={f.status ?? ""} onChange={(e) => set("status", e.target.value)} aria-label="Status">
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </Select>
          <Select value={f.origin ?? ""} onChange={(e) => set("origin", e.target.value)} aria-label="Origin">
            <option value="">Any origin</option>
            <option value="self_served">Self-served</option>
            <option value="ops_created">Ops-created</option>
          </Select>
          <Select value={f.destinationId ?? ""} onChange={(e) => set("destinationId", e.target.value)} aria-label="Destination">
            <option value="">Any destination</option>
            {dests.data?.content?.map((d: any) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </Select>
          <Input type="date" value={f.arrivalFrom ?? ""} onChange={(e) => set("arrivalFrom", e.target.value)} aria-label="Trip start from" title="Trip start from" />
          <Input type="date" value={f.arrivalTo ?? ""} onChange={(e) => set("arrivalTo", e.target.value)} aria-label="Trip start to" title="Trip start to" />
        </div>
        <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
          <span className="text-muted">Sort:</span>
          {[["urgency", "Urgency"], ["arrival", "Trip date"], ["newest", "Newest"]].map(([v, l]) => (
            <button key={v} onClick={() => set("sort", v)} className={`rounded-full px-3 py-1 ${(f.sort ?? "urgency") === v ? "bg-primary text-white" : "bg-background"}`}>{l}</button>
          ))}
          {params.toString() && <button className="ml-auto text-primary" onClick={() => setParams(new URLSearchParams())}>Clear filters</button>}
        </div>
        <ErrorBox error={list.error} />
        {list.isLoading ? <Loading /> : list.data?.content?.length === 0 ? <Empty>No bookings match these filters.</Empty> : (
          <Table head={["Voucher", "Status", "Destination", "Trip start", "Party", "Lead booker", "Origin", "Invoice"]}>
            {list.data?.content?.map((b: any) => (
              <tr key={b.id} className="hover:bg-background/40">
                <Td><Link className="font-semibold text-primary" to={`/bookings/${b.id}`}>{b.voucherCode}</Link></Td>
                <Td><StatusBadge status={b.status} /></Td>
                <Td>{b.destinationNameSnapshot}</Td>
                <Td>{date(b.arrivalDate)}</Td>
                <Td>{b.partySize}</Td>
                <Td>{b.leadBookerLegalName ?? <span className="text-muted">—</span>}</Td>
                <Td>{b.origin === "ops_created" ? <Badge tone="mustard">Ops-created</Badge> : <Badge>Self-served</Badge>}</Td>
                <Td className="mono">{b.invoiceNumber ?? "—"}</Td>
              </tr>
            ))}
          </Table>
        )}
        <Pager page={page} totalPages={list.data?.totalPages ?? 0} onPage={(p) => set("page", String(p))} />
      </Card>
    </>
  );
}
