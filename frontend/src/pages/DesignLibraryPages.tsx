import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, ChevronRight, ImagePlus, Link2, Pencil, Trash2 } from "lucide-react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { Button, Empty, Field, Pill } from "../components/ui";
import { api, errorMessage } from "../services/api";
import type { Envelope } from "../types";

const CATEGORIES = [
  "WEDDING",
  "ANNIVERSARY",
  "BABY",
  "BIRTHDAY",
  "CORPORATE",
  "PORTFOLIO",
  "FESTIVAL",
  "OTHER",
] as const;

const ARCHETYPES = [
  "luxury-editorial",
  "botanical-romance",
  "modern-minimal",
  "royal-heritage",
  "playful-contemporary",
  "cinematic-luxury",
  "contemporary-art",
  "warm-storybook",
  "bold-typographic",
  "classic-formal",
] as const;

type ReferenceSummary = {
  id: string;
  seedKey: string;
  name: string;
  description: string;
  status: string;
  sourceType: string;
  eventCategory: string;
  archetypeId: string;
  qualityScore: number;
  retrievalCount: number;
  hasDesktopScreenshot: boolean;
  hasMobileScreenshot: boolean;
  inspirationUrl?: string | null;
};

type MediaView = {
  id: string;
  kind: string;
  mimeType: string;
  url: string;
};

type ReferenceDetail = ReferenceSummary & {
  mood?: string[];
  styleTags?: string[];
  keywords?: string[];
  colourPalette?: Record<string, unknown>;
  typography?: Record<string, unknown>;
  layout?: Record<string, unknown>;
  patternJson?: Record<string, unknown>;
  componentMap?: Record<string, unknown>;
  attribution?: string | null;
  usagePermission?: string | null;
  desktopMediaId?: string | null;
  mobileMediaId?: string | null;
  desktopMedia?: MediaView | null;
  mobileMedia?: MediaView | null;
  embeddingModel?: string | null;
  embeddingUpdatedAt?: string | null;
  sections?: Array<{
    id?: string;
    sectionKey: string;
    purpose: string;
    composition: string;
  }>;
};

type LibraryDashboard = {
  total: number;
  approved: number;
  draft: number;
  archived: number;
  original: number;
  inspirationLinks: number;
  byCategory: Record<string, number>;
  byArchetype: Record<string, number>;
  mostRetrieved: ReferenceSummary[];
};

type EditForm = {
  name: string;
  description: string;
  status: string;
  sourceType: string;
  eventCategory: string;
  archetypeId: string;
  qualityScore: string;
  mood: string;
  styleTags: string;
  keywords: string;
  inspirationUrl: string;
  attribution: string;
  usagePermission: string;
};

function toForm(row: ReferenceDetail): EditForm {
  return {
    name: row.name ?? "",
    description: row.description ?? "",
    status: row.status ?? "DRAFT",
    sourceType: row.sourceType ?? "ORIGINAL",
    eventCategory: row.eventCategory ?? "WEDDING",
    archetypeId: row.archetypeId ?? "luxury-editorial",
    qualityScore: String(row.qualityScore ?? 7),
    mood: (row.mood ?? []).join(", "),
    styleTags: (row.styleTags ?? []).join(", "),
    keywords: (row.keywords ?? []).join(", "),
    inspirationUrl: row.inspirationUrl ?? "",
    attribution: row.attribution ?? "",
    usagePermission: row.usagePermission ?? "ORIGINAL",
  };
}

function blankForm(): EditForm {
  return {
    name: "",
    description: "",
    status: "DRAFT",
    sourceType: "ORIGINAL",
    eventCategory: "WEDDING",
    archetypeId: "luxury-editorial",
    qualityScore: "7",
    mood: "",
    styleTags: "",
    keywords: "",
    inspirationUrl: "",
    attribution: "",
    usagePermission: "ORIGINAL",
  };
}

