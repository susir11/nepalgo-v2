import { useState } from "react";
import { Link } from "react-router";
import { api } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { dateTime, label } from "../lib/format";
import { Button, Card, Empty, ErrorBox, Loading, PageHeader, Pager, StatusBadge, useConfirm } from "../components/ui";

type Review = {
  id: number; bookingId: number; voucherCode: string | null; customerId: number; destinationId: number;
  destinationName: string | null; rating: number; title: string | null; body: string; authorName: string;
  tripMonth: string; status: "PENDING" | "PUBLISHED" | "REJECTED" | "HIDDEN"; moderationNote: string | null;
  moderatedAt: string | null; createdAt: string;
};
type Page = { items: Review[]; page: number; totalPages: number; totalItems: number; pendingCount: number };

const TABS = ["PENDING", "PUBLISHED", "HIDDEN", "REJECTED", "ALL"] as const;
const stars = (n: number) => "★★★★★".slice(0, n) + "☆☆☆☆☆".slice(0, 5 - n);

// Review moderation (Apple 1.2: user-generated content must be filtered).
// Nothing reaches the public destination page until staff publish it; any
// customer edit puts it back in this queue. Every decision is audit-logged.
export default function Reviews() {
  const [tab, setTab] = useState<(typeof TABS)[number]>("PENDING");
  const [page, setPage] = useState(0);
  const list = useGet<Page>(`/api/admin/reviews?status=${tab}&page=${page}&size=20`);
  const confirm = useConfirm();
  const invalidate = useInvalidate();
  const moderate = (r: Review, action: "PUBLISH" | "REJECT" | "HIDE", note?: string) =>
    api(`/api/admin/reviews/${r.id}/moderate`, { body: { action, note } }).then(() => invalidate("/api/admin/reviews"));

  const summary = (r: Review): [string, string][] => [
    ["Destination", r.destinationName ?? `#${r.destinationId}`], ["Rating", stars(r.rating)], ["Shown as", r.authorName]];

  const publish = (r: Review) => confirm({
    title: "Publish review", summary: summary(r), confirmLabel: "Publish",
    consequence: "The review becomes visible to everyone on the destination page and counts toward its average rating.",
    run: () => moderate(r, "PUBLISH"),
  });
  const reject = (r: Review) => confirm({
    title: "Reject review", summary: summary(r), confirmLabel: "Reject", danger: true,
    consequence: "The traveller sees your reason in the app and can edit and resubmit.",
    reason: { label: "Reason shown to the traveller", required: true },
    run: (note) => moderate(r, "REJECT", note),
  });
  const hide = (r: Review) => confirm({
    title: "Take review down", summary: summary(r), confirmLabel: "Hide", danger: true,
    consequence: "The review disappears from the public page. The reason is internal only. You can publish it again later.",
    reason: { label: "Internal reason", required: true },
    run: (note) => moderate(r, "HIDE", note),
  });

  return (
    <>
      <PageHeader title="Reviews" subtitle="Verified-traveller reviews waiting for moderation (oldest first)" />
      <Card>
        <div className="mb-4 flex flex-wrap gap-2">
          {TABS.map((t) => (
            <button key={t} onClick={() => { setTab(t); setPage(0); }}
              className={`rounded-full px-4 py-1.5 text-sm ${tab === t ? "bg-primary text-white" : "bg-background"}`}>
              {label(t)}{t === "PENDING" && (list.data?.pendingCount ?? 0) > 0 ? ` (${list.data!.pendingCount})` : ""}
            </button>
          ))}
        </div>
        <ErrorBox error={list.error} />
        {list.isLoading ? <Loading /> : !list.data?.items.length ? (
          <Empty>{tab === "PENDING" ? "No reviews waiting. Nice." : "Nothing here."}</Empty>
        ) : (
          <ul className="divide-y divide-divider">
            {list.data.items.map((r) => (
              <li key={r.id} className="py-4">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                  <span className="text-lg tracking-wider text-secondary" aria-label={`${r.rating} of 5 stars`}>{stars(r.rating)}</span>
                  <StatusBadge status={r.status} />
                  <span className="font-semibold">{r.destinationName ?? `Destination #${r.destinationId}`}</span>
                  <span className="text-muted">by {r.authorName} · travelled {r.tripMonth} · submitted {dateTime(r.createdAt)}</span>
                </div>
                {r.title && <div className="mt-2 font-semibold">{r.title}</div>}
                <p className="mt-1 whitespace-pre-wrap text-sm">{r.body}</p>
                <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-muted">
                  <Link className="text-primary" to={`/bookings/${r.bookingId}`}>Booking {r.voucherCode ?? `#${r.bookingId}`}</Link>
                  <Link className="text-primary" to={`/customers/${r.customerId}`}>Customer #{r.customerId}</Link>
                  {r.moderationNote && <span>Note: {r.moderationNote}</span>}
                </div>
                <div className="mt-3 flex gap-2">
                  {(r.status === "PENDING" || r.status === "HIDDEN") && <Button onClick={() => publish(r)}>Publish</Button>}
                  {r.status === "PENDING" && <Button variant="secondary" onClick={() => reject(r)}>Reject…</Button>}
                  {r.status === "PUBLISHED" && <Button variant="secondary" onClick={() => hide(r)}>Take down…</Button>}
                </div>
              </li>
            ))}
          </ul>
        )}
        {list.data && list.data.totalPages > 1 && <Pager page={page} totalPages={list.data.totalPages} onPage={setPage} />}
      </Card>
    </>
  );
}
