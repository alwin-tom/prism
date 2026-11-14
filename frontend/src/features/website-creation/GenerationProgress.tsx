import { useEffect, useMemo, useState } from "react";
import { Check, Info, Loader2, Sparkles } from "lucide-react";
import { Button } from "../../components/ui";
import type { EventSite } from "../../types";

type StageCode = "QUEUED" | "PLANNING" | "GENERATING" | "REPAIRING" | "VALIDATING" | "COMPLETED" | "FAILED" | "CANCELLED";

type StageDef = {
  code: StageCode;
  title: string;
  description: string;
  activeHint: string;
  activities: string[];
};

const STAGES: StageDef[] = [
  {
    code: "QUEUED",
    title: "Understanding your requirements",
    description: "Reading your answers, event details, and media so the draft matches what you shared.",
    activeHint: "Parsing answers…",
    activities: ["Collecting questionnaire answers", "Mapping people, dates, and locations", "Preparing generation context"],
  },
  {
    code: "PLANNING",
    title: "Choosing the art direction",
    description: "Selecting palette, typography, and layout tone for your category and preferences.",
    activeHint: "Selecting creative direction…",
    activities: ["Choosing colour palette", "Picking display typography", "Planning section rhythm"],
  },
  {
    code: "GENERATING",
    title: "Composing the page",
    description: "Writing sections, arranging media, and assembling the interactive draft.",
    activeHint: "Generating sections…",
    activities: ["Building the hero and story sections", "Wiring RSVP and countdown blocks", "Placing photos and venue details"],
  },
  {
    code: "REPAIRING",
    title: "Preparing your responsive layout",
    description: "Tightening spacing, typography scale, and mobile-first layout for guest devices.",
    activeHint: "Calibrating layout…",
    activities: ["Checking mobile breakpoints", "Balancing whitespace", "Refining visual hierarchy"],
  },
  {
    code: "VALIDATING",
    title: "Validating your website",
    description: "Running final checks so the draft opens cleanly before you review it.",
    activeHint: "Running checks…",
    activities: ["Checking page structure", "Verifying media links", "Preparing preview"],
  },
];

const TIPS = [
  {
    quote: "Adding a dietary preference option in RSVP helps hosts plan catering with less waste.",
    detail: "Guests answer once; you see counts when responses come in.",
  },
  {
    quote: "WhatsApp RSVP links get confirmations faster than paper cards or long email threads.",
    detail: "Share one link; guests reply without creating an account.",
  },
  {
    quote: "A clear countdown and venue map on the home page reduces day-of questions.",
    detail: "Keep date, time, and place above the fold for mobile guests.",
  },
];

const PROGRESS: Record<string, number> = {
  QUEUED: 12,
  PLANNING: 32,
  GENERATING: 58,
  REPAIRING: 78,
  VALIDATING: 92,
  COMPLETED: 100,
  FAILED: 0,
  CANCELLED: 0,
};

function stageIndex(status: string): number {
  const idx = STAGES.findIndex((s) => s.code === status);
  if (status === "COMPLETED") return STAGES.length;
  if (idx >= 0) return idx;
  return 0;
}

function answerText(answers: Record<string, unknown> | undefined, keys: string[]): string | null {
  if (!answers) return null;
  for (const key of keys) {
    const value = answers[key];
    if (typeof value === "string" && value.trim()) return value.trim();
    if (Array.isArray(value) && value.length) {
      const first = value[0];
      if (typeof first === "string" && first.trim()) return first.trim();
      if (first && typeof first === "object" && "name" in first && typeof (first as { name: unknown }).name === "string") {
        return (first as { name: string }).name.trim() || null;
      }
    }
  }
  return null;
}

function coupleLabel(answers: Record<string, unknown> | undefined, fallback: string): string {
  const couple = answerText(answers, ["couple_names", "coupleNames", "names"]);
  if (couple) return couple;
  const a = answerText(answers, ["bride_name", "partner_a_name", "person_a"]);
  const b = answerText(answers, ["groom_name", "partner_b_name", "person_b"]);
  if (a && b) return `${a} & ${b}`;
  if (a || b) return a || b || fallback;
  return fallback;
}

