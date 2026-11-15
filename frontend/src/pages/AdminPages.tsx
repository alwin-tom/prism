import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { DeleteWebsiteButton } from "../components/DeleteWebsiteButton";
import { ModerationPanel } from "../components/moderation/ModerationPanel";
import { Button, Empty, Field, Pill } from "../components/ui";
import { PublicSite } from "../components/templates/PublicSite";
import { api, errorMessage } from "../services/api";
import type { Account, Envelope, EventSite } from "../types";
import { formatPaise } from "../utils/money";

type Dashboard = {
  waitingOnReview: number;
  published: number;
  customers: number;
  paidTodayPaise: number;
  paidThisMonthPaise: number;
  hostingEndingSoon: Array<{ eventId: string; eventName: string; slug: string; endAt: string }>;
  submissions: Array<{ day: string; count: number }>;
};

type ModerationFilter = "ALL" | "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "CLEAN" | "PROCESSING" | "FAILED";

export function AdminHomePage() {
  const [filter, setFilter] = useState<ModerationFilter>("ALL");
  const dashboard = useQuery({
    queryKey: ["admin-dashboard"],
    queryFn: async () => (await api.get<Envelope<Dashboard>>("/admin/dashboard")).data.data,
  });
  const queue = useQuery({
    queryKey: ["admin-reviews"],
    queryFn: async () => (await api.get<Envelope<EventSite[]>>("/admin/reviews")).data.data,
    refetchInterval: 5000,
  });
  const moderationSummary = useQuery({
    queryKey: ["admin-moderation-summary"],
    queryFn: async () => (await api.get<Envelope<Record<string, number>>>("/admin/moderation/summary")).data.data,
  });
  const data = dashboard.data;
  const max = Math.max(1, ...(data?.submissions.map((row) => row.count) ?? [1]));
  const rows = useMemo(() => {
    const list = queue.data ?? [];
    return list.filter((event) => matchesModerationFilter(event, filter));
  }, [queue.data, filter]);
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Review queue</h1>
          <p>{data ? `${data.waitingOnReview} sites are waiting for a decision.` : "Loading the queue."}</p>
        </div>
      </div>
      <div className="work">
        {data ? (
          <div className="split">
            <div>
              <div className="kpis">
                <div className="kpi"><span>Waiting</span><strong className="num">{data.waitingOnReview}</strong><em>Submitted or in review</em></div>
                <div className="kpi"><span>Published</span><strong className="num">{data.published}</strong><em>Live event sites</em></div>
                <div className="kpi"><span>Paid this month</span><strong className="num">{formatPaise(data.paidThisMonthPaise)}</strong><em>{formatPaise(data.paidTodayPaise)} today</em></div>
              </div>
              {moderationSummary.data ? (
                <div className="kpis" style={{ marginTop: 12 }}>
                  <div className="kpi"><span>Critical</span><strong className="num">{moderationSummary.data.critical ?? 0}</strong><em>Completed reports</em></div>
                  <div className="kpi"><span>High risk</span><strong className="num">{moderationSummary.data.high ?? 0}</strong><em>Need close review</em></div>
                  <div className="kpi"><span>Processing</span><strong className="num">{(moderationSummary.data.processing ?? 0) + (moderationSummary.data.pending ?? 0)}</strong><em>AI moderation</em></div>
                </div>
              ) : null}
              <div className="moderation-actions" style={{ margin: "12px 0" }}>
                {(["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW", "CLEAN", "PROCESSING", "FAILED"] as ModerationFilter[]).map((item) => (
                  <Button key={item} type="button" tone={filter === item ? "primary" : "secondary"} onClick={() => setFilter(item)}>
                    {item === "ALL" ? "All" : item.toLowerCase()}
                  </Button>
                ))}
              </div>
              <section className="list">
                {rows.length === 0 ? <Empty>The queue is empty. New submissions appear here in the order they arrived.</Empty> : rows.map((event) => {
                  const risk = event.moderation?.overallRisk ?? "CLEAN";
                  const moderationStatus = event.moderation?.status ?? "PENDING";
                  return (
                    <Link key={event.id} to={`/admin/reviews/${event.id}`} className={`site-row risk-${risk.toLowerCase()}`}>
                      <div>
                        <strong>{event.eventName}</strong>
                        <div className="id">/{event.slug}</div>
                      </div>
                      <Pill status={event.submissionStatus ?? event.status} />
                      <span className={`risk-chip risk-badge risk-${risk.toLowerCase()}`}>{risk}</span>
                      <span className="id">{labelStatus(moderationStatus)} · C{event.moderation?.summary.critical ?? 0}/H{event.moderation?.summary.high ?? 0}/M{event.moderation?.summary.medium ?? 0}/L{event.moderation?.summary.low ?? 0}</span>
                      <span className="num">{event.requestedDays} days</span>
                    </Link>
                  );
                })}
              </section>
            </div>
            <aside>
              <h2 className="block-title">Submissions, 14 days</h2>
              <svg className="bar-chart" viewBox="0 0 280 160" role="img" aria-label="Submissions over the last fourteen days">
                {(data.submissions ?? []).map((row, index) => {
                  const height = (row.count / max) * 120;
                  return <rect key={row.day} x={index * 20} y={140 - height} width="12" height={height} />;
                })}
              </svg>
              <h2 className="block-title" style={{ marginTop: 16 }}>Hosting ending within a week</h2>
              {(data.hostingEndingSoon ?? []).length === 0 ? <p className="empty">None.</p> : (data.hostingEndingSoon ?? []).map((row) => (
                <p key={row.eventId}>{row.eventName} · <span className="id">/{row.slug}</span></p>
              ))}
            </aside>
          </div>
        ) : <div className="skeleton" style={{ height: 72 }} />}
      </div>
    </>
  );
}

