import { Link, useSearchParams } from "react-router";
import { useGet, qs } from "../lib/hooks";
import { date } from "../lib/format";
import { Card, Empty, ErrorBox, Input, Loading, PageHeader, Pager, Select, StatusBadge, Table, Td } from "../components/ui";

export default function Customers() {
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const status = params.get("status") ?? "";
  const page = Number(params.get("page") ?? 0);
  const list = useGet<any>(`/api/v1/admin/customers?${qs({ q, status, page, size: 20 })}`);
  const set = (k: string, v: string) => { const n = new URLSearchParams(params); v ? n.set(k, v) : n.delete(k); if (k !== "page") n.delete("page"); setParams(n); };

  return (
    <>
      <PageHeader title="Customers" subtitle="Search by NepalGo ID, name or email" />
      <Card>
        <div className="mb-4 flex flex-wrap gap-3">
          <Input className="max-w-sm" placeholder="e.g. 4829 1057, Susir, @gmail.com" defaultValue={q}
            onKeyDown={(e) => e.key === "Enter" && set("q", (e.target as HTMLInputElement).value.trim())} aria-label="Search customers" />
          <Select className="max-w-[180px]" value={status} onChange={(e) => set("status", e.target.value)} aria-label="Status">
            <option value="">Any status</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option>
          </Select>
        </div>
        <ErrorBox error={list.error} />
        {list.isLoading ? <Loading /> : !list.data?.content?.length ? <Empty>No customers found.</Empty> : (
          <Table head={["NepalGo ID", "Name", "Email", "Status", "Signed up"]}>
            {list.data.content.map((c: any) => (
              <tr key={c.id} className="hover:bg-background/40">
                <Td><Link className="mono font-semibold text-primary" to={`/customers/${c.id}`}>{c.nepalgoIdDisplay}</Link></Td>
                <Td>{c.displayName ?? "—"}</Td><Td>{c.email ?? "—"}</Td><Td><StatusBadge status={c.status} /></Td><Td>{date(c.createdAt)}</Td>
              </tr>
            ))}
          </Table>
        )}
        <Pager page={page} totalPages={list.data?.totalPages ?? 0} onPage={(p) => set("page", String(p))} />
      </Card>
    </>
  );
}
