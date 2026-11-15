import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Baby, Briefcase, Building2, Cake, Gem, Heart, Monitor, Sparkles, UserRound } from "lucide-react";
import { HostingRunway } from "../../components/hosting/HostingRunway";
import { DeleteWebsiteButton } from "../../components/DeleteWebsiteButton";
import { Button, Empty, Field, Pill } from "../../components/ui";
import { quoteFromDays } from "../../utils/money";
import { api, errorMessage } from "../../services/api";
import type { Envelope, EventSite } from "../../types";
import { isEmpty, visible } from "./conditions";
import { DraftSide, PALETTES, type Palette } from "./DraftSide";
import { GenerationProgress } from "./GenerationProgress";
import { QuestionField } from "./QuestionField";
import { GROUPS, type CreationSession, type GenerationJob, type Question, type WebsiteCategory } from "./types";

const ICONS = {
  wedding: Heart,
  birthday: Cake,
  anniversary: Gem,
  baby: Baby,
  corporate: Building2,
  conference: Monitor,
  business: Briefcase,
  portfolio: UserRound,
  custom: Sparkles,
} as const;

export function CreationPage() {
  const { sessionId } = useParams();
  if (!sessionId) return <Welcome />;
  return <Wizard sessionId={sessionId} />;
}

function Welcome() {
  const navigate = useNavigate();
  const sessions = useQuery({
    queryKey: ["creation-sessions"],
    queryFn: async () => (await api.get<Envelope<CreationSession[]>>("/website-creation-sessions")).data.data,
  });
  const categories = useQuery({
    queryKey: ["website-categories"],
    queryFn: async () => (await api.get<Envelope<WebsiteCategory[]>>("/website-categories")).data.data,
  });
  const [picking, setPicking] = useState(false);
  const start = useMutation({
    mutationFn: async (code: string) => (await api.post<Envelope<CreationSession>>("/website-creation-sessions", { categoryCode: code })).data.data,
    onSuccess: (session) => navigate(`/app/create/${session.id}`),
  });
  const drafts = (sessions.data ?? []).filter((session) => session.status !== "SUBMITTED");
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Let's create your website</h1>
          <p>Tell us a little about your event or website. We'll use your answers to create a personalised website that you can customise.</p>
        </div>
        <Button type="button" onClick={() => setPicking(true)}>Start creating</Button>
      </div>
      {picking ? (
        <div className="cards">
          {(categories.data ?? []).map((category) => {
            const Icon = ICONS[category.icon as keyof typeof ICONS] ?? Sparkles;
            return (
              <button key={category.code} type="button" className="choice" onClick={() => start.mutate(category.code)} disabled={start.isPending}>
                <Icon size={18} />
                <strong>{category.name}</strong>
                <p>{category.description}</p>
              </button>
            );
          })}
        </div>
      ) : null}
      {start.isError ? <p className="error-line">{errorMessage(start.error, "The website could not be started.")}</p> : null}
      <section className="list">
        {drafts.length === 0 ? <Empty>No website drafts yet.</Empty> : drafts.map((session) => (
          <div key={session.id} className="site-row-line">
            <Link to={`/app/create/${session.id}`} className="site-row">
              <div>
                <strong>{session.categoryName ?? "Website"}</strong>
                <p>{session.status.replaceAll("_", " ").toLowerCase()}</p>
              </div>
            </Link>
            <DeleteWebsiteButton
              id={session.id}
              name={session.categoryName ?? "Website"}
              path={`/website-creation-sessions/${session.id}`}
            />
          </div>
        ))}
      </section>
    </>
  );
}

