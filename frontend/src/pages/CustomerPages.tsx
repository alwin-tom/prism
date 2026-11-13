import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, Heading1, Image as ImageIcon, Link2, MessageSquare, Redo2, Square, SquareMousePointer, Type, Undo2 } from "lucide-react";
import { DeleteWebsiteButton } from "../components/DeleteWebsiteButton";
import { Button, Empty, Field, Pill } from "../components/ui";
import { InviteCard } from "../components/invite/InviteCard";
import { HostingRunway } from "../components/hosting/HostingRunway";
import { PublicSite, type SiteEdit } from "../components/templates/PublicSite";
import { createElement, ensureCanvas, growCanvas, orderCanvas, redoHistory, rememberHistory, sectionsToCanvas, undoHistory } from "../components/templates/canvasModel";
import { settlePayment } from "../services/checkout";
import { api, errorMessage } from "../services/api";
import type { CanvasElement, Category, Envelope, EventSite, Quote, RsvpSummary, TemplateSummary } from "../types";
import { quoteFromDays } from "../utils/money";

export function DashboardPage() {
  const events = useQuery({
    queryKey: ["events"],
    queryFn: async () => (await api.get<Envelope<EventSite[]>>("/events")).data.data,
  });
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<"all" | "live" | "draft">("all");
  const [selected, setSelected] = useState<string[]>([]);
  const rows = events.data ?? [];
  const visible = rows.filter((event) => {
    const published = event.status === "PUBLISHED";
    if (filter === "live" && !published) return false;
    if (filter === "draft" && published) return false;
    const needle = query.trim().toLowerCase();
    if (!needle) return true;
    return event.eventName.toLowerCase().includes(needle) || event.slug.toLowerCase().includes(needle);
  });
  const live = rows.filter((event) => event.status === "PUBLISHED").length;
  const review = rows.filter((event) => event.status === "SUBMITTED" || event.status === "IN_REVIEW" || event.status === "CHANGES_REQUESTED").length;
  const payable = rows.filter((event) => event.status === "APPROVED").length;
  const features = useQuery({
    queryKey: ["features"],
    queryFn: async () => (await api.get<Envelope<{ aiWebsiteGenerationEnabled: boolean }>>("/config/features")).data.data,
  });
  const finding = rows.find((event) => event.status === "CHANGES_REQUESTED")
    ?? rows.find((event) => event.status === "APPROVED")
    ?? rows.find((event) => event.hosting.daysRemaining > 0 && event.hosting.daysRemaining <= 3);
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Event sites</h1>
          <p>{finding ? nextStep(finding) : rows.length === 0 ? "No site is waiting on you." : "Nothing needs a decision today."}</p>
        </div>
        {features.data?.aiWebsiteGenerationEnabled ? <Button asChild><Link to="/app/create">Create a website</Link></Button> : <Button asChild><Link to="/app/new">New event site</Link></Button>}
      </div>
      <div className="work">
        {events.isLoading ? <div className="skeleton" style={{ height: 72 }} /> : (
          <div className="kpis">
            <div className="kpi"><span>Live</span><strong className="num">{live}</strong><em>Published and inside hosting</em></div>
            <div className="kpi"><span>With a reviewer</span><strong className="num">{review}</strong><em>Submitted or sent back</em></div>
            <div className="kpi"><span>Awaiting payment</span><strong className="num">{payable}</strong><em>Approved, not yet hosted</em></div>
          </div>
        )}
        {events.isError ? <p className="error-line">{errorMessage(events.error, "The sites could not be loaded.")}</p> : null}
        {rows.length > 0 ? (
          <div className="site-tools">
            <input type="search" value={query} placeholder="Search name or address" aria-label="Search event sites" onChange={(item) => setQuery(item.target.value)} />
            {(["all", "live", "draft"] as const).map((item) => (
              <button key={item} type="button" className="filter-chip" aria-pressed={filter === item} onClick={() => setFilter(item)}>{item === "all" ? "All" : item === "live" ? "Live" : "Drafts"}</button>
            ))}
            <label className="site-select-all">
              <input
                type="checkbox"
                aria-label="Select the sites in this list"
                checked={visible.length > 0 && visible.every((event) => selected.includes(event.id))}
                onChange={(item) => setSelected(item.target.checked ? visible.map((event) => event.id) : [])}
              />
              Select list
            </label>
            <Button type="button" tone="secondary" disabled={visible.length === 0} onClick={() => exportSites(visible.filter((event) => selected.includes(event.id)).length > 0 ? visible.filter((event) => selected.includes(event.id)) : visible)}>Export CSV</Button>
          </div>
        ) : null}
        <section className="list">
          {events.isLoading ? null : rows.length === 0 ? (
            <Empty>When you start a site it will sit here, with its hosting days beside the name. <Link to="/app/new">New event site</Link></Empty>
          ) : visible.length === 0 ? (
            <Empty>No site matches this search.</Empty>
          ) : visible.map((event) => (
            <div key={event.id} className="site-row-line">
              <label className="site-check">
                <input
                  type="checkbox"
                  aria-label={`Select ${event.eventName}`}
                  checked={selected.includes(event.id)}
                  onChange={(item) => setSelected((current) => item.target.checked ? [...current, event.id] : current.filter((id) => id !== event.id))}
                />
              </label>
              <Link to={`/app/events/${event.id}`} className="site-row">
                <div>
                  <strong>{event.eventName}</strong>
                  <div className="id">/{event.slug}</div>
                </div>
                <Pill status={event.status} />
                <HostingRunway days={event.requestedDays} filled={event.hosting.daysRemaining || event.requestedDays} label={`${event.hosting.daysRemaining} hosting days left`} />
                <span className="num">{event.hosting.daysRemaining} days left</span>
              </Link>
              <DeleteWebsiteButton id={event.id} name={event.eventName} />
            </div>
          ))}
        </section>
      </div>
    </>
  );
}

