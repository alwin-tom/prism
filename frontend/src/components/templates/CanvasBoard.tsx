import { useRef, useState, type ChangeEvent, type CSSProperties, type DragEvent, type PointerEvent as ReactPointerEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlignCenter, AlignLeft, AlignRight, Baseline, Blend, Bold, BringToFront, Eraser, GripVertical, Italic, Link2, Minus, PaintBucket, Plus, Radius, SendToBack, Square, SquareDashed, SquareX, Trash2, Underline, Upload, X } from "lucide-react";
import { api, errorMessage } from "../../services/api";
import type { CanvasElement, Envelope, PublicEvent, SiteConfiguration } from "../../types";
import type { SiteEdit } from "./PublicSite";
import { TEXT_FONTS, TEXT_FONT_NAMES } from "./fonts";

export function CanvasBoard({ event, preview, mediaEventId, onEdit }: { event: PublicEvent; preview: boolean; mediaEventId?: string; onEdit?: (edit: SiteEdit) => void }) {
  const canvas = event.configuration?.canvas;
  const [selected, setSelected] = useState<string[]>([]);
  if (!canvas) return null;
  const elements = [...canvas.elements].sort((left, right) => left.z - right.z);
  function choose(id: string, extend: boolean) {
    setSelected((current) => {
      if (extend) return current.includes(id) ? current.filter((item) => item !== id) : [...current, id];
      if (current.includes(id)) return current;
      return [id];
    });
  }
  return (
    <div className="canvas-page" style={{ height: canvas.height }} onPointerDown={(pointer) => { if (pointer.target === pointer.currentTarget) setSelected([]); }}>
      {elements.map((element) => (
        <CanvasItem
          key={element.id}
          element={element}
          peers={elements}
          selected={selected.includes(element.id)}
          primary={selected[selected.length - 1] === element.id}
          selectedIds={selected}
          editable={Boolean(onEdit)}
          event={event}
          preview={preview}
          mediaEventId={mediaEventId}
          onSelect={(extend) => choose(element.id, extend)}
          onEdit={onEdit}
        />
      ))}
      <p className="prism-credit">Made with WebAux</p>
    </div>
  );
}

