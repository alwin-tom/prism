import { Fragment, useEffect, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type ReactNode } from "react";
import { AlignCenter, AlignLeft, AlignRight, GripVertical, Minus, Plus, X } from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { api, errorMessage } from "../../services/api";
import type { CanvasElement, PublicEvent, Section, SiteConfiguration } from "../../types";
import { CanvasBoard } from "./CanvasBoard";
import { TEXT_FONTS } from "./fonts";
import { FontRequest } from "../../features/typography/fonts";
import { VisualPage } from "../../features/visual-editor/VisualPage";

export type SiteEdit = (
  | { target: "event"; key: "name" | "subtitle" | "description" | "venueName" | "venueAddress"; value: string }
  | { target: "section"; id: string; key: string; value: string }
  | { target: "align"; id: string; key: string; value: string }
  | { target: "box"; id: string; x: string; y: string; w: string; h: string }
  | { target: "frame"; id: string; x: string; w: string; h: string; order?: string }
  | { target: "detach"; id: string; mediaId: string }
  | { target: "item"; id: string; index: number; key: "time" | "title" | "detail"; value: string }
  | { target: "canvas-element"; id: string; patch: Record<string, string | number | boolean> }
  | { target: "canvas-elements"; patches: { id: string; patch: Record<string, string | number | boolean> }[] }
  | { target: "canvas-order"; id: string; direction: "front" | "back" }
  | { target: "canvas-add"; element: CanvasElement }
  | { target: "canvas-remove"; id: string; ids?: string[] }
) & { history?: "coalesce" };

export function PublicSite({ event, preview = false, mediaEventId, onEdit }: { event: PublicEvent; preview?: boolean; mediaEventId?: string; onEdit?: (edit: SiteEdit) => void }) {
  const configuration = event.configuration;
  if (event.availability !== "PUBLISHED" || !configuration) {
    const title = event.availability === "SUSPENDED" ? `${event.eventName} is paused` : `${event.eventName} has ended`;
    const copy = event.availability === "SUSPENDED"
      ? "WebAux has paused this site. The page will return if the owner publishes it again."
      : "The hosting period for this site has ended. The invitation is no longer public.";
    return (
      <main className="event-status">
        <div>
          <h1>{title}</h1>
          <p style={{ marginTop: 12, maxWidth: 460 }}>{copy}</p>
        </div>
      </main>
    );
  }
  const theme = configuration.theme;
  const themeStyle = {
    ["--event-bg" as string]: theme.background,
    ["--event-ink" as string]: theme.ink,
    ["--event-accent" as string]: theme.accent,
    ["--event-font" as string]: TEXT_FONTS[theme.font] ?? TEXT_FONTS["Public Sans"],
    ["--event-display" as string]: TEXT_FONTS[theme.displayFont] ?? TEXT_FONTS.Fraunces,
  };
  if (event.editorDocument?.root) {
    return (
      <main className="event-site" style={themeStyle}>
        <FontRequest owner="theme" families={[theme.font, theme.displayFont]} />
        <VisualPage
          document={event.editorDocument}
          followViewport
          mediaEventId={preview ? mediaEventId : undefined}
          countdownFallback={configuration.event.startAt}
          rsvp={{
            slug: event.slug,
            preview,
            enabled: configuration.rsvp.enabled,
            collectPhone: configuration.rsvp.collectPhone,
            collectEmail: configuration.rsvp.collectEmail,
            maxGuests: configuration.rsvp.maxGuestsPerResponse,
            confirmationMessage: configuration.rsvp.confirmationMessage,
          }}
        />
      </main>
    );
  }
  if (configuration.canvas) {
    return (
      <main className="event-site" style={themeStyle}>
        <FontRequest owner="theme" families={[theme.font, theme.displayFont]} />
        <CanvasBoard event={event} preview={preview} mediaEventId={mediaEventId} onEdit={onEdit} />
      </main>
    );
  }
  return (
    <main
      className="event-site"
      style={themeStyle}
    >
      <FontRequest owner="theme" families={[theme.font, theme.displayFont]} />
      <div className="event-wrap">
        {configuration.sections.map((section, index) => (
          <Fragment key={section.id}>
            {picturesAt(configuration.sections, mediaEventId, index).map((picture) => (
              <FreeImage key={`${picture.id}-picture`} picture={picture} onEdit={onEdit} />
            ))}
            {hasBlock(section) ? (
              <BlockFrame section={section} onEdit={onEdit}>
                <SectionView section={section} configuration={configuration} slug={event.slug} preview={preview} mediaEventId={mediaEventId} onEdit={onEdit} />
              </BlockFrame>
            ) : null}
          </Fragment>
        ))}
        {picturesAt(configuration.sections, mediaEventId, configuration.sections.length).map((picture) => (
          <FreeImage key={`${picture.id}-picture`} picture={picture} onEdit={onEdit} />
        ))}
      </div>
      <p className="prism-credit">Made with WebAux</p>
    </main>
  );
}

