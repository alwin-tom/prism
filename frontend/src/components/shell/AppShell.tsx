import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { CalendarDays, CircleHelp, CreditCard, Globe, Images, Inbox, LayoutDashboard, LayoutTemplate, LogOut, Menu, PanelLeftClose, PanelLeftOpen, Plus, ScrollText, Settings, Sparkles, Tags, Users, X } from "lucide-react";
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
      label: "Sites",
      items: [
        { to: "/app", label: "Event sites", icon: LayoutDashboard, end: true },
        ...(creation ? [{ to: "/app/create", label: "Create a website", icon: Sparkles }] : []),
        { to: "/app/new", label: "New event site", icon: Plus },
      ],
    },
    {
      label: "Library",
      items: [
        { to: "/app/media", label: "Media library", icon: Images },
        { to: "/app/domains", label: "Domains and hosting", icon: Globe },
        { to: "/app/billing", label: "Billing", icon: CreditCard },
        { to: "/app/help", label: "Help", icon: CircleHelp },
      ],
    },
    {
      label: "Account",
      items: [
        { to: "/app/account", label: "Account", icon: Settings },
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
  const groups = mode === "admin" ? admin : customerGroups(features.data?.aiWebsiteGenerationEnabled === true);
  const scope = mode === "admin" ? "Staff" : "Customer";
  const editor = /\/events\/[^/]+\/(edit|studio)$/.test(pathname);

  function toggleSidebar() {
    setCollapsed((value) => {
      const next = !value;
      try {
        localStorage.setItem("prism-sidebar", next ? "collapsed" : "open");
      } catch {
        // The preference stays for this visit when storage is blocked.
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
            <span>{scope}</span>
          </span>
          <button className="rail-button menu-close" type="button" aria-label="Close menu" onClick={() => setOpen(false)}>
            <X size={16} />
          </button>
          <button className="rail-button sidebar-toggle" type="button" aria-expanded={!collapsed} aria-controls="app-sidebar" aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"} title={collapsed ? "Expand sidebar" : "Collapse sidebar"} onClick={toggleSidebar}>
            {collapsed ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
          </button>
        </div>
        <nav className="nav-scroll" aria-label={mode === "admin" ? "Admin" : "Your sites"}>
          {groups.map((group) => (
            <div key={group.label} className="nav-group">
              <div className="nav-label">{group.label}</div>
              {group.items.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className="nav-link" aria-label={item.label} title={item.label} onClick={() => setOpen(false)}>
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
          </div>
          <button className="nav-link sign-out" type="button" aria-label="Sign out" title="Sign out" onClick={() => void signOut()}>
            <LogOut size={16} />
            <span>Sign out</span>
          </button>
        </div>
      </aside>
      <header className="topbar">
        <button className="menu-button btn-secondary" type="button" aria-expanded={open} aria-controls="app-sidebar" onClick={() => setOpen((value) => !value)}>
          <Menu size={16} /> Menu
        </button>
        <span className="brand-mini"><Mark /> WebAux</span>
      </header>
      <div className="sheet-wrap">
        <div className="sheet">
          <Outlet key={pathname} />
        </div>
      </div>
    </div>
  );
}