function Wizard({ sessionId }: { sessionId: string }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const session = useQuery({
    queryKey: ["creation-session", sessionId],
    queryFn: async () => (await api.get<Envelope<CreationSession>>(`/website-creation-sessions/${sessionId}`)).data.data,
  });
  const [answers, setAnswers] = useState<Record<string, unknown>>({});
  const [ready, setReady] = useState(false);
  const [index, setIndex] = useState(0);
  const [step, setStep] = useState("questions");
  const [jobId, setJobId] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [dirty, setDirty] = useState(false);
  const [palette, setPalette] = useState<Palette>(PALETTES[0]);
  const [applyingPalette, setApplyingPalette] = useState(false);

  const event = useQuery({
    queryKey: ["event", session.data?.eventId],
    enabled: Boolean(session.data?.eventId),
    queryFn: async () => (await api.get<Envelope<EventSite>>(`/events/${session.data?.eventId}`)).data.data,
  });

  useEffect(() => {
    if (!session.data || ready) return;
    const nextAnswers = session.data.answers ?? {};
    setAnswers(nextAnswers);
    setPalette(paletteFromAnswers(nextAnswers) ?? PALETTES[0]);
    setStep(session.data.currentStep || "questions");
    setJobId(session.data.latestJobId ?? null);
    setReady(true);
  }, [session.data, ready]);

  useEffect(() => {
    if (!ready || !event.data?.configuration?.theme) return;
    setPalette((current) => {
      const fromAnswers = paletteFromAnswers(answers);
      if (fromAnswers) return fromAnswers;
      return matchPalette(event.data!.configuration.theme) ?? current;
    });
  }, [answers, event.data, ready]);

  const questions = session.data?.questions ?? [];
  const screen = step === "editor" ? "preview" : step;
  const shown = useMemo(() => questions.filter((question) => visible(question.when, answers)), [questions, answers]);
  const current = shown[Math.min(index, Math.max(shown.length - 1, 0))];
  useEffect(() => {
    if (!ready || !dirty) return;
    const timer = window.setTimeout(() => {
      void api.put(`/website-creation-sessions/${sessionId}/answers`, { answers }).catch(() => setMessage("Answers are still on this page. Saving will retry."));
    }, 700);
    return () => window.clearTimeout(timer);
  }, [answers, dirty, ready, sessionId]);

  const job = useQuery({
    queryKey: ["generation-job", jobId],
    enabled: Boolean(jobId) && (step === "generating" || step === "editor"),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && ["COMPLETED", "FAILED", "CANCELLED"].includes(status) ? false : 1500;
    },
    queryFn: async () => (await api.get<Envelope<GenerationJob>>(`/website-generation-jobs/${jobId}`)).data.data,
  });

  useEffect(() => {
    if (job.data?.status === "COMPLETED") {
      setStep("preview");
      void queryClient.invalidateQueries({ queryKey: ["creation-session", sessionId] });
      if (session.data?.eventId) void queryClient.invalidateQueries({ queryKey: ["event", session.data.eventId] });
    }
  }, [job.data?.status, queryClient, session.data?.eventId, sessionId]);

  function rememberPalette(next: Palette) {
    setPalette(next);
    setAnswers((currentAnswers) => ({
      ...currentAnswers,
      colourPalette: {
        name: next.name,
        background: next.background,
        ink: next.ink,
        accent: next.accent,
      },
    }));
    setDirty(true);
    setMessage("");
  }

  async function applyPalette() {
    if (!event.data) return;
    setApplyingPalette(true);
    try {
      rememberPalette(palette);
      const configuration = structuredClone(event.data.configuration);
      configuration.theme.background = palette.background;
      configuration.theme.ink = palette.ink;
      configuration.theme.accent = palette.accent;
      const saved = (await api.put<Envelope<EventSite>>(`/events/${event.data.id}/configuration`, configuration)).data.data;
      queryClient.setQueryData(["event", event.data.id], saved);
      setMessage("");
    } catch (error) {
      setMessage(errorMessage(error, "The palette was not saved."));
    } finally {
      setApplyingPalette(false);
    }
  }

  function change(key: string, value: unknown) {
    setAnswers((currentAnswers) => ({ ...currentAnswers, [key]: value }));
    setDirty(true);
    setMessage("");
  }

  function next() {
    if (current?.required && isEmpty(answers[current.key])) {
      setMessage("This one is needed before you continue. You can skip the others.");
      return;
    }
    if (index < shown.length - 1) setIndex(index + 1);
    else {
      setStep("details");
      void api.put(`/website-creation-sessions/${sessionId}/progress`, { step: "details" });
    }
  }

  async function generate(confirmReplace: boolean) {
    setMessage("");
    try {
      const withPalette = {
        ...answers,
        colourPalette: {
          name: palette.name,
          background: palette.background,
          ink: palette.ink,
          accent: palette.accent,
        },
      };
      setAnswers(withPalette);
      await api.put(`/website-creation-sessions/${sessionId}/answers`, { answers: withPalette });
      setDirty(false);
      const created = (await api.post<Envelope<GenerationJob>>(`/website-creation-sessions/${sessionId}/generate`, { confirmReplace })).data.data;
      setJobId(created.id);
      setStep("generating");
    } catch (error) {
      const text = errorMessage(error, "Generation could not start.");
      if (text.toLowerCase().includes("confirm")) {
        if (window.confirm("A draft page already exists. Replace that draft with a new one?")) {
          await generate(true);
        }
        return;
      }
      setMessage(text);
    }
  }

  if (session.isLoading || !ready) return <p className="studio-note">Loading your answers.</p>;
  if (session.isError || !session.data) return <p className="error-line">{errorMessage(session.error, "That website draft was not found.")}</p>;

  return (
    <>
      {screen === "generating" ? null : (
        <div className="sheet-title">
          <div>
            <h1>{session.data.categoryName ?? "Website"}</h1>
            <p>{screenCopy(screen)}</p>
          </div>
        </div>
      )}
      <div className={screen === "generating" ? "gen-shell" : "wizard-stage"}>
      <div>
      {screen === "questions" && current ? (
        <section className="wizard-question">
          <p className="studio-hint">Question {Math.min(index + 1, shown.length)} of {shown.length}</p>
          <div className="wizard-progress" aria-hidden="true"><span style={{ width: `${shown.length === 0 ? 0 : ((index + 1) / shown.length) * 100}%` }} /></div>
          <h2>{current.label}</h2>
          {current.help ? <p className="studio-hint">{current.help}</p> : null}
          <QuestionField question={current} value={answers[current.key]} onChange={(value) => change(current.key, value)} eventId={session.data.eventId} />
          {!current.required ? <p className="studio-hint">Optional</p> : null}
          <div className="wizard-actions">
            <Button type="button" tone="secondary" onClick={() => setIndex(Math.max(0, index - 1))} disabled={index === 0}>Previous</Button>
            {!current.required ? <Button type="button" tone="text" onClick={() => next()}>Skip</Button> : null}
            <Button type="button" onClick={next}>{index === shown.length - 1 ? "Site details" : "Next"}</Button>
          </div>
        </section>
      ) : null}
      {screen === "details" ? (
        event.isLoading ? <p className="studio-note">Loading the site.</p>
          : event.data ? (
            <SiteDetails
              event={event.data}
              mode="continue"
              onBack={() => setStep("questions")}
              onSaved={() => {
                setStep("review");
                void api.put(`/website-creation-sessions/${sessionId}/progress`, { step: "review" });
              }}
            />
          ) : <p className="error-line">{errorMessage(event.error, "The site details could not be loaded.")}</p>
      ) : null}
      {screen === "review" ? (
        <Review
          site={event.data}
          answers={answers}
          questions={shown}
          onEdit={(key) => { setIndex(Math.max(0, shown.findIndex((question) => question.key === key))); setStep("questions"); }}
          onDetails={() => setStep("details")}
          onGenerate={() => void generate(false)}
        />
      ) : null}
      {screen === "generating" ? (
        <GenerationProgress
          status={job.data?.status ?? session.data.latestJobStatus ?? "QUEUED"}
          error={job.data?.errorMessage}
          title={event.data?.eventName && event.data.eventName !== "Untitled website" ? event.data.eventName : (session.data.categoryName ?? "Website")}
          categoryName={session.data.categoryName}
          answers={answers}
          event={event.data}
          imageProgress={job.data?.imageProgress}
          onCancel={() => { if (jobId) void api.post(`/website-generation-jobs/${jobId}/cancel`).then(() => setStep("review")); }}
          onRetry={() => { if (jobId) void api.post<Envelope<GenerationJob>>(`/website-generation-jobs/${jobId}/retry`).then((response) => { setJobId(response.data.data.id); }); }}
        />
      ) : null}
      {screen === "preview" ? (
        <Preview
          event={event.data}
          onRegenerate={() => void generate(true)}
          onAnswers={() => setStep("review")}
          onEdit={async () => {
            const opened = (await api.post<Envelope<{ path: string }>>(`/website-creation-sessions/${sessionId}/open-editor`)).data.data;
            navigate(opened.path);
          }}
        />
      ) : null}
      {message ? <p className="error-line">{message}</p> : null}
      </div>
      {screen === "generating" ? null : (
        <DraftSide
          questions={shown}
          answers={answers}
          palette={palette}
          onPalette={rememberPalette}
          event={event.data}
          applying={applyingPalette}
          onApply={() => void applyPalette()}
          onFill={(patch) => {
            setAnswers((currentAnswers) => ({ ...currentAnswers, ...patch }));
            setDirty(true);
            setMessage("");
          }}
        />
      )}
      </div>
    </>
  );
}

