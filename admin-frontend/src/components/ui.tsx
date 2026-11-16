// Small headless-first UI kit (spec: Radix primitives + our own tokens, not a
// pre-styled library). Shapes follow 16.1: pill buttons, 24px cards, no shadows.
import * as Dialog from "@radix-ui/react-dialog";
import { createContext, useCallback, useContext, useState, type ReactNode, type ButtonHTMLAttributes, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { ApiError } from "../lib/api";
import { label, statusTone } from "../lib/format";

const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(" ");

// ---------------------------------------------------------------- Button
type BtnVariant = "primary" | "secondary" | "danger" | "ghost";
export function Button({ variant = "primary", busy, className, children, disabled, ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: BtnVariant; busy?: boolean }) {
  const styles: Record<BtnVariant, string> = {
    primary: "bg-primary text-white hover:bg-primary-pressed",
    secondary: "bg-primary-tint text-primary hover:bg-[#d5e7dc]",
    danger: "bg-error text-white hover:brightness-95",
    ghost: "bg-transparent text-ink hover:bg-black/5",
  };
  return (
    <button {...rest} disabled={disabled || busy}
      className={cx("inline-flex items-center justify-center gap-2 rounded-full px-5 py-2.5 text-sm font-semibold transition disabled:opacity-50 disabled:cursor-not-allowed", styles[variant], className)}>
      {busy && <span className="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden />}
      {children}
    </button>
  );
}

// ---------------------------------------------------------------- Surfaces
export function Card({ children, className, title, actions }: { children: ReactNode; className?: string; title?: ReactNode; actions?: ReactNode }) {
  return (
    <section className={cx("rounded-[24px] bg-card p-5", className)}>
      {(title || actions) && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          {title && <h2 className="text-base font-semibold">{title}</h2>}
          {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-[28px] font-bold tracking-[-0.4px]">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </header>
  );
}

const tones = {
  success: "bg-[#e3f3e9] text-success",
  warning: "bg-[#fdf0e1] text-warning",
  error: "bg-[#fbe6e6] text-error",
  brand: "bg-primary-tint text-primary",
  neutral: "bg-divider text-muted",
  mustard: "bg-[#fbf1d6] text-[#8a6a10]",
};
export function Badge({ children, tone = "neutral" }: { children: ReactNode; tone?: keyof typeof tones }) {
  return <span className={cx("inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap", tones[tone])}>{children}</span>;
}
export const StatusBadge = ({ status }: { status: string }) => <Badge tone={statusTone(status)}>{label(status)}</Badge>;

export function KeyValue({ items }: { items: [ReactNode, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[minmax(120px,max-content)_1fr] gap-x-4 gap-y-2 text-sm">
      {items.map(([k, v], i) => (
        <div key={i} className="contents">
          <dt className="text-muted">{k}</dt>
          <dd className="min-w-0 break-words">{v ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}

// ---------------------------------------------------------------- Form controls
const control = "w-full rounded-[14px] border border-divider bg-white px-3 py-2 text-sm outline-none focus:border-primary";
export function Field({ label: l, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium">{l}</span>
      {children}
      {hint && !error && <span className="mt-1 block text-xs text-muted">{hint}</span>}
      {error && <span className="mt-1 block text-xs text-error">{error}</span>}
    </label>
  );
}
export const Input = (p: InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={cx(control, p.className)} />;
export const Textarea = (p: TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea {...p} className={cx(control, p.className)} />;
export const Select = (p: SelectHTMLAttributes<HTMLSelectElement>) => <select {...p} className={cx(control, p.className)} />;

// ---------------------------------------------------------------- States
export function Loading({ text = "Loading…" }: { text?: string }) {
  return <div className="flex items-center gap-2 py-8 text-sm text-muted"><span className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" />{text}</div>;
}
export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  const e = error as ApiError;
  return (
    <div role="alert" className="rounded-[14px] bg-[#fbe6e6] px-4 py-3 text-sm text-error">
      <strong className="font-semibold">{e.code ? label(e.code) : "Something went wrong"}:</strong> {e.message}
      {e.fieldErrors && <ul className="mt-1 list-disc pl-5">{Object.entries(e.fieldErrors).map(([f, m]) => <li key={f}>{f}: {m}</li>)}</ul>}
    </div>
  );
}
export const Empty = ({ children }: { children: ReactNode }) => <p className="py-8 text-center text-sm text-muted">{children}</p>;

// ---------------------------------------------------------------- Table
export function Table({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead><tr className="text-xs uppercase tracking-wide text-muted">{head.map((h) => <th key={h} className="px-3 py-2 font-semibold">{h}</th>)}</tr></thead>
        <tbody className="[&>tr]:border-t [&>tr]:border-divider">{children}</tbody>
      </table>
    </div>
  );
}
export const Td = ({ children, className }: { children?: ReactNode; className?: string }) => <td className={cx("px-3 py-2.5 align-top", className)}>{children}</td>;

export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-end gap-2 text-sm">
      <Button variant="ghost" disabled={page <= 0} onClick={() => onPage(page - 1)}>← Prev</Button>
      <span className="text-muted">Page {page + 1} of {totalPages}</span>
      <Button variant="ghost" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next →</Button>
    </div>
  );
}

// ---------------------------------------------------------------- Modal
export function Modal({ open, onOpenChange, title, children, wide }: { open: boolean; onOpenChange: (o: boolean) => void; title: string; children: ReactNode; wide?: boolean }) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
        <Dialog.Content className={cx("fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-[calc(100vw-32px)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-[24px] bg-card p-6", wide ? "max-w-4xl" : "max-w-lg")}>
          <Dialog.Title className="mb-4 text-xl font-bold tracking-[-0.2px]">{title}</Dialog.Title>
          <Dialog.Description className="sr-only">{title}</Dialog.Description>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

// ---------------------------------------------------------------- Confirm-before-you-commit (spec 15.1)
// Every high-stakes action shows exactly what's about to happen (amounts,
// recipient, consequence) and, where needed, demands a reason, before it runs.
type ConfirmOpts = {
  title: string;
  summary: [ReactNode, ReactNode][];
  consequence: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  reason?: { label: string; required: boolean };
  run: (reason: string) => Promise<unknown>;
};
const ConfirmCtx = createContext<(o: ConfirmOpts) => void>(() => {});
export const useConfirm = () => useContext(ConfirmCtx);

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [opts, setOpts] = useState<ConfirmOpts | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const open = useCallback((o: ConfirmOpts) => { setOpts(o); setReason(""); setError(null); }, []);
  const go = async () => {
    if (!opts) return;
    setBusy(true); setError(null);
    try { await opts.run(reason.trim()); setOpts(null); }
    catch (e) { setError(e); }
    finally { setBusy(false); }
  };
  return (
    <ConfirmCtx.Provider value={open}>
      {children}
      <Modal open={!!opts} onOpenChange={(o) => !o && !busy && setOpts(null)} title={opts?.title ?? ""}>
        {opts && (
          <div className="space-y-4">
            <KeyValue items={opts.summary} />
            <div className={cx("rounded-[14px] px-4 py-3 text-sm", opts.danger ? "bg-[#fbe6e6] text-error" : "bg-primary-tint text-primary")}>{opts.consequence}</div>
            {opts.reason && (
              <Field label={opts.reason.label + (opts.reason.required ? " *" : "")}>
                <Textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
              </Field>
            )}
            <ErrorBox error={error} />
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setOpts(null)} disabled={busy}>Cancel</Button>
              <Button variant={opts.danger ? "danger" : "primary"} busy={busy}
                disabled={!!opts.reason?.required && !reason.trim()} onClick={go}>{opts.confirmLabel}</Button>
            </div>
          </div>
        )}
      </Modal>
    </ConfirmCtx.Provider>
  );
}

// ---------------------------------------------------------------- Toast
const ToastCtx = createContext<(msg: string) => void>(() => {});
export const useToast = () => useContext(ToastCtx);
export function ToastProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null);
  const show = useCallback((m: string) => { setMsg(m); setTimeout(() => setMsg(null), 3500); }, []);
  return (
    <ToastCtx.Provider value={show}>
      {children}
      {msg && <div role="status" className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-full bg-nav px-5 py-3 text-sm text-white">{msg}</div>}
    </ToastCtx.Provider>
  );
}