export function ReviewPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [previewRoot, setPreviewRoot] = useState<HTMLDivElement | null>(null);
  const me = useQuery({
    queryKey: ["me"],
    queryFn: async () => (await api.get<Envelope<Account>>("/auth/me")).data.data,
  });
  const [comments, setComments] = useState("");
  const event = useQuery({
    queryKey: ["admin-event", id],
    queryFn: async () => (await api.get<Envelope<EventSite>>(`/admin/events/${id}`)).data.data,
    refetchInterval: (query) => (query.state.data?.moderation?.blocksApproval ? 2500 : false),
  });
  const decide = useMutation({
    mutationFn: async (action: "approve" | "request-changes" | "reject") => api.post(`/admin/events/${id}/${action}`, { comments }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-event", id] });
      void queryClient.invalidateQueries({ queryKey: ["admin-reviews"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-events"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-moderation", id] });
    },
  });
  const site = event.data;
  if (!site) return <div className="skeleton" style={{ height: 28, width: 200 }} />;
  const blocked = site.moderation?.blocksApproval === true;
  const approveBlocked = blocked || site.moderation?.status !== "COMPLETED";
  const riskTone = site.moderation?.status === "COMPLETED"
    ? (site.moderation.overallRisk ?? "CLEAN").toLowerCase()
    : (site.moderation?.status ?? "pending").toLowerCase();
  return (
    <div className="review-page">
      <div className="sheet-title">
        <div>
          <h1>{site.eventName}</h1>
          <p>/{site.slug} · {site.requestedDays} hosting days{site.templateName ? ` · ${site.templateName}` : ""}</p>
        </div>
        <div className="title-actions">
          {me.data?.roles.includes("ADMIN") ? <DeleteWebsiteButton id={site.id} name={site.eventName} onDeleted={() => navigate("/admin/events")} /> : null}
          <Pill status={site.status} />
          {site.moderation ? (
            <span className={`risk-badge risk-${riskTone}`}>
              {site.moderation.status === "COMPLETED" ? site.moderation.overallRisk : site.moderation.status.replaceAll("_", " ")}
            </span>
          ) : null}
        </div>
      </div>

      <ModerationPanel eventId={id} previewRoot={previewRoot} />

      <div className="review-workspace">
        <div className="review-preview-panel">
          <div className="review-preview-label">
            <span>Submitted preview</span>
            <span className="id">Revision {site.revisionNumber ?? "—"}</span>
          </div>
          <div className="preview-frame review-preview-frame" ref={setPreviewRoot}>
            <PublicSite
              preview
              mediaEventId={site.id}
              event={{
                slug: site.slug,
                eventName: site.eventName,
                availability: "PUBLISHED",
                templateCode: site.templateCode,
                configuration: site.configuration,
                editorDocument: site.editorDocument,
              }}
            />
          </div>
        </div>

        <aside className="review-decision-panel">
          <h2>Review decision</h2>
          <p className="review-decision-copy">Approve publishes into the existing payment flow. Ask for changes or reject with a note the customer can read.</p>
          <Field label="Note to the customer">
            <textarea
              value={comments}
              onChange={(item) => setComments(item.target.value)}
              placeholder="You can reference moderation findings when asking for changes."
            />
          </Field>
          {decide.isError ? <p className="error-line">{errorMessage(decide.error, "The decision was not saved.")}</p> : null}
          {blocked ? <p className="moderation-wait">Approve and reject stay locked until AI moderation finishes.</p> : null}
          <div className="review-decision-actions">
            <Button type="button" disabled={decide.isPending || approveBlocked} onClick={() => decide.mutate("approve")}>Approve</Button>
            <Button type="button" tone="secondary" disabled={decide.isPending || blocked} onClick={() => decide.mutate("request-changes")}>Ask for changes</Button>
            <Button type="button" tone="secondary" disabled={decide.isPending || blocked} onClick={() => decide.mutate("reject")}>Reject</Button>
          </div>
        </aside>
      </div>
    </div>
  );
}