export function GenerationProgress({
  status: rawStatus,
  error,
  title,
  categoryName,
  answers,
  event,
  onCancel,
  onRetry,
}: {
  status: string;
  error?: string | null;
  title: string;
  categoryName: string | null;
  answers?: Record<string, unknown>;
  event?: EventSite;
  onCancel: () => void;
  onRetry: () => void;
}) {
  const [tipIndex, setTipIndex] = useState(0);
  const [activityTick, setActivityTick] = useState(0);
  const status = (rawStatus || "QUEUED") as StageCode;
  const currentIdx = stageIndex(status);
  const percent = PROGRESS[status] ?? 12;
  const failed = status === "FAILED" || status === "CANCELLED";
  const completedCount = failed ? 0 : Math.min(currentIdx, STAGES.length);
  const remaining = failed
    ? STAGES.length
    : Math.max(0, STAGES.length - completedCount - (status === "COMPLETED" ? 0 : 1));

  const displayName = useMemo(() => {
    const fromEvent = event?.eventName;
    if (fromEvent && fromEvent !== "Untitled website") return fromEvent;
    return coupleLabel(answers, title);
  }, [answers, event?.eventName, title]);

  const venue = answerText(answers, ["venue", "venue_name", "location", "ceremony_venue"]);
  const dateHint = answerText(answers, ["event_date", "wedding_date", "date"]);
  const styleHint = answerText(answers, ["style", "design_style", "theme", "vibe"]);
  const slug = event?.slug;

  useEffect(() => {
    if (failed || status === "COMPLETED") return;
    const id = window.setInterval(() => setTipIndex((i) => (i + 1) % TIPS.length), 8000);
    return () => window.clearInterval(id);
  }, [failed, status]);

  useEffect(() => {
    if (failed || status === "COMPLETED") return;
    const id = window.setInterval(() => setActivityTick((n) => n + 1), 2800);
    return () => window.clearInterval(id);
  }, [failed, status]);

  const tip = TIPS[tipIndex];
  const etaLabel = failed || status === "COMPLETED"
    ? status === "COMPLETED" ? "Ready" : "Stopped"
    : percent < 40 ? "~1 min" : percent < 75 ? "~40 sec" : "~20 sec";

  return (
    <div className="gen-progress">
      <section className="gen-hero">
        <div className="gen-hero-glow" aria-hidden />
        <div className="gen-hero-top">
          <div className="gen-hero-copy">
            <div className="gen-hero-title-row">
              <h2>{displayName}</h2>
              <span className="gen-badge">AI Generation</span>
            </div>
            <p>
              Crafting your {categoryName ? categoryName.toLowerCase() : "event website"}.
              We are compiling your details, choosing accents, and preparing the draft for review.
            </p>
          </div>
          <div className="gen-metric">
            <div className="gen-ring" aria-hidden>
              <svg viewBox="0 0 36 36">
                <path className="gen-ring-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                <path
                  className="gen-ring-fg"
                  strokeDasharray={`${percent}, 100`}
                  d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                />
              </svg>
              <span>{percent}%</span>
            </div>
            <div>
              <p className="gen-metric-label">Estimated remaining</p>
              <p className="gen-metric-value">
                {etaLabel}
                {!failed && status !== "COMPLETED" ? <span className="gen-pulse-dot" /> : null}
              </p>
            </div>
          </div>
        </div>
        <div className="gen-bar-block">
          <div className="gen-bar-meta">
            <span>
              {!failed && status !== "COMPLETED" ? <Loader2 className="gen-spin" size={14} aria-hidden /> : null}
              {failed
                ? status === "CANCELLED" ? "Generation cancelled" : "Generation paused"
                : status === "COMPLETED"
                  ? "All stages complete"
                  : `Stage ${Math.min(currentIdx + 1, 5)} of 5 in progress`}
            </span>
            <span>
              {failed
                ? "You can retry when ready"
                : `${completedCount} steps completed / ${remaining} remaining`}
            </span>
          </div>
          <div className="gen-bar-track">
            <div className="gen-bar-fill" style={{ width: `${percent}%` }} />
          </div>
        </div>
      </section>

      <div className="gen-grid">
        <section className="gen-pipeline" aria-label="Generation stages">
          <div className="gen-pipeline-head">
            <h3>Execution pipeline</h3>
            <span>Auto-proceed enabled</span>
          </div>

          {STAGES.map((stage, index) => {
            const done = status === "COMPLETED" || (!failed && index < currentIdx);
            const active = !failed && status !== "COMPLETED" && index === currentIdx;
            const queued = !done && !active;
            const activityFocus = active ? activityTick % stage.activities.length : -1;

            return (
              <article
                key={stage.code}
                className={`gen-stage${done ? " is-done" : ""}${active ? " is-active" : ""}${queued ? " is-queued" : ""}`}
              >
                <div className="gen-stage-inner">
                  <div className={`gen-stage-icon${active ? " is-live" : ""}`}>
                    {done ? <Check size={16} strokeWidth={2.5} /> : active ? <Loader2 className="gen-spin" size={16} /> : <span>{index + 1}</span>}
                    {active ? <span className="gen-stage-ping" aria-hidden /> : null}
                  </div>
                  <div className="gen-stage-body">
                    <div className="gen-stage-title-row">
                      <div className="gen-stage-title">
                        <h4>{stage.title}</h4>
                        {active ? <span className="gen-chip-active">Active</span> : null}
                      </div>
                      {done ? <span className="gen-chip-done">Completed</span> : null}
                      {active ? <span className="gen-chip-hint">{stage.activeHint}</span> : null}
                      {queued ? <span className="gen-chip-queued">Queued</span> : null}
                    </div>
                    <p>{stage.description}</p>
                    {done && index === 0 && (venue || styleHint || dateHint) ? (
                      <div className="gen-chips">
                        {venue ? <span className="gen-chip"><Check size={12} /> {venue}</span> : null}
                        {styleHint ? <span className="gen-chip"><Check size={12} /> {styleHint}</span> : null}
                        {dateHint ? <span className="gen-chip"><Check size={12} /> {dateHint}</span> : null}
                      </div>
                    ) : null}
                    {done && index === 1 ? (
                      <div className="gen-palette">
                        <span className="swatch" style={{ background: "#faf7f2" }} />
                        <span className="swatch" style={{ background: "#d4af37" }} />
                        <span className="swatch" style={{ background: "#0d9488" }} />
                        <span className="swatch" style={{ background: "#f87171" }} />
                        <em>Editorial · responsive type scale</em>
                      </div>
                    ) : null}
                    {active ? (
                      <ul className="gen-activity">
                        {stage.activities.map((item, i) => (
                          <li key={item} className={i < activityFocus ? "done" : i === activityFocus ? "live" : ""}>
                            {i < activityFocus ? <Check size={14} /> : i === activityFocus ? <span className="gen-live-dot" /> : <span className="gen-todo-dot" />}
                            {item}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </div>
                </div>
              </article>
            );
          })}

          <div className="gen-pipeline-actions">
            {failed ? (
              <>
                <Button type="button" tone="secondary" onClick={onCancel}>Back to review</Button>
                <Button type="button" onClick={onRetry}>Retry generation</Button>
              </>
            ) : (
              <>
                <Button type="button" tone="secondary" disabled={status === "COMPLETED"} onClick={onCancel}>
                  Cancel generation
                </Button>
                <span className="gen-autosave"><Check size={14} /> Progress saved</span>
              </>
            )}
          </div>
          {failed && error ? <p className="error-line" role="alert">{error}</p> : null}
          {failed && !error ? <p className="error-line" role="alert">The website could not be generated. Your answers are still saved.</p> : null}
        </section>

        <aside className="gen-side">
          <div className="gen-overview">
            <div className="gen-overview-head">
              <span>Website overview</span>
              <span className="gen-chip-hint">{status === "COMPLETED" ? "Ready" : failed ? "Paused" : "Readying assets"}</span>
            </div>
            <div className="gen-overview-body">
              <div>
                <span className="gen-kicker">Event title</span>
                <h4>{displayName}</h4>
                <p>
                  {[categoryName, venue, dateHint].filter(Boolean).join(" · ") || "Personalized draft in progress"}
                </p>
              </div>
              <div className="gen-overview-grid">
                <div>
                  <span className="gen-kicker">Deployment</span>
                  <strong>WebAux hosting</strong>
                  <span className="gen-ok"><span /> Preview after generation</span>
                </div>
                <div>
                  <span className="gen-kicker">Guest access</span>
                  <strong>WhatsApp RSVP</strong>
                  <span className="gen-ok"><span /> Share link ready later</span>
                </div>
              </div>
              <dl className="gen-overview-meta">
                <div><dt>Category</dt><dd>{categoryName || "Event website"}</dd></div>
                <div><dt>Responsive</dt><dd>Mobile first + desktop</dd></div>
                <div><dt>Address</dt><dd className="mono">{slug ? `/${slug}` : "Assigned on publish"}</dd></div>
              </dl>
            </div>
          </div>

          <div className="gen-tip">
            <div className="gen-tip-label">
              <Info size={16} />
              <span>Did you know?</span>
            </div>
            <p className="gen-tip-quote">{tip.quote}</p>
            <p className="gen-tip-detail">{tip.detail}</p>
            <div className="gen-tip-footer">
              <div className="gen-tip-dots" aria-hidden>
                {TIPS.map((_, i) => <span key={i} className={i === tipIndex ? "is-on" : ""} />)}
              </div>
              <span>Tip {tipIndex + 1} of {TIPS.length}</span>
            </div>
          </div>

          <div className="gen-away">
            <div className="gen-away-icon"><Sparkles size={16} /></div>
            <div>
              <h5>Need to step away?</h5>
              <p>You can leave this page. Your answers are saved — open this draft again anytime to check progress or review the result.</p>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
