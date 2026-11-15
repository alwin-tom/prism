import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { AppShell } from "../components/shell/AppShell";
import { LoginPage, RegisterPage, StaffLoginPage } from "../pages/AuthPages";
import { AccountPage, DashboardPage, DraftPreviewPage, EventPage, ExtendPage, PaymentPage, RsvpPage } from "../pages/CustomerPages";
import { BillingPage, DomainsPage, HelpPage, MediaLibraryPage } from "../pages/LibraryPages";
import { StudioPage } from "../features/visual-editor/StudioPage";
import { CreationPage } from "../features/website-creation/CreationPage";
import { usePageSeo } from "../seo/usePageSeo";
import { api } from "../services/api";
import type { Account, Envelope } from "../types";

function EditToStudioRedirect() {
  const { id = "" } = useParams();
  return <Navigate to={`/app/events/${id}/studio`} replace />;
}

const AdminHomePage = lazy(() => import("../pages/AdminPages").then((module) => ({ default: module.AdminHomePage })));
const ReviewPage = lazy(() => import("../pages/AdminPages").then((module) => ({ default: module.ReviewPage })));
const AdminEventsPage = lazy(() => import("../pages/AdminPages").then((module) => ({ default: module.AdminEventsPage })));
const CustomersPage = lazy(() => import("../pages/AdminPages").then((module) => ({ default: module.CustomersPage })));
const CategoriesPage = lazy(() => import("../pages/AdminPages").then((module) => ({ default: module.CategoriesPage })));
const PaymentsPage = lazy(() => import("../pages/AdminPages").then((module) => ({ default: module.PaymentsPage })));
const AuditPage = lazy(() => import("../pages/AdminPages").then((module) => ({ default: module.AuditPage })));
const DesignIntelligencePage = lazy(() => import("../pages/DesignLibraryPages").then((module) => ({ default: module.DesignIntelligencePage })));
const DesignReferencesPage = lazy(() => import("../pages/DesignLibraryPages").then((module) => ({ default: module.DesignReferencesPage })));
const DesignReferenceDetailPage = lazy(() => import("../pages/DesignLibraryPages").then((module) => ({ default: module.DesignReferenceDetailPage })));
const PublicEventPage = lazy(() => import("../pages/PublicEventPage").then((module) => ({ default: module.PublicEventPage })));

export function AppRouter() {
  return (
    <Suspense fallback={<main className="event-status"><p>Loading.</p></main>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/staff" element={<StaffLoginPage />} />
        <Route path="/app/events/:id/preview" element={<DraftPreviewPage />} />
        <Route path="/app" element={<Gate mode="customer" />}>
          <Route index element={<DashboardPage />} />
          <Route path="new" element={<Navigate to="/app/create" replace />} />
          <Route path="create" element={<CreationPage />} />
          <Route path="create/:sessionId" element={<CreationPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route path="media" element={<MediaLibraryPage />} />
          <Route path="domains" element={<DomainsPage />} />
          <Route path="billing" element={<BillingPage />} />
          <Route path="help" element={<HelpPage />} />
          <Route path="events/:id" element={<EventPage />} />
          <Route path="events/:id/edit" element={<EditToStudioRedirect />} />
          <Route path="events/:id/studio" element={<StudioPage />} />
          <Route path="events/:id/pay" element={<PaymentPage />} />
          <Route path="events/:id/extend" element={<ExtendPage />} />
          <Route path="events/:id/rsvps" element={<RsvpPage />} />
        </Route>
        <Route path="/admin" element={<Gate mode="admin" />}>
          <Route index element={<AdminHomePage />} />
          <Route path="reviews/:id" element={<ReviewPage />} />
          <Route path="events" element={<AdminEventsPage />} />
          <Route path="customers" element={<CustomersPage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="templates" element={<Navigate to="/admin/categories" replace />} />
          <Route path="design-intelligence" element={<DesignIntelligencePage />} />
          <Route path="design-references" element={<DesignReferencesPage />} />
          <Route path="design-references/new" element={<DesignReferenceDetailPage />} />
          <Route path="design-references/:id" element={<DesignReferenceDetailPage />} />
          <Route path="payments" element={<PaymentsPage />} />
          <Route path="audit" element={<AuditPage />} />
        </Route>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/:slug" element={<PublicEventPage />} />
      </Routes>
    </Suspense>
  );
}

function Gate({ mode }: { mode: "customer" | "admin" }) {
  usePageSeo({
    title: mode === "admin" ? "Staff" : "Event Sites",
    description: mode === "admin"
      ? "WebAux staff workspace."
      : "Manage your WebAux event websites, hosting, and guest replies.",
    path: mode === "admin" ? "/admin" : "/app",
    noIndex: true,
  });
  const session = useQuery({
    queryKey: ["me"],
    queryFn: async () => (await api.get<Envelope<Account>>("/auth/me")).data.data,
    retry: false,
  });
  if (session.isLoading) return <main className="event-status"><p>Loading.</p></main>;
  if (!session.data) return <Navigate to="/login" replace />;
  const staff = session.data.roles.includes("ADMIN") || session.data.roles.includes("REVIEWER");
  if (mode === "admin" && !staff) return <Navigate to="/app" replace />;
  return <AppShell account={session.data} mode={mode} />;
}
