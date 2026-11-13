import { useState } from "react";
import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { InviteCard } from "../components/invite/InviteCard";
import { Button, Empty, Field, Pill } from "../components/ui";
import { api, errorMessage } from "../services/api";
import type { Envelope, EventSite } from "../types";
import { formatPaise } from "../utils/money";

type MediaFile = { id: string; mediaType: string; url: string; originalFilename: string; eventId: string; eventName: string };
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
  const chosen = eventId || sites[0]?.id || "";
  const files: MediaFile[] = sites.flatMap((site, index) => (media[index]?.data ?? []).map((file) => ({ ...file, eventId: site.id, eventName: site.eventName })));
  const mediaLoading = events.isLoading || media.some((query) => query.isLoading);

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
        <form className="stack" style={{ maxWidth: 520 }} onSubmit={async (formEvent) => {
          formEvent.preventDefault();
          const input = formEvent.currentTarget.elements.namedItem("file") as HTMLInputElement;
          const file = input.files?.[0];
          if (!file || !chosen) return;
          const body = new FormData();
          body.append("file", file);
          setBusy(true);
          setMessage("");
          try {
            await api.post(`/events/${chosen}/media`, body);
            await queryClient.invalidateQueries({ queryKey: ["media", chosen] });
            input.value = "";
            setMessage("Uploaded.");
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
          <Field label="Image or video" hint="JPEG, PNG, GIF, WebP, MP4 or WebM.">
            <input name="file" type="file" accept="image/*,video/mp4,video/webm" disabled={sites.length === 0} />
          </Field>
          <Button type="submit" tone="secondary" disabled={busy || sites.length === 0}>{busy ? "Uploading" : "Upload"}</Button>
          {message ? <p className={message === "Uploaded." ? "studio-hint" : "error-line"}>{message}</p> : null}
        </form>
        {mediaLoading ? <div className="skeleton" style={{ height: 160 }} /> : files.length === 0 ? (
          <Empty>No pictures yet. Upload one into a site, or add it from the editor.</Empty>
        ) : (
          <div className="library-grid">
            {files.map((file) => (
              <article key={`${file.eventId}-${file.id}`} className="library-tile">
                {file.mediaType === "IMAGE" ? <img src={file.url} alt="" /> : <p className="media-name">{file.mediaType}</p>}
                <strong>{file.originalFilename || "Untitled file"}</strong>
                <Link to={`/app/events/${file.eventId}`}>{file.eventName}</Link>
              </article>
            ))}
          </div>
        )}
      </div>
    </>
  );
}

export function DomainsPage() {
  const events = useQuery({
    queryKey: ["events"],
    queryFn: async () => (await api.get<Envelope<EventSite[]>>("/events")).data.data,
  });
  const rows = events.data ?? [];
  return (
    <>
      <div className="sheet-title">
        <div>
          <h1>Domains and hosting</h1>
          <p>Each site has one public address and a hosting run of ₹500 a day. Payment opens after a reviewer approves the draft.</p>
        </div>
      </div>
      <div className="work">
        {events.isError ? <p className="error-line">{errorMessage(events.error, "The sites could not be loaded.")}</p> : null}
        {events.isLoading ? <div className="skeleton" style={{ height: 120 }} /> : rows.length === 0 ? (
          <Empty>A site shows its address here once you create it. <Link to="/app/new">New event site</Link></Empty>
        ) : rows.map((event) => (
          <section key={event.id} className="panel domain-row">
            <div>
              <strong>{event.eventName}</strong>
              <div className="id">/{event.slug}</div>
              <p>{event.hosting.daysRemaining} hosting days left · {event.requestedDays} requested</p>
              <div className="title-actions">
                <Pill status={event.status} />
                <Link to={`/app/events/${event.id}`}>Open</Link>
                {event.status === "APPROVED" ? <Link to={`/app/events/${event.id}/pay`}>Pay and publish</Link> : null}
                {event.status === "PUBLISHED" || event.status === "EXPIRED" ? <Link to={`/app/events/${event.id}/extend`}>Extend hosting</Link> : null}
                {event.status === "PUBLISHED" ? <a href={event.publicUrl || `/${event.slug}`}>Public site</a> : null}
              </div>
            </div>
            <InviteCard name={event.eventName} slug={event.slug} when={event.configuration.event.startAt} venue={event.configuration.event.venueName} />
          </section>
        ))}
      </div>
    </>
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
          <p>Create a website walks through questions, then the event name, public address, and hosting days. New event site starts from a template instead. Hosting is ₹500 a day.</p>
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
          <p>The studio saves a version of the page. Push version writes that version immediately. The invite card and QR code use the public address, so a guest can open the site from a printed badge.</p>
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
