import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { api, download } from "../lib/api";
import { useAuth } from "../lib/auth";
import { useGet, useInvalidate } from "../lib/hooks";
import { dateTime } from "../lib/format";
import { Button, Card, Empty, ErrorBox, Field, Input, Loading, PageHeader, StatusBadge, Table, Td, Textarea, useConfirm } from "../components/ui";

// Billing & Company Settings (spec 15): VAT toggle, percentages, seller
// details, FX maker-checker, and the accountant's invoice export.
export default function Billing() {
  const { session } = useAuth();
  const isSuper = session?.role === "SUPER_ADMIN";
  return (
    <>
      <PageHeader title="Billing, FX & export" subtitle="Changes apply to bills created from now on. Existing bills keep their frozen values." />
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <Settings canEdit={isSuper} />
        <div className="space-y-5"><Fx canEdit={isSuper} /><Export /></div>
      </div>
    </>
  );
}

function Settings({ canEdit }: { canEdit: boolean }) {
  const s = useGet<any>("/api/admin/billing-settings");
  const confirm = useConfirm();
  const invalidate = useInvalidate();
  const { register, handleSubmit, reset, watch } = useForm<any>();
  useEffect(() => { if (s.data) reset(s.data); }, [s.data, reset]);
  const vat = watch("vatRegistered");
  const submit = handleSubmit((f) => confirm({
    title: "Change billing settings",
    summary: [["VAT registered", f.vatRegistered ? `Yes — ${f.vatPercent}% (Tax Invoice format)` : "No (PAN-style invoice)"],
      ["Service charge", `${f.serviceChargePercent}%`], ["Payment window", `${f.paymentWindowHours} hours`],
      ["Seller", f.sellerLegalName], ["PAN/VAT no.", f.sellerPanVatNumber]],
    consequence: "Every bill CREATED after this uses the new values. Bills that already exist are never changed. "
      + "The payment window applies immediately to every booking waiting for payment (it counts from when the bill was issued).",
    confirmLabel: "Save settings",
    run: () => api("/api/admin/billing-settings", { method: "PUT", body: { ...f, vatPercent: Number(f.vatPercent), serviceChargePercent: Number(f.serviceChargePercent), paymentWindowHours: Number(f.paymentWindowHours) } })
      .then(() => invalidate("/api/admin/billing-settings")),
  }));
  if (s.isLoading) return <Card title="Billing & company settings"><Loading /></Card>;
  return (
    <Card title="Billing & company settings">
      <ErrorBox error={s.error} />
      <form onSubmit={submit} className="space-y-3">
        <fieldset disabled={!canEdit} className="space-y-3">
          <label className="flex items-center gap-3 rounded-[14px] bg-background/60 p-3 text-sm">
            <input type="checkbox" {...register("vatRegistered")} />
            <span><b>VAT registered</b> — switches invoices to the full "Tax Invoice" format (VAT Rules 2053, Rule 17)</span>
          </label>
          <div className="grid grid-cols-2 gap-3">
            <Field label="VAT %"><Input type="number" step="0.01" disabled={!vat} {...register("vatPercent")} /></Field>
            <Field label="Service charge %"><Input type="number" step="0.01" {...register("serviceChargePercent")} /></Field>
          </div>
          <Field label="Payment window (hours)" hint="How long a customer has to pay and upload the receipt before the booking expires. 1–336 (14 days).">
            <Input type="number" min={1} max={336} step={1} {...register("paymentWindowHours", { required: true, min: 1, max: 336 })} />
          </Field>
          <Field label="Seller legal name"><Input {...register("sellerLegalName", { required: true })} /></Field>
          <Field label="PAN / VAT number"><Input {...register("sellerPanVatNumber", { required: true })} /></Field>
          <Field label="Registered address"><Textarea rows={2} {...register("sellerAddress", { required: true })} /></Field>
        </fieldset>
        {canEdit ? <Button type="submit">Review changes</Button> : <p className="text-xs text-muted">Read-only: only SUPER_ADMIN can change billing settings.</p>}
        <p className="text-xs text-muted">Last changed {dateTime(s.data?.updatedAt)}{s.data?.updatedBy ? ` by staff #${s.data.updatedBy}` : ""}. Confirm invoice rules with a Nepali accountant before go-live.</p>
      </form>
    </Card>
  );
}

