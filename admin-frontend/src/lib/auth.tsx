import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api, claims, getAccessToken, onAuthChange, setTokens, tryRefresh } from "./api";

// Staff login = password, then (if enrolled) a 6-digit TOTP code.
// The pending token from step 1 can ONLY be used at /mfa/verify (aud
// nepalgo-admin-pending, rejected by every other service).
type Session = { staffId: number; role: "SUPER_ADMIN" | "OPS" } | null;
type Ctx = {
  session: Session;
  ready: boolean;
  login: (email: string, password: string) => Promise<{ mfa: true; pendingToken: string } | { enroll: true; pendingToken: string } | { mfa: false }>;
  startEnroll: (pendingToken: string) => Promise<{ secret: string; qrContentUri: string }>;
  confirmEnroll: (pendingToken: string, code: string) => Promise<void>;
  verifyMfa: (pendingToken: string, code: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthCtx = createContext<Ctx>(null!);
export const useAuth = () => useContext(AuthCtx);

function currentSession(): Session {
  const c = claims();
  return getAccessToken() && c.sub && c.role ? { staffId: Number(c.sub), role: c.role as "SUPER_ADMIN" | "OPS" } : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const off = onAuthChange(() => setSession(currentSession()));
    // A page reload loses the in-memory access token: restore via refresh token.
    tryRefresh().finally(() => { setSession(currentSession()); setReady(true); });
    return off;
  }, []);

  const value: Ctx = {
    session,
    ready,
    async login(email, password) {
      const r = await api("/api/v1/admin/auth/login", { body: { email, password }, auth: false });
      if (r.mfaEnrollmentRequired) return { enroll: true, pendingToken: r.pendingToken };
      if (r.mfaRequired) return { mfa: true, pendingToken: r.pendingToken };
      setTokens(r.tokens.accessToken, r.tokens.refreshToken);
      return { mfa: false };
    },
    // SUPER_ADMIN without MFA: the server gives no access token until TOTP is enrolled.
    startEnroll: (pendingToken) => api("/api/v1/admin/auth/mfa/enroll/start", { body: { pendingToken }, auth: false }),
    async confirmEnroll(pendingToken, code) {
      const r = await api("/api/v1/admin/auth/mfa/enroll/confirm", { body: { pendingToken, code }, auth: false });
      setTokens(r.accessToken, r.refreshToken);
    },
    async verifyMfa(pendingToken, code) {
      const r = await api("/api/v1/admin/auth/mfa/verify", { body: { pendingToken, code }, auth: false });
      setTokens(r.accessToken, r.refreshToken);
    },
    async logout() {
      let rt: string | null = null;
      try { rt = sessionStorage.getItem("nepalgo.admin.refresh"); } catch { /* ignore */ }
      if (rt) await api("/api/v1/admin/auth/logout", { body: { refreshToken: rt }, auth: false }).catch(() => {});
      setTokens(null, null);
    },
  };
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}
