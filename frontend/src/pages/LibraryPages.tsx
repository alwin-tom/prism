import { useEffect, useId, useRef, useState } from "react";
import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Clock3, Eye, Server, X } from "lucide-react";
import { InviteCard } from "../components/invite/InviteCard";
import { Button, Empty, Field, Pill } from "../components/ui";
import { api, errorMessage } from "../services/api";
import { uploadEventMediaMany } from "../services/mediaUpload";
import type { Envelope, EventSite } from "../types";
import { formatPaise } from "../utils/money";

type MediaFile = { id: string; mediaType: string; url: string; originalFilename: string; eventId: string; eventName: string };
type PendingFile = { key: string; file: File; previewUrl: string };
type PreviewTarget = { kind: "image" | "video"; src: string; name: string };
type PaymentRow = {
  id: string;
  eventId: string;
  eventName: string;
  paymentType: string;
  amountPaise: number;
  days: number;
  status: string;
  createdAt: string;
};

export function MediaLibraryPage() {
  const queryClient = useQueryClient();
  const events = useQuery({
    queryKey: ["events"],
    queryFn: async () => (await api.get<Envelope<EventSite[]>>("/events")).data.data,
  });
  const sites = events.data ?? [];
  const media = useQueries({
    queries: sites.map((site) => ({
      queryKey: ["media", site.id],
      queryFn: async () => (await api.get<Envelope<Omit<MediaFile, "eventId" | "eventName">[]>>(`/events/${site.id}/media`)).data.data,
    })),
  });
  const [eventId, setEventId] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<PendingFile[]>([]);
  const [preview, setPreview] = useState<PreviewTarget | null>(null);
  const pendingRef = useRef(pending);
  pendingRef.current = pending;
  const chosen = eventId || sites[0]?.id || "";
  const files: MediaFile[] = sites.flatMap((site, index) => (media[index]?.data ?? []).map((file) => ({ ...file, eventId: site.id, eventName: site.eventName })));
  const mediaLoading = events.isLoading || media.some((query) => query.isLoading);

  useEffect(() => () => {
    pendingRef.current.forEach((item) => URL.revokeObjectURL(item.previewUrl));
  }, []);

  function clearPending() {
    setPending((current) => {
      current.forEach((item) => URL.revokeObjectURL(item.previewUrl));
      return [];
    });
    setPreview((current) => (current && current.src.startsWith("blob:") ? null : current));
  }

  function addPending(selected: File[]) {
    if (selected.length === 0) return;
    setPending((current) => [
      ...current,
      ...selected.map((file) => ({
        key: `${file.name}-${file.size}-${file.lastModified}-${crypto.randomUUID()}`,
        file,
        previewUrl: URL.createObjectURL(file),
      })),
    ]);
    setMessage("");
  }

  function removePending(key: string) {
    setPending((current) => {
      const removed = current.find((item) => item.key === key);
      if (removed) {
        URL.revokeObjectURL(removed.previewUrl);
        setPreview((open) => (open?.src === removed.previewUrl ? null : open));
      }
      return current.filter((item) => item.key !== key);
    });
  }

  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Media library</h1>
          <p>Pictures and video already uploaded to your sites. A file stays with the site it was added to.</p>
        </div>
      </div>
      <div className="work">
        {events.isError ? <p className="error-line">{errorMessage(events.error, "The sites could not be loaded.")}</p> : null}
        <form className="stack media-upload" style={{ maxWidth: 640 }} onSubmit={async (formEvent) => {
          formEvent.preventDefault();
          if (pending.length === 0 || !chosen) return;
          setBusy(true);
          setMessage("");
          try {
            const uploaded = await uploadEventMediaMany(chosen, pending.map((item) => item.file));
            await queryClient.invalidateQueries({ queryKey: ["media", chosen] });
            clearPending();
            setMessage(uploaded.length === 1 ? "Uploaded." : `Uploaded ${uploaded.length} files.`);
          } catch (error) {
            setMessage(errorMessage(error, "The file was not saved."));
          } finally {
            setBusy(false);
          }
        }}>
          <Field label="Add to">
            <select value={chosen} onChange={(item) => setEventId(item.target.value)} disabled={sites.length === 0}>
              {sites.map((site) => <option key={site.id} value={site.id}>{site.eventName}</option>)}
            </select>
          </Field>
          <Field label="Images or video" hint="Select one or many. Preview before uploading. JPEG, PNG, GIF, WebP, MP4 or WebM.">
            <input
              name="file"
              type="file"
              accept="image/*,video/mp4,video/webm"
              multiple
              disabled={sites.length === 0 || busy}
              onChange={(event) => {
                addPending([...(event.target.files ?? [])]);
                event.target.value = "";
              }}
            />
          </Field>
          {pending.length > 0 ? (
            <div className="media-pending">
              <div className="media-pending-head">
                <p className="studio-hint">{pending.length === 1 ? "1 file ready to upload" : `${pending.length} files ready to upload`}</p>
                <Button type="button" tone="text" disabled={busy} onClick={clearPending}>Clear</Button>
              </div>
              <div className="upload-previews">
                {pending.map((item) => {
                  const isVideo = item.file.type.startsWith("video/");
                  return (
                    <figure key={item.key}>
                      <button
                        type="button"
                        className="media-thumb-btn"
                        aria-label={`Preview ${item.file.name}`}
                        onClick={() => setPreview({ kind: isVideo ? "video" : "image", src: item.previewUrl, name: item.file.name })}
                      >
                        {isVideo ? (
                          <video src={item.previewUrl} muted playsInline preload="metadata" />
                        ) : (
                          <img src={item.previewUrl} alt="" />
                        )}
                        <span className="media-thumb-eye" aria-hidden><Eye size={14} /></span>
                      </button>
                      <figcaption title={item.file.name}>{item.file.name}</figcaption>
                      <button type="button" aria-label={`Remove ${item.file.name}`} disabled={busy} onClick={() => removePending(item.key)}>
                        <X size={14} />
                      </button>
                    </figure>
                  );
                })}
              </div>
            </div>
          ) : null}
          <Button type="submit" tone="secondary" disabled={busy || sites.length === 0 || pending.length === 0}>
            {busy ? "Uploading" : pending.length > 1 ? `Upload ${pending.length} files` : "Upload"}
          </Button>
          {message ? <p className={message.startsWith("Uploaded") ? "studio-hint" : "error-line"}>{message}</p> : null}
        </form>
        {mediaLoading ? <div className="skeleton" style={{ height: 160 }} /> : files.length === 0 ? (
          <Empty>No pictures yet. Upload one into a site, or add it from the editor.</Empty>
        ) : (
          <div className="library-grid">
            {files.map((file) => {
              const isImage = file.mediaType === "IMAGE";
              const isVideo = file.mediaType === "VIDEO";
              return (
                <article key={`${file.eventId}-${file.id}`} className="library-tile">
                  {isImage || isVideo ? (
                    <button
                      type="button"
                      className="media-thumb-btn library-thumb"
                      aria-label={`Preview ${file.originalFilename || "file"}`}
                      onClick={() => setPreview({ kind: isVideo ? "video" : "image", src: file.url, name: file.originalFilename || "Untitled file" })}
                    >
                      {isImage ? <img src={file.url} alt="" /> : <video src={file.url} muted playsInline preload="metadata" />}
                      <span className="media-thumb-eye" aria-hidden><Eye size={14} /></span>
                    </button>
                  ) : (
                    <p className="media-name">{file.mediaType}</p>
                  )}
                  <strong>{file.originalFilename || "Untitled file"}</strong>
                  <Link to={`/app/events/${file.eventId}`}>{file.eventName}</Link>
                </article>
              );
            })}
          </div>
        )}
      </div>
      {preview ? <MediaPreviewDialog target={preview} onClose={() => setPreview(null)} /> : null}
    </>
  );
}

