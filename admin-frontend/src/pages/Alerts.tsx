import { Link } from "react-router";
import { api } from "../lib/api";
import { useGet, useInvalidate } from "../lib/hooks";
import { dateTime } from "../lib/format";
import { Badge, Button, Card, Empty, ErrorBox, Loading, PageHeader } from "../components/ui";

// Admin in-app alerts (spec 19: "customer uploads payment receipt -> admin
// in-app alert / email"). Same rows notification-service e-mails to ops.
export default function Alerts() {
  const alerts = useGet<any>("/api/admin/notifications", { refreshMs: 30_000 });
  const invalidate = useInvalidate();
  const read = (id: number) => api(`/api/admin/notifications/${id}/read`, { body: {} }).then(() => invalidate("/api/admin/notifications"));
  return (
    <>
      <PageHeader title="Alerts" subtitle={alerts.data ? `${alerts.data.unread} unread` : undefined} />
      <Card>
        <ErrorBox error={alerts.error} />
        {alerts.isLoading ? <Loading /> : !alerts.data?.content?.length ? <Empty>No alerts yet.</Empty> : (
          <ul className="divide-y divide-divider">
            {alerts.data.content.map((n: any) => (
              <li key={n.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 font-semibold">{!n.read && <Badge tone="warning">new</Badge>}{n.title}</div>
                  <p className="mt-1 text-sm text-muted">{n.body}</p>
                  <p className="mt-1 text-xs text-muted">{dateTime(n.createdAt)}</p>
                </div>
                <div className="flex gap-2">
                  {n.bookingId && <Link to={`/bookings/${n.bookingId}`}><Button variant="secondary" onClick={() => !n.read && read(n.id)}>Open booking</Button></Link>}
                  {!n.read && <Button variant="ghost" onClick={() => read(n.id)}>Mark read</Button>}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}