function exportSites(sites: EventSite[]) {
  const header = ["Name", "Address", "Status", "Requested days", "Days left", "Public URL"];
  const lines = [header, ...sites.map((event) => [
    event.eventName,
    event.slug,
    event.status,
    String(event.requestedDays ?? ""),
    String(event.hosting.daysRemaining ?? ""),
    event.publicUrl || "",
  ])];
  const csv = lines.map((line) => line.map((cell) => `"${cell.replaceAll("\"", "\"\"")}"`).join(",")).join("\n");
  const blob = new Blob([csv], { type: "text/csv" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "event-sites.csv";
  link.click();
  URL.revokeObjectURL(url);
}

function nextStep(event: EventSite) {
  if (event.status === "CHANGES_REQUESTED") return `${event.eventName} was sent back. Open it and answer the note.`;
  if (event.status === "APPROVED") return `${event.eventName} is approved. Payment publishes it.`;
  if (event.hosting.daysRemaining > 0 && event.hosting.daysRemaining <= 3) return `${event.eventName} has ${event.hosting.daysRemaining} hosting days left.`;
  return `${event.eventName} is the one to open.`;
}

export function WizardPage() {
  const [step, setStep] = useState(0);
  const [categoryId, setCategoryId] = useState<string>();
  const [templateId, setTemplateId] = useState<string>();
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [days, setDays] = useState(7);
  const navigate = useNavigate();
  const categories = useQuery({ queryKey: ["categories"], queryFn: async () => (await api.get<Envelope<Category[]>>("/categories")).data.data });
  const templates = useQuery({
    queryKey: ["templates", categoryId],
    enabled: Boolean(categoryId),
    queryFn: async () => (await api.get<Envelope<TemplateSummary[]>>(`/categories/${categoryId}/templates`)).data.data,
  });
  const create = useMutation({
    mutationFn: async () => (await api.post<Envelope<EventSite>>("/events", {
      categoryId, templateId, eventName: name, slug, requestedDays: days,
    })).data.data,
    onSuccess: (event) => navigate(`/app/events/${event.id}/edit?step=4`),
  });
  const quote = quoteFromDays(days);
  return (
    <>
      <div className="sheet-title"><div><h1>New event site</h1><p>Eight short steps. The site is saved once the name and address are in.</p></div></div>
      <div className="steps" aria-hidden="true">
        {["Category", "Template", "The event", "Look", "Pictures", "Replies", "Address", "Submit"].map((label, index) => (
          <span key={label} className={index === step ? "on" : undefined}>{label}</span>
        ))}
      </div>
      {step === 0 ? (
        <div className="cards">
          {(categories.data ?? []).map((category) => (
            <button key={category.id} className="choice" type="button" aria-pressed={categoryId === category.id} onClick={() => { setCategoryId(category.id); setStep(1); }}>
              <strong>{category.name}</strong>
              <p>{category.description}</p>
            </button>
          ))}
        </div>
      ) : null}
      {step === 1 ? (
        <div className="cards">
          {(templates.data ?? []).map((template) => (
            <button key={template.id} className="choice" type="button" aria-pressed={templateId === template.id} onClick={() => { setTemplateId(template.id); setStep(2); }}>
              {template.thumbnailUrl ? <img src={template.thumbnailUrl} alt="" /> : <div className="thumb" />}
              <strong>{template.name}</strong>
              <p>{template.description}</p>
            </button>
          ))}
        </div>
      ) : null}
      {step === 2 ? (
        <form className="stack" style={{ maxWidth: 520 }} onSubmit={(event) => { event.preventDefault(); create.mutate(); }}>
          <Field label="Event name"><input value={name} onChange={(event) => setName(event.target.value)} required /></Field>
          <Field label="Public address" hint="Letters, numbers and hyphens. This becomes the URL.">
            <input value={slug} onChange={(event) => setSlug(event.target.value)} required />
          </Field>
          <Field label="Hosting days">
            <input type="number" min={1} max={365} value={days} onChange={(event) => setDays(Number(event.target.value))} />
          </Field>
          <HostingRunway days={days} label={`${quote.days} hosting days, ${quote.formatted}`} />
          <p className="num">{quote.formatted} · ₹500 a day</p>
          {create.isError ? <p className="error-line">{errorMessage(create.error, "The site was not created.")}</p> : null}
          <Button type="submit" disabled={create.isPending}>Save and continue</Button>
        </form>
      ) : null}
    </>
  );
}

export function EventPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const event = useQuery({ queryKey: ["event", id], queryFn: async () => (await api.get<Envelope<EventSite>>(`/events/${id}`)).data.data });
  const site = event.data;
  if (event.isLoading) return <div className="skeleton" style={{ height: 28, width: 180 }} />;
  if (!site) return <p className="error-line">{errorMessage(event.error, "That site was not found.")}</p>;
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>{site.eventName}</h1>
          <p>{statusLine(site)}</p>
        </div>
        <div className="title-actions">
          {site.status === "DRAFT" || site.status === "CHANGES_REQUESTED" || site.status === "PUBLISHED" ? <Button asChild tone="secondary"><Link to={`/app/events/${site.id}/edit`}>Edit</Link></Button> : null}
          {site.status === "APPROVED" ? <Button asChild><Link to={`/app/events/${site.id}/pay`}>Pay and publish</Link></Button> : null}
          {site.status === "PUBLISHED" || site.status === "EXPIRED" ? <Button asChild><Link to={`/app/events/${site.id}/extend`}>Extend hosting</Link></Button> : null}
          <DeleteWebsiteButton id={site.id} name={site.eventName} onDeleted={() => navigate("/app")} />
        </div>
      </div>
      <div className="work">
        <div className="kpis">
          <div className="kpi"><span>Status</span><strong style={{ fontSize: 16 }}><Pill status={site.status} /></strong></div>
          <div className="kpi"><span>Hosting days left</span><strong className="num">{site.hosting.daysRemaining}</strong></div>
          <div className="kpi"><span>Address</span><strong className="id" style={{ fontSize: 16 }}>/{site.slug}</strong></div>
        </div>
        {site.latestReview?.comments ? <section className="panel" style={{ padding: 16 }}><h2>From the reviewer</h2><p style={{ marginTop: 8 }}>{site.latestReview.comments}</p></section> : null}
        <p><Link to={`/app/events/${site.id}/rsvps`}>Replies</Link>{site.status === "PUBLISHED" ? <> · <a href={`/${site.slug}`}>Open the public site</a></> : null}</p>
        <InviteCard name={site.eventName} slug={site.slug} when={site.configuration.event.startAt} venue={site.configuration.event.venueName} />
        <div className="preview-frame"><PublicSite preview mediaEventId={site.id} event={{ slug: site.slug, eventName: site.eventName, availability: "PUBLISHED", templateCode: site.templateCode, configuration: site.configuration, editorDocument: site.editorDocument }} /></div>
      </div>
    </>
  );
}

