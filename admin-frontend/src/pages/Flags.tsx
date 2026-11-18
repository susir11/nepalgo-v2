import { useGet } from "../lib/hooks";
import { Badge, Card, ErrorBox, Loading, PageHeader, Table, Td } from "../components/ui";

// Feature Flag Status Viewer (spec 20): READ-ONLY. Each service reports the
// flags it owns from the same config its code reads; flipping one is a config
// change + deploy, deliberately not a button here.
export default function Flags() {
  const booking = useGet<any[]>("/api/admin/feature-flags/booking");
  const payment = useGet<any[]>("/api/admin/feature-flags/payment");
  const support = useGet<any[]>("/api/admin/feature-flags/support");
  const flags = [...(booking.data ?? []), ...(payment.data ?? []), ...(support.data ?? [])];
  return (
    <>
      <PageHeader title="Feature flags" subtitle="Read-only. Changing a flag is a reviewed config change, not a click." />
      <Card>
        <ErrorBox error={booking.error || payment.error || support.error} />
        {booking.isLoading || payment.isLoading ? <Loading /> : (
          <Table head={["Flag", "State", "Owner", "What it does"]}>
            {flags.map((f) => (
              <tr key={f.key}><Td className="mono">{f.key}</Td><Td>{f.enabled ? <Badge tone="success">ON</Badge> : <Badge>OFF</Badge>}</Td>
                <Td>{f.owner}</Td><Td className="max-w-md text-muted">{f.description}</Td></tr>
            ))}
          </Table>
        )}
      </Card>
    </>
  );
}
