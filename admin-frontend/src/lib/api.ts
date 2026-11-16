// Tiny fetch wrapper for the NepalGo gateway.
//
// Security choices (interview notes):
// - The ACCESS token lives only in memory: an XSS payload can't read it from
//   storage, and it dies with the tab.
// - The REFRESH token sits in sessionStorage so a page reload doesn't log the
//   admin out; it's cleared when the tab closes. (Production upgrade: an
//   httpOnly SameSite cookie set by the gateway, invisible to JavaScript.)
// - On a 401 we refresh ONCE and retry; concurrent requests share the same
//   in-flight refresh instead of stampeding the auth service.

export class ApiError extends Error {
  status: number;
  code?: string;
  fieldErrors?: Record<string, string>;
  body?: any;
  constructor(status: number, message: string, body?: any) {
    super(message);
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors;
    this.body = body;
  }
}

const REFRESH_KEY = "nepalgo.admin.refresh";
let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
const listeners = new Set<() => void>();

export function setTokens(access: string | null, refresh: string | null) {
  accessToken = access;
  try {
    if (refresh) sessionStorage.setItem(REFRESH_KEY, refresh);
    else sessionStorage.removeItem(REFRESH_KEY);
  } catch { /* storage blocked: session just won't survive reloads */ }
  listeners.forEach((l) => l());
}

export function getAccessToken() { return accessToken; }
export function onAuthChange(l: () => void) { listeners.add(l); return () => { listeners.delete(l); }; }

function storedRefresh(): string | null {
  try { return sessionStorage.getItem(REFRESH_KEY); } catch { return null; }
}

export async function tryRefresh(): Promise<boolean> {
  const rt = storedRefresh();
  if (!rt) return false;
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch("/api/v1/admin/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refreshToken: rt }),
        });
        if (!res.ok) { setTokens(null, null); return false; }
        const j = await res.json();
        setTokens(j.accessToken, j.refreshToken ?? rt);
        return true;
      } catch {
        return false;
      } finally {
        refreshing = null;
      }
    })();
  }
  return refreshing;
}

type Opts = { method?: string; body?: any; form?: FormData; raw?: boolean; auth?: boolean };

export async function api<T = any>(path: string, opts: Opts = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = {};
  if (opts.auth !== false && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  let body: BodyInit | undefined;
  if (opts.form) body = opts.form;
  else if (opts.body !== undefined) { headers["Content-Type"] = "application/json"; body = JSON.stringify(opts.body); }

  const res = await fetch(path, { method: opts.method ?? (body ? "POST" : "GET"), headers, body });

  if (res.status === 401 && opts.auth !== false && !retried && (await tryRefresh())) {
    return api<T>(path, opts, true);
  }
  if (!res.ok) {
    let b: any = null;
    try { b = await res.json(); } catch { /* empty / non-JSON body */ }
    const msg = b?.detail || b?.title || b?.message || `${res.status} ${res.statusText}`;
    throw new ApiError(res.status, msg, b);
  }
  if (opts.raw) return res as any;
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// Download a protected file (PDF / CSV) with the bearer token and hand it to the browser.
export async function download(path: string, fallbackName: string) {
  const res: Response = await api(path, { raw: true });
  const blob = await res.blob();
  const cd = res.headers.get("Content-Disposition") ?? "";
  const name = /filename="?([^";]+)"?/.exec(cd)?.[1] ?? fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

// Decode the JWT payload (NOT a verification — the backend verifies; this is
// only to show the admin's role/id in the UI and hide buttons they can't use).
export function claims(): { sub?: string; role?: string; exp?: number } {
  if (!accessToken) return {};
  try {
    const p = accessToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(p.padEnd(p.length + ((4 - (p.length % 4)) % 4), "=")));
  } catch {
    return {};
  }
}
