import { StrictMode, lazy, Suspense, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import "./index.css";
import { AuthProvider, useAuth } from "./lib/auth";
import { ConfirmProvider, Loading, ToastProvider } from "./components/ui";
import Layout from "./components/Layout";
import Login from "./pages/Login";

// Route-level code splitting: each screen loads only when first opened.
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Bookings = lazy(() => import("./pages/Bookings"));
const BookingDetail = lazy(() => import("./pages/BookingDetail"));
const ManualBooking = lazy(() => import("./pages/ManualBooking"));
const Customers = lazy(() => import("./pages/Customers"));
const CustomerDetail = lazy(() => import("./pages/CustomerDetail"));
const Refunds = lazy(() => import("./pages/Refunds"));
const Destinations = lazy(() => import("./pages/Destinations"));
const DestinationEdit = lazy(() => import("./pages/DestinationEdit"));
const Credits = lazy(() => import("./pages/Credits"));
const Billing = lazy(() => import("./pages/Billing"));
const Legal = lazy(() => import("./pages/Legal"));
const Content = lazy(() => import("./pages/Content"));
const Staff = lazy(() => import("./pages/Staff"));
const Flags = lazy(() => import("./pages/Flags"));

// Admin data changes under the user's feet (other admins, customers paying),
// so keep it fresh but don't refetch on every keystroke.
const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 15_000, refetchOnWindowFocus: true, retry: 1 } },
});

function Protected({ children }: { children: ReactNode }) {
  const { session, ready } = useAuth();
  if (!ready) return <Loading text="Restoring session…" />;
  return session ? <>{children}</> : <Navigate to="/login" replace />;
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ToastProvider>
          <ConfirmProvider>
            <BrowserRouter>
              <Suspense fallback={<Loading />}>
                <Routes>
                  <Route path="/login" element={<Login />} />
                  <Route element={<Protected><Layout /></Protected>}>
                    <Route index element={<Dashboard />} />
                    <Route path="bookings" element={<Bookings />} />
                    <Route path="bookings/new" element={<ManualBooking />} />
                    <Route path="bookings/:id" element={<BookingDetail />} />
                    <Route path="customers" element={<Customers />} />
                    <Route path="customers/:id" element={<CustomerDetail />} />
                    <Route path="refunds" element={<Refunds />} />
                    <Route path="destinations" element={<Destinations />} />
                    <Route path="destinations/:id" element={<DestinationEdit />} />
                    <Route path="credits" element={<Credits />} />
                    <Route path="billing" element={<Billing />} />
                    <Route path="legal" element={<Legal />} />
                    <Route path="content" element={<Content />} />
                    <Route path="staff" element={<Staff />} />
                    <Route path="flags" element={<Flags />} />
                    <Route path="*" element={<Navigate to="/" replace />} />
                  </Route>
                </Routes>
              </Suspense>
            </BrowserRouter>
          </ConfirmProvider>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
