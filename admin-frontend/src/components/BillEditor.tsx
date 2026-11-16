import { useFieldArray, useForm } from "react-hook-form";
import { useState } from "react";
import { api, ApiError } from "../lib/api";
import { Button, ErrorBox, Field, Input, Select } from "./ui";

const CATEGORIES = ["ACCOMMODATION", "TRANSPORT", "FOOD", "GUIDE", "ACTIVITY", "OTHER"];

type Line = { description: string; category: string; itineraryDay: string; quantity: string; unitPriceNpr: string };
type Form = { buyerPan: string; payoneerLink: string; reason: string; lines: Line[] };

// Itemised bill editor (spec 21). Prices are typed in NPR (tax currency);
// every total shown afterwards comes from the SERVER's BillCalculator, never
// from client-side maths — one source of truth for money.
//
// The same form serves both "create" and "edit". For a LOCKED (paid) bill the
// server turns the edit into a correction (new invoice number); the admin only
// sees one extra field: the reason.
export default function BillEditor({ bookingId, bill, onSaved, onCancel }: {
  bookingId: number; bill?: any; onSaved: (b: any) => void; onCancel: () => void;
}) {
  const locked = bill?.status === "LOCKED";
  const { register, control, handleSubmit, formState } = useForm<Form>({
    defaultValues: {
      buyerPan: bill?.buyerPan ?? "",
      payoneerLink: bill?.payoneerLink ?? "",
      reason: "",
      lines: bill?.lineItems?.length
        ? bill.lineItems.map((l: any) => ({ description: l.description, category: l.category, itineraryDay: l.itineraryDay ?? "",
            quantity: String(l.quantity), unitPriceNpr: String(l.unitPriceNpr) }))
        : [{ description: "", category: "ACCOMMODATION", itineraryDay: "", quantity: "1", unitPriceNpr: "" }],
    },
  });
  const { fields, append, remove, move } = useFieldArray({ control, name: "lines" });
  const [error, setError] = useState<unknown>(null);

  const save = handleSubmit(async (f) => {
    setError(null);
    const lineItems = f.lines.map((l) => ({
      description: l.description.trim(), category: l.category,
      itineraryDay: l.itineraryDay ? Number(l.itineraryDay) : null,
      quantity: Number(l.quantity), unitPriceNpr: Number(l.unitPriceNpr),
    }));
    const body = { buyerPan: f.buyerPan || null, payoneerLink: f.payoneerLink || null, lineItems, reason: f.reason || null };
    try {
      const saved = bill
        ? await api(`/api/admin/bills/${bill.id}`, { method: "PUT", body })
        : await api("/api/admin/bills", { body: { ...body, bookingId } });
      onSaved(saved);
    } catch (e) {
      setError(e instanceof ApiError ? e : new Error(String(e)));
    }
  });

  return (
    <form onSubmit={save} className="space-y-4">
      {locked && (
        <div className="rounded-[14px] bg-[#fdf0e1] px-4 py-3 text-sm text-warning">
          This bill is <b>paid and locked</b>. Saving will create a <b>correction</b>: a new bill with a new invoice number,
          linked to {bill.invoiceNumber}. The original stays unchanged for the tax record.
        </div>
      )}
      <div className="space-y-2">
        <div className="hidden grid-cols-[2fr_1.1fr_0.6fr_0.6fr_1fr_auto] gap-2 px-1 text-xs font-semibold uppercase tracking-wide text-muted md:grid">
          <span>Description</span><span>Category</span><span>Day</span><span>Qty</span><span>Unit price (NPR)</span><span />
        </div>
        {fields.map((f, i) => (
          <div key={f.id} className="grid grid-cols-2 gap-2 rounded-[14px] bg-background/50 p-2 md:grid-cols-[2fr_1.1fr_0.6fr_0.6fr_1fr_auto] md:bg-transparent md:p-0">
            <Input className="col-span-2 md:col-span-1" placeholder="e.g. Day 2 — Private car, Kathmandu to Pokhara" aria-label="Description"
              {...register(`lines.${i}.description`, { required: true, maxLength: 500 })} />
            <Select aria-label="Category" {...register(`lines.${i}.category`)}>{CATEGORIES.map((c) => <option key={c}>{c}</option>)}</Select>
            <Input type="number" min={1} placeholder="—" aria-label="Itinerary day" {...register(`lines.${i}.itineraryDay`)} />
            <Input type="number" step="0.01" min="0.01" aria-label="Quantity" {...register(`lines.${i}.quantity`, { required: true, min: 0.01 })} />
            <Input type="number" step="0.01" min="0" aria-label="Unit price NPR" {...register(`lines.${i}.unitPriceNpr`, { required: true, min: 0 })} />
            <div className="flex gap-1">
              <Button type="button" variant="ghost" className="px-2" disabled={i === 0} onClick={() => move(i, i - 1)} aria-label="Move up">↑</Button>
              <Button type="button" variant="ghost" className="px-2" disabled={fields.length === 1} onClick={() => remove(i)} aria-label="Remove line">✕</Button>
            </div>
          </div>
        ))}
        <Button type="button" variant="secondary" onClick={() => append({ description: "", category: "OTHER", itineraryDay: "", quantity: "1", unitPriceNpr: "" })}>
          + Add line
        </Button>
        {formState.errors.lines && <p className="text-xs text-error">Every line needs a description, a quantity above 0 and a price.</p>}
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <Field label="Payoneer payment link" hint="Generate a Request-a-Payment in Payoneer for the USD total, then paste it here.">
          <Input placeholder="https://link.payoneer.com/…" {...register("payoneerLink", { pattern: /^https:\/\/.+/ })} />
        </Field>
        <Field label="Buyer PAN (optional)" hint="Only for Nepali buyers who need it on the invoice."><Input {...register("buyerPan")} /></Field>
      </div>
      {locked && (
        <Field label="Reason for correction *"><Input {...register("reason", { required: locked })} placeholder="e.g. Porter was not provided" /></Field>
      )}
      <ErrorBox error={error} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        <Button type="submit" busy={formState.isSubmitting}>{bill ? (locked ? "Save as correction" : "Save changes") : "Create bill"}</Button>
      </div>
    </form>
  );
}