function CanvasItem({ element, peers, selected, primary, selectedIds, editable, event, preview, mediaEventId, onSelect, onEdit }: {
  element: CanvasElement;
  peers: CanvasElement[];
  selected: boolean;
  primary: boolean;
  selectedIds: string[];
  editable: boolean;
  event: PublicEvent;
  preview: boolean;
  mediaEventId?: string;
  onSelect: (extend: boolean) => void;
  onEdit?: (edit: SiteEdit) => void;
}) {
  const src = mediaSrc(element.mediaId, mediaEventId);
  const href = !onEdit && element.type === "button" ? anchorHref(element.href) : "";
  const gesture = useRef("");
  function beginGesture() {
    gesture.current = "";
    const end = () => {
      gesture.current = "";
      window.removeEventListener("pointerup", end);
    };
    window.addEventListener("pointerup", end);
  }
  function emit(edit: SiteEdit, key?: string) {
    if (!onEdit) return;
    if (key && gesture.current === key) onEdit({ ...edit, history: "coalesce" });
    else onEdit(edit);
    if (key) gesture.current = key;
  }
  function move(pointer: ReactPointerEvent) {
    if (!onEdit) return;
    const target = pointer.target as HTMLElement;
    if (!(target instanceof Element)) return;
    const grip = target.closest("[aria-label='Move element']");
    if (target.closest(".canvas-style, .canvas-tools, .canvas-resize, .free-remove") && !grip) return;
    const extend = Boolean(pointer.shiftKey || pointer.ctrlKey || pointer.metaKey);
    onSelect(extend);
    if (extend || target.closest("textarea, input, select, a, label")) return;
    const page = pointer.currentTarget.closest(".canvas-page");
    if (!(page instanceof HTMLElement)) return;
    const ids = selectedIds.includes(element.id) ? selectedIds : [element.id];
    const origins = peers.filter((item) => ids.includes(item.id));
    const startX = pointer.clientX;
    const startY = pointer.clientY;
    beginGesture();
    watchPointer(pointer, (point) => {
      const width = page.getBoundingClientRect().width || 1;
      const dx = ((point.clientX - startX) / width) * 100;
      const dy = point.clientY - startY;
      emit({
        target: "canvas-elements",
        patches: origins.map((origin) => ({
          id: origin.id,
          patch: {
            x: clamp(origin.x + dx, 0, 100 - origin.w),
            y: clamp(origin.y + dy, 0, 11000),
          },
        })),
      }, "move-group");
    }, () => { gesture.current = ""; });
  }
  return (
    <div
      className={`canvas-el${selected ? " is-selected" : ""}${editable ? " can-edit" : ""}`}
      data-kind={element.type}
      style={{ left: `${element.x}%`, top: element.y, width: `${element.w}%`, height: element.h, zIndex: element.z }}
      onPointerDown={move}
    >
      <div className="canvas-face" style={faceStyle(element)}>
        {element.type === "image" ? (
          src ? <img src={src} alt="" draggable={false} /> : onEdit ? <PictureSlot eventId={mediaEventId} onPick={(mediaId) => onEdit({ target: "canvas-element", id: element.id, patch: { mediaId } })} /> : null
        ) : null}
        {href ? (
          <a href={href} target={opensNewTab(href) ? "_blank" : undefined} rel={opensNewTab(href) ? "noopener noreferrer" : undefined}>{element.text}</a>
        ) : null}
        {(element.type === "text" || element.type === "button") && !href ? (
          selected && primary && onEdit ? (
            <textarea className="edit-copy" aria-label="Element text" value={element.text} onPointerDown={beginGesture} onFocus={beginGesture} onBlur={() => { gesture.current = ""; }} onChange={(change) => emit({ target: "canvas-element", id: element.id, patch: { text: change.target.value } }, `text:${element.id}`)} />
          ) : <p>{element.text}</p>
        ) : null}
        {element.type === "rsvp" && event.configuration ? (
          <ReplyCard slug={event.slug} configuration={event.configuration} preview={preview} heading={element.text} headingStyle={typeStyle(element)} />
        ) : null}
      </div>
      {primary && onEdit ? (
        <div className="canvas-tools">
          <button type="button" aria-label="Move element"><GripVertical size={14} /></button>
          <button type="button" aria-label="Align left" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { align: "left" } })}><AlignLeft size={14} /></button>
          <button type="button" aria-label="Align middle" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { align: "center" } })}><AlignCenter size={14} /></button>
          <button type="button" aria-label="Align right" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { align: "right" } })}><AlignRight size={14} /></button>
          <button type="button" aria-label="Smaller text" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { fontSize: clamp(element.fontSize - 2, 10, 120) } })}><Minus size={14} /></button>
          <button type="button" aria-label="Larger text" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { fontSize: clamp(element.fontSize + 2, 10, 120) } })}><Plus size={14} /></button>
          <button type="button" aria-label="Bring forward" title="Front" onClick={() => onEdit({ target: "canvas-order", id: element.id, direction: "front" })}><BringToFront size={14} /></button>
          <button type="button" aria-label="Send backward" title="Back" onClick={() => onEdit({ target: "canvas-order", id: element.id, direction: "back" })}><SendToBack size={14} /></button>
          <button type="button" aria-label="Remove element" onClick={() => onEdit({ target: "canvas-remove", id: element.id, ids: selectedIds.includes(element.id) ? selectedIds : [element.id] })}><Trash2 size={14} /></button>
        </div>
      ) : null}
      {primary && onEdit ? <StyleBar element={element} onEdit={onEdit} beginGesture={beginGesture} emit={emit} /> : null}
      {selected && onEdit ? (
        <>
          <ResizeHandle axis="x" label="Resize width" element={element} peers={peers} selectedIds={selectedIds} beginGesture={beginGesture} endGesture={() => { gesture.current = ""; }} emit={emit} />
          <ResizeHandle axis="y" label="Resize height" element={element} peers={peers} selectedIds={selectedIds} beginGesture={beginGesture} endGesture={() => { gesture.current = ""; }} emit={emit} />
          <ResizeHandle axis="both" label="Resize element" element={element} peers={peers} selectedIds={selectedIds} beginGesture={beginGesture} endGesture={() => { gesture.current = ""; }} emit={emit} />
        </>
      ) : null}
      {selected && onEdit && element.type === "image" && src ? (
        <button type="button" className="free-remove" aria-label="Remove from the page" onClick={() => onEdit({ target: "canvas-remove", id: element.id })}><X size={14} /></button>
      ) : null}
    </div>
  );
}