function Fx({ canEdit }: { canEdit: boolean }) {
  const current = useGet<any>("/api/admin/fx-rates/current?pair=USD%2FNPR", { allow404: true });
  const history = useGet<any[]>("/api/admin/fx-rates?pair=USD%2FNPR");
  const invalidate = useInvalidate();
  const confirm = useConfirm();
  const [rate, setRate] = useState("");
  const [err, setErr] = useState<unknown>(null);
  const propose = async () => {
    setErr(null);
    try { await api("/api/admin/fx-rates", { body: { currencyPair: "USD/NPR", rate: Number(rate) } }); setRate(""); invalidate("/api/admin/fx-rates"); }
    catch (e) { setErr(e); }
  };
  const approve = (r: any) => confirm({
    title: "Approve FX rate (checker)",
    summary: [["Pair", r.currencyPair], ["New rate", `1 USD = ${r.rate} NPR`], ["Current rate", current.data ? `1 USD = ${current.data.rate} NPR` : "none"], ["Proposed by", `staff #${r.proposedBy}`]],
    consequence: "Becomes the rate copied onto every NEW bill. Bills already created keep the rate they were priced at.",
    confirmLabel: "Approve rate",
    run: () => api(`/api/admin/fx-rates/${r.id}/approve`, { body: {} }).then(() => invalidate("/api/admin/fx-rates")),
  });
  return (
    <Card title="FX rate — USD/NPR (maker-checker)">
      <div className="mb-4 rounded-[14px] bg-primary-tint p-4">
        <div className="text-sm text-primary">Current approved rate</div>
        <div className="text-2xl font-bold">{current.data ? `1 USD = ${current.data.rate} NPR` : "None approved yet"}</div>
        {current.data && <div className="text-xs text-muted">effective {dateTime(current.data.effectiveFrom)}</div>}
      </div>
      {canEdit && (
        <div className="mb-4 flex gap-2">
          <Input type="number" step="0.000001" min="0" placeholder="Propose new rate (maker)" value={rate} onChange={(e) => setRate(e.target.value)} />
          <Button variant="secondary" disabled={!rate} onClick={propose}>Propose</Button>
        </div>
      )}
      <ErrorBox error={err} />
      {!history.data?.length ? <Empty>No rates yet.</Empty> : (
        <Table head={["Rate", "Status", "Maker", "Checker", "Created", ""]}>
          {history.data.slice(0, 10).map((r) => (
            <tr key={r.id}><Td className="font-semibold">{r.rate}</Td><Td><StatusBadge status={r.status} /></Td><Td>#{r.proposedBy}</Td>
              <Td>{r.approvedBy ? `#${r.approvedBy}` : "—"}</Td><Td>{dateTime(r.createdAt)}</Td>
              <Td>{canEdit && r.status === "PENDING" && <Button onClick={() => approve(r)}>Approve</Button>}</Td></tr>
          ))}
        </Table>
      )}
    </Card>
  );
}

function Export() {
  const today = new Date().toISOString().slice(0, 10);
  const [from, setFrom] = useState(today.slice(0, 4) + "-01-01");
  const [to, setTo] = useState(today);
  const [err, setErr] = useState<unknown>(null);
  return (
    <Card title="Invoice export (for the accountant)">
      <p className="mb-3 text-sm text-muted">Every issued bill whose invoice number was assigned in the range, one row per line item (CSV). Input data for the accountant — not a filed VAT return.</p>
      <div className="flex flex-wrap items-end gap-3">
        <Field label="From"><Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
        <Field label="To"><Input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
        <Button onClick={() => { setErr(null); download(`/api/admin/bills/export?from=${from}&to=${to}`, `invoices_${from}_${to}.csv`).catch(setErr); }}>Download CSV</Button>
      </div>
      <ErrorBox error={err} />
    </Card>
  );
}