function matchesModerationFilter(event: EventSite, filter: ModerationFilter) {
  const moderation = event.moderation;
  if (filter === "ALL") return true;
  if (!moderation) return filter === "PROCESSING";
  if (filter === "PROCESSING") return moderation.status === "PENDING" || moderation.status === "PROCESSING" || moderation.status === "RETRY_REQUIRED";
  if (filter === "FAILED") return moderation.status === "FAILED";
  if (filter === "CLEAN") return moderation.status === "COMPLETED" && moderation.overallRisk === "CLEAN";
  return moderation.overallRisk === filter;
}

function labelStatus(value: string) {
  return value.replaceAll("_", " ").toLowerCase();
}

export function AdminEventsPage() {
  const me = useQuery({
    queryKey: ["me"],
    queryFn: async () => (await api.get<Envelope<Account>>("/auth/me")).data.data,
  });
  const admin = me.data?.roles.includes("ADMIN") ?? false;
  const events = useQuery({
    queryKey: ["admin-events"],
    queryFn: async () => (await api.get<Envelope<EventSite[]>>("/admin/events")).data.data,
  });
  const rows = events.data ?? [];
  return (
    <>
      <div className="sheet-title"><div><h1>Event sites</h1><p>{rows.length} sites, newest change first.</p></div></div>
      <section className="list">
        {rows.length === 0 ? <Empty>No event sites yet.</Empty> : rows.map((event) => (
          <div key={event.id} className="site-row">
            <div><strong>{event.eventName}</strong><div className="id">/{event.slug}</div></div>
            <Pill status={event.status} />
            <Link to={`/admin/reviews/${event.id}`}>Open</Link>
            <SiteActions id={event.id} name={event.eventName} status={event.status} admin={admin} />
          </div>
        ))}
      </section>
    </>
  );
}