export function EditorPage() {
  const { id = "" } = useParams();
  const [params, setParams] = useSearchParams();
  const step = Number(params.get("step") ?? "4");
  const queryClient = useQueryClient();
  const event = useQuery({ queryKey: ["event", id], queryFn: async () => (await api.get<Envelope<EventSite>>(`/events/${id}`)).data.data });
  const features = useQuery({ queryKey: ["features"], queryFn: async () => (await api.get<Envelope<{ advancedEditorEnabled: boolean }>>("/config/features")).data.data });
  const site = event.data;
  const [draft, setDraft] = useState<EventSite | null>(null);
  const past = useRef<EventSite[]>([]);
  const future = useRef<EventSite[]>([]);
  const currentRef = useRef<EventSite | null>(null);
  const [historyTick, setHistoryTick] = useState(0);
  const [pictureOpen, setPictureOpen] = useState(false);
  useEffect(() => {
    setDraft(null);
    past.current = [];
    future.current = [];
  }, [id]);
  const current = draft ?? site ?? null;
  currentRef.current = current;
  function update(change: (copy: EventSite) => void, mode?: "coalesce") {
    if (!current) return;
    rememberHistory(past.current, future.current, current, mode);
    patch(setDraft, current, change);
    if (mode !== "coalesce") setHistoryTick((tick) => tick + 1);
  }
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const key = event.key.toLowerCase();
      const meta = event.metaKey || event.ctrlKey;
      const redo = meta && ((key === "z" && event.shiftKey) || key === "y");
      const undo = meta && key === "z" && !event.shiftKey;
      if (!redo && !undo) return;
      const live = currentRef.current;
      if (!live) return;
      event.preventDefault();
      const restored = redo ? redoHistory(past.current, future.current, live) : undoHistory(past.current, future.current, live);
      if (restored) {
        setDraft(restored);
        setHistoryTick((tick) => tick + 1);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [id]);
  if (current && current.id === id && !current.configuration.canvas) {
    const copy = structuredClone(current);
    copy.configuration.canvas = sectionsToCanvas(copy.configuration);
    setDraft(copy);
  }
  const save = useMutation({
    mutationFn: async (next: EventSite) => (await api.put<Envelope<EventSite>>(`/events/${id}/configuration`, next.configuration)).data.data,
    onSuccess: (saved) => {
      setDraft(saved);
      queryClient.setQueryData(["event", id], saved);
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    },
  });
  const submit = useMutation({
    mutationFn: async () => (await api.post<Envelope<EventSite>>(`/events/${id}/submit`)).data.data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["event", id] });
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    },
  });
  if (!current) return <div className="skeleton" style={{ height: 28, width: 180 }} />;
  const configuration = current.configuration;
  const quote = quoteFromDays(current.requestedDays);
  const canUndo = historyTick >= 0 && past.current.length > 0;
  const canRedo = historyTick >= 0 && future.current.length > 0;
  function stepHistory(direction: "undo" | "redo") {
    const restored = direction === "redo"
      ? redoHistory(past.current, future.current, current)
      : undoHistory(past.current, future.current, current);
    if (!restored) return;
    setDraft(restored);
    setHistoryTick((tick) => tick + 1);
  }
  return (
    <div className="stage">
      <header className="stage-bar">
        <Link className="stage-back" to="/app" aria-label="Event sites" title="Event sites"><ArrowLeft size={16} /></Link>
        <h1>{current.eventName}</h1>
        <Pill status={current.status} />
        <div className="stage-actions">
          <button className="stage-icon" type="button" aria-label="Undo" title="Undo" disabled={!canUndo} onClick={() => stepHistory("undo")}><Undo2 size={16} /></button>
          <button className="stage-icon" type="button" aria-label="Redo" title="Redo" disabled={!canRedo} onClick={() => stepHistory("redo")}><Redo2 size={16} /></button>
          <Button type="button" tone="secondary" onClick={() => {
            const next = structuredClone(current);
            ensureCanvas(next.configuration);
            openFullPreview(id, next);
          }}>Open in a new tab</Button>
          {features.data?.advancedEditorEnabled ? <Link className="btn btn-secondary" to={`/app/events/${id}/studio`}>Visual editor</Link> : null}
          <Button type="button" onClick={() => {
            const next = structuredClone(current);
            ensureCanvas(next.configuration);
            save.mutate(next);
          }} disabled={save.isPending}>Save draft</Button>
        </div>
      </header>
      <div className="stage-body">
        <div className="stage-tools" role="toolbar" aria-label="Add to the page">
          <button type="button" aria-label="Heading" title="Heading" onClick={() => update((copy) => addCanvasPiece(copy, "text", { weight: "display", text: "Heading", fontSize: 42, h: 72 }))}><Heading1 size={18} /></button>
          <button type="button" aria-label="Text" title="Text" onClick={() => update((copy) => addCanvasPiece(copy, "text"))}><Type size={18} /></button>
        <div className={pictureOpen ? "stage-tool is-open" : "stage-tool"}>
          <button type="button" aria-label="Picture" title="Picture" aria-expanded={pictureOpen} onClick={() => setPictureOpen((open) => !open)}><ImageIcon size={18} /></button>
          {pictureOpen ? (
            <>
              <button className="picture-backdrop" type="button" aria-label="Close pictures" onClick={() => setPictureOpen(false)} />
              <PictureMenu eventId={current.id} onPlace={(mediaId) => { update((copy) => addCanvasPiece(copy, "image", { mediaId })); setPictureOpen(false); }} />
            </>
          ) : null}
        </div>
          <button type="button" aria-label="Button" title="Button" onClick={() => update((copy) => addCanvasPiece(copy, "button"))}><SquareMousePointer size={18} /></button>
          <button type="button" aria-label="Link" title="Link" onClick={() => update((copy) => addCanvasPiece(copy, "button", { text: "Link", href: "https://" }))}><Link2 size={18} /></button>
          <button type="button" aria-label="Shape" title="Shape" onClick={() => update((copy) => addCanvasPiece(copy, "box"))}><Square size={18} /></button>
          <button type="button" aria-label="Reply" title="Reply" onClick={() => update((copy) => addCanvasPiece(copy, "rsvp", { text: "Reply" }))}><MessageSquare size={18} /></button>
        </div>
        <div className="stage-canvas">
          <div className="preview-frame">
            <PublicSite preview mediaEventId={id} onEdit={(edit) => update((copy) => { ensureCanvas(copy.configuration); applySiteEdit(copy, edit); }, edit.history)} event={{ slug: current.slug, eventName: current.eventName, availability: "PUBLISHED", templateCode: current.templateCode, configuration: shownConfiguration(current) }} />
          </div>
        </div>
        <aside className="stage-side">
          <div className="tabs" role="tablist">
            {[4, 5, 6, 7, 8].map((value) => (
              <button key={value} type="button" role="tab" aria-selected={step === value} onClick={() => setParams({ step: String(value) })}>
                {["Look", "Pictures", "Replies", "Address", "Preview"][value - 4]}
              </button>
            ))}
          </div>
          <div className="stack">
          {step === 4 ? (
            <>
              <ColorField label="Background" value={configuration.theme.background} onChange={(value) => update((copy) => { copy.configuration.theme.background = value; })} />
              <ColorField label="Ink" value={configuration.theme.ink} onChange={(value) => update((copy) => { copy.configuration.theme.ink = value; })} />
              <ColorField label="Accent" value={configuration.theme.accent} onChange={(value) => update((copy) => { copy.configuration.theme.accent = value; })} />
              <Field label="Title"><input value={configuration.event.name} onChange={(event) => update((copy) => { copy.eventName = event.target.value; copy.configuration.event.name = event.target.value; writeSection(copy, "HERO", "title", event.target.value); })} /></Field>
              <Field label="Line under the title"><input value={configuration.event.subtitle} onChange={(event) => update((copy) => { copy.configuration.event.subtitle = event.target.value; writeSection(copy, "HERO", "subtitle", event.target.value); })} /></Field>
              <Field label="When it starts"><input value={configuration.event.startAt} onChange={(event) => update((copy) => { copy.configuration.event.startAt = event.target.value; })} /></Field>
              <Field label="Venue"><input value={configuration.event.venueName} onChange={(event) => update((copy) => { copy.configuration.event.venueName = event.target.value; })} /></Field>
            </>
          ) : null}
          {step === 5 ? (
            <MediaManager
              eventId={current.id}
              placed={placedMediaIds(current)}
              onChoose={(file) => update((copy) => placeUpload(copy, file))}
              onRemove={async (fileId) => {
                const next = structuredClone(current);
                removeUpload(next, fileId);
                setDraft(next);
                const saved = (await api.put<Envelope<EventSite>>(`/events/${id}/configuration`, next.configuration)).data.data;
                setDraft(saved);
                queryClient.setQueryData(["event", id], saved);
              }}
            />
          ) : null}
          {step === 6 ? (
            <>
              <label><input type="checkbox" checked={configuration.rsvp.enabled} onChange={(event) => update((copy) => { copy.configuration.rsvp.enabled = event.target.checked; })} /> Accept replies</label>
              <Field label="Guests per reply"><input type="number" min={1} max={10} value={configuration.rsvp.maxGuestsPerResponse} onChange={(event) => update((copy) => { copy.configuration.rsvp.maxGuestsPerResponse = Number(event.target.value); })} /></Field>
              <label><input type="checkbox" checked={configuration.rsvp.collectPhone} onChange={(event) => update((copy) => { copy.configuration.rsvp.collectPhone = event.target.checked; })} /> Ask for a phone number</label>
              <Field label="Confirmation"><textarea value={configuration.rsvp.confirmationMessage} onChange={(event) => update((copy) => { copy.configuration.rsvp.confirmationMessage = event.target.value; })} /></Field>
            </>
          ) : null}
          {step === 7 ? (
            <>
              <Field label="Public address"><input value={current.slug} onChange={(event) => update((copy) => { copy.slug = event.target.value; })} /></Field>
              <Field label="Hosting days"><input type="number" min={1} max={365} value={current.requestedDays} onChange={(event) => update((copy) => { copy.requestedDays = Number(event.target.value); })} /></Field>
              <HostingRunway days={current.requestedDays} label={`${quote.days} days, ${quote.formatted}`} />
              <p className="num">{quote.formatted}</p>
              <Button type="button" tone="secondary" onClick={async () => {
                const saved = await api.put<Envelope<EventSite>>(`/events/${id}`, { slug: current.slug, requestedDays: current.requestedDays, eventName: current.eventName });
                setDraft(saved.data.data);
                queryClient.setQueryData(["event", id], saved.data.data);
                void queryClient.invalidateQueries({ queryKey: ["events"] });
              }}>Save address and days</Button>
            </>
          ) : null}
          {step === 8 ? (
            <>
              <p>Submit the draft for review. Payment comes after approval, then the site goes live at /{current.slug}.</p>
              {submit.isError || save.isError ? <p className="error-line">{errorMessage(submit.error ?? save.error, "The site was not submitted.")}</p> : null}
              <Button type="button" disabled={submit.isPending} onClick={async () => { await save.mutateAsync(current); submit.mutate(); }}>Submit for review</Button>
            </>
          ) : null}
          </div>
        </aside>
      </div>
    </div>
  );
}

