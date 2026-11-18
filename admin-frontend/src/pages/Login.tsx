import { useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../lib/auth";
import { Button, ErrorBox, Field, Input } from "../components/ui";

export default function Login() {
  const { login, verifyMfa, startEnroll, confirmEnroll } = useAuth();
  const [enroll, setEnroll] = useState<{ secret: string; qrContentUri: string } | null>(null);
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      if (!pending) {
        const r = await login(email.trim(), password);
        if ("enroll" in r) { setPending(r.pendingToken); setEnroll(await startEnroll(r.pendingToken)); setBusy(false); return; }
        if (r.mfa) { setPending(r.pendingToken); setBusy(false); return; }
      } else if (enroll) {
        await confirmEnroll(pending, code.trim());
      } else {
        await verifyMfa(pending, code.trim());
      }
      nav("/", { replace: true });
    } catch (err) {
      setError(err);
      if (pending) setCode("");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-full items-center justify-center p-5">
      <form onSubmit={submit} className="w-full max-w-sm rounded-[28px] bg-card p-8">
        <div className="mb-6">
          <div className="mb-2 inline-flex rounded-full bg-primary-tint px-3 py-1 text-xs font-semibold text-primary">NepalGo Admin</div>
          <h1 className="text-[28px] font-bold tracking-[-0.4px]">{enroll ? "Set up two-step verification" : pending ? "Two-step verification" : "Sign in"}</h1>
          <p className="mt-1 text-sm text-muted">
            {pending ? "Enter the 6-digit code from your authenticator app." : "Staff accounts only. Every action is logged."}
          </p>
        </div>
        <div className="space-y-4">
          {!pending ? (
            <>
              <Field label="Email"><Input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
              <Field label="Password"><Input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
            </>
          ) : (
            <>
            {enroll && (
              <div className="rounded-[14px] bg-primary-tint p-4 text-sm">
                <p className="mb-2">Required for SUPER_ADMIN. In Google Authenticator / Authy choose <b>Enter a setup key</b> and type:</p>
                <p className="mono break-all rounded-[10px] bg-card px-3 py-2 text-base font-semibold tracking-widest">{enroll.secret}</p>
                <p className="mt-2 text-xs text-muted">Or open on your phone: <a className="break-all text-primary underline" href={enroll.qrContentUri}>authenticator link</a>. Then enter the 6-digit code it shows.</p>
              </div>
            )}
            <Field label="Authenticator code">
              <Input inputMode="numeric" autoComplete="one-time-code" pattern="\d{6}" maxLength={6} required autoFocus
                value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} className="text-center text-2xl tracking-[0.5em]" />
            </Field>
            </>
          )}
          <ErrorBox error={error} />
          <Button type="submit" busy={busy} className="w-full">{pending ? "Verify" : "Continue →"}</Button>
          {pending && <Button type="button" variant="ghost" className="w-full" onClick={() => { setPending(null); setEnroll(null); setCode(""); }}>Back</Button>}
        </div>
      </form>
    </div>
  );
}