function paletteFromAnswers(answers: Record<string, unknown>): Palette | null {
  const raw = answers.colourPalette ?? answers.colorPalette;
  if (!raw || typeof raw !== "object") return null;
  const palette = raw as { name?: string; background?: string; ink?: string; accent?: string };
  return matchPalette({
    name: palette.name,
    background: palette.background ?? "",
    ink: palette.ink ?? "",
    accent: palette.accent ?? "",
  });
}

function matchPalette(theme: { name?: string; background?: string; ink?: string; accent?: string }): Palette | null {
  const byName = theme.name ? PALETTES.find((item) => item.name === theme.name) : undefined;
  if (byName) return byName;
  const background = (theme.background ?? "").toLowerCase();
  const ink = (theme.ink ?? "").toLowerCase();
  const accent = (theme.accent ?? "").toLowerCase();
  return PALETTES.find((item) =>
    item.background.toLowerCase() === background
    && item.ink.toLowerCase() === ink
    && item.accent.toLowerCase() === accent
  ) ?? null;
}

function Review({ site, answers, questions, onEdit, onDetails, onGenerate }: {
  site?: EventSite;
  answers: Record<string, unknown>;
  questions: Question[];
  onEdit: (key: string) => void;
  onDetails: () => void;
  onGenerate: () => void;
}) {
  const missing = questions.filter((question) => !question.required && isEmpty(answers[question.key]));
  const quote = quoteFromDays(site?.requestedDays || 7);
  const named = Boolean(site && site.eventName.trim() !== "" && site.eventName !== "Untitled website");
  return (
    <section className="wizard-review">
      <div>
        <h2>Hosting and review</h2>
        <div className="review-row">
          <div>
            <span>Event name</span>
            <strong>{named && site ? site.eventName : "Not named yet"}</strong>
          </div>
        </div>
        <div className="review-row">
          <div>
            <span>Public address</span>
            <strong>/{site?.slug || "not-set"}</strong>
          </div>
        </div>
        <div className="review-row">
          <div>
            <span>Hosting</span>
            <strong>{quote.days} days, {quote.formatted}</strong>
          </div>
          <Button type="button" tone="text" onClick={onDetails}>Edit</Button>
        </div>
        <p className="studio-hint">A reviewer checks the draft before it goes live. After approval you pay {quote.formatted}, and the site is published at /{site?.slug || "your-address"}.</p>
      </div>
      {GROUPS.map((group) => {
        const rows = questions.filter((question) => (question.group ?? "Additional requirements") === group);
        if (rows.length === 0) return null;
        return (
          <div key={group}>
            <h2>{group}</h2>
            {rows.map((question) => (
              <div key={question.key} className="review-row">
                <div>
                  <span>{question.label}</span>
                  <strong>{display(answers[question.key])}</strong>
                </div>
                <Button type="button" tone="text" onClick={() => onEdit(question.key)}>Edit</Button>
              </div>
            ))}
          </div>
        );
      })}
      {missing.length > 0 ? <p className="studio-hint">Still open, and not required: {missing.map((question) => question.label).join(", ")}.</p> : null}
      {!site ? <Button type="button" disabled>Generate website</Button> : named ? <Button type="button" onClick={onGenerate}>Generate website</Button> : <Button type="button" onClick={onDetails}>Add the event name</Button>}
    </section>
  );
}