function PictureMenu({ eventId, onPlace }: { eventId: string; onPlace: (mediaId: string) => void }) {
  const queryClient = useQueryClient();
  const [message, setMessage] = useState("");
  const media = useQuery({
    queryKey: ["media", eventId],
    queryFn: async () => (await api.get<Envelope<MediaFile[]>>(`/events/${eventId}/media`)).data.data,
    retry: false,
  });
  const pictures = (media.data ?? []).filter((file) => file.mediaType === "IMAGE");
  async function send(file: File | undefined) {
    if (!file) return;
    const body = new FormData();
    body.append("file", file);
    try {
      const response = await api.post<Envelope<{ id: string }>>(`/events/${eventId}/media`, body);
      await queryClient.invalidateQueries({ queryKey: ["media", eventId] });
      onPlace(response.data.data.id);
    } catch (error) {
      setMessage(errorMessage(error, "The picture was not saved."));
    }
  }
  return (
    <div className="picture-pop" role="dialog" aria-label="Pictures">
      <label className="picture-upload">
        Upload
        <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" aria-label="Upload a picture" onChange={(change) => void send(change.target.files?.[0])} />
      </label>
      {pictures.length > 0 ? (
        <div className="picture-grid">
          {pictures.map((file) => (
            <button key={file.id} type="button" aria-label={file.originalFilename || "Use this picture"} onClick={() => onPlace(file.id)}>
              <img src={file.url} alt="" />
            </button>
          ))}
        </div>
      ) : <p>No pictures yet.</p>}
      {message ? <p className="error-line">{message}</p> : null}
    </div>
  );
}