function splitList(value: string) {
  return value
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean);
}

function formPayload(form: EditForm) {
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    status: form.status,
    sourceType: form.sourceType,
    eventCategory: form.eventCategory,
    archetypeId: form.archetypeId,
    qualityScore: Number(form.qualityScore),
    mood: splitList(form.mood),
    styleTags: splitList(form.styleTags),
    keywords: splitList(form.keywords),
    inspirationUrl: form.inspirationUrl.trim(),
    attribution: form.attribution.trim(),
    usagePermission: form.usagePermission.trim() || "ORIGINAL",
  };
}

export function DesignIntelligencePage() {
  const dashboard = useQuery({
    queryKey: ["design-library-dashboard"],
    queryFn: async () => (await api.get<Envelope<LibraryDashboard>>("/admin/design-references/dashboard")).data.data,
  });
  const data = dashboard.data;
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Design intelligence</h1>
          <p>Reference library health, retrievals, and quality.</p>
        </div>
        <Button asChild><Link to="/admin/design-references">Open library</Link></Button>
      </div>
      {data ? (
        <>
          <div className="kpis">
            <div className="kpi"><span>Total</span><strong className="num">{data.total}</strong><em>{data.original} original</em></div>
            <div className="kpi"><span>Approved</span><strong className="num">{data.approved}</strong><em>{data.draft} draft · {data.archived} archived</em></div>
            <div className="kpi"><span>Inspiration links</span><strong className="num">{data.inspirationLinks}</strong><em>URL-only, no screenshots</em></div>
          </div>
          <div className="split" style={{ marginTop: 16 }}>
            <section className="list">
              <h2>By category</h2>
              {Object.entries(data.byCategory).map(([key, value]) => (
                <div key={key} className="site-row"><strong>{key}</strong><span className="num">{value}</span></div>
              ))}
            </section>
            <section className="list">
              <h2>By archetype</h2>
              {Object.entries(data.byArchetype).map(([key, value]) => (
                <div key={key} className="site-row"><strong>{key}</strong><span className="num">{value}</span></div>
              ))}
            </section>
          </div>
          <section className="list" style={{ marginTop: 16 }}>
            <h2>Most retrieved</h2>
            {data.mostRetrieved.length === 0 ? <Empty>No retrievals yet.</Empty> : data.mostRetrieved.map((row) => (
              <Link key={row.id} to={`/admin/design-references/${row.id}`} className="site-row">
                <div>
                  <strong>{row.name}</strong>
                  <p>{row.eventCategory} · {row.archetypeId}</p>
                </div>
                <span className="num">{row.retrievalCount}</span>
              </Link>
            ))}
          </section>
        </>
      ) : <p className="studio-note">Loading design intelligence.</p>}
    </>
  );
}