function ResizeHandle({ axis, label, element, peers, selectedIds, beginGesture, endGesture, emit }: {
  axis: "x" | "y" | "both";
  label: string;
  element: CanvasElement;
  peers: CanvasElement[];
  selectedIds: string[];
  beginGesture: () => void;
  endGesture: () => void;
  emit: (edit: SiteEdit, key?: string) => void;
}) {
  return (
    <button
      type="button"
      className={`canvas-resize canvas-resize-${axis}`}
      aria-label={label}
      onPointerDown={(pointer) => {
        const page = pointer.currentTarget.closest(".canvas-page");
        if (!(page instanceof HTMLElement)) return;
        const ids = selectedIds.includes(element.id) ? selectedIds : [element.id];
        const origins = peers.filter((item) => ids.includes(item.id));
        const startX = pointer.clientX;
        const startY = pointer.clientY;
        beginGesture();
        watchPointer(pointer, (point) => {
          const width = page.getBoundingClientRect().width || 1;
          const dw = ((point.clientX - startX) / width) * 100;
          const dh = point.clientY - startY;
          emit({
            target: "canvas-elements",
            patches: origins.map((origin) => ({
              id: origin.id,
              patch: {
                ...(axis === "y" ? {} : { w: clamp(origin.w + dw, 8, 100 - origin.x) }),
                ...(axis === "x" ? {} : { h: clamp(origin.h + dh, 16, 1600) }),
              },
            })),
          }, "resize-group");
        }, () => endGesture());
      }}
    />
  );
}

function StyleBar({ element, onEdit, beginGesture, emit }: { element: CanvasElement; onEdit: (edit: SiteEdit) => void; beginGesture: () => void; emit: (edit: SiteEdit, key?: string) => void }) {
  const patch = (next: Record<string, string | number | boolean>, key: string) => emit({ target: "canvas-element", id: element.id, patch: next }, key);
  const textColour = element.type === "text" || element.type === "button" || element.type === "rsvp";
  const fontChoice = element.font && TEXT_FONTS[element.font] ? element.font : element.weight === "display" ? "display" : "body";
  return (
    <div className="canvas-style" onPointerDown={beginGesture}>
      {textColour ? (
        <>
          <label title="Font">
            <select aria-label="Font" value={fontChoice} onChange={(change) => {
              const value = change.target.value;
              if (value === "body") patch({ font: "", weight: "regular" }, `font:${element.id}`);
              else if (value === "display") patch({ font: "", weight: "display" }, `font:${element.id}`);
              else patch({ font: value }, `font:${element.id}`);
            }}>
              <option value="body">Body</option>
              <option value="display">Display</option>
              {TEXT_FONT_NAMES.map((name) => <option key={name} value={name}>{name}</option>)}
            </select>
          </label>
          <button type="button" aria-label="Bold" aria-pressed={Boolean(element.bold)} title="Bold" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { bold: !element.bold } })}><Bold size={14} /></button>
          <button type="button" aria-label="Italic" aria-pressed={Boolean(element.italic)} title="Italic" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { italic: !element.italic } })}><Italic size={14} /></button>
          <button type="button" aria-label="Underline" aria-pressed={Boolean(element.underline)} title="Underline" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { underline: !element.underline } })}><Underline size={14} /></button>
          <label title="Text colour"><Baseline size={14} aria-hidden /> <input type="color" aria-label="Text colour" value={hex(element.color)} onChange={(change) => patch({ color: change.target.value }, `color:${element.id}`)} /></label>
        </>
      ) : null}
      <label title="Fill"><PaintBucket size={14} aria-hidden /> <input type="color" aria-label="Fill colour" value={hex(element.fill || "#ffffff")} onChange={(change) => patch({ fill: change.target.value }, `fill:${element.id}`)} /></label>
      <button type="button" aria-label="Remove fill" title="No fill" onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { fill: "" } })}><Eraser size={14} /></button>
      <label title="Border"><Square size={14} aria-hidden /> <input type="color" aria-label="Border colour" value={hex(element.borderColor || "#171717")} onChange={(change) => patch({ borderColor: change.target.value, borderWidth: Math.max(element.borderWidth ?? 0, 1) }, `border:${element.id}`)} /></label>
      <button type="button" aria-label="Remove border" title="No border" aria-pressed={(element.borderWidth ?? 0) === 0} onClick={() => onEdit({ target: "canvas-element", id: element.id, patch: { borderWidth: 0 } })}><SquareX size={14} /></button>
      <label title="Border width"><SquareDashed size={14} aria-hidden /> <input type="range" min={0} max={12} aria-label="Border width" value={element.borderWidth ?? 0} onChange={(change) => patch({ borderWidth: Number(change.target.value) }, `border-width:${element.id}`)} /></label>
      <label title="Corners"><Radius size={14} aria-hidden /> <input type="range" min={0} max={48} aria-label="Corner radius" value={element.radius ?? 0} onChange={(change) => patch({ radius: Number(change.target.value) }, `radius:${element.id}`)} /></label>
      <label title="Shadow"><Blend size={14} aria-hidden /> <input type="range" min={0} max={40} aria-label="Shadow" value={element.shadow ?? 0} onChange={(change) => patch({ shadow: Number(change.target.value) }, `shadow:${element.id}`)} /></label>
      {element.type === "button" ? (
        <label title="Link">
          <Link2 size={14} aria-hidden />
          <input type="url" aria-label="Link address" placeholder="https://" spellCheck={false} value={element.href ?? ""} onChange={(change) => patch({ href: change.target.value }, `href:${element.id}`)} onBlur={(change) => {
            const value = change.target.value.trim();
            if (value && !/^(https?:|mailto:|tel:|#|\/)/i.test(value)) onEdit({ target: "canvas-element", id: element.id, patch: { href: `https://${value}` } });
          }} />
        </label>
      ) : null}
    </div>
  );
}

