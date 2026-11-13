import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import {
  CalendarDays,
  ChevronRight,
  CircleHelp,
  CreditCard,
  Globe,
  Images,
  Inbox,
  LayoutDashboard,
  LayoutTemplate,
  LogOut,
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  ScrollText,
  Settings,
  Sparkles,
  Tags,
  Users,
  X,
} from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../services/api";
import type { Account, Envelope } from "../../types";
import { Mark } from "./Mark";

function readCollapsed() {
  try {
    return localStorage.getItem("prism-sidebar") === "collapsed";
  } catch {
    return false;
  }
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
}

type Item = { to: string; label: string; icon: typeof LayoutDashboard; end?: boolean };
type Group = { label: string; items: Item[] };

function customerGroups(creation: boolean): Group[] {
  return [
    {
      label: "Workspace",
      items: [
        { to: "/app", label: "Event Sites", icon: LayoutDashboard, end: true },
        ...(creation ? [{ to: "/app/create", label: "Create a Website", icon: Sparkles }] : []),
        { to: "/app/new", label: creation ? "Templates" : "New event site", icon: LayoutTemplate },
        { to: "/app/media", label: "Media Library", icon: Images },
        { to: "/app/domains", label: "Domains & Hosting", icon: Globe },
        { to: "/app/billing", label: "Billing & Invoices", icon: CreditCard },
        { to: "/app/account", label: "Settings", icon: Settings },
        { to: "/app/help", label: "Help & Support", icon: CircleHelp },
      ],
    },
  ];
}

const admin: Group[] = [
  {
    label: "Review",
    items: [
      { to: "/admin", label: "Review queue", icon: Inbox, end: true },
      { to: "/admin/events", label: "Event sites", icon: CalendarDays },
    ],
  },
  {
    label: "Library",
    items: [
      { to: "/admin/categories", label: "Categories", icon: Tags },
      { to: "/admin/templates", label: "Templates", icon: LayoutTemplate },
      { to: "/admin/design-references", label: "Design refs", icon: LayoutTemplate },
      { to: "/admin/design-intelligence", label: "Design intel", icon: Inbox },
    ],
  },
  {
    label: "Records",
    items: [
      { to: "/admin/customers", label: "Accounts", icon: Users },
      { to: "/admin/payments", label: "Payments", icon: CreditCard },
      { to: "/admin/audit", label: "Audit", icon: ScrollText },
    ],
  },
];

function crumb(pathname: string, mode: "customer" | "admin") {
  if (mode === "admin") {
    if (pathname === "/admin") return "Review queue";
    if (pathname.startsWith("/admin/events")) return "Event sites";
    if (pathname.startsWith("/admin/customers")) return "Accounts";
    if (pathname.startsWith("/admin/payments")) return "Payments";
    if (pathname.startsWith("/admin/audit")) return "Audit";
    if (pathname.startsWith("/admin/categories")) return "Categories";
    if (pathname.startsWith("/admin/templates")) return "Templates";
    if (pathname.startsWith("/admin/design")) return "Design";
    return "Staff";
  }
  if (pathname === "/app") return "Event Sites";
  if (pathname.startsWith("/app/create")) return "Create a Website";
  if (pathname.startsWith("/app/new")) return "Templates";
  if (pathname.startsWith("/app/media")) return "Media Library";
  if (pathname.startsWith("/app/domains")) return "Domains & Hosting";
  if (pathname.startsWith("/app/billing")) return "Billing";
  if (pathname.startsWith("/app/account")) return "Settings";
  if (pathname.startsWith("/app/help")) return "Help";
  if (pathname.includes("/studio")) return "Studio";
  if (pathname.includes("/edit")) return "Editor";
  if (pathname.includes("/pay")) return "Payment";
  if (pathname.includes("/extend")) return "Extend hosting";
  if (pathname.includes("/rsvps")) return "Replies";
  if (pathname.startsWith("/app/events/")) return "Site";
  return "Workspace";
}