function ColorField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  const swatch = /^#[0-9a-fA-F]{6}$/.test(value) ? value : "#000000";
  return (
    <div className="field">
      <span>{label}</span>
      <span className="color-row">
        <input type="color" aria-label={`${label} colour`} value={swatch} onChange={(event) => onChange(event.target.value)} />
        <input aria-label={`${label} hex`} value={value} spellCheck={false} onChange={(event) => onChange(event.target.value)} />
      </span>
    </div>
  );
}

function patch(setDraft: (event: EventSite) => void, current: EventSite, change: (copy: EventSite) => void) {
  const copy = structuredClone(current);
  change(copy);
  setDraft(copy);
}

function applySiteEdit(event: EventSite, edit: SiteEdit) {
  if (edit.target === "event") {
    event.configuration.event[edit.key] = edit.value;
    if (edit.key === "name") event.eventName = edit.value;
    return;
  }
  if (edit.target === "align") {
    const section = event.configuration.sections.find((item) => item.id === edit.id);
    if (!section) return;
    const current = section.properties.alignments;
    const alignments = current && typeof current === "object" ? { ...(current as Record<string, unknown>) } : {};
    alignments[edit.key] = edit.value;
    section.properties.alignments = alignments;
    return;
  }
  if (edit.target === "box") {
    const section = event.configuration.sections.find((item) => item.id === edit.id);
    if (!section) return;
    section.properties.imageX = edit.x;
    section.properties.imageY = edit.y;
    section.properties.imageW = edit.w;
    section.properties.imageH = edit.h;
    return;
  }
  if (edit.target === "frame") {
    const sections = event.configuration.sections;
    const section = sections.find((item) => item.id === edit.id);
    if (!section) return;
    section.properties.frameX = edit.x;
    section.properties.frameW = edit.w;
    section.properties.frameH = edit.h;
    if (edit.order) {
      const ids = edit.order.split(",").filter(Boolean);
      const byId = new Map(sections.map((item) => [item.id, item]));
      const next = ids.map((id) => byId.get(id)).filter((item): item is NonNullable<typeof item> => Boolean(item));
      for (const item of sections) {
        if (!ids.includes(item.id)) next.push(item);
      }
      event.configuration.sections = next;
    }
    return;
  }
  if (edit.target === "canvas-element") {
    const canvas = ensureCanvas(event.configuration);
    const element = canvas.elements.find((item) => item.id === edit.id);
    if (!element) return;
    Object.assign(element, edit.patch);
    growCanvas(canvas);
    return;
  }
  if (edit.target === "canvas-elements") {
    const canvas = ensureCanvas(event.configuration);
    for (const change of edit.patches) {
      const element = canvas.elements.find((item) => item.id === change.id);
      if (element) Object.assign(element, change.patch);
    }
    growCanvas(canvas);
    return;
  }
  if (edit.target === "canvas-order") {
    const canvas = ensureCanvas(event.configuration);
    orderCanvas(canvas.elements, edit.id, edit.direction);
    return;
  }
  if (edit.target === "canvas-add") {
    const canvas = ensureCanvas(event.configuration);
    canvas.elements.push(edit.element);
    growCanvas(canvas);
    return;
  }
  if (edit.target === "canvas-remove") {
    const canvas = event.configuration.canvas;
    if (!canvas) return;
    canvas.elements = canvas.elements.filter((item) => !(edit.ids ?? [edit.id]).includes(item.id));
    growCanvas(canvas);
    return;
  }
  if (edit.target === "detach") {
    const sections = event.configuration.sections;
    const index = sections.findIndex((item) => item.id === edit.id);
    const section = index < 0 ? undefined : sections[index];
    if (!section) return;
    if (section.type === "IMAGE" && section.properties.mediaId === edit.mediaId && !String(section.properties.caption ?? "").trim()) {
      sections.splice(index, 1);
      return;
    }
    if (section.properties.backgroundImage === edit.mediaId) section.properties.backgroundImage = "";
    if (section.properties.mediaId === edit.mediaId) section.properties.mediaId = "";
    if (Array.isArray(section.properties.mediaIds)) {
      section.properties.mediaIds = section.properties.mediaIds.map(String).filter((item) => item !== edit.mediaId);
    }
    return;
  }
  const section = event.configuration.sections.find((item) => item.id === edit.id);
  if (!section) return;
  if (edit.target === "section") {
    section.properties[edit.key] = edit.value;
    if (section.type === "HERO" && edit.key === "title") {
      event.eventName = edit.value;
      event.configuration.event.name = edit.value;
    }
    if (section.type === "HERO" && edit.key === "subtitle") event.configuration.event.subtitle = edit.value;
    return;
  }
  if (!Array.isArray(section.properties.items) || !section.properties.items[edit.index]) return;
  const items = section.properties.items.map((item) => ({ ...(item as Record<string, unknown>) }));
  items[edit.index] = { ...items[edit.index], [edit.key]: edit.value };
  section.properties.items = items;
}

