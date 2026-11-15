import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, Copy, LoaderCircle, RotateCcw, ShieldAlert } from "lucide-react";
import { Button, Field } from "../ui";
import { api, errorMessage } from "../../services/api";
import type { Envelope } from "../../types";

export type ModerationFinding = {
  id: string;
  category: string;
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  confidence: number;
  pageId: string | null;
  sectionId: string | null;
  elementId: string | null;
  elementType: string | null;
  matchedText: string;
  startOffset: number | null;
  endOffset: number | null;
  context: string | null;
  reason: string;
  suggestion: string | null;
  reviewStatus: string;
  administratorNote: string | null;
};

export type ModerationReport = {
  id: string;
  eventId: string;
  revisionId: string;
  revisionNumber: number;
  status: string;
  overallRisk: string;
  summary: { critical: number; high: number; medium: number; low: number; clean: boolean };
  explanation: string | null;
  provider: string;
  model: string | null;
  startedAt: string | null;
  completedAt: string | null;
  errorDetails: string | null;
  findings: ModerationFinding[];
  readyForDecision: boolean;
  blocksApproval: boolean;
};

const FINDING_ACTIONS = [
  { value: "CONFIRMED", label: "Confirm" },
  { value: "FALSE_POSITIVE", label: "False positive" },
  { value: "RESOLVED", label: "Resolved" },
  { value: "DISMISSED", label: "Dismiss" },
] as const;