function Preview({ event, onRegenerate, onAnswers, onEdit }: {
  event?: EventSite;
  onRegenerate: () => void;
  onAnswers: () => void;
  onEdit: () => void;
}) {
  return (
    <section className="creation-preview">
      {event ? <SiteDetails event={event} mode="preview" onSaved={() => undefined} /> : <p className="studio-note">Loading the hosting details.</p>}
      <div className="wizard-actions">
        <Button type="button" tone="secondary" onClick={onRegenerate}>Regenerate</Button>
        <Button type="button" tone="secondary" onClick={onAnswers}>Edit answers</Button>
        <Button type="button" onClick={onEdit}>Visual editor</Button>
      </div>
    </section>
  );
}

function SiteDetails({ event, mode, onBack, onSaved }: {
  event: EventSite;
  mode: "continue" | "preview";
  onBack?: () => void;
  onSaved: (event: EventSite) => void;
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(event.eventName === "Untitled website" ? "" : event.eventName);
  const [slug, setSlug] = useState(event.slug);
  const [days, setDays] = useState(event.requestedDays || 7);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const hostedDays = Number.isFinite(days) ? Math.min(365, Math.max(1, Math.round(days))) : 1;
  const quote = quoteFromDays(hostedDays);
  const locked = ["SUBMITTED", "IN_REVIEW", "APPROVED", "PUBLISHED", "SUSPENDED", "EXPIRED"].includes(event.status);
  const availability = useQuery({
    queryKey: ["slug-available", event.id, slug],
    enabled: slug.trim().length >= 3 && !locked,
    queryFn: async () => (await api.get<Envelope<{ slug: string; available: boolean }>>("/events/slug-available", { params: { slug, eventId: event.id } })).data.data,
  });
  async function save() {
    const trimmed = name.trim();
    if (trimmed.length < 2) {
      setError("Add the event name.");
      return null;
    }
    if (!Number.isFinite(days) || days < 1 || days > 365) {
      setError("Hosting can run from 1 to 365 days.");
      return null;
    }
    if (availability.data && !availability.data.available) {
      setError("That address is already taken.");
      return null;
    }
    setBusy(true);
    setError("");
    try {
      const saved = (await api.put<Envelope<EventSite>>(`/events/${event.id}`, {
        eventName: trimmed,
        slug: slug.trim(),
        requestedDays: hostedDays,
      })).data.data;
      const configuration = structuredClone(saved.configuration);
      configuration.event.name = trimmed;
      const named = (await api.put<Envelope<EventSite>>(`/events/${event.id}/configuration`, configuration)).data.data;
      queryClient.setQueryData(["event", event.id], named);
      void queryClient.invalidateQueries({ queryKey: ["events"] });
      if (mode === "preview") setNotice("Saved.");
      onSaved(named);
      return named;
    } catch (failure) {
      setError(errorMessage(failure, "The site details were not saved."));
      return null;
    } finally {
      setBusy(false);
    }
  }
  async function submit() {
    const saved = await save();
    if (!saved) return;
    setBusy(true);
    try {
      const submitted = (await api.post<Envelope<EventSite>>(`/events/${event.id}/submit`)).data.data;
      queryClient.setQueryData(["event", event.id], submitted);
      void queryClient.invalidateQueries({ queryKey: ["events"] });
      setNotice("Submitted for review. Payment comes after approval.");
    } catch (failure) {
      setError(errorMessage(failure, "The site was not submitted."));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="wizard-question">
      <h2>Hosting and review</h2>
      <p className="studio-hint">{reviewStatus(event)}</p>
      <Field label="Event name">
        <input value={name} onChange={(item) => setName(item.target.value)} maxLength={160} disabled={locked} required />
      </Field>
      <Field label="Public address" hint="Letters, numbers and hyphens. This becomes the URL.">
        <input value={slug} onChange={(item) => setSlug(item.target.value)} disabled={locked} required />
      </Field>
      {availability.data ? <p className={availability.data.available ? "studio-hint" : "error-line"}>{availability.data.available ? `Available as /${availability.data.slug}` : "That address is already taken."}</p> : null}
      <Field label="Hosting days">
        <input type="number" min={1} max={365} value={days} disabled={locked} onChange={(item) => setDays(Number(item.target.value))} />
      </Field>
      <HostingRunway days={quote.days} label={`${quote.days} hosting days, ${quote.formatted}`} />
      <p className="num">{quote.formatted} · ₹500 a day</p>
      <div className="review-row">
        <div>
          <span>Review</span>
          <strong><Pill status={event.status} /></strong>
        </div>
      </div>
      <p className="studio-hint">Submit the draft for review. A reviewer approves it or asks for a change. You pay after approval, then the site goes live at /{availability.data?.slug || slug || "your-address"}.</p>
      {event.latestReview?.comments ? <p>From the reviewer: {event.latestReview.comments}</p> : null}
      {event.status === "APPROVED" ? <p><Link to={`/app/events/${event.id}/pay`}>Pay {quote.formatted} and publish</Link></p> : null}
      {event.status === "PUBLISHED" ? <p><a href={`/${event.slug}`}>Open the public site</a></p> : null}
      {error ? <p className="error-line">{error}</p> : null}
      {notice ? <p className="studio-hint">{notice}</p> : null}
      <div className="wizard-actions">
        {onBack ? <Button type="button" tone="secondary" onClick={onBack}>Previous</Button> : null}
        {locked ? null : mode === "continue" ? <Button type="button" disabled={busy} onClick={() => void save()}>Save and review answers</Button> : <Button type="button" disabled={busy} onClick={() => void submit()}>Submit for review</Button>}
        {!locked && mode === "preview" ? <Button type="button" tone="secondary" disabled={busy} onClick={() => void save()}>Save address and days</Button> : null}
      </div>
    </section>
  );
}

function screenCopy(screen: string) {
  if (screen === "details") return "Name the event, choose the public address, and see what the hosting days cost.";
  if (screen === "review") return "Review what you told us, then generate the page.";
  if (screen === "preview") return "Set the address and hosting, then open the visual editor, regenerate, or submit for review.";
  return "Answer what you know. Optional questions can be skipped.";
}

function reviewStatus(site: EventSite) {
  if (site.status === "CHANGES_REQUESTED") return "The reviewer asked for a change. Edit the draft and submit it again.";
  if (site.status === "APPROVED") return "Approved. Pay for the hosting days to publish it.";
  if (site.status === "PUBLISHED") return `Live at /${site.slug}. ${site.hosting.daysRemaining} hosting days left.`;
  if (site.status === "SUBMITTED" || site.status === "IN_REVIEW") return "With a reviewer. The draft is locked until they answer.";
  if (site.status === "EXPIRED") return "Hosting has ended. Extend it to publish the site again.";
  return "A draft. Submit it when the page and the address are ready.";
}

function display(value: unknown) {
  if (isEmpty(value)) return "Not answered";
  if (Array.isArray(value)) {
    if (value.length === 0) return "Not answered";
    if (typeof value[0] === "object") return `${value.length} added`;
    return value.join(", ");
  }
  return String(value);
}