function MediaPreviewDialog({ target, onClose }: { target: PreviewTarget; onClose: () => void }) {
  const titleId = useId();
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="media-lightbox" role="dialog" aria-modal="true" aria-labelledby={titleId} onClick={onClose}>
      <div className="media-lightbox-panel" onClick={(event) => event.stopPropagation()}>
        <div className="media-lightbox-head">
          <h2 id={titleId}>{target.name}</h2>
          <button type="button" className="media-lightbox-close" aria-label="Close preview" onClick={onClose}>
            <X size={18} />
          </button>
        </div>
        <div className="media-lightbox-body">
          {target.kind === "video" ? (
            <video src={target.src} controls autoPlay playsInline />
          ) : (
            <img src={target.src} alt={target.name} />
          )}
        </div>
      </div>
    </div>
  );
}

export function DomainsPage() {
  const events = useQuery({
    queryKey: ["events"],
    queryFn: async () => (await api.get<Envelope<EventSite[]>>("/events")).data.data,
  });
  const rows = events.data ?? [];
  return (
    <div className="domains-page">
      <header className="domains-hero">
        <div className="domains-hero-copy">
          <p className="domains-eyebrow"><Server size={16} aria-hidden /> WebAux infrastructure</p>
          <h1>Domains and hosting</h1>
          <p>Each site has one public address and a hosting run of ₹500 a day. Payment opens after a reviewer approves the draft.</p>
        </div>
        <div className="domains-stat" aria-live="polite">
          <span className="domains-stat-dot" aria-hidden />
          <span>{rows.length === 1 ? "1 site tracked" : `${rows.length} sites tracked`}</span>
        </div>
      </header>

      {events.isError ? <p className="error-line">{errorMessage(events.error, "The sites could not be loaded.")}</p> : null}
      {events.isLoading ? <div className="skeleton" style={{ height: 160 }} /> : rows.length === 0 ? (
        <Empty>A site shows its address here once you create it. <Link to="/app/create">Create a Website</Link></Empty>
      ) : (
        <div className="domains-grid">
          {rows.map((event) => (
            <section key={event.id} className="domains-card">
              <div className="domains-card-main">
                <div className="domains-card-title">
                  <h2>{event.eventName}</h2>
                  <Pill status={event.status} />
                  <Link className="domains-open" to={`/app/events/${event.id}`}>Open</Link>
                </div>
                <p className="domains-slug">/{event.slug}</p>
                <p className="domains-hosting">
                  <Clock3 size={15} aria-hidden />
                  <span>{event.hosting.daysRemaining} hosting days left · {event.requestedDays} requested</span>
                </p>
                <div className="domains-actions">
                  {event.status === "APPROVED" ? <Link to={`/app/events/${event.id}/pay`}>Pay and publish</Link> : null}
                  {event.status === "PUBLISHED" || event.status === "EXPIRED" ? <Link to={`/app/events/${event.id}/extend`}>Extend hosting</Link> : null}
                  {event.status === "PUBLISHED" ? <a href={event.publicUrl || `/${event.slug}`}>Public site</a> : null}
                </div>
              </div>
              <InviteCard
                compact
                name={event.eventName}
                slug={event.slug}
                when={event.configuration.event.startAt}
                venue={event.configuration.event.venueName}
              />
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

export function BillingPage() {
  const payments = useQuery({
    queryKey: ["payments"],
    queryFn: async () => (await api.get<Envelope<PaymentRow[]>>("/payments")).data.data,
  });
  const rows = payments.data ?? [];
  const paid = rows.filter((row) => row.status === "PAID").reduce((sum, row) => sum + row.amountPaise, 0);
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Billing</h1>
          <p>Hosting is ₹500 a day. These are the payments already started for your sites.</p>
        </div>
      </div>
      <div className="work">
        <div className="kpis">
          <div className="kpi"><span>Paid</span><strong className="num">{formatPaise(paid)}</strong><em>Recorded as paid</em></div>
          <div className="kpi"><span>Payments</span><strong className="num">{rows.length}</strong><em>Including ones still pending</em></div>
        </div>
        {payments.isError ? <p className="error-line">{errorMessage(payments.error, "Payments could not be loaded.")}</p> : null}
        {payments.isLoading ? <div className="skeleton" style={{ height: 80 }} /> : rows.length === 0 ? (
          <Empty>Nothing has been paid yet. Payment opens after a site is approved.</Empty>
        ) : (
          <section className="list">
            {rows.map((row) => (
              <div key={row.id} className="site-row">
                <div>
                  <strong>{row.eventName || "Event site"}</strong>
                  <div className="id">{paymentKind(row.paymentType)} · {row.days} days</div>
                </div>
                <span>{paymentLabel(row.status)}</span>
                <span className="num">{formatPaise(row.amountPaise)}</span>
                <span className="num">{new Date(row.createdAt).toLocaleDateString("en-IN")}</span>
                <Link to={`/app/events/${row.eventId}`}>Site</Link>
              </div>
            ))}
          </section>
        )}
      </div>
    </>
  );
}

export function HelpPage() {
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Help</h1>
          <p>How a site goes from a draft to a public address.</p>
        </div>
      </div>
      <div className="work help-copy">
        <section className="panel">
          <h2>Create</h2>
          <p>Create a website walks through questions, then the event name, public address, and hosting days. Hosting is ₹500 a day.</p>
        </section>
        <section className="panel">
          <h2>Review</h2>
          <p>Submit the draft when the page and the address are ready. A reviewer approves it or sends it back with a note. The draft stays locked while it is with them.</p>
        </section>
        <section className="panel">
          <h2>Pay and publish</h2>
          <p>After approval, pay for the hosting days. The site then goes live at /your-address. Extend hosting before the days run out.</p>
        </section>
        <section className="panel">
          <h2>Studio and invites</h2>
          <p>The visual editor saves the page. Push version writes a restore point. Submit for review sends the draft to a reviewer. The invite card and QR code use the public address.</p>
        </section>
      </div>
    </>
  );
}

function paymentKind(type: string) {
  if (type === "HOSTING_EXTENSION") return "Extension";
  return "Hosting";
}

function paymentLabel(status: string) {
  if (status === "PAID") return "Paid";
  if (status === "FAILED") return "Failed";
  return "Pending";
}