function SectionView({ section, configuration, slug, preview, mediaEventId, onEdit }: { section: Section; configuration: SiteConfiguration; slug: string; preview: boolean; mediaEventId?: string; onEdit?: (edit: SiteEdit) => void }) {
  const properties = section.properties;
  const text = (key: string) => String(properties[key] ?? "");
  const sectionEdit = (key: string) => (onEdit ? (value: string) => onEdit({ target: "section", id: section.id, key, value }) : undefined);
  const at = (field: string, fallback: "left" | "center" = "left") => readAlign(properties, field, fallback);
  const hold = (field: string, node: ReactNode, fallback: "left" | "center" = "left") => (
    <Hold field={field} value={at(field, fallback)} onChange={onEdit ? (value) => onEdit({ target: "align", id: section.id, key: field, value }) : undefined}>{node}</Hold>
  );
  const eventEdit = (key: "name" | "subtitle" | "description" | "venueName" | "venueAddress") =>
    (onEdit ? (value: string) => onEdit({ target: "event", key, value }) : undefined);
  switch (section.type) {
    case "HERO":
      return (
        <header className="event-hero">
          {hold("title", <h1 aria-label={onEdit ? text("title") || configuration.event.name || "Title" : undefined}><Copy value={onEdit ? text("title") : text("title") || configuration.event.name} label="Title" maxLength={80} placeholder={configuration.event.name} onChange={sectionEdit("title")} /></h1>)}
          {hold("subtitle", <p><Copy value={onEdit ? text("subtitle") : text("subtitle") || configuration.event.subtitle} label="Line under the title" maxLength={160} placeholder={configuration.event.subtitle} onChange={sectionEdit("subtitle")} /></p>)}
        </header>
      );
    case "COUNTDOWN":
      return (
        <section className="event-section">
          {hold("label", <h2><Copy value={text("label")} label="Countdown label" maxLength={80} placeholder="Until the day" onChange={sectionEdit("label")} /></h2>)}
          {hold("count", <Countdown iso={configuration.event.startAt} />)}
        </section>
      );
    case "TEXT_BLOCK":
      return (
        <section className="event-section">
          {hold("heading", <h2><Copy value={text("heading")} label="Heading" maxLength={120} onChange={sectionEdit("heading")} /></h2>)}
          {hold("body", <p><Copy value={text("body")} label="Text" maxLength={2000} multiline onChange={sectionEdit("body")} /></p>)}
        </section>
      );
    case "EVENT_DETAILS":
      return (
        <section className="event-section">
          {hold("heading", <h2><Copy value={text("heading")} label="Details heading" maxLength={120} placeholder="Details" onChange={sectionEdit("heading")} /></h2>)}
          {hold("when", <p>{formatWhen(configuration.event.startAt, configuration.event.timezone)}</p>)}
          {hold("venue", <p><Copy value={configuration.event.venueName} label="Venue" maxLength={160} onChange={eventEdit("venueName")} /></p>)}
          {hold("address", <p><Copy value={configuration.event.venueAddress} label="Address" maxLength={300} multiline onChange={eventEdit("venueAddress")} /></p>)}
          {onEdit || configuration.event.description ? hold("description", <p><Copy value={configuration.event.description} label="Description" maxLength={2000} multiline onChange={eventEdit("description")} /></p>) : null}
        </section>
      );
    case "EVENT_SCHEDULE":
      return (
        <section className="event-section">
          {hold("heading", <h2><Copy value={text("heading")} label="Plan heading" maxLength={120} placeholder="Schedule" onChange={sectionEdit("heading")} /></h2>)}
          <ul className="event-schedule">
            {items(properties.items).map((item, index) => (
              <li key={`${section.id}-${index}`} className={`aligned align-${at(`item-${index}`)}`}>
                {onEdit ? <AlignBar value={at(`item-${index}`)} onChange={(value) => onEdit({ target: "align", id: section.id, key: `item-${index}`, value })} /> : null}
                <strong className="num">
                  <Copy value={item.time} label={`Time ${index + 1}`} maxLength={20} onChange={onEdit ? (value) => onEdit({ target: "item", id: section.id, index, key: "time", value }) : undefined} />
                </strong>
                <span>
                  {onEdit ? (
                    <>
                      <Copy value={item.title} label={`Plan item ${index + 1}`} maxLength={120} onChange={(value) => onEdit({ target: "item", id: section.id, index, key: "title", value })} />
                      <Copy value={item.detail} label={`Plan detail ${index + 1}`} maxLength={300} onChange={(value) => onEdit({ target: "item", id: section.id, index, key: "detail", value })} />
                    </>
                  ) : (
                    <>
                      {item.title}
                      {item.detail ? ` — ${item.detail}` : ""}
                    </>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </section>
      );
    case "IMAGE":
      if (!text("caption")) return null;
      return (
        <section className="event-section">
          {hold("caption", <p><Copy value={text("caption")} label="Caption" maxLength={180} onChange={sectionEdit("caption")} /></p>)}
        </section>
      );
    case "IMAGE_GALLERY": {
      const pictures = strings(properties.mediaIds).map((raw) => ({ raw, src: mediaSrc(raw, mediaEventId) })).filter((item) => item.src);
      return (
        <section className="event-section">
          {hold("caption", <h2><Copy value={text("caption")} label="Gallery caption" maxLength={180} placeholder="Pictures" onChange={sectionEdit("caption")} /></h2>)}
          {pictures.length > 0 ? hold("pictures", (
            <div className="event-gallery">
              {pictures.map((item) => (
                <figure key={item.raw} className="gallery-shot">
                  <img src={item.src} alt="" />
                  {onEdit ? <button type="button" aria-label="Remove from the page" onClick={() => onEdit({ target: "detach", id: section.id, mediaId: item.raw })}><X size={14} /></button> : null}
                </figure>
              ))}
            </div>
          ), "center") : null}
        </section>
      );
    }
    case "VIDEO": {
      const video = mediaSrc(text("mediaId"), mediaEventId);
      return video ? (
        <section className="event-section">
          {hold("video", <video className={`placed img-${at("video", "center")}`} src={video} controls playsInline />, "center")}
          {onEdit || text("caption") ? hold("caption", <p><Copy value={text("caption")} label="Video caption" maxLength={180} onChange={sectionEdit("caption")} /></p>) : null}
        </section>
      ) : null;
    }
    case "LOCATION_MAP":
      return (
        <section className="event-section">
          {hold("heading", <h2><Copy value={text("heading")} label="Place heading" maxLength={120} placeholder="Place" onChange={sectionEdit("heading")} /></h2>)}
          {hold("venue", <p><Copy value={configuration.event.venueName} label="Venue" maxLength={160} onChange={eventEdit("venueName")} /></p>)}
          {hold("address", <p><Copy value={configuration.event.venueAddress} label="Address" maxLength={300} multiline onChange={eventEdit("venueAddress")} /></p>)}
          {hold("note", <p><Copy value={text("note")} label="Place note" maxLength={300} multiline onChange={sectionEdit("note")} /></p>)}
          {configuration.event.mapUrl ? hold("map", <p><a href={configuration.event.mapUrl}>Open the map</a></p>) : null}
        </section>
      );
    case "RSVP_FORM":
      if (!configuration.rsvp.enabled) return null;
      return <RsvpBlock heading={text("heading")} headingAlign={at("heading")} formAlign={at("form")} slug={slug} configuration={configuration} preview={preview} onChange={sectionEdit("heading")} onAlign={onEdit ? (key, value) => onEdit({ target: "align", id: section.id, key, value }) : undefined} />;
    case "FOOTER":
      return (
        <footer className="event-footer">
          {hold("note", <p><Copy value={onEdit ? text("note") : text("note") || configuration.event.name} label="Closing line" maxLength={300} placeholder={configuration.event.name} onChange={sectionEdit("note")} /></p>)}
        </footer>
      );
    default:
      return null;
  }
}

function Countdown({ iso }: { iso: string }) {
  const target = Date.parse(iso);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  if (Number.isNaN(target)) return <p>The date is not set yet.</p>;
  const diff = Math.max(0, target - now);
  const days = Math.floor(diff / 86_400_000);
  const hours = Math.floor((diff % 86_400_000) / 3_600_000);
  const minutes = Math.floor((diff % 3_600_000) / 60_000);
  return (
    <div className="event-count" aria-live="polite">
      <div><strong className="num">{days}</strong><span>days</span></div>
      <div><strong className="num">{hours}</strong><span>hours</span></div>
      <div><strong className="num">{minutes}</strong><span>minutes</span></div>
    </div>
  );
}

function Hold({ field, value, onChange, children }: { field: string; value: string; onChange?: (value: string) => void; children: ReactNode }) {
  return (
    <div className={`aligned align-${value}`} data-field={field}>
      {onChange ? <AlignBar value={value} onChange={onChange} /> : null}
      {children}
    </div>
  );
}

function AlignBar({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const options = [
    { side: "left", label: "Align left", icon: AlignLeft },
    { side: "center", label: "Align middle", icon: AlignCenter },
    { side: "right", label: "Align right", icon: AlignRight },
  ] as const;
  return (
    <div className="align-bar" role="group" aria-label="Alignment">
      {options.map((option) => (
        <button key={option.side} type="button" aria-label={option.label} aria-pressed={value === option.side} onClick={() => onChange(option.side)}>
          <option.icon size={14} strokeWidth={1.75} />
        </button>
      ))}
    </div>
  );
}

function BlockFrame({ section, onEdit, children }: { section: Section; onEdit?: (edit: SiteEdit) => void; children: ReactNode }) {
  const frame = {
    x: measure(section.properties.frameX, 0),
    w: measure(section.properties.frameW, 100),
    h: measure(section.properties.frameH, 0),
    size: measure(section.properties.fontSize, 0),
  };
  const fitted = frame.w < 100 || frame.h > 0;
  return (
    <div
      className={`event-block${fitted ? " is-fitted" : ""}${frame.h > 0 ? " is-sized" : ""}${frame.size > 0 ? " has-size" : ""}`}
      data-section={section.id}
      style={columnStyle(frame.x, frame.w, frame.h, frame.size)}
    >
      {onEdit ? <BlockTools id={section.id} frame={frame} onEdit={onEdit} /> : null}
      {children}
    </div>
  );
}

function BlockTools({ id, frame, onEdit }: { id: string; frame: { x: number; w: number; h: number; size: number }; onEdit: (edit: SiteEdit) => void }) {
  const typeSize = (next: number) => onEdit({ target: "section", id, key: "fontSize", value: String(next) });
  return (
    <>
      <div className="block-tools">
        <button
          type="button"
          className="block-move"
          aria-label="Move block"
          onPointerDown={(event) => {
            const origin = frame;
            const startX = event.clientX;
            const wrap = event.currentTarget.closest(".event-wrap");
            const startOrder = wrap ? [...wrap.querySelectorAll(":scope > .event-block")].map((el) => el.getAttribute("data-section") ?? "") : [];
            watchPointer(event, (point, page) => {
              const width = page.getBoundingClientRect().width || 1;
              const x = bound(origin.x + ((point.clientX - startX) / width) * 100, 0, Math.max(0, 100 - origin.w));
              const others = startOrder.filter((item) => item && item !== id);
              let insertAt = others.length;
              for (let index = 0; index < others.length; index += 1) {
                const el = page.querySelector(`[data-section="${CSS.escape(others[index])}"]`);
                if (!(el instanceof HTMLElement)) continue;
                const rect = el.getBoundingClientRect();
                if (point.clientY < rect.top + rect.height / 2) {
                  insertAt = index;
                  break;
                }
              }
              const order = [...others];
              order.splice(insertAt, 0, id);
              onEdit({ target: "frame", id, x: String(x), w: String(origin.w), h: String(origin.h), order: order.join(",") });
            });
          }}
        >
          <GripVertical size={14} strokeWidth={1.75} />
        </button>
        <button type="button" aria-label="Smaller text" onClick={() => typeSize(Math.max(12, (frame.size || 18) - 2))}><Minus size={14} strokeWidth={1.75} /></button>
        <button type="button" aria-label="Larger text" onClick={() => typeSize(Math.min(72, (frame.size || 18) + 2))}><Plus size={14} strokeWidth={1.75} /></button>
      </div>
      <button
        type="button"
        className="block-resize"
        aria-label="Resize block"
        onPointerDown={(event) => {
          const origin = frame;
          const startX = event.clientX;
          const startY = event.clientY;
          const block = event.currentTarget.closest(".event-block");
          watchPointer(event, (point, page) => {
            const width = page.getBoundingClientRect().width || 1;
            const measured = block instanceof HTMLElement ? Math.round((block.offsetHeight / width) * 100) : 24;
            onEdit({
              target: "frame",
              id,
              x: String(origin.x),
              w: String(bound(origin.w + ((point.clientX - startX) / width) * 100, 16, Math.max(16, 100 - origin.x))),
              h: String(bound((origin.h > 0 ? origin.h : measured) + ((point.clientY - startY) / width) * 100, 8, 80)),
            });
          });
        }}
      />
    </>
  );
}

function watchPointer(event: ReactPointerEvent, apply: (point: globalThis.PointerEvent, frame: HTMLElement) => void) {
  const frame = event.currentTarget.closest(".event-wrap");
  if (!(frame instanceof HTMLElement)) return;
  event.preventDefault();
  event.stopPropagation();
  const move = (ev: Event) => apply(ev as globalThis.PointerEvent, frame);
  const up = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", up);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", up);
}

function columnStyle(x: number, w: number, h: number, size: number): CSSProperties {
  const span = Math.min(12, Math.max(2, Math.round((w / 100) * 12)));
  const start = Math.min(13 - span, Math.max(1, Math.round((x / 100) * 12) + 1));
  return {
    gridColumn: `${start} / span ${span}`,
    ["--block-h" as string]: h > 0 ? String(h) : undefined,
    fontSize: size > 0 ? `${size}px` : undefined,
  };
}

function FreeImage({ picture, onEdit }: { picture: PlacedPicture; onEdit?: (edit: SiteEdit) => void }) {
  const box = picture.box;
  const onBox = onEdit
    ? (next: Box) => onEdit({ target: "box", id: picture.id, x: String(next.x), y: String(next.y), w: String(next.w), h: String(next.h) })
    : undefined;
  function follow(event: ReactPointerEvent, apply: (point: globalThis.PointerEvent, frame: HTMLElement) => void) {
    const frame = event.currentTarget.closest(".event-wrap");
    if (!(frame instanceof HTMLElement)) return;
    event.preventDefault();
    event.stopPropagation();
    const move = (ev: Event) => apply(ev as globalThis.PointerEvent, frame);
    const up = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
  }
  return (
    <div
      className={`flow-image${onBox ? " can-move" : ""}`}
      style={{ width: `${box.w}%`, marginLeft: `${box.x}%`, aspectRatio: `${Math.max(box.w, 1)} / ${Math.max(box.h, 1)}` }}
      onPointerDown={onBox ? (event) => {
        if ((event.target as HTMLElement).closest("button")) return;
        const origin = box;
        const startX = event.clientX;
        follow(event, (point, frame) => {
          const width = frame.getBoundingClientRect().width || 1;
          const sections = [...frame.querySelectorAll(":scope > .event-block")];
          let slot = 0;
          for (const el of sections) {
            const bounds = el.getBoundingClientRect();
            if (point.clientY > bounds.top + bounds.height / 2) slot += 1;
          }
          onBox({
            x: bound(origin.x + ((point.clientX - startX) / width) * 100, 0, 100 - origin.w),
            y: yFor(slot, sections.length),
            w: origin.w,
            h: origin.h,
          });
        });
      } : undefined}
    >
      <img src={picture.src} alt="" draggable={false} />
      {onEdit ? <button type="button" className="free-remove" aria-label="Remove from the page" onPointerDown={(event) => event.stopPropagation()} onClick={() => onEdit({ target: "section", id: picture.id, key: picture.clear, value: "" })}><X size={14} /></button> : null}
      {onBox ? <button type="button" className="flow-resize" aria-label="Resize image" onPointerDown={(event) => {
        const origin = box;
        const startX = event.clientX;
        const startY = event.clientY;
        follow(event, (point, frame) => {
          const width = frame.getBoundingClientRect().width || 1;
          onBox({
            x: origin.x,
            y: origin.y,
            w: bound(origin.w + ((point.clientX - startX) / width) * 100, 12, Math.max(12, 100 - origin.x)),
            h: bound(origin.h + ((point.clientY - startY) / width) * 100, 8, 100),
          });
        });
      }} /> : null}
    </div>
  );
}

function Copy({ value, label, onChange, maxLength, placeholder, multiline = false }: { value: string; label: string; onChange?: (value: string) => void; maxLength?: number; placeholder?: string; multiline?: boolean }) {
  if (!onChange) return value || placeholder || "";
  return (
    <textarea
      className="edit-copy"
      aria-label={label}
      value={value}
      maxLength={maxLength}
      placeholder={placeholder}
      rows={multiline ? Math.min(6, Math.max(2, value.split("\n").length)) : 1}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

function RsvpBlock({ heading, headingAlign, formAlign, slug, configuration, preview, onChange, onAlign }: { heading: string; headingAlign: string; formAlign: string; slug: string; configuration: SiteConfiguration; preview: boolean; onChange?: (value: string) => void; onAlign?: (key: string, value: string) => void }) {
  const [done, setDone] = useState("");
  const mutation = useMutation({
    mutationFn: async (form: FormData) => {
      const body = {
        guestName: String(form.get("guestName") ?? ""),
        guestPhone: String(form.get("guestPhone") ?? ""),
        guestEmail: String(form.get("guestEmail") ?? ""),
        attendanceStatus: String(form.get("attendanceStatus") ?? ""),
        numberOfGuests: Number(form.get("numberOfGuests") ?? 1),
        message: String(form.get("message") ?? ""),
        website: String(form.get("website") ?? ""),
      };
      const response = await api.post(`/public/events/${slug}/rsvp`, body);
      return response.data.data.confirmationMessage as string;
    },
    onSuccess: (message) => setDone(message),
  });
  return (
    <section className="event-section">
      <Hold field="heading" value={headingAlign} onChange={onAlign ? (value) => onAlign("heading", value) : undefined}>
        <h2><Copy value={heading} label="Reply heading" maxLength={120} placeholder="Reply" onChange={onChange} /></h2>
      </Hold>
      {done ? <Hold field="done" value={formAlign} onChange={onAlign ? (value) => onAlign("form", value) : undefined}><p>{done}</p></Hold> : (
        <Hold field="form" value={formAlign} onChange={onAlign ? (value) => onAlign("form", value) : undefined}>
        <form
          className="event-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (preview) {
              setDone(configuration.rsvp.confirmationMessage || "Preview only. Replies are saved on the published site.");
              return;
            }
            mutation.mutate(new FormData(event.currentTarget));
          }}
        >
          <input name="guestName" required placeholder="Your name" aria-label="Your name" />
          {configuration.rsvp.collectPhone ? <input name="guestPhone" required placeholder="Phone" aria-label="Phone" /> : <input name="guestPhone" placeholder="Phone, if you want" aria-label="Phone" />}
          {configuration.rsvp.collectEmail ? <input name="guestEmail" type="email" required placeholder="Email" aria-label="Email" /> : null}
          <select name="attendanceStatus" aria-label="Attendance" defaultValue="ACCEPTED">
            <option value="ACCEPTED">I will be there</option>
            <option value="DECLINED">I cannot come</option>
          </select>
          <input name="numberOfGuests" type="number" min={1} max={configuration.rsvp.maxGuestsPerResponse} defaultValue={1} aria-label="Number of guests" />
          <textarea name="message" placeholder="A note" aria-label="A note" />
          <input name="website" tabIndex={-1} autoComplete="off" aria-hidden="true" style={{ position: "absolute", left: "-9999px" }} />
          <button type="submit">{mutation.isPending ? "Sending" : "Send reply"}</button>
          {mutation.isError ? <p>{errorMessage(mutation.error, "The reply was not saved.")}</p> : null}
        </form>
        </Hold>
      )}
    </section>
  );
}

function items(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.map((item) => {
    const row = item as Record<string, unknown>;
    return { time: String(row.time ?? ""), title: String(row.title ?? ""), detail: String(row.detail ?? "") };
  });
}

function strings(value: unknown) {
  return Array.isArray(value) ? value.map(String) : [];
}

function mediaSrc(value: string, eventId?: string) {
  if (!value || value.startsWith("/placeholders/")) return "";
  if (value.startsWith("/") || value.startsWith("http")) return value;
  return eventId ? `/api/v1/events/${eventId}/media/${value}` : `/api/v1/public/media/${value}`;
}

type Box = { x: number; y: number; w: number; h: number };

function hasBlock(section: Section) {
  if (section.type === "IMAGE") return String(section.properties.caption ?? "").trim().length > 0;
  return true;
}

type PlacedPicture = { id: string; src: string; box: Box; clear: "backgroundImage" | "mediaId" };

function picturesAt(sections: Section[], mediaEventId: string | undefined, slot: number) {
  const found: PlacedPicture[] = [];
  for (const section of sections) {
    const source = section.type === "HERO" ? String(section.properties.backgroundImage ?? "") : section.type === "IMAGE" ? String(section.properties.mediaId ?? "") : "";
    const src = mediaSrc(source, mediaEventId);
    if (!src) continue;
    const box = boxOf(section.properties);
    if (slotFor(box.y, sections.length) !== slot) continue;
    found.push({ id: section.id, src, box, clear: section.type === "HERO" ? "backgroundImage" : "mediaId" });
  }
  return found;
}

function slotFor(y: number, count: number) {
  if (count <= 0) return 0;
  return Math.min(count, Math.max(0, Math.round((y / 100) * count)));
}

function yFor(slot: number, count: number) {
  if (count <= 0) return 0;
  return Math.round((slot / count) * 100);
}

function boxOf(properties: Record<string, unknown>): Box {
  return {
    x: measure(properties.imageX, 52),
    y: measure(properties.imageY, 4),
    w: measure(properties.imageW, 40),
    h: measure(properties.imageH, 24),
  };
}

function measure(value: unknown, fallback: number) {
  if (value === undefined || value === null || value === "") return fallback;
  const number = Number(value);
  return Number.isFinite(number) ? bound(number, 0, 100) : fallback;
}

function bound(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, Math.round(value)));
}

function readAlign(properties: Record<string, unknown>, key: string, fallback: "left" | "center") {
  const stored = properties.alignments;
  if (stored && typeof stored === "object") {
    const value = (stored as Record<string, unknown>)[key];
    if (value === "left" || value === "center" || value === "right") return value;
  }
  if (key === "image" || key === "pictures" || key === "video") return side(properties.imageAlign, "center");
  return side(properties.align, fallback);
}

function side(value: unknown, fallback: "left" | "center") {
  return value === "left" || value === "center" || value === "right" ? value : fallback;
}

function formatWhen(iso: string, timezone: string) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "Date to be set";
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "full", timeStyle: "short", timeZone: timezone || "Asia/Kolkata" }).format(date);
}