export function DesignReferencesPage() {
  const [status, setStatus] = useState("APPROVED");
  const [category, setCategory] = useState("");
  const [q, setQ] = useState("");
  const query = useQuery({
    queryKey: ["design-references", status, category, q],
    queryFn: async () => (await api.get<Envelope<ReferenceSummary[]>>("/admin/design-references", {
      params: { status, category: category || undefined, q: q || undefined },
    })).data.data,
  });
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Design references</h1>
          <p>Original WebAux patterns and external inspiration links. Open a row to upload screenshots and edit details.</p>
        </div>
        <div className="title-actions">
          <Button asChild tone="secondary"><Link to="/admin/design-intelligence">Intelligence</Link></Button>
          <Button asChild><Link to="/admin/design-references/new">New reference</Link></Button>
        </div>
      </div>
      <div className="choice-row" style={{ gap: 12, flexWrap: "wrap" }}>
        <Field label="Status">
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="APPROVED">Approved</option>
            <option value="DRAFT">Draft</option>
            <option value="ARCHIVED">Archived</option>
            <option value="ALL">All</option>
          </select>
        </Field>
        <Field label="Category">
          <input value={category} placeholder="WEDDING" onChange={(event) => setCategory(event.target.value)} />
        </Field>
        <Field label="Search">
          <input value={q} placeholder="Name or description" onChange={(event) => setQ(event.target.value)} />
        </Field>
      </div>
      <section className="list" style={{ marginTop: 16 }}>
        {(query.data ?? []).length === 0 ? <Empty>No references match these filters.</Empty> : (query.data ?? []).map((row) => (
          <Link key={row.id} to={`/admin/design-references/${row.id}`} className="site-row">
            <div>
              <strong>{row.name}</strong>
              <p>{row.eventCategory} · {row.archetypeId} · {row.sourceType}</p>
              <p className="studio-hint" style={{ marginTop: 4 }}>
                {row.hasDesktopScreenshot || row.hasMobileScreenshot
                  ? `${row.hasDesktopScreenshot ? "Desktop" : ""}${row.hasDesktopScreenshot && row.hasMobileScreenshot ? " · " : ""}${row.hasMobileScreenshot ? "Mobile" : ""} screenshot`
                  : "No screenshots yet"}
              </p>
            </div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <Pill status={row.status} />
              <span className="num">{row.qualityScore}</span>
            </div>
          </Link>
        ))}
      </section>
    </>
  );
}

function ScreenshotPanel({
  title,
  hint,
  media,
  busy,
  onUpload,
}: {
  title: string;
  hint: string;
  media?: MediaView | null;
  busy: boolean;
  onUpload: (file: File) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <article className="design-shot">
      <div className="design-shot-frame">
        {media?.url ? (
          <img src={media.url} alt={`${title} screenshot`} />
        ) : (
          <div className="design-shot-empty">
            <ImagePlus size={28} strokeWidth={1.5} />
            <span>No {title.toLowerCase()} screenshot</span>
          </div>
        )}
      </div>
      <div className="design-shot-meta">
        <div>
          <strong>{title}</strong>
          <p>{hint}</p>
        </div>
        <div className="title-actions">
          <input
            ref={inputRef}
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (file) onUpload(file);
            }}
          />
          <Button type="button" tone="secondary" disabled={busy} onClick={() => inputRef.current?.click()}>
            {media ? "Replace" : "Upload"}
          </Button>
        </div>
      </div>
    </article>
  );
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function asText(value: unknown) {
  if (value == null) return "";
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return String(value);
  return "";
}

function asStringList(value: unknown) {
  if (!Array.isArray(value)) return [] as string[];
  return value.map((item) => asText(item)).filter(Boolean);
}

function isHexColor(value: string) {
  return /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(value.trim());
}