export function AppShell({ account, mode }: { account: Account; mode: "customer" | "admin" }) {
  const [open, setOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const { pathname } = useLocation();
  const queryClient = useQueryClient();
  const features = useQuery({
    queryKey: ["features"],
    queryFn: async () => (await api.get<Envelope<{ aiWebsiteGenerationEnabled: boolean }>>("/config/features")).data.data,
    enabled: mode === "customer",
  });
  const creation = features.data?.aiWebsiteGenerationEnabled === true;
  const groups = mode === "admin" ? admin : customerGroups(creation);
  const createTo = creation ? "/app/create" : "/app/new";
  const editor = /\/events\/[^/]+\/(edit|studio)$/.test(pathname);
  const current = crumb(pathname, mode);

  function toggleSidebar() {
    setCollapsed((value) => {
      const next = !value;
      try {
        localStorage.setItem("prism-sidebar", next ? "collapsed" : "open");
      } catch {
        // Preference stays for this visit when storage is blocked.
      }
      return next;
    });
  }

  async function signOut() {
    await api.post("/auth/logout");
    queryClient.setQueryData(["me"], null);
    window.location.assign("/login");
  }

  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <div className={`app${collapsed ? " is-collapsed" : ""}${editor ? " is-editor" : ""}`}>
      {open ? <button className="sidebar-backdrop" type="button" aria-label="Close menu" onClick={() => setOpen(false)} /> : null}
      <aside id="app-sidebar" className={open ? "sidebar open" : "sidebar"}>
        <div className="brand">
          <Mark />
          <span className="brand-copy">
            <strong>WebAux</strong>
          </span>
          <button className="rail-button menu-close" type="button" aria-label="Close menu" onClick={() => setOpen(false)}>
            <X size={16} />
          </button>
          <button
            className="rail-button sidebar-toggle"
            type="button"
            aria-expanded={!collapsed}
            aria-controls="app-sidebar"
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            onClick={toggleSidebar}
          >
            {collapsed ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
          </button>
        </div>

        {mode === "customer" ? (
          <Link to={createTo} className="sidebar-create" onClick={() => setOpen(false)}>
            <Plus size={16} aria-hidden />
            <span>Create Website</span>
          </Link>
        ) : null}

        <nav className="nav-scroll" aria-label={mode === "admin" ? "Admin" : "Your sites"}>
          {groups.map((group) => (
            <div key={group.label} className="nav-group">
              <div className="nav-label">{group.label}</div>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className="nav-link"
                  aria-label={item.label}
                  title={item.label}
                  onClick={() => setOpen(false)}
                >
                  <item.icon />
                  <span>{item.label}</span>
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-foot">
          <div className="sidebar-user">
            <span className="avatar" aria-hidden="true">{initials(account.fullName)}</span>
            <span className="user-copy">
              <strong>{account.fullName}</strong>
              <span>{account.phoneDisplay}</span>
            </span>
            <span className="user-live" aria-hidden />
          </div>
          <button className="nav-link sign-out" type="button" aria-label="Sign out" title="Sign out" onClick={() => void signOut()}>
            <LogOut size={16} />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      <header className="topbar">
        <button className="menu-button btn-secondary" type="button" aria-expanded={open} aria-controls="app-sidebar" onClick={() => setOpen((value) => !value)}>
          <Menu size={16} /> Menu
        </button>
        <span className="brand-mini"><Mark /> WebAux</span>
        <div className="topbar-main">
          <nav className="topbar-crumbs" aria-label="Breadcrumb">
            <span>WebAux</span>
            <ChevronRight size={14} aria-hidden />
            <strong>{current}</strong>
          </nav>
          <div className="topbar-meta">
            <span className="topbar-chip">₹500/day tier</span>
            <span className="topbar-ops">
              <span className="ops-pill-dot" aria-hidden />
              Operational
            </span>
          </div>
        </div>
      </header>

      <div className="sheet-wrap">
        <div className="sheet">
          <Outlet key={pathname} />
        </div>
      </div>
    </div>
  );
}