function writeSection(event: EventSite, type: string, key: string, value: string) {
  for (const section of event.configuration.sections) {
    if (section.type === type) section.properties[key] = value;
  }
}

function placedMediaIds(event: EventSite) {
  const ids: string[] = [];
  for (const section of event.configuration.sections) {
    const hero = String(section.properties.backgroundImage ?? "");
    const single = String(section.properties.mediaId ?? "");
    if (hero && !hero.startsWith("/placeholders/")) ids.push(hero);
    if (single && !single.startsWith("/placeholders/")) ids.push(single);
    if (Array.isArray(section.properties.mediaIds)) ids.push(...section.properties.mediaIds.map(String).filter((item) => item && !item.startsWith("/placeholders/")));
  }
  for (const element of event.configuration.canvas?.elements ?? []) {
    if (element.mediaId && !element.mediaId.startsWith("/placeholders/")) ids.push(element.mediaId);
  }
  return ids;
}

function shownConfiguration(event: EventSite) {
  if (event.configuration.canvas) return event.configuration;
  return { ...event.configuration, canvas: sectionsToCanvas(event.configuration) };
}

function addCanvasPiece(event: EventSite, type: CanvasElement["type"], extra: Partial<CanvasElement> = {}) {
  const canvas = ensureCanvas(event.configuration);
  const ink = event.configuration.theme.ink;
  const element = createElement({
    type,
    z: canvas.elements.reduce((max, item) => Math.max(max, item.z), 0) + 1,
    y: 48 + (canvas.elements.length % 8) * 16,
    w: type === "image" ? 32 : type === "rsvp" ? 42 : type === "box" ? 22 : type === "button" ? 28 : 46,
    h: type === "image" ? 200 : type === "rsvp" ? 340 : type === "box" ? 80 : type === "button" ? 48 : 72,
    text: type === "text" ? "New text" : type === "button" ? "Button" : type === "rsvp" ? "Reply" : "",
    fontSize: extra.fontSize ?? (type === "button" ? 16 : 20),
    fill: type === "button" ? ink : type === "box" ? event.configuration.theme.accent : "",
    color: type === "button" ? event.configuration.theme.background : ink,
    ...extra,
  }, ink);
  canvas.elements.push(element);
  growCanvas(canvas);
}

function placeUpload(event: EventSite, file: MediaFile) {
  if (file.mediaType === "VIDEO") {
    const video = event.configuration.sections.find((section) => section.type === "VIDEO");
    if (video) video.properties.mediaId = file.id;
    return;
  }
  if (placedMediaIds(event).includes(file.id)) return;
  const canvas = ensureCanvas(event.configuration);
  canvas.elements.push(createElement({
    type: "image",
    mediaId: file.id,
    x: 8 + (canvas.elements.filter((item) => item.type === "image").length % 3) * 28,
    y: 40,
    w: 32,
    h: 200,
    z: canvas.elements.reduce((max, item) => Math.max(max, item.z), 0) + 1,
  }, event.configuration.theme.ink));
  growCanvas(canvas);
}

function removeUpload(event: EventSite, fileId: string) {
  event.configuration.sections = event.configuration.sections.filter((section) => !(section.type === "IMAGE" && section.properties.mediaId === fileId && !String(section.properties.caption ?? "").trim()));
  if (event.configuration.canvas) {
    event.configuration.canvas.elements = event.configuration.canvas.elements.filter((element) => element.mediaId !== fileId);
  }
  for (const section of event.configuration.sections) {
    if (section.properties.backgroundImage === fileId) section.properties.backgroundImage = "";
    if (section.properties.mediaId === fileId) section.properties.mediaId = "";
    if (Array.isArray(section.properties.mediaIds)) {
      section.properties.mediaIds = section.properties.mediaIds.map(String).filter((item) => item !== fileId);
    }
  }
}