function SiteActions({ id, name, status, admin }: { id: string; name: string; status: string; admin: boolean }) {
  const queryClient = useQueryClient();
  const act = useMutation({
    mutationFn: async (action: string) => api.post(`/admin/events/${id}/${action}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-events"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard"] });
    },
  });
  return (
    <span style={{ display: "flex", gap: 8, alignItems: "center" }}>
      {status === "PUBLISHED" ? (
        <>
          <button className="btn-text" type="button" onClick={() => act.mutate("suspend")}>Suspend</button>
          <button className="btn-text" type="button" onClick={() => act.mutate("unpublish")}>Unpublish</button>
        </>
      ) : null}
      {status === "SUSPENDED" || status === "APPROVED" ? <button className="btn-text" type="button" onClick={() => act.mutate("restore")}>Restore</button> : null}
      {admin ? <DeleteWebsiteButton id={id} name={name} /> : null}
    </span>
  );
}

export function CustomersPage() {
  const users = useQuery({
    queryKey: ["admin-users"],
    queryFn: async () => (await api.get<Envelope<Array<{ id: string; fullName: string; phoneDisplay: string; roles: string[]; status: string }>>>("/admin/users")).data.data,
  });
  return (
    <>
      <div className="sheet-title"><div><h1>Accounts</h1><p>Customers, reviewers and admins.</p></div></div>
      <table className="table" style={{ marginTop: 16 }}>
        <thead><tr><th>Name</th><th>Mobile</th><th>Roles</th><th>Status</th></tr></thead>
        <tbody>
          {(users.data ?? []).map((user) => (
            <tr key={user.id}><td>{user.fullName}</td><td>{user.phoneDisplay}</td><td>{user.roles.join(", ")}</td><td>{user.status}</td></tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

export function CategoriesPage() {
  const categories = useQuery({
    queryKey: ["admin-categories"],
    queryFn: async () => (await api.get<Envelope<Array<{ id: string; name: string; code: string; active: boolean; description: string }>>>("/admin/categories")).data.data,
  });
  const queryClient = useQueryClient();
  const toggle = useMutation({
    mutationFn: async (category: { id: string; name: string; code: string; description: string; active: boolean }) => api.put(`/admin/categories/${category.id}`, { ...category, active: !category.active }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-categories"] }),
  });
  return (
    <>
      <div className="sheet-title"><div><h1>Categories</h1><p>A hidden category disappears from the customer catalogue.</p></div></div>
      <section className="list">
        {(categories.data ?? []).map((category) => (
          <div key={category.id} className="site-row">
            <div><strong>{category.name}</strong><div>{category.description}</div></div>
            <span className="id">{category.code}</span>
            <Pill status={category.active ? "PUBLISHED" : "SUSPENDED"} />
            <button className="btn-text" type="button" onClick={() => toggle.mutate(category)}>{category.active ? "Hide" : "Show"}</button>
          </div>
        ))}
      </section>
    </>
  );
}

export function PaymentsPage() {
  const payments = useQuery({
    queryKey: ["admin-payments"],
    queryFn: async () => (await api.get<Envelope<Array<{ id: string; amountPaise: number; status: string; gateway: string; paymentType: string; days: number }>>>("/admin/payments")).data.data,
  });
  return (
    <>
      <div className="sheet-title"><div><h1>Payments</h1><p>Every hosting charge, including development bypasses.</p></div></div>
      <table className="table" style={{ marginTop: 16 }}>
        <thead><tr><th>Type</th><th>Gateway</th><th className="num">Days</th><th className="num">Amount</th><th>Status</th></tr></thead>
        <tbody>
          {(payments.data ?? []).map((payment) => (
            <tr key={payment.id}>
              <td>{payment.paymentType}</td>
              <td>{payment.gateway}</td>
              <td className="num">{payment.days}</td>
              <td className="num">{formatPaise(payment.amountPaise)}</td>
              <td><Pill status={payment.status === "PAID" ? "PUBLISHED" : payment.status} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

export function AuditPage() {
  const logs = useQuery({
    queryKey: ["audit"],
    queryFn: async () => (await api.get<Envelope<Array<{ id: string; action: string; createdAt: string; actorId: string | null }>>>("/admin/audit-logs")).data.data,
  });
  return (
    <>
      <div className="sheet-title"><div><h1>Audit</h1><p>Sign-ins, reviews, payments and publishing.</p></div></div>
      <table className="table" style={{ marginTop: 16 }}>
        <thead><tr><th>When</th><th>Action</th><th>Actor</th></tr></thead>
        <tbody>
          {(logs.data ?? []).map((log) => (
            <tr key={log.id}><td>{log.createdAt}</td><td>{log.action}</td><td className="id">{log.actorId ?? "—"}</td></tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
