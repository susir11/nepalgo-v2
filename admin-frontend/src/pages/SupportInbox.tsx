import { useState } from "react";
import { Link } from "react-router";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { useGet, useInvalidate } from "../lib/hooks";
import { dateTime } from "../lib/format";
import { Badge, Button, Card, Empty, ErrorBox, Loading, PageHeader, StatusBadge, Table, Td, Textarea, useConfirm } from "../components/ui";

// Shared support inbox (spec 12): any available staff member picks a thread up.
// Only reachable while in_app_support_chat_enabled is ON (support-service 404s otherwise).
export default function SupportInbox() {
  const status = useGet<any>("/api/support-status");
  const [tab, setTab] = useState<"OPEN" | "CLOSED" | "CALLBACKS">("OPEN");
  const [open, setOpen] = useState<number | null>(null);
  if (status.isLoading) return <Loading />;
  if (!status.data?.inAppChatEnabled) {
    return (
      <>
        <PageHeader title="Support inbox" />
        <Card><Empty>In-app support chat is OFF (feature flag <span className="mono">in_app_support_chat_enabled</span>). Customers use WhatsApp.</Empty></Card>
      </>
    );
  }
  return (
    <>
      <PageHeader title="Support inbox" subtitle="Shared inbox: pick up a thread to own it" />
      <div className="mb-4 flex gap-2">
        {(["OPEN", "CLOSED", "CALLBACKS"] as const).map((t) => (
          <button key={t} onClick={() => { setTab(t); setOpen(null); }} className={`rounded-full px-4 py-1.5 text-sm ${tab === t ? "bg-primary text-white" : "bg-card"}`}>
            {t === "CALLBACKS" ? "Callback requests" : t === "OPEN" ? "Open" : "Closed"}
          </button>
        ))}
      </div>
      {tab === "CALLBACKS" ? <Callbacks /> : (
        <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1fr_1.3fr]">
          <Threads status={tab} selected={open} onSelect={setOpen} />
          {open ? <Thread id={open} /> : <Card><Empty>Select a conversation.</Empty></Card>}
        </div>
      )}
    </>
  );
}

function Threads({ status, selected, onSelect }: { status: string; selected: number | null; onSelect: (id: number) => void }) {
  const list = useGet<any[]>(`/api/admin/support/threads?status=${status}`);
  return (
    <Card>
      <ErrorBox error={list.error} />
      {list.isLoading ? <Loading /> : !list.data?.length ? <Empty>No conversations.</Empty> : (
        <ul className="space-y-1">
          {list.data.map((t) => (
            <li key={t.id}>
              <button onClick={() => onSelect(t.id)} className={`w-full rounded-[14px] px-3 py-2 text-left text-sm ${selected === t.id ? "bg-primary-tint" : "hover:bg-background"}`}>
                <div className="flex items-center gap-2"><b className="truncate">{t.subject}</b>{!t.assignedStaffId && <Badge tone="warning">unassigned</Badge>}</div>
                <div className="text-xs text-muted">Customer #{t.customerId} · {dateTime(t.lastMessageAt)}</div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Thread({ id }: { id: number }) {
  const { session } = useAuth();
  const t = useGet<any>(`/api/admin/support/threads/${id}`);
  const invalidate = useInvalidate();
  const [text, setText] = useState("");
  const [err, setErr] = useState<unknown>(null);
  const act = async (path: string, body: any = {}) => {
    setErr(null);
    try { await api(`/api/admin/support/threads/${id}/${path}`, { body }); invalidate("/api/admin/support"); }
    catch (e) { setErr(e); }
  };
  if (t.isLoading || !t.data) return <Card><Loading /></Card>;
  const th = t.data.thread;
  const mineOrFree = !th.assignedStaffId || th.assignedStaffId === session?.staffId;
  return (
    <Card title={th.subject} actions={<>
      {!th.assignedStaffId && <Button variant="secondary" onClick={() => act("pick-up")}>Pick up</Button>}
      {th.status === "OPEN" && <Button variant="ghost" onClick={() => act("close")}>Close</Button>}
    </>}>
      <p className="mb-3 text-xs text-muted">
        <Link className="text-primary" to={`/customers/${th.customerId}`}>Customer #{th.customerId}</Link>
        {th.bookingId && <> · <Link className="text-primary" to={`/bookings/${th.bookingId}`}>booking #{th.bookingId}</Link></>}
        {" · "}<StatusBadge status={th.status} />{th.assignedStaffId && ` · owned by staff #${th.assignedStaffId}`}
      </p>
      <ol className="mb-4 max-h-[50vh] space-y-2 overflow-y-auto">
        {t.data.messages.map((m: any) => (
          <li key={m.id} className={`max-w-[85%] rounded-[14px] px-3 py-2 text-sm ${m.senderType === "STAFF" ? "ml-auto bg-primary text-white" : "bg-background"}`}>
            {m.body}<div className={`mt-1 text-[11px] ${m.senderType === "STAFF" ? "text-white/70" : "text-muted"}`}>{dateTime(m.createdAt)}</div>
          </li>
        ))}
      </ol>
      <Textarea rows={3} value={text} onChange={(e) => setText(e.target.value)} placeholder={mineOrFree ? "Reply… (replying also picks the thread up)" : "Owned by another staff member"} disabled={!mineOrFree} />
      <ErrorBox error={err} />
      <div className="mt-2 flex justify-end"><Button disabled={!text.trim() || !mineOrFree} onClick={() => act("messages", { body: text.trim() }).then(() => setText(""))}>Send</Button></div>
    </Card>
  );
}

function Callbacks() {
  const list = useGet<any[]>("/api/admin/support/callbacks?status=PENDING");
  const invalidate = useInvalidate();
  const confirm = useConfirm();
  return (
    <Card>
      <ErrorBox error={list.error} />
      {list.isLoading ? <Loading /> : !list.data?.length ? <Empty>No pending callback requests.</Empty> : (
        <Table head={["Customer", "Phone", "Preferred time", "Booking", "Requested", ""]}>
          {list.data.map((c) => (
            <tr key={c.id}>
              <Td><Link className="text-primary" to={`/customers/${c.customerId}`}>#{c.customerId}</Link></Td>
              <Td className="mono">{c.phoneNumber}</Td><Td>{c.preferredTime}</Td>
              <Td>{c.bookingId ? <Link className="text-primary" to={`/bookings/${c.bookingId}`}>#{c.bookingId}</Link> : "—"}</Td>
              <Td>{dateTime(c.createdAt)}</Td>
              <Td><Button onClick={() => confirm({ title: "Mark callback done", summary: [["Phone", c.phoneNumber]],
                consequence: "Only after you actually called the customer.", confirmLabel: "Mark done",
                run: () => api(`/api/admin/support/callbacks/${c.id}/complete`, { body: {} }).then(() => invalidate("/api/admin/support")) })}>Done</Button></Td>
            </tr>
          ))}
        </Table>
      )}
    </Card>
  );
}