function openFullPreview(id: string, event: EventSite) {
  sessionStorage.setItem(`prism-preview-${id}`, JSON.stringify({
    slug: event.slug,
    eventName: event.eventName,
    templateCode: event.templateCode,
    configuration: event.configuration,
  }));
  window.open(`/app/events/${id}/preview`, "_blank", "noopener");
}

type MediaFile = { id: string; mediaType: string; url: string; originalFilename: string };

function MediaManager({ eventId, placed, onChoose, onRemove }: { eventId: string; placed: string[]; onChoose: (file: MediaFile) => void; onRemove: (fileId: string) => Promise<void> }) {
  const queryClient = useQueryClient();
  const [message, setMessage] = useState("");
  const [pendingId, setPendingId] = useState("");
  const media = useQuery({
    queryKey: ["media", eventId],
    queryFn: async () => (await api.get<Envelope<MediaFile[]>>(`/events/${eventId}/media`)).data.data,
  });
  const files = media.data ?? [];
  async function remove(file: MediaFile) {
    setPendingId(file.id);
    try {
      await api.delete(`/events/${eventId}/media/${file.id}`);
      await queryClient.invalidateQueries({ queryKey: ["media", eventId] });
      try {
        await onRemove(file.id);
        setMessage("Removed from the draft.");
      } catch (error) {
        setMessage(errorMessage(error, "The file was deleted. Save draft to take it off the page."));
      }
    } catch (error) {
      setMessage(errorMessage(error, "The file was not removed."));
    } finally {
      setPendingId("");
    }
  }
  return (
    <form className="stack" onSubmit={async (formEvent) => {
      formEvent.preventDefault();
      const input = formEvent.currentTarget.elements.namedItem("file") as HTMLInputElement;
      const file = input.files?.[0];
      if (!file) return;
      const body = new FormData();
      body.append("file", file);
      try {
        const response = await api.post<Envelope<MediaFile>>(`/events/${eventId}/media`, body);
        onChoose(response.data.data);
        await queryClient.invalidateQueries({ queryKey: ["media", eventId] });
        setMessage("The picture is on the page. Click another to add more.");
        input.value = "";
      } catch (error) {
        setMessage(errorMessage(error, "The file was not saved."));
      }
    }}>
      <p>Upload a picture and click it to put it on the page. Click another picture to add that one too. Drag each picture to the gap you want, and use the corner to resize it.</p>
      {files.length > 0 ? (
        <div className="media-grid">
          {files.map((file) => (
            <div key={file.id} className="media-tile">
              {file.mediaType === "IMAGE" ? (
                <button type="button" className="media-pick" aria-pressed={placed.includes(file.id)} aria-label={file.originalFilename} onClick={() => onChoose(file)}>
                  <img src={file.url} alt="" />
                </button>
              ) : (
                <p className="media-name">{file.originalFilename}</p>
              )}
              <button type="button" className="media-remove" disabled={pendingId === file.id} onClick={() => void remove(file)}>
                {pendingId === file.id ? "Removing" : "Remove"}
              </button>
            </div>
          ))}
        </div>
      ) : null}
      <Field label="Image or video" hint="JPEG, PNG, GIF, WebP, MP4 or WebM. The file type is checked on the server.">
        <input name="file" type="file" accept="image/*,video/mp4,video/webm" />
      </Field>
      <Button type="submit" tone="secondary">Upload</Button>
      {message ? <p>{message}</p> : null}
    </form>
  );
}

export function DraftPreviewPage() {
  const { id = "" } = useParams();
  const stored = useMemo(() => readPreview(id), [id]);
  const event = useQuery({
    queryKey: ["event", id],
    enabled: !stored,
    queryFn: async () => (await api.get<Envelope<EventSite>>(`/events/${id}`)).data.data,
  });
  const site = stored ?? event.data;
  if (!site) {
    return <main className="event-status"><p>{event.isError ? "Sign in, then open the preview again." : "Loading the preview."}</p></main>;
  }
  return (
    <div className="full-preview">
      <div className="draft-watermark" aria-hidden="true" />
      <PublicSite
        preview
        mediaEventId={id}
        event={{ slug: site.slug, eventName: site.eventName, availability: "PUBLISHED", templateCode: site.templateCode, configuration: site.configuration, editorDocument: site.editorDocument }}
      />
    </div>
  );
}

function readPreview(id: string): EventSite | null {
  const raw = sessionStorage.getItem(`prism-preview-${id}`);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Pick<EventSite, "slug" | "eventName" | "templateCode" | "configuration" | "editorDocument">;
    return { ...parsed, id } as EventSite;
  } catch {
    return null;
  }
}

export function PaymentPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const event = useQuery({ queryKey: ["event", id], queryFn: async () => (await api.get<Envelope<EventSite>>(`/events/${id}`)).data.data });
  const quote = useQuery({
    queryKey: ["quote", id, event.data?.requestedDays],
    enabled: Boolean(event.data),
    queryFn: async () => (await api.get<Envelope<Quote>>(`/events/${id}/quote`, { params: { days: event.data?.requestedDays } })).data.data,
  });
  const pay = useMutation({
    mutationFn: async () => settlePayment((await api.post(`/events/${id}/payments`, { paymentType: "INITIAL_HOSTING", days: event.data?.requestedDays })).data.data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["event", id] });
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    },
  });
  const site = event.data;
  const priced = quote.data;
  return (
    <>
      <div className="sheet-title"><div><h1>Pay for hosting</h1><p>{site ? `${site.eventName} publishes when this payment is verified.` : "Loading the quote."}</p></div></div>
      {priced ? (
        <div className="stack" style={{ maxWidth: 480, marginTop: 24 }}>
          <HostingRunway days={priced.days} label={`${priced.days} hosting days, ${priced.formatted}`} />
          <p className="num" style={{ fontSize: 28 }}>{priced.formatted}</p>
          <p>{priced.days} days at ₹500.</p>
          {pay.isSuccess ? <p>Paid. The site is published at /{site?.slug}.</p> : null}
          {pay.isError ? <p className="error-line">{errorMessage(pay.error, "The payment did not complete.")}</p> : null}
          <Button type="button" disabled={pay.isPending || pay.isSuccess} onClick={() => pay.mutate()}>Pay {priced.formatted}</Button>
        </div>
      ) : <div className="skeleton" style={{ height: 28, width: 120 }} />}
    </>
  );
}