function PictureSlot({ eventId, onPick }: { eventId?: string; onPick: (mediaId: string) => void }) {
  const queryClient = useQueryClient();
  const [message, setMessage] = useState("");
  const media = useQuery({
    queryKey: ["media", eventId],
    enabled: Boolean(eventId),
    retry: false,
    queryFn: async () => (await api.get<Envelope<{ id: string; mediaType: string; url: string }[]>>(`/events/${eventId}/media`)).data.data,
  });
  const pictures = (media.data ?? []).filter((file) => file.mediaType === "IMAGE");
  async function send(file: File | undefined) {
    if (!file || !eventId) return;
    const body = new FormData();
    body.append("file", file);
    try {
      const response = await api.post<Envelope<{ id: string }>>(`/events/${eventId}/media`, body);
      onPick(response.data.data.id);
      await queryClient.invalidateQueries({ queryKey: ["media", eventId] });
      setMessage("");
    } catch {
      setMessage("The picture was not saved.");
    }
  }
  function drop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    void send(event.dataTransfer.files?.[0]);
  }
  return (
    <div className="canvas-upload" onDragOver={(event) => event.preventDefault()} onDrop={drop}>
      <label>
        <Upload size={18} aria-hidden />
        <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" aria-label="Upload a picture" onChange={(change: ChangeEvent<HTMLInputElement>) => void send(change.target.files?.[0])} />
      </label>
      {pictures.length > 0 ? (
        <div className="canvas-picks">
          {pictures.map((file) => (
            <button key={file.id} type="button" aria-label="Use this picture" onClick={() => onPick(file.id)}>
              <img src={file.url} alt="" />
            </button>
          ))}
        </div>
      ) : null}
      {message ? <span>{message}</span> : null}
    </div>
  );
}

function faceStyle(element: CanvasElement): CSSProperties {
  const borderWidth = element.borderWidth ?? 0;
  const shadow = element.shadow ?? 0;
  return {
    ...typeStyle(element),
    background: element.fill || "transparent",
    border: borderWidth > 0 ? `${borderWidth}px solid ${element.borderColor || element.color}` : element.type === "image" && !element.mediaId ? "1px dashed currentColor" : "0 solid transparent",
    borderRadius: element.radius ?? 0,
    boxShadow: shadow > 0 ? `0 ${Math.max(2, Math.round(shadow / 4))}px ${shadow}px rgb(0 0 0 / 30%)` : "none",
    overflow: "hidden",
    width: "100%",
    height: "100%",
    boxSizing: "border-box",
  };
}