export function ModerationPanel({
  eventId,
  previewRoot,
  compact = false,
}: {
  eventId: string;
  previewRoot: HTMLElement | null;
  compact?: boolean;
}) {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const report = useQuery({
    queryKey: ["admin-moderation", eventId],
    queryFn: async () => (await api.get<Envelope<ModerationReport>>(`/admin/events/${eventId}/moderation`)).data.data,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "PENDING" || status === "PROCESSING" || status === "RETRY_REQUIRED" ? 2500 : false;
    },
  });

  const retry = useMutation({
    mutationFn: async () => api.post(`/admin/events/${eventId}/moderation/retry`),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin-moderation", eventId] });
      await queryClient.invalidateQueries({ queryKey: ["admin-event", eventId] });
      await queryClient.invalidateQueries({ queryKey: ["admin-reviews"] });
    },
  });

  const updateStatus = useMutation({
    mutationFn: async ({ findingId, status }: { findingId: string; status: string }) =>
      api.patch(`/admin/moderation/findings/${findingId}/status`, { status, note: note || undefined }),
    onSuccess: async () => {
      setNote("");
      await queryClient.invalidateQueries({ queryKey: ["admin-moderation", eventId] });
    },
  });

  const addNote = useMutation({
    mutationFn: async (findingId: string) => api.post(`/admin/moderation/findings/${findingId}/notes`, { note }),
    onSuccess: async () => {
      setNote("");
      await queryClient.invalidateQueries({ queryKey: ["admin-moderation", eventId] });
    },
  });

  const data = report.data;
  const selected = useMemo(
    () => data?.findings.find((item) => item.id === selectedId) ?? null,
    [data, selectedId],
  );

  useEffect(() => {
    if (!previewRoot || !data) return;
    previewRoot.querySelectorAll(".moderation-hit").forEach((node) => {
      node.classList.remove(
        "moderation-hit",
        "moderation-hit-critical",
        "moderation-hit-high",
        "moderation-hit-medium",
        "moderation-hit-low",
        "moderation-hit-active",
      );
      node.removeAttribute("title");
    });
    for (const finding of data.findings) {
      if (!finding.elementId) continue;
      const target = previewRoot.querySelector(`[data-node="${CSS.escape(finding.elementId)}"]`);
      if (!target) continue;
      target.classList.add("moderation-hit", `moderation-hit-${finding.severity.toLowerCase()}`);
      if (selected?.id === finding.id) {
        target.classList.add("moderation-hit-active");
        target.scrollIntoView({ block: "center", behavior: "smooth" });
      }
      target.setAttribute("title", `${finding.severity}: ${finding.matchedText}`);
    }
  }, [previewRoot, data, selected]);

  if (report.isLoading) {
    return (
      <section className="review-moderation">
        <div className="review-moderation-loading">
          <LoaderCircle className="spin" size={16} />
          <span>Loading moderation…</span>
        </div>
      </section>
    );
  }

  if (report.isError || !data) {
    return (
      <section className="review-moderation">
        <div className="review-moderation-error">
          <ShieldAlert size={16} />
          <p>{errorMessage(report.error, "Moderation report is not available yet.")}</p>
        </div>
      </section>
    );
  }

  const running = data.blocksApproval;
  const failed = data.status === "FAILED" || data.status === "RETRY_REQUIRED";
  const clean = data.status === "COMPLETED" && data.findings.length === 0;

  return (
    <section className={`review-moderation ${compact ? "is-compact" : ""}`}>
      <div className="review-moderation-bar">
        <div className="review-moderation-identity">
          <div className="review-moderation-title-row">
            <h2>Content moderation</h2>
            <span className={`risk-badge risk-${badgeTone(data)}`}>{badgeLabel(data)}</span>
          </div>
          <p className="review-moderation-summary">
            {data.explanation || "AI review of submitted website text."}
          </p>
          <p className="review-moderation-meta-line">
            <span>{data.provider}</span>
            {data.model ? <span>{data.model}</span> : null}
            {data.completedAt ? <span>{new Date(data.completedAt).toLocaleString()}</span> : null}
          </p>
        </div>

        <div className="review-severity-row" aria-label="Moderation summary">
          <div className="review-severity-chip">
            <span>Status</span>
            <strong>{titleCase(data.status)}</strong>
          </div>
          <div className="review-severity-chip">
            <span>Findings</span>
            <strong>{data.findings.length}</strong>
          </div>
          <div className={`review-severity-chip tone-critical ${data.summary.critical ? "is-hot" : ""}`}>
            <span>Critical</span>
            <strong>{data.summary.critical}</strong>
          </div>
          <div className={`review-severity-chip tone-high ${data.summary.high ? "is-hot" : ""}`}>
            <span>High</span>
            <strong>{data.summary.high}</strong>
          </div>
          <div className={`review-severity-chip tone-medium ${data.summary.medium ? "is-hot" : ""}`}>
            <span>Medium</span>
            <strong>{data.summary.medium}</strong>
          </div>
          <div className={`review-severity-chip tone-low ${data.summary.low ? "is-hot" : ""}`}>
            <span>Low</span>
            <strong>{data.summary.low}</strong>
          </div>
        </div>
      </div>

      {data.errorDetails ? <p className="error-line">{data.errorDetails}</p> : null}

      <div className="review-moderation-actions-row">
        {running ? (
          <p className="moderation-wait">
            <LoaderCircle className="spin" size={14} />
            Review decisions stay locked until moderation finishes.
          </p>
        ) : null}
        {failed ? (
          <Button type="button" tone="secondary" disabled={retry.isPending} onClick={() => retry.mutate()}>
            <RotateCcw size={14} />
            Retry moderation
          </Button>
        ) : null}
      </div>

      <div className="review-findings-layout">
        <div className="review-findings-list">
          <div className="review-findings-heading">
            <h3>Findings</h3>
            <span>{data.findings.length}</span>
          </div>

          {data.findings.length === 0 ? (
            <div className={`review-findings-empty ${clean ? "is-clean" : ""}`}>
              {clean ? <CheckCircle2 size={18} /> : running ? <LoaderCircle className="spin" size={18} /> : <AlertTriangle size={18} />}
              <p>
                {clean
                  ? "No concerning text was detected."
                  : running
                    ? "Moderation is still running."
                    : "No findings for this submission."}
              </p>
            </div>
          ) : (
            data.findings.map((finding) => (
              <button
                key={finding.id}
                type="button"
                className={`review-finding ${selected?.id === finding.id ? "is-active" : ""} severity-${finding.severity.toLowerCase()}`}
                onClick={() => setSelectedId((current) => (current === finding.id ? null : finding.id))}
              >
                <div className="review-finding-top">
                  <span className={`severity-badge severity-${finding.severity.toLowerCase()}`}>{finding.severity}</span>
                  <span className="review-finding-category">{titleCase(finding.category)}</span>
                  <span className="review-finding-status">{titleCase(finding.reviewStatus)}</span>
                </div>
                <p className="review-finding-quote">“{finding.matchedText}”</p>
                <p className="review-finding-reason">{finding.reason}</p>
              </button>
            ))
          )}
        </div>

        {selected ? (
          <div className="review-finding-detail">
            <div className="review-finding-detail-head">
              <h3>Selected finding</h3>
              <span className={`severity-badge severity-${selected.severity.toLowerCase()}`}>{selected.severity}</span>
            </div>
            <dl className="review-finding-facts">
              <div><dt>Category</dt><dd>{titleCase(selected.category)}</dd></div>
              <div><dt>Confidence</dt><dd>{Math.round((selected.confidence ?? 0) * 100)}%</dd></div>
              <div><dt>Element</dt><dd>{[selected.elementType, selected.elementId].filter(Boolean).join(" · ") || "—"}</dd></div>
              <div><dt>Section</dt><dd>{[selected.pageId, selected.sectionId].filter(Boolean).join(" · ") || "—"}</dd></div>
            </dl>
            <p className="review-finding-quote">“{selected.matchedText}”</p>
            <p className="review-finding-reason">{selected.reason}</p>
            {selected.context ? <p className="review-finding-context">{selected.context}</p> : null}
            {selected.suggestion ? (
              <div className="review-suggestion">
                <div className="review-suggestion-head">
                  <span>Suggested rewrite</span>
                  <button
                    type="button"
                    className="btn-text"
                    onClick={() => void navigator.clipboard.writeText(selected.suggestion ?? "")}
                  >
                    <Copy size={14} />
                    Copy
                  </button>
                </div>
                <p>{selected.suggestion}</p>
              </div>
            ) : null}
            <Field label="Internal note">
              <textarea
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder={selected.administratorNote ?? "Optional note for reviewers"}
              />
            </Field>
            <div className="review-finding-actions">
              {FINDING_ACTIONS.map((action) => (
                <Button
                  key={action.value}
                  type="button"
                  tone="secondary"
                  disabled={updateStatus.isPending}
                  onClick={() => updateStatus.mutate({ findingId: selected.id, status: action.value })}
                >
                  {action.label}
                </Button>
              ))}
              <Button
                type="button"
                tone="text"
                disabled={addNote.isPending || note.trim().length < 2}
                onClick={() => addNote.mutate(selected.id)}
              >
                Save note
              </Button>
            </div>
            {updateStatus.isError || addNote.isError || retry.isError ? (
              <p className="error-line">
                {errorMessage(updateStatus.error ?? addNote.error ?? retry.error, "The moderation action failed.")}
              </p>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
}

function titleCase(value: string) {
  return value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function badgeTone(data: ModerationReport) {
  if (data.status !== "COMPLETED") return data.status.toLowerCase();
  return data.overallRisk.toLowerCase();
}

function badgeLabel(data: ModerationReport) {
  if (data.status !== "COMPLETED") return titleCase(data.status);
  return data.overallRisk;
}
