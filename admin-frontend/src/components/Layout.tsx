import { NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { useAuth } from "../lib/auth";
import { useGet } from "../lib/hooks";
import ErrorBoundary from "./ErrorBoundary";

type Item = { to: string; label: string; superOnly?: boolean };
const NAV: { group: string; items: Item[] }[] = [
  { group: "Operations", items: [
    { to: "/", label: "Dashboard" },
    { to: "/alerts", label: "Alerts" },
    { to: "/bookings", label: "Booking queue" },
    { to: "/bookings/new", label: "Create booking manually" },
    { to: "/customers", label: "Customers" },
    { to: "/refunds", label: "Refund requests" },
    { to: "/support", label: "Support inbox" },
  ] },
  { group: "Catalogue", items: [{ to: "/destinations", label: "Destinations & itineraries" }] },
  { group: "Money", items: [
    { to: "/credits", label: "Wallet & travel credits" },
    { to: "/billing", label: "Billing, FX & export" },
  ] },
  { group: "Content", items: [
    { to: "/legal", label: "Policy content", superOnly: true },
    { to: "/content", label: "Onboarding & company", superOnly: true },
  ] },
  { group: "System", items: [
    { to: "/staff", label: "Admin team", superOnly: true },
    { to: "/flags", label: "Feature flags" },
  ] },
];

export default function Layout() {
  const { session, logout } = useAuth();
  const nav = useNavigate();
  const location = useLocation();   // new boundary per page: one crash doesn't stick across navigation
  const isSuper = session?.role === "SUPER_ADMIN";
  const alerts = useGet<any>("/api/admin/notifications");
  const unread = alerts.data?.unread ?? 0;
  return (
    <div className="flex min-h-full flex-col md:flex-row">
      <aside className="bg-nav px-4 py-5 text-white md:sticky md:top-0 md:h-screen md:w-64 md:shrink-0 md:overflow-y-auto">
        <div className="mb-6 flex items-center justify-between md:block">
          <div>
            <div className="text-lg font-bold tracking-[-0.2px]">NepalGo</div>
            <div className="text-xs text-white/60">Admin portal</div>
          </div>
        </div>
        <nav className="flex gap-1 overflow-x-auto md:block md:space-y-5" aria-label="Main">
          {NAV.map((g) => (
            <div key={g.group} className="flex gap-1 md:block md:space-y-1">
              <div className="hidden px-3 text-[11px] font-semibold uppercase tracking-wider text-white/40 md:block">{g.group}</div>
              {g.items.filter((i) => !i.superOnly || isSuper).map((i) => (
                <NavLink key={i.to} to={i.to} end={i.to === "/" || i.to === "/bookings"}
                  className={({ isActive }) => `block whitespace-nowrap rounded-full px-3 py-2 text-sm ${isActive ? "bg-primary text-white" : "text-white/80 hover:bg-white/10"}`}>
                  {i.label}{i.to === "/alerts" && unread > 0 && <span className="ml-2 rounded-full bg-secondary px-2 text-xs font-bold text-nav">{unread}</span>}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="mt-6 hidden border-t border-white/10 pt-4 text-xs text-white/60 md:block">
          <div>Staff #{session?.staffId} · <span className="text-secondary">{session?.role}</span></div>
          <button className="mt-2 rounded-full px-3 py-1.5 text-white/80 hover:bg-white/10" onClick={async () => { await logout(); nav("/login"); }}>Sign out</button>
        </div>
      </aside>
      <main className="min-w-0 flex-1 px-5 py-6 md:px-8">
        <ErrorBoundary key={location.pathname}><Outlet /></ErrorBoundary>
      </main>
    </div>
  );
}