function typeStyle(element: CanvasElement): CSSProperties {
  return {
    color: element.color,
    fontSize: element.fontSize,
    textAlign: element.align,
    fontFamily: element.font && TEXT_FONTS[element.font] ? TEXT_FONTS[element.font] : element.weight === "display" ? "var(--event-display)" : "inherit",
    fontWeight: element.bold ? 700 : element.weight === "display" && !element.font ? 500 : 400,
    fontStyle: element.italic ? "italic" : "normal",
    textDecoration: element.underline ? "underline" : "none",
  };
}

function hex(value: string) {
  return /^#[0-9a-fA-F]{6}$/.test(value) ? value : "#171717";
}

function anchorHref(value: string | undefined) {
  const href = (value ?? "").trim();
  if (!href || /[\s"'<>]/.test(href) || /^(javascript|data|vbscript):/i.test(href)) return "";
  if (/^(https?:|mailto:|tel:)/i.test(href) || href.startsWith("#") || (href.startsWith("/") && !href.startsWith("//"))) return href;
  return "";
}

function opensNewTab(href: string) {
  return /^https?:/i.test(href);
}

function ReplyCard({ slug, configuration, preview, heading, headingStyle }: { slug: string; configuration: SiteConfiguration; preview: boolean; heading: string; headingStyle?: CSSProperties }) {
  const [done, setDone] = useState("");
  const mutation = useMutation({
    mutationFn: async (form: FormData) => {
      const response = await api.post(`/public/events/${slug}/rsvp`, {
        guestName: String(form.get("guestName") ?? ""),
        guestPhone: String(form.get("guestPhone") ?? ""),
        guestEmail: "",
        attendanceStatus: String(form.get("attendanceStatus") ?? ""),
        numberOfGuests: Number(form.get("numberOfGuests") ?? 1),
        message: String(form.get("message") ?? ""),
        website: "",
      });
      return response.data.data.confirmationMessage as string;
    },
    onSuccess: (message) => setDone(message),
  });
  return (
    <div className="canvas-reply">
      <p className="canvas-reply-title" style={headingStyle}>{heading}</p>
      {done ? <p>{done}</p> : (
        <form className="event-form" onSubmit={(formEvent) => {
          formEvent.preventDefault();
          if (preview) {
            setDone(configuration.rsvp.confirmationMessage || "Preview only. Replies are saved on the published site.");
            return;
          }
          mutation.mutate(new FormData(formEvent.currentTarget));
        }}>
          <input name="guestName" required placeholder="Your name" aria-label="Your name" />
          {configuration.rsvp.collectPhone ? <input name="guestPhone" required placeholder="Phone" aria-label="Phone" /> : null}
          <select name="attendanceStatus" aria-label="Attendance" defaultValue="ACCEPTED">
            <option value="ACCEPTED">I will be there</option>
            <option value="DECLINED">I cannot come</option>
          </select>
          <input name="numberOfGuests" type="number" min={1} max={configuration.rsvp.maxGuestsPerResponse} defaultValue={1} aria-label="Number of guests" />
          <textarea name="message" placeholder="A note" aria-label="A note" />
          <button type="submit">{mutation.isPending ? "Sending" : "Send reply"}</button>
          {mutation.isError ? <p>{errorMessage(mutation.error, "The reply was not saved.")}</p> : null}
        </form>
      )}
    </div>
  );
}

function watchPointer(event: ReactPointerEvent, apply: (point: globalThis.PointerEvent) => void, done?: () => void) {
  event.preventDefault();
  event.stopPropagation();
  const move = (ev: Event) => apply(ev as globalThis.PointerEvent);
  const up = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", up);
    done?.();
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", up);
}

function mediaSrc(value: string, eventId?: string) {
  if (!value || value.startsWith("/placeholders/")) return "";
  if (value.startsWith("/") || value.startsWith("http")) return value;
  return eventId ? `/api/v1/events/${eventId}/media/${value}` : `/api/v1/public/media/${value}`;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, Math.round(value)));
}
