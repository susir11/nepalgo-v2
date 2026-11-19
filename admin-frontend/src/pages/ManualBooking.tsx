import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { api } from "../lib/api";
import { useGet, qs } from "../lib/hooks";
import { date } from "../lib/format";
import { Button, Card, Empty, ErrorBox, Field, Input, PageHeader, Select, StatusBadge, Textarea, useConfirm } from "../components/ui";
import { DayListEditor, daysValid, toApiDays, toDays, type Day } from "../components/DayListEditor";

type Form = { destinationId: string; partySize: string; arrivalDate: string; guideRequested: boolean; porterRequested: boolean; specialRequestText: string; internalNotes: string };

// Create Booking Manually (spec 8b): for trips negotiated on WhatsApp. The
// booking starts in AWAITING_PAYMENT; next step is to build + issue its bill.
export default function ManualBooking() {
  const nav = useNavigate();
  const confirm = useConfirm();
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [customer, setCustomer] = useState<any>(null);
  const customers = useGet<any>(search ? `/api/v1/admin/customers?${qs({ q: search, size: 8 })}` : null);
  const dests = useGet<any>("/api/destinations?size=100");
  const { register, handleSubmit, watch, formState } = useForm<Form>({ defaultValues: { partySize: "2", guideRequested: true } as any });
  const destId = watch("destinationId");
  const dest = useGet<any>(destId ? `/api/destinations/${destId}` : null);
  const [error, setError] = useState<unknown>(null);
  // Itinerary: the destination's DEFAULT, or a one-off day list agreed on WhatsApp
  // (spec 8b). Custom days live only in this booking's snapshot, never in the catalogue.
  const [mode, setMode] = useState<"DEFAULT" | "CUSTOM">("DEFAULT");
  const [days, setDays] = useState<Day[]>([]);
  // Switching destination resets the custom draft to that destination's DEFAULT as a starting point.
  useEffect(() => { setDays(toDays(dest.data?.defaultItinerary?.days)); }, [dest.data]);
  const custom = mode === "CUSTOM";

  const submit = handleSubmit((f) => {
    setError(null);
    if (!custom && !dest.data?.defaultItinerary) { setError(new Error("This destination has no DEFAULT itinerary yet. Use a custom itinerary or author the default first.")); return; }
    if (custom && !daysValid(days)) { setError(new Error("Every custom day needs a description.")); return; }
    confirm({
      title: "Create booking on customer's behalf",
      summary: [["Customer", `${customer.displayName ?? "—"} (${customer.nepalgoIdDisplay})`], ["Destination", dest.data.name],
        ["Trip start", date(f.arrivalDate)], ["Party", f.partySize], ["Itinerary", custom ? `${days.length} days (custom, this booking only)` : `${dest.data.defaultItinerary.days.length} days (DEFAULT)`]],
      consequence: "Skips approval: the booking starts in Awaiting Payment and holds these dates. Next, build the bill and press Issue bill.",
      confirmLabel: "Create booking",
      run: async () => {
        const r = await api("/api/admin/bookings/manual", { body: {
          customerId: customer.id, destinationId: Number(f.destinationId),
          // Day numbers come from list order; the server ignores any client day numbers.
          customItinerary: custom ? toApiDays(days).map(({ dayNumber: _n, ...d }) => d) : null,
          partySize: Number(f.partySize), arrivalDate: f.arrivalDate, guideRequested: f.guideRequested, porterRequested: f.porterRequested,
          specialRequestText: f.specialRequestText || null, internalNotes: f.internalNotes || null } });
        nav(`/bookings/${r.id}`);
      },
    });
  });

  return (
    <>
      <PageHeader title="Create booking manually" subtitle="For trips agreed with the customer over WhatsApp" />
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <Card title="1 · Customer">
          {customer ? (
            <div className="flex items-center justify-between gap-3 rounded-[14px] bg-primary-tint p-4 text-sm">
              <div><b>{customer.displayName ?? "(no name)"}</b> · {customer.email}<br /><span className="mono">{customer.nepalgoIdDisplay}</span> <StatusBadge status={customer.status} /></div>
              <Button variant="ghost" onClick={() => setCustomer(null)}>Change</Button>
            </div>
          ) : (
            <>
              <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); setSearch(q.trim()); }}>
                <Input placeholder="NepalGo ID, name or email" value={q} onChange={(e) => setQ(e.target.value)} />
                <Button type="submit">Search</Button>
              </form>
              <div className="mt-3 space-y-2">
                {customers.data?.content?.map((c: any) => (
                  <button key={c.id} disabled={c.status === "SUSPENDED"} onClick={() => setCustomer(c)}
                    className="flex w-full items-center justify-between rounded-[14px] bg-background/60 px-4 py-3 text-left text-sm hover:bg-primary-tint disabled:opacity-50">
                    <span><b>{c.displayName ?? "(no name)"}</b> · {c.email}</span>
                    <span className="mono">{c.nepalgoIdDisplay}</span>
                  </button>
                ))}
                {search && customers.data?.content?.length === 0 && <Empty>No customer found. They must sign up in the app first (that creates their NepalGo ID).</Empty>}
              </div>
            </>
          )}
        </Card>
        <Card title="2 · Trip">
          <form onSubmit={submit} className="space-y-3">
            <Field label="Destination">
              <Select {...register("destinationId", { required: true })} defaultValue="">
                <option value="" disabled>Choose…</option>
                {dests.data?.content?.map((d: any) => <option key={d.id} value={d.id}>{d.name} — {d.region}</option>)}
              </Select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Trip start"><Input type="date" {...register("arrivalDate", { required: true })} /></Field>
              <Field label="Party size"><Input type="number" min={1} {...register("partySize", { required: true, min: 1 })} /></Field>
            </div>
            <div className="flex gap-6 text-sm">
              <label className="flex items-center gap-2"><input type="checkbox" {...register("guideRequested")} /> Guide</label>
              <label className="flex items-center gap-2"><input type="checkbox" {...register("porterRequested")} /> Porter</label>
            </div>
            <Field label="Itinerary">
              <div className="flex flex-wrap gap-2">
                {(["DEFAULT", "CUSTOM"] as const).map((m) => (
                  <button key={m} type="button" onClick={() => setMode(m)} disabled={!destId}
                    className={`rounded-full px-4 py-1.5 text-sm ${mode === m ? "bg-primary text-white" : "bg-background"} disabled:opacity-50`}>
                    {m === "DEFAULT" ? `Destination default${dest.data?.defaultItinerary ? ` (${dest.data.defaultItinerary.days.length} days)` : ""}` : "Custom for this booking"}
                  </button>
                ))}
              </div>
            </Field>
            {custom && (
              <div className="rounded-[14px] border border-divider p-3">
                <p className="mb-3 text-xs text-muted">Starts as a copy of the default. Edit, add, remove or drag days to match what was agreed on WhatsApp.
                  Saved only on this booking; the catalogue is not changed.</p>
                <DayListEditor days={days} onChange={setDays} />
              </div>
            )}
            <Field label="Special requests (customer's words)"><Textarea rows={2} {...register("specialRequestText")} /></Field>
            <Field label="Internal notes (staff only)"><Textarea rows={2} {...register("internalNotes")} placeholder="e.g. Agreed on WhatsApp 14 Nov, custom teahouse stops" /></Field>
            <ErrorBox error={error} />
            <Button type="submit" disabled={!customer} busy={formState.isSubmitting}>Review & create</Button>
            {!customer && <p className="text-xs text-muted">Pick a customer first.</p>}
          </form>
        </Card>
      </div>
    </>
  );
}
