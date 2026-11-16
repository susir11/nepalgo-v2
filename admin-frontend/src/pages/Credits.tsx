import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useSearchParams } from "react-router";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { useGet, useInvalidate, qs } from "../lib/hooks";
import { date, dateTime, label, usd } from "../lib/format";
import { Button, Card, Empty, ErrorBox, Field, Input, KeyValue, Loading, Modal, PageHeader, Pager, Select, StatusBadge, Table, Td, Textarea, useConfirm } from "../components/ui";

// Wallet & Card Management (spec 15.2): ONE screen, two views —
// the ledger (every credit) and a detail view where edit/void live.
export default function Credits() {
  const { session } = useAuth();
  const isSuper = session?.role === "SUPER_ADMIN";
  const [params, setParams] = useSearchParams();
  const customerId = params.get("customerId") ?? "";
  const status = params.get("status") ?? "";
  const page = Number(params.get("page") ?? 0);
  const [openId, setOpenId] = useState<number | null>(null);
  const [issueOpen, setIssueOpen] = useState(false);
  const ledger = useGet<any>(`/api/admin/store-credits?${qs({ customerId, status, page, size: 20 })}`);
  const set = (k: string, v: string) => { const n = new URLSearchParams(params); v ? n.set(k, v) : n.delete(k); if (k !== "page") n.delete("page"); setParams(n); };

  return (
    <>
      <PageHeader title="Wallet & travel credits" subtitle="Every NepalGo Travel Credit ever issued"
        actions={isSuper && <Button onClick={() => setIssueOpen(true)}>+ Issue goodwill credit</Button>} />
      <Card>
        <div className="mb-4 flex flex-wrap gap-3">
          <Input className="max-w-[200px]" placeholder="Customer id" defaultValue={customerId} onKeyDown={(e) => e.key === "Enter" && set("customerId", (e.target as HTMLInputElement).value.trim())} />
          <Select className="max-w-[200px]" value={status} onChange={(e) => set("status", e.target.value)}>
            <option value="">Any status</option>{["ACTIVE", "RESERVED", "REDEEMED", "VOID"].map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </Select>
        </div>
        <ErrorBox error={ledger.error} />
        {ledger.isLoading ? <Loading /> : !ledger.data?.content?.length ? <Empty>No credits.</Empty> : (
          <Table head={["Code", "Customer", "Value", "Expires", "Status", "Source"]}>
            {ledger.data.content.map((c: any) => (
              <tr key={c.id} className="cursor-pointer hover:bg-background/40" onClick={() => setOpenId(c.id)}>
                <Td className="mono font-semibold text-primary">{c.code}</Td>
                <Td><Link onClick={(e) => e.stopPropagation()} className="text-primary" to={`/customers/${c.customerId}`}>#{c.customerId}</Link></Td>
                <Td>{usd(c.amountUsd)}</Td><Td>{date(c.expiresOn)}</Td><Td><StatusBadge status={c.status} /></Td>
                <Td>{c.source === "CANCELLATION" ? `Cancellation of #${c.sourceBookingId}` : "Manual / goodwill"}</Td>
              </tr>
            ))}
          </Table>
        )}
        <Pager page={page} totalPages={ledger.data?.totalPages ?? 0} onPage={(p) => set("page", String(p))} />
      </Card>
      {openId && <CreditDetail id={openId} canEdit={isSuper} onClose={() => setOpenId(null)} />}
      <IssueCredit open={issueOpen} onOpenChange={setIssueOpen} defaultCustomer={customerId} />
    </>
  );
}

function CreditDetail({ id, canEdit, onClose }: { id: number; canEdit: boolean; onClose: () => void }) {
  const d = useGet<any>(`/api/admin/store-credits/${id}`);
  const invalidate = useInvalidate();
  const confirm = useConfirm();
  const { register, handleSubmit } = useForm<any>();
  const [error, setError] = useState<unknown>(null);
  const c = d.data?.credit;
  const active = c?.status === "ACTIVE";
  const save = handleSubmit(async (f) => {
    setError(null);
    try {
      await api(`/api/admin/store-credits/${id}`, { method: "PUT", body: { amountUsd: f.amountUsd ? Number(f.amountUsd) : null, expiresOn: f.expiresOn || null } });
      invalidate("/api/admin/store-credits");
    } catch (e) { setError(e); }
  });
  const voidIt = () => confirm({
    title: "Void travel credit", danger: true, summary: [["Code", c.code], ["Value", usd(c.amountUsd)], ["Customer", `#${c.customerId}`]],
    consequence: "The credit disappears from the customer's wallet and can never be used. Logged in the credit's history.",
    confirmLabel: "Void credit", reason: { label: "Reason", required: true },
    run: (reason) => api(`/api/admin/store-credits/${id}/void`, { body: { reason } }).then(() => invalidate("/api/admin/store-credits")),
  });
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={c ? `Credit ${c.code}` : "Credit"} wide>
      {!c ? <Loading /> : (
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
          <div className="space-y-4">
            <KeyValue items={[["Status", <StatusBadge status={c.status} />], ["Value", usd(c.amountUsd)], ["Expires", date(c.expiresOn)],
              ["Customer", `#${c.customerId}`], ["Source", c.source === "CANCELLATION" ? `Cancellation of booking #${c.sourceBookingId}` : "Manual"],
              ["Applied to booking", c.reservedBookingId ? `#${c.reservedBookingId}` : "—"], ["Note", c.note], ["Void reason", c.voidReason]]} />
            {canEdit && active && (
              <form onSubmit={save} className="space-y-3 rounded-[14px] bg-background/60 p-4">
                <div className="grid grid-cols-2 gap-3">
                  <Field label="New value (USD)"><Input type="number" step="0.01" min="0.01" {...register("amountUsd")} placeholder={c.amountUsd} /></Field>
                  <Field label="New expiry"><Input type="date" {...register("expiresOn")} /></Field>
                </div>
                <ErrorBox error={error} />
                <div className="flex gap-2"><Button type="submit" variant="secondary">Save edit</Button><Button type="button" variant="danger" onClick={voidIt}>Void</Button></div>
              </form>
            )}
          </div>
          <div>
            <h3 className="mb-2 text-sm font-semibold">History</h3>
            {!d.data.history.length ? <Empty>No edits.</Empty> : (
              <ol className="space-y-2 text-sm">{d.data.history.map((h: any) => (
                <li key={h.id}><b>{h.fieldChanged}</b>: {h.oldValue} → {h.newValue}<br /><span className="text-xs text-muted">staff #{h.adminUserId} · {dateTime(h.createdAt)}</span></li>
              ))}</ol>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}

function IssueCredit({ open, onOpenChange, defaultCustomer }: { open: boolean; onOpenChange: (o: boolean) => void; defaultCustomer: string }) {
  const { register, handleSubmit, reset } = useForm<any>({ values: { customerId: defaultCustomer, amountUsd: "", expiresOn: "", note: "" } });
  const confirm = useConfirm();
  const invalidate = useInvalidate();
  const submit = handleSubmit((f) => confirm({
    title: "Issue goodwill credit",
    summary: [["Customer", `#${f.customerId}`], ["Value", usd(f.amountUsd)], ["Expires", f.expiresOn ? date(f.expiresOn) : "in 365 days"], ["Note", f.note || "—"]],
    consequence: "Appears immediately in the customer's wallet as a spendable NepalGo Travel Credit.",
    confirmLabel: "Issue credit",
    run: () => api("/api/admin/store-credits", { body: { customerId: Number(f.customerId), amountUsd: Number(f.amountUsd), expiresOn: f.expiresOn || null, note: f.note || null } })
      .then(() => { invalidate("/api/admin/store-credits"); reset(); onOpenChange(false); }),
  }));
  return (
    <Modal open={open} onOpenChange={onOpenChange} title="Issue goodwill credit">
      <form onSubmit={submit} className="space-y-3">
        <Field label="Customer id" hint="Open the customer's profile to find it."><Input type="number" {...register("customerId", { required: true })} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Value (USD)"><Input type="number" step="0.01" min="0.01" {...register("amountUsd", { required: true })} /></Field>
          <Field label="Expires (optional)"><Input type="date" {...register("expiresOn")} /></Field>
        </div>
        <Field label="Note"><Textarea rows={2} {...register("note")} placeholder="e.g. Apology for the delayed pickup in Pokhara" /></Field>
        <div className="flex justify-end gap-2"><Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>Cancel</Button><Button type="submit">Review</Button></div>
      </form>
    </Modal>
  );
}
