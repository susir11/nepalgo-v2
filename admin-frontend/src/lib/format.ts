// Display helpers. Money is formatted, never re-calculated: the backend owns
// every number (BillCalculator), the UI only shows them.

const usdFmt = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
const nprFmt = new Intl.NumberFormat("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export const usd = (v: number | string | null | undefined) => (v == null ? "—" : usdFmt.format(Number(v)));
// en-IN grouping = 1,25,000.00 (lakh style), what Nepali accountants read naturally.
export const npr = (v: number | string | null | undefined) => (v == null ? "—" : `NPR ${nprFmt.format(Number(v))}`);

export function dateTime(v?: string | null) {
  if (!v) return "—";
  return new Date(v).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" });
}

export function date(v?: string | null) {
  if (!v) return "—";
  return new Date(v.length === 10 ? v + "T00:00:00" : v).toLocaleDateString("en-GB", { dateStyle: "medium" });
}

export const label = (s?: string | null) => (s ? s.replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase()) : "—");

// Status -> badge tone (status colours are deliberately distinct from brand colours, spec 16.1).
export function statusTone(s: string): "success" | "warning" | "error" | "neutral" | "brand" {
  if (["CONFIRMED", "COMPLETED", "ACTIVE", "PUBLISHED", "APPROVED", "REDEEMED", "LOCKED", "ACCEPTED"].includes(s)) return "success";
  if (["VERIFYING_PAYMENT", "PENDING_APPROVAL", "PENDING", "DRAFT", "RESERVED", "PENDING_DECISION", "SUBMITTED"].includes(s)) return "warning";
  if (["REJECTED", "EXPIRED", "SUSPENDED", "VOID", "CANCELLED_TIERED", "CANCELLED_FORCE_MAJEURE"].includes(s)) return "error";
  if (["AWAITING_PAYMENT"].includes(s)) return "brand";
  return "neutral";
}