export function ExtendPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const [days, setDays] = useState(7);
  const quote = useMemo(() => quoteFromDays(days), [days]);
  const pay = useMutation({
    mutationFn: async () => settlePayment((await api.post(`/events/${id}/extend`, { days })).data.data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["event", id] });
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    },
  });
  return (
    <>
      <div className="sheet-title"><div><h1>Extend hosting</h1><p>Added days start when the current period ends. If the site has already expired, they start when this payment is verified.</p></div></div>
      <div className="stack" style={{ maxWidth: 480, marginTop: 24 }}>
        <Field label="Additional days"><input type="number" min={1} max={365} value={days} onChange={(event) => setDays(Number(event.target.value))} /></Field>
        <HostingRunway days={days} label={`${quote.days} more days, ${quote.formatted}`} />
        <p className="num" style={{ fontSize: 28 }}>{quote.formatted}</p>
        {pay.isSuccess ? <p>The extension is recorded.</p> : null}
        {pay.isError ? <p className="error-line">{errorMessage(pay.error, "The extension was not saved.")}</p> : null}
        <Button type="button" disabled={pay.isPending} onClick={() => pay.mutate()}>Pay {quote.formatted}</Button>
      </div>
    </>
  );
}

export function RsvpPage() {
  const { id = "" } = useParams();
  const summary = useQuery({ queryKey: ["rsvp", id], queryFn: async () => (await api.get<Envelope<RsvpSummary>>(`/events/${id}/rsvps/summary`)).data.data });
  const list = useQuery({ queryKey: ["rsvp-list", id], queryFn: async () => (await api.get<Envelope<Array<Record<string, string | number>>>>(`/events/${id}/rsvps`)).data.data });
  const data = summary.data;
  return (
    <>
      <div className="sheet-title">
        <div><h1>Replies</h1><p>{data ? `${data.expectedGuests} people expected from ${data.accepted} acceptances.` : "Loading replies."}</p></div>
        <a className="btn-secondary" href={`/api/v1/events/${id}/rsvps/export`}>Download CSV</a>
      </div>
      {data ? (
        <div className="kpis" style={{ marginTop: 24 }}>
          <div className="kpi"><span>Replies</span><strong className="num">{data.total}</strong></div>
          <div className="kpi"><span>Accepted</span><strong className="num">{data.accepted}</strong></div>
          <div className="kpi"><span>Declined</span><strong className="num">{data.declined}</strong></div>
          <div className="kpi"><span>Page loads</span><strong className="num">{data.pageViews ?? 0}</strong></div>
        </div>
      ) : null}
      <table className="table" style={{ marginTop: 24 }}>
        <thead><tr><th>Name</th><th>Attendance</th><th className="num">Guests</th><th>Note</th></tr></thead>
        <tbody>
          {(list.data ?? []).map((row) => (
            <tr key={String(row.id)}>
              <td>{row.guestName}</td>
              <td>{row.attendanceStatus}</td>
              <td className="num">{row.numberOfGuests}</td>
              <td>{row.message}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {list.data?.length === 0 ? <Empty>No replies yet. The count stays at zero until someone writes in.</Empty> : null}
    </>
  );
}

export function AccountPage() {
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ["me"], queryFn: async () => (await api.get<Envelope<{ fullName: string; email: string | null; phoneDisplay: string }>>("/auth/me")).data.data });
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const save = useMutation({
    mutationFn: async () => (await api.put("/auth/me", { fullName: name || me.data?.fullName, email })).data.data,
    onSuccess: (account) => queryClient.setQueryData(["me"], account),
  });
  return (
    <>
      <div className="sheet-title"><div><h1>Account</h1><p>The mobile number signs this account in. It is not edited here.</p></div></div>
      <form className="stack" style={{ maxWidth: 420, marginTop: 24 }} onSubmit={(event) => { event.preventDefault(); save.mutate(); }}>
        <Field label="Name"><input value={name || me.data?.fullName || ""} onChange={(event) => setName(event.target.value)} /></Field>
        <Field label="Email"><input value={email || me.data?.email || ""} onChange={(event) => setEmail(event.target.value)} /></Field>
        <p>Mobile {me.data?.phoneDisplay}</p>
        <Button type="submit">Save account</Button>
      </form>
    </>
  );
}

function statusLine(site: EventSite) {
  if (site.status === "CHANGES_REQUESTED") return "The reviewer asked for a change. Edit the draft and submit it again.";
  if (site.status === "APPROVED") return "Approved. Pay for the hosting days to publish it.";
  if (site.status === "PUBLISHED") return `Live at /${site.slug}. ${site.hosting.daysRemaining} hosting days left.`;
  if (site.status === "SUBMITTED" || site.status === "IN_REVIEW") return "With a reviewer. The draft is locked until they answer.";
  if (site.status === "EXPIRED") return "Hosting has ended. Extend it to publish the site again.";
  return "A draft. Submit it when the words and the date are right.";
}