function humanizeKey(key: string) {
  return key
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function PatternVisual({
  patternJson,
  colourPalette,
  typography,
  layout,
}: {
  patternJson?: Record<string, unknown>;
  colourPalette?: Record<string, unknown>;
  typography?: Record<string, unknown>;
  layout?: Record<string, unknown>;
}) {
  const [showRaw, setShowRaw] = useState(false);
  const pattern = asRecord(patternJson);
  const colours = asRecord(colourPalette);
  const type = asRecord(typography);
  const layoutMap = asRecord(layout);
  const colourEntries = Object.entries(colours).filter(([, value]) => isHexColor(asText(value)));
  const typeEntries = Object.entries(type).filter(([, value]) => asText(value));
  const factEntries = [
    ["Hero", asText(pattern.hero) || asText(layoutMap.heroPattern)],
    ["Composition", asText(layoutMap.composition)],
    ["Image treatment", asText(pattern.imageTreatment)],
    ["Section rhythm", asText(pattern.sectionRhythm) || asText(layoutMap.sectionRhythm)],
    ["Density", asText(pattern.density) || asText(type.density)],
    ["Motion", asText(pattern.motion)],
    ["Content width", asText(layoutMap.contentWidth)],
    ["Alignment", asText(layoutMap.alignmentBias)],
  ].filter(([, value]) => Boolean(value));
  const avoid = asStringList(pattern.avoid);
  const responsive = asRecord(pattern.responsive);
  const responsiveEntries = Object.entries(responsive).filter(([, value]) => asText(value) !== "");
  const background = asText(colours.background) || "#F8FAFC";
  const textColor = asText(colours.text) || "#0F172A";
  const accent = asText(colours.accent) || "#087f80";
  const displayFont = asText(type.display) || asText(type.heading) || "Georgia, serif";
  const bodyFont = asText(type.body) || "system-ui, sans-serif";

  return (
    <section className="design-ref-panel">
      <div className="design-ref-panel-head">
        <div>
          <h2>Design pattern</h2>
          <p className="studio-hint">Palette, type, and layout used to guide generation.</p>
        </div>
        <Button type="button" tone="secondary" onClick={() => setShowRaw((value) => !value)}>
          <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
            {showRaw ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
            {showRaw ? "Hide JSON" : "Raw JSON"}
          </span>
        </Button>
      </div>

      <div
        className="design-ref-preview"
        style={{ background, color: textColor, borderColor: "color-mix(in srgb, currentColor 12%, transparent)" }}
      >
        <p className="design-ref-preview-kicker" style={{ color: accent }}>Pattern preview</p>
        <strong style={{ fontFamily: `"${displayFont}", Georgia, serif` }}>
          {asText(pattern.hero) || asText(layoutMap.heroPattern) || "Hero pattern"}
        </strong>
        <p style={{ fontFamily: `"${bodyFont}", system-ui, sans-serif` }}>
          {asText(pattern.sectionRhythm) || asText(layoutMap.sectionRhythm) || "Section rhythm"}
          {asText(pattern.density) ? ` · ${asText(pattern.density)} density` : ""}
        </p>
        <span className="design-ref-preview-accent" style={{ background: accent }} />
      </div>

      {colourEntries.length > 0 ? (
        <div className="design-ref-block">
          <h3>Colour palette</h3>
          <div className="design-ref-swatches">
            {colourEntries.map(([key, value]) => {
              const hex = asText(value);
              return (
                <div key={key} className="design-ref-swatch">
                  <span style={{ background: hex }} title={hex} />
                  <div>
                    <strong>{humanizeKey(key)}</strong>
                    <em>{hex}</em>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      ) : null}

      {typeEntries.length > 0 ? (
        <div className="design-ref-block">
          <h3>Typography</h3>
          <div className="design-ref-type-grid">
            {typeEntries.map(([key, value]) => {
              const font = asText(value);
              const sample = key === "body" || key === "density" ? "Body sample for readable event copy." : "Display headline";
              return (
                <div key={key} className="design-ref-type-card">
                  <span>{humanizeKey(key)}</span>
                  <strong style={key === "density" ? undefined : { fontFamily: `"${font}", Georgia, serif` }}>
                    {key === "density" ? font : sample}
                  </strong>
                  {key === "density" ? null : <em>{font}</em>}
                </div>
              );
            })}
          </div>
        </div>
      ) : null}

      {factEntries.length > 0 ? (
        <div className="design-ref-block">
          <h3>Layout & motion</h3>
          <dl className="design-ref-facts">
            {factEntries.map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        </div>
      ) : null}

      {(avoid.length > 0 || responsiveEntries.length > 0) ? (
        <div className="design-ref-block">
          <h3>Constraints</h3>
          {avoid.length > 0 ? (
            <div className="design-ref-chips">
              {avoid.map((item) => <span key={item} className="design-ref-chip">Avoid {item.replaceAll("-", " ")}</span>)}
            </div>
          ) : null}
          {responsiveEntries.length > 0 ? (
            <dl className="design-ref-facts" style={{ marginTop: avoid.length ? 12 : 0 }}>
              {responsiveEntries.map(([key, value]) => (
                <div key={key}>
                  <dt>{humanizeKey(key)}</dt>
                  <dd>{asText(value)}</dd>
                </div>
              ))}
            </dl>
          ) : null}
        </div>
      ) : null}

      {showRaw ? (
        <pre className="design-ref-json">{JSON.stringify({
          patternJson: pattern,
          colourPalette: colours,
          typography: type,
          layout: layoutMap,
        }, null, 2)}</pre>
      ) : null}
    </section>
  );
}

function ReferenceEditor({
  form,
  setForm,
  message,
  saving,
  onSave,
  onCancel,
  saveLabel,
}: {
  form: EditForm;
  setForm: (next: EditForm) => void;
  message: string;
  saving: boolean;
  onSave: () => void;
  onCancel?: () => void;
  saveLabel: string;
}) {
  return (
    <section className="design-ref-form">
      <h2>Details</h2>
      <div className="design-ref-form-grid">
        <Field label="Name">
          <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
        </Field>
        <Field label="Quality score">
          <input type="number" min={0} max={10} step={0.1} value={form.qualityScore} onChange={(event) => setForm({ ...form, qualityScore: event.target.value })} />
        </Field>
        <Field label="Status">
          <select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}>
            <option value="DRAFT">Draft</option>
            <option value="APPROVED">Approved</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </Field>
        <Field label="Source type">
          <select value={form.sourceType} onChange={(event) => setForm({ ...form, sourceType: event.target.value })}>
            <option value="ORIGINAL">Original</option>
            <option value="INSPIRATION_LINK">Inspiration link</option>
          </select>
        </Field>
        <Field label="Event category">
          <select value={form.eventCategory} onChange={(event) => setForm({ ...form, eventCategory: event.target.value })}>
            {CATEGORIES.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </Field>
        <Field label="Archetype">
          <select value={form.archetypeId} onChange={(event) => setForm({ ...form, archetypeId: event.target.value })}>
            {ARCHETYPES.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </Field>
        <div className="design-ref-form-span">
          <Field label="Description">
            <textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
          </Field>
        </div>
        <Field label="Mood" hint="Comma-separated">
          <input value={form.mood} onChange={(event) => setForm({ ...form, mood: event.target.value })} />
        </Field>
        <Field label="Style tags" hint="Comma-separated">
          <input value={form.styleTags} onChange={(event) => setForm({ ...form, styleTags: event.target.value })} />
        </Field>
        <Field label="Keywords" hint="Comma-separated">
          <input value={form.keywords} onChange={(event) => setForm({ ...form, keywords: event.target.value })} />
        </Field>
        <Field label="Usage permission">
          <input value={form.usagePermission} onChange={(event) => setForm({ ...form, usagePermission: event.target.value })} />
        </Field>
        <Field label="Inspiration URL">
          <input value={form.inspirationUrl} onChange={(event) => setForm({ ...form, inspirationUrl: event.target.value })} />
        </Field>
        <Field label="Attribution">
          <input value={form.attribution} onChange={(event) => setForm({ ...form, attribution: event.target.value })} />
        </Field>
      </div>
      <div className="design-ref-form-actions">
        <Button type="button" disabled={saving || !form.name.trim()} onClick={onSave}>{saving ? "Saving…" : saveLabel}</Button>
        {onCancel ? <Button type="button" tone="secondary" disabled={saving} onClick={onCancel}>Cancel</Button> : null}
      </div>
      {message ? <p className="error-line">{message}</p> : null}
    </section>
  );
}

export function DesignReferenceDetailPage() {
  const { id: routeId = "" } = useParams();
  const location = useLocation();
  const creating = routeId === "new" || location.pathname.endsWith("/design-references/new");
  const id = creating ? "new" : routeId;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<EditForm>(blankForm());
  const [editing, setEditing] = useState(creating);
  const [message, setMessage] = useState("");

  const detail = useQuery({
    queryKey: ["design-reference", id],
    enabled: Boolean(id) && !creating,
    queryFn: async () => (await api.get<Envelope<ReferenceDetail>>(`/admin/design-references/${id}`)).data.data,
  });

  useEffect(() => {
    if (creating) {
      setForm(blankForm());
      setEditing(true);
      return;
    }
    if (detail.data) {
      setForm(toForm(detail.data));
      setEditing(false);
    }
  }, [creating, detail.data]);

  const save = useMutation({
    mutationFn: async () => {
      const body = formPayload(form);
      if (creating) {
        return (await api.post<Envelope<ReferenceDetail>>("/admin/design-references", body)).data.data;
      }
      return (await api.put<Envelope<ReferenceDetail>>(`/admin/design-references/${id}`, body)).data.data;
    },
    onSuccess: async (row) => {
      setMessage("");
      await queryClient.invalidateQueries({ queryKey: ["design-references"] });
      await queryClient.invalidateQueries({ queryKey: ["design-library-dashboard"] });
      if (creating) {
        navigate(`/admin/design-references/${row.id}`, { replace: true });
        return;
      }
      await queryClient.invalidateQueries({ queryKey: ["design-reference", id] });
      setEditing(false);
    },
    onError: (error) => setMessage(errorMessage(error, "The reference could not be saved.")),
  });

  const status = useMutation({
    mutationFn: async (next: string) => api.post(`/admin/design-references/${id}/status`, { status: next }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["design-reference", id] });
      await queryClient.invalidateQueries({ queryKey: ["design-references"] });
      setMessage("");
    },
    onError: (error) => setMessage(errorMessage(error, "Status could not be updated.")),
  });

  const upload = useMutation({
    mutationFn: async ({ kind, file }: { kind: string; file: File }) => {
      const body = new FormData();
      body.append("file", file);
      return api.post(`/admin/design-references/${id}/screenshots?kind=${encodeURIComponent(kind)}`, body);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["design-reference", id] });
      await queryClient.invalidateQueries({ queryKey: ["design-references"] });
      setMessage("");
    },
    onError: (error) => setMessage(errorMessage(error, "Screenshot could not be uploaded.")),
  });

  const remove = useMutation({
    mutationFn: async () => api.delete(`/admin/design-references/${id}`),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["design-references"] });
      await queryClient.invalidateQueries({ queryKey: ["design-library-dashboard"] });
      navigate("/admin/design-references");
    },
    onError: (error) => setMessage(errorMessage(error, "The reference could not be deleted.")),
  });

  const embed = useMutation({
    mutationFn: async () => api.post(`/admin/design-references/${id}/embed`),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["design-reference", id] });
      setMessage("");
    },
    onError: (error) => setMessage(errorMessage(error, "Embedding could not be generated.")),
  });

  const row = detail.data;
  const sections = useMemo(() => row?.sections ?? [], [row]);

  if (!creating && detail.isLoading) return <p className="studio-note">Loading reference.</p>;
  if (!creating && !row) return <Empty>That reference was not found.</Empty>;

  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>{creating ? "New design reference" : String(row?.name)}</h1>
          <p>{creating ? "Create a pattern, then upload optional desktop and mobile screenshots." : String(row?.description ?? "")}</p>
        </div>
        <div className="title-actions">
          <Button type="button" tone="secondary" onClick={() => navigate("/admin/design-references")}>Back</Button>
          {!creating ? (
            <>
              <Button type="button" tone="secondary" onClick={() => { setEditing((value) => !value); setMessage(""); }}>
                <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                  <Pencil size={16} /> {editing ? "Close editor" : "Edit"}
                </span>
              </Button>
              <Button
                type="button"
                tone="secondary"
                disabled={remove.isPending}
                onClick={() => {
                  if (window.confirm("Delete this design reference?")) remove.mutate();
                }}
              >
                <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                  <Trash2 size={16} /> Delete
                </span>
              </Button>
            </>
          ) : null}
        </div>
      </div>

      {!creating && row ? (
        <>
          <div className="choice-row" style={{ gap: 8, flexWrap: "wrap" }}>
            <Pill status={row.status} />
            <Pill status={row.sourceType} />
            <Pill status={row.eventCategory} />
            <Pill status={row.archetypeId} />
            <span className="num">Quality {String(row.qualityScore)}</span>
            <span className="num">Retrieved {String(row.retrievalCount)}</span>
          </div>
          {row.inspirationUrl ? (
            <p className="studio-hint" style={{ marginTop: 8 }}>
              <a href={row.inspirationUrl} target="_blank" rel="noreferrer" style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                <Link2 size={14} /> {row.inspirationUrl}
              </a>
            </p>
          ) : null}

          <section style={{ marginTop: 16 }}>
            <div className="sheet-title" style={{ marginBottom: 12 }}>
              <div>
                <h2>Screenshots</h2>
                <p className="studio-hint">Seeds ship without images. Upload WebAux-owned or licensed screenshots here.</p>
              </div>
              <div className="title-actions">
                {["DRAFT", "APPROVED", "ARCHIVED"].map((next) => (
                  <Button
                    key={next}
                    type="button"
                    tone={row.status === next ? "primary" : "secondary"}
                    disabled={status.isPending || row.status === next}
                    onClick={() => status.mutate(next)}
                  >
                    {next}
                  </Button>
                ))}
                <Button type="button" tone="secondary" disabled={embed.isPending} onClick={() => embed.mutate()}>
                  {embed.isPending ? "Embedding…" : "Embed"}
                </Button>
              </div>
            </div>
            <div className="design-shot-grid">
              <ScreenshotPanel
                title="Desktop"
                hint="Wide reference frame for generation guidance."
                media={row.desktopMedia}
                busy={upload.isPending}
                onUpload={(file) => upload.mutate({ kind: "DESKTOP", file })}
              />
              <ScreenshotPanel
                title="Mobile"
                hint="Narrow reference frame for responsive patterns."
                media={row.mobileMedia}
                busy={upload.isPending}
                onUpload={(file) => upload.mutate({ kind: "MOBILE", file })}
              />
            </div>
          </section>
        </>
      ) : null}

      {(creating || editing) ? (
        <ReferenceEditor
          form={form}
          setForm={setForm}
          message={message}
          saving={save.isPending}
          onSave={() => save.mutate()}
          onCancel={creating ? () => navigate("/admin/design-references") : () => {
            if (row) setForm(toForm(row));
            setEditing(false);
            setMessage("");
          }}
          saveLabel={creating ? "Create reference" : "Save changes"}
        />
      ) : null}

      {!creating && row && !editing ? (
        <>
          <section className="design-ref-panel">
            <h2>Section patterns</h2>
            {sections.length === 0 ? (
              <Empty>No section patterns.</Empty>
            ) : (
              <ul className="design-ref-sections">
                {sections.map((section) => (
                  <li key={String(section.id ?? section.sectionKey)}>
                    <strong>{section.sectionKey}</strong>
                    <p>{section.purpose} · {section.composition}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <PatternVisual
            patternJson={row.patternJson}
            colourPalette={row.colourPalette}
            typography={row.typography}
            layout={row.layout}
          />
          {row.embeddingModel ? (
            <p className="studio-hint" style={{ marginTop: 12 }}>
              Embedded with {row.embeddingModel}
              {row.embeddingUpdatedAt ? ` · ${row.embeddingUpdatedAt}` : ""}
            </p>
          ) : (
            <p className="studio-hint" style={{ marginTop: 12 }}>No embedding yet. Use Embed after enabling embeddings.</p>
          )}
        </>
      ) : null}

      {message && !editing && !creating ? <p className="error-line">{message}</p> : null}
    </>
  );
}
