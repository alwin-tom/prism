import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type DragEvent, type MouseEvent, type PointerEvent as ReactPointerEvent, type ReactNode } from "react";
import { useMutation } from "@tanstack/react-query";
import { X } from "lucide-react";
import { api, errorMessage } from "../../services/api";
import { collectFonts, presentedStyles, resolvedTokens, stack, useFonts, type TypographySettings } from "../typography/fonts";
import { hasOverlay, overlayCss, BACKGROUND_META_KEYS } from "./background";
import type { Breakpoint, EditorDocument, EditorNode } from "./document";
import { findNode, propsFor, stylesFor, viewportDevice } from "./document";
import { alignMove, centeredOn, contentBoxWidth, contentFrame, edgeDrift, moveStyles, pageFrame, percentOf, readRotate, resizeStyles, rotateTransform, shiftFrame, usedOffset, type Frame, type GuideLine, type Handle } from "./geometry";
import { RichTextContent, WholeLink, linkStyleFromProps } from "./RichTextContent";
import { MarqueeFrame } from "./MarqueeFrame";
import { runsHaveLinks, effectiveRuns, type PropValue } from "./richText";

export type VisualRsvp = {
  slug: string;
  preview?: boolean;
  enabled?: boolean;
  collectPhone?: boolean;
  collectEmail?: boolean;
  maxGuests?: number;
  confirmationMessage?: string;
};

export function VisualPage({ document, breakpoint = "desktop", followViewport = false, mediaEventId, countdownFallback, rsvp, selectedId, selectedIds, zoom = 1, onSelect, onDropType, onResize, onUploadPicture, onAddPictures }: {
  document: EditorDocument;
  breakpoint?: Breakpoint;
  followViewport?: boolean;
  mediaEventId?: string;
  countdownFallback?: string;
  rsvp?: VisualRsvp;
  selectedId?: string;
  selectedIds?: string[];
  zoom?: number;
  onSelect?: (id: string, extend?: boolean) => void;
  onDropType?: (parentId: string, type: string) => void;
  onResize?: (patches: { id: string; styles: Record<string, string> }[], coalesce: boolean) => void;
  onUploadPicture?: (nodeId: string, file: File) => Promise<void>;
  onAddPictures?: (parentId: string, files: File[]) => Promise<void>;
}) {
  const ids = selectedIds?.length ? selectedIds : selectedId ? [selectedId] : [];
  const [guides, setGuides] = useState<GuideLine[]>([]);
  const [rsvpOpen, setRsvpOpen] = useState(false);
  const tokens = resolvedTokens(document);
  useFonts("page", collectFonts(document));
  const [viewport, setViewport] = useState<Breakpoint>(followViewport ? viewportDevice() : breakpoint);
  useEffect(() => {
    if (!followViewport) {
      setViewport(breakpoint);
      return;
    }
    const mobile = window.matchMedia("(max-width: 767px)");
    const tablet = window.matchMedia("(max-width: 1023px)");
    const apply = () => setViewport(mobile.matches ? "mobile" : tablet.matches ? "tablet" : "desktop");
    apply();
    mobile.addEventListener("change", apply);
    tablet.addEventListener("change", apply);
    return () => {
      mobile.removeEventListener("change", apply);
      tablet.removeEventListener("change", apply);
    };
  }, [followViewport, breakpoint]);
  useEffect(() => {
    if (!rsvp || onSelect) return;
    const openFromHash = () => {
      const hash = window.location.hash.replace(/^#/, "").toLowerCase();
      if (hash === "rsvp" || hash === "reply") setRsvpOpen(true);
    };
    openFromHash();
    window.addEventListener("hashchange", openFromHash);
    return () => window.removeEventListener("hashchange", openFromHash);
  }, [rsvp, onSelect]);
  const active = followViewport ? viewport : breakpoint;
  const tokenStyle = designTokenStyle(document);
  const openRsvp = rsvp && !onSelect ? () => setRsvpOpen(true) : undefined;
  return (
    <div className={onSelect ? "visual-page is-editing" : "visual-page"} style={tokenStyle} onClick={() => onSelect?.("", false)}>
      <VisualNode node={document.root} root={document.root} tokens={tokens} breakpoint={active} mediaEventId={mediaEventId} countdownFallback={countdownFallback} selectedId={selectedId} selectedIds={ids} zoom={zoom} onSelect={onSelect} onDropType={onDropType} onResize={onResize} onUploadPicture={onUploadPicture} onAddPictures={onAddPictures} onGuides={onResize ? setGuides : undefined} onOpenRsvp={openRsvp} />
      {onResize ? ids.map((id) => (
        <SelectionChrome key={id} selectedId={id} selectedIds={ids} document={document} breakpoint={active} zoom={zoom} onResize={onResize} onGuides={setGuides} />
      )) : null}
      {guides.map((guide, index) => (
        <span
          key={`${guide.axis}-${index}`}
          className={guide.axis === "x" ? "visual-guide is-x" : "visual-guide is-y"}
          style={guide.axis === "x"
            ? { left: guide.at, top: guide.start, height: Math.max(1, guide.end - guide.start) }
            : { top: guide.at, left: guide.start, width: Math.max(1, guide.end - guide.start) }}
        />
      ))}
      {rsvpOpen && rsvp ? <VisualRsvpDialog rsvp={rsvp} onClose={() => setRsvpOpen(false)} /> : null}
      <p className="prism-credit">Made with WebAux</p>
    </div>
  );
}

function VisualNode({ node, root, tokens, breakpoint, mediaEventId, countdownFallback, selectedId, selectedIds, zoom = 1, onSelect, onDropType, onResize, onUploadPicture, onAddPictures, onGuides, onOpenRsvp }: {
  node: EditorNode;
  root: EditorNode;
  tokens: TypographySettings;
  breakpoint: Breakpoint;
  mediaEventId?: string;
  countdownFallback?: string;
  selectedId?: string;
  selectedIds?: string[];
  zoom?: number;
  onSelect?: (id: string, extend?: boolean) => void;
  onDropType?: (parentId: string, type: string) => void;
  onResize?: (patches: { id: string; styles: Record<string, string> }[], coalesce: boolean) => void;
  onUploadPicture?: (nodeId: string, file: File) => Promise<void>;
  onAddPictures?: (parentId: string, files: File[]) => Promise<void>;
  onGuides?: (guides: GuideLine[]) => void;
  onOpenRsvp?: () => void;
}) {
  const presented = presentedStyles(node, breakpoint, tokens);
  const style = softenTextEllipse(node, applySurface(css(presented), presented, mediaEventId));
  const local = propsFor(node, breakpoint);
  if (node.type === "SPACER") style.height = `${Number(local.size ?? 24)}px`;
  const overlay = node.type === "IMAGE" ? null : overlayCss(presented);
  const selected = selectedId === node.id;
  const editing = Boolean(onSelect);
  const select = (event: MouseEvent) => {
    if (!onSelect) return;
    event.stopPropagation();
    onSelect(node.id, event.shiftKey || event.metaKey || event.ctrlKey);
  };
  const drop = (event: DragEvent) => {
    if (!onDropType) return;
    event.preventDefault();
    event.stopPropagation();
    const type = event.dataTransfer.getData("application/prism-element");
    if (type) onDropType(node.id, type);
  };
  const dragged = useRef(false);
  const className = editing ? `visual-node${selected ? " is-selected" : ""}${node.id === root.id ? " is-root" : ""}` : undefined;
  const edit = editing ? {
    className,
    "data-node": node.id,
    onClick: (event: MouseEvent) => {
      if (dragged.current) {
        dragged.current = false;
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      select(event);
    },
    onPointerDown: (event: ReactPointerEvent<HTMLElement>) => {
      if (!onSelect || !onResize || node.id === root.id) return;
      event.stopPropagation();
      if (node.locked || event.shiftKey || event.metaKey || event.ctrlKey) return;
      const hit = event.target instanceof Element ? event.target.closest("input, textarea, select, button, a, label") : null;
      if (hit && hit !== event.currentTarget) return;
      event.preventDefault();
      const chosen = (selectedIds ?? []).filter((id) => id !== root.id && !findNode(root, id)?.locked);
      const ids = chosen.includes(node.id) ? chosen : [node.id];
      if (!chosen.includes(node.id)) onSelect(node.id, false);
      const page = event.currentTarget.closest(".visual-page");
      const origins = new Map(ids.map((id) => {
        const element = id === event.currentTarget.getAttribute("data-node")
          ? event.currentTarget
          : page?.querySelector(`[data-node="${CSS.escape(id)}"]`);
        const item = findNode(root, id);
        const position = item ? stylesFor(item, breakpoint).position ?? "" : "";
        return [id, element instanceof HTMLElement ? moveOrigin(element, position, zoom) : { left: 0, top: 0, width: 0, height: 0, position, basis: 0 }];
      }));
      const startX = event.clientX;
      const startY = event.clientY;
      const guides = page instanceof HTMLElement ? captureGuides(page, ids, zoom) : null;
      let moved = false;
      const move = (point: PointerEvent) => {
        const rawX = (point.clientX - startX) / zoom;
        const rawY = (point.clientY - startY) / zoom;
        if (!moved && Math.hypot(rawX, rawY) < 3) return;
        dragged.current = true;
        const aligned = guides ? snappedDelta(guides, node.id, rawX, rawY) : { dx: rawX, dy: rawY, guides: [] as GuideLine[] };
        onGuides?.(aligned.guides);
        onResize(ids.map((id) => {
          const origin = origins.get(id) ?? { left: 0, top: 0, width: 0, height: 0, position: "", basis: 0 };
          return { id, styles: dragStyles(origin, aligned.dx, aligned.dy) };
        }), moved);
        moved = true;
      };
      const end = () => {
        onGuides?.([]);
        window.removeEventListener("pointermove", move);
        window.removeEventListener("pointerup", end);
      };
      window.addEventListener("pointermove", move);
      window.addEventListener("pointerup", end);
    },
    onDragOver: (event: DragEvent) => event.preventDefault(),
    onDrop: drop,
  } : { className };
  const body = node.children.map((child) => (
    <VisualNode key={child.id} node={child} root={root} tokens={tokens} breakpoint={breakpoint} mediaEventId={mediaEventId} countdownFallback={countdownFallback} selectedId={selectedId} selectedIds={selectedIds} zoom={zoom} onSelect={onSelect} onDropType={onDropType} onResize={onResize} onUploadPicture={onUploadPicture} onAddPictures={onAddPictures} onGuides={onGuides} onOpenRsvp={onOpenRsvp} />
  ));
  const veil = overlay ? <span className="visual-bg-overlay" aria-hidden style={{ background: overlay.background, opacity: overlay.opacity }} /> : null;
  if (node.type === "HEADING") {
    const level = Math.min(6, Math.max(1, Number(node.props.level ?? 2)));
    const Tag = `h${level}` as "h1";
    const runs = effectiveRuns(node.props as Record<string, PropValue>);
    return (
      <Tag {...edit} style={style}>
        {veil}
        <WholeLink
          href={String(node.props.href ?? "")}
          target={String(node.props.linkTarget ?? "_self")}
          title={String(node.props.linkTitle ?? "")}
          editing={editing}
          hasInlineLinks={runsHaveLinks(runs)}
        >
          <RichTextContent props={node.props as Record<string, PropValue>} editing={editing} linkStyle={linkStyleFromProps(node.props as Record<string, PropValue>)} />
        </WholeLink>
      </Tag>
    );
  }
  if (node.type === "PARAGRAPH") {
    const runs = effectiveRuns(node.props as Record<string, PropValue>);
    return (
      <p {...edit} style={style}>
        {veil}
        <WholeLink
          href={String(node.props.href ?? "")}
          target={String(node.props.linkTarget ?? "_self")}
          title={String(node.props.linkTitle ?? "")}
          editing={editing}
          hasInlineLinks={runsHaveLinks(runs)}
        >
          <RichTextContent props={node.props as Record<string, PropValue>} editing={editing} linkStyle={linkStyleFromProps(node.props as Record<string, PropValue>)} />
        </WholeLink>
      </p>
    );
  }
  if (node.type === "QUOTE") {
    const runs = effectiveRuns(node.props as Record<string, PropValue>);
    return (
      <blockquote {...edit} style={style}>
        {veil}
        <WholeLink
          href={String(node.props.href ?? "")}
          target={String(node.props.linkTarget ?? "_self")}
          title={String(node.props.linkTitle ?? "")}
          editing={editing}
          hasInlineLinks={runsHaveLinks(runs)}
        >
          <p><RichTextContent props={node.props as Record<string, PropValue>} editing={editing} linkStyle={linkStyleFromProps(node.props as Record<string, PropValue>)} /></p>
        </WholeLink>
        {String(node.props.attribution ?? "") ? <cite>{String(node.props.attribution)}</cite> : null}
      </blockquote>
    );
  }
  if (node.type === "BUTTON") {
    const href = String(node.props.href ?? "");
    const label = String(node.props.text ?? "");
    const reply = isReplyButton(href, label);
    const safe = /^(https?:|mailto:|tel:|#|\/)/i.test(href);
    const button = { ...edit, className: [className, "visual-button", reply && onOpenRsvp ? "is-reply" : ""].filter(Boolean).join(" ") };
    if (!editing && reply && onOpenRsvp) {
      return (
        <button
          type="button"
          className={button.className}
          style={style}
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onOpenRsvp();
          }}
        >
          {veil}{label}
        </button>
      );
    }
    if (editing) {
      return <span {...button} style={style} data-href={safe ? href : undefined}>{veil}{label}</span>;
    }
    return safe
      ? <a {...button} style={style} href={href} target={String(node.props.linkTarget ?? "") === "_blank" ? "_blank" : undefined} rel={String(node.props.linkTarget ?? "") === "_blank" ? "noopener noreferrer" : undefined}>{veil}{label}</a>
      : <span {...button} style={style}>{veil}{label}</span>;
  }
  if (node.type === "MARQUEE") {
    return (
      <MarqueeFrame
        props={local as Record<string, PropValue>}
        style={style}
        edit={edit}
        overlay={veil}
        mediaEventId={mediaEventId}
        editing={editing}
      />
    );
  }
  if (node.type === "CAROUSEL") {
    return <CarouselFrame node={node} edit={edit} style={style} mediaEventId={mediaEventId} breakpoint={breakpoint} paused={editing} onUploadPicture={onUploadPicture} onAddPictures={onAddPictures} overlay={veil} />;
  }
  if (node.type === "IMAGE") {
    const src = mediaSrc(String(local.mediaId ?? ""), mediaEventId);
    return src
      ? <img {...edit} style={style} src={src} alt={String(node.props.alt ?? "")} draggable={false} />
      : <EmptyPicture edit={edit} style={style} onFile={onUploadPicture ? (file) => onUploadPicture(node.id, file) : undefined} />;
  }
  if (node.type === "COUNTDOWN") {
    const target = String(local.target ?? "").trim() || String(countdownFallback ?? "").trim();
    return (
      <div {...edit} style={style} data-kind="COUNTDOWN">
        {veil}
        {String(local.label ?? "") ? <p className="visual-countdown-label">{String(local.label)}</p> : null}
        <CountdownFace target={target} />
      </div>
    );
  }
  if (node.type === "FAQ_ACCORDION") {
    return (
      <section {...edit} style={style} data-kind="FAQ_ACCORDION">
        {veil}
        {String(local.title ?? "") ? <h2>{String(local.title)}</h2> : null}
        {body}
      </section>
    );
  }
  if (node.type === "FAQ_ITEM") {
    return (
      <details {...edit} style={style} data-kind="FAQ_ITEM">
        {veil}
        <summary>{String(local.title ?? "Question")}</summary>
        <p><RichTextContent props={local as Record<string, PropValue>} editing={editing} linkStyle={linkStyleFromProps(local as Record<string, PropValue>)} /></p>
      </details>
    );
  }
  if (node.type === "TIMELINE") {
    return (
      <section {...edit} style={style} data-kind="TIMELINE">
        {veil}
        {String(local.title ?? "") ? <h2>{String(local.title)}</h2> : null}
        <ol className="visual-timeline">{body}</ol>
      </section>
    );
  }
  if (node.type === "TIMELINE_ITEM") {
    return (
      <li {...edit} style={style} data-kind="TIMELINE_ITEM">
        {veil}
        <strong>{String(local.title ?? "")}</strong>
        {String(local.text ?? "") ? <p><RichTextContent props={local as Record<string, PropValue>} editing={editing} linkStyle={linkStyleFromProps(local as Record<string, PropValue>)} /></p> : null}
      </li>
    );
  }
  return <div {...edit} style={style} data-kind={node.type}>{veil}{body}</div>;
}

function CountdownFace({ target }: { target: string }) {
  const end = Date.parse(target);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (Number.isNaN(end)) return;
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [end]);
  if (!target) {
    return <p className="visual-countdown-empty">Date to be announced</p>;
  }
  if (Number.isNaN(end)) {
    return <p className="visual-countdown-empty">{target}</p>;
  }
  const diff = Math.max(0, end - now);
  const days = Math.floor(diff / 86_400_000);
  const hours = Math.floor((diff % 86_400_000) / 3_600_000);
  const minutes = Math.floor((diff % 3_600_000) / 60_000);
  const seconds = Math.floor((diff % 60_000) / 1000);
  const dayDigits = Math.max(3, String(days).length);
  return (
    <div className="visual-countdown" aria-live="polite">
      <div><strong className="num" style={{ ["--countdown-digits" as string]: dayDigits }}>{String(days).padStart(dayDigits, "0")}</strong><span>days</span></div>
      <div><strong className="num">{String(hours).padStart(2, "0")}</strong><span>hours</span></div>
      <div><strong className="num">{String(minutes).padStart(2, "0")}</strong><span>minutes</span></div>
      <div><strong className="num">{String(seconds).padStart(2, "0")}</strong><span>seconds</span></div>
    </div>
  );
}

function isReplyButton(href: string, text: string) {
  const link = href.trim().toLowerCase();
  if (link === "#rsvp" || link === "#reply") return true;
  if (link) return false;
  return /\b(rsvp|reply|respond|let us know|send your reply|send a reply)\b/i.test(text);
}

function VisualRsvpDialog({ rsvp, onClose }: { rsvp: VisualRsvp; onClose: () => void }) {
  const [done, setDone] = useState("");
  const enabled = rsvp.enabled !== false;
  const mutation = useMutation({
    mutationFn: async (form: FormData) => {
      const response = await api.post(`/public/events/${rsvp.slug}/rsvp`, {
        guestName: String(form.get("guestName") ?? ""),
        guestPhone: String(form.get("guestPhone") ?? ""),
        guestEmail: String(form.get("guestEmail") ?? ""),
        attendanceStatus: String(form.get("attendanceStatus") ?? ""),
        numberOfGuests: Number(form.get("numberOfGuests") ?? 1),
        message: String(form.get("message") ?? ""),
        website: String(form.get("website") ?? ""),
      });
      return response.data.data.confirmationMessage as string;
    },
    onSuccess: (message) => setDone(message),
  });
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="visual-rsvp-layer" role="presentation" onClick={onClose}>
      <div
        className="visual-rsvp-panel"
        id="rsvp"
        role="dialog"
        aria-modal="true"
        aria-labelledby="visual-rsvp-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="visual-rsvp-head">
          <h2 id="visual-rsvp-title">Send your reply</h2>
          <button type="button" className="visual-rsvp-close" aria-label="Close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>
        {!enabled ? <p>Replies are turned off for this invitation.</p> : null}
        {enabled && done ? <p>{done}</p> : null}
        {enabled && !done ? (
          <form
            className="event-form visual-rsvp-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (rsvp.preview) {
                setDone(rsvp.confirmationMessage || "Preview only. Replies are saved on the published site.");
                return;
              }
              mutation.mutate(new FormData(event.currentTarget));
            }}
          >
            <input name="guestName" required placeholder="Your name" aria-label="Your name" />
            {rsvp.collectPhone
              ? <input name="guestPhone" required placeholder="Phone" aria-label="Phone" />
              : <input name="guestPhone" placeholder="Phone, if you want" aria-label="Phone" />}
            {rsvp.collectEmail
              ? <input name="guestEmail" type="email" required placeholder="Email" aria-label="Email" />
              : null}
            <select name="attendanceStatus" aria-label="Attendance" defaultValue="ACCEPTED">
              <option value="ACCEPTED">I will be there</option>
              <option value="DECLINED">I cannot come</option>
            </select>
            <input
              name="numberOfGuests"
              type="number"
              min={1}
              max={Math.max(1, rsvp.maxGuests ?? 4)}
              defaultValue={1}
              aria-label="Number of guests"
            />
            <textarea name="message" placeholder="A note" aria-label="A note" />
            <input name="website" tabIndex={-1} autoComplete="off" aria-hidden="true" style={{ position: "absolute", left: "-9999px" }} />
            <button type="submit">{mutation.isPending ? "Sending" : "Send reply"}</button>
            {mutation.isError ? <p>{errorMessage(mutation.error, "The reply was not saved.")}</p> : null}
          </form>
        ) : null}
      </div>
    </div>
  );
}

function designTokenStyle(document: EditorDocument): CSSProperties | undefined {
  const colors = document.designTokens?.colors ?? {};
  const fonts = document.designTokens?.fonts ?? {};
  const spacing = document.designTokens?.spacing ?? {};
  const entries = [
    ...Object.entries(colors).map(([key, value]) => [`--wa-color-${key}`, value] as const),
    ...Object.entries(fonts).map(([key, value]) => [`--wa-font-${key}`, value] as const),
    ...Object.entries(spacing).map(([key, value]) => [`--wa-space-${key}`, value] as const),
  ].filter(([, value]) => Boolean(value));
  if (!entries.length) return undefined;
  const style: Record<string, string> = {};
  for (const [key, value] of entries) style[key] = value;
  if (colors.background) style.background = colors.background;
  if (colors.text) style.color = colors.text;
  if (fonts.body) style.fontFamily = fonts.body;
  return style;
}

const HANDLES: Array<[Handle, string]> = [
  ["n", "Resize top"],
  ["s", "Resize bottom"],
  ["e", "Resize width"],
  ["w", "Resize from the left"],
  ["ne", "Resize top right"],
  ["nw", "Resize top left"],
  ["se", "Resize element"],
  ["sw", "Resize bottom left"],
];

function SelectionChrome({ selectedId, selectedIds, document, breakpoint, zoom, onResize, onGuides }: {
  selectedId: string;
  selectedIds: string[];
  document: EditorDocument;
  breakpoint: Breakpoint;
  zoom: number;
  onResize: (patches: { id: string; styles: Record<string, string> }[], coalesce: boolean) => void;
  onGuides?: (guides: GuideLine[]) => void;
}) {
  const frame = useRef<HTMLDivElement>(null);
  const [box, setBox] = useState<{ top: number; left: number; width: number; height: number } | null>(null);
  const node = findNode(document.root, selectedId);
  useLayoutEffect(() => {
    const page = frame.current?.parentElement;
    const element = page?.querySelector(`[data-node="${CSS.escape(selectedId)}"]`);
    if (!page || !(element instanceof HTMLElement)) {
      setBox(null);
      return;
    }
    const place = () => {
      const pageBox = page.getBoundingClientRect();
      const nodeBox = element.getBoundingClientRect();
      setBox({
        top: (nodeBox.top - pageBox.top) / zoom,
        left: (nodeBox.left - pageBox.left) / zoom,
        width: nodeBox.width / zoom,
        height: nodeBox.height / zoom,
      });
    };
    place();
    const observer = new ResizeObserver(place);
    observer.observe(element);
    return () => observer.disconnect();
  }, [selectedId, document, zoom]);
  if (!node) return null;
  if (!box) return <div ref={frame} className="visual-chrome" />;
  function startBox(element: HTMLElement) {
    const styles = stylesFor(node!, breakpoint);
    const rect = element.getBoundingClientRect();
    const basis = horizontalBasis(element, zoom);
    return {
      left: usedOffset(element, "left"),
      top: usedOffset(element, "top"),
      width: rect.width / zoom,
      height: rect.height / zoom,
      position: styles.position ?? "",
      transform: styles.transform ?? "",
      verticalCenter: centeredOn(element, "y"),
      horizontalCenter: centeredOn(element, "x"),
      basis,
    };
  }
  function track(event: ReactPointerEvent<HTMLButtonElement>, apply: (point: PointerEvent, start: ReturnType<typeof startBox>, moved: boolean) => void, finish?: () => void) {
    if (node?.locked) return;
    event.preventDefault();
    event.stopPropagation();
    const element = event.currentTarget.closest(".visual-page")?.querySelector(`[data-node="${CSS.escape(selectedId)}"]`);
    if (!(element instanceof HTMLElement)) return;
    const origin = startBox(element);
    const startX = event.clientX;
    const startY = event.clientY;
    let moved = false;
    const move = (point: PointerEvent) => {
      apply(point, { ...origin, dx: point.clientX - startX, dy: point.clientY - startY } as ReturnType<typeof startBox> & { dx: number; dy: number }, moved);
      moved = true;
    };
    const end = () => {
      finish?.();
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", end);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", end);
  }
  return (
    <div ref={frame} className={`visual-chrome${node.locked ? " is-locked" : ""}`} style={{ top: box.top, left: box.left, width: box.width, height: box.height }}>
      <button type="button" className="visual-move" aria-label="Move element" onClick={(event) => event.stopPropagation()} onPointerDown={(event) => {
        const ids = selectedIds.includes(selectedId) ? selectedIds.filter((id) => !findNode(document.root, id)?.locked) : [selectedId];
        const origins = new Map(ids.map((id) => {
          const element = frame.current?.parentElement?.querySelector(`[data-node="${CSS.escape(id)}"]`);
          const item = findNode(document.root, id);
          const position = item ? stylesFor(item, breakpoint).position ?? "" : "";
          return [id, element instanceof HTMLElement ? moveOrigin(element, position, zoom) : { left: 0, top: 0, width: 0, height: 0, position, basis: 0 }];
        }));
        const page = frame.current?.parentElement;
        const guides = page ? captureGuides(page, ids, zoom) : null;
        track(event, (_point, start, moved) => {
          const delta = start as ReturnType<typeof startBox> & { dx: number; dy: number };
          const aligned = guides ? snappedDelta(guides, selectedId, delta.dx / zoom, delta.dy / zoom) : { dx: delta.dx / zoom, dy: delta.dy / zoom, guides: [] as GuideLine[] };
          onGuides?.(aligned.guides);
          onResize(ids.map((id) => {
            const origin = origins.get(id) ?? start;
            return { id, styles: dragStyles(origin, aligned.dx, aligned.dy) };
          }), moved);
        }, () => onGuides?.([]));
      }}>{Math.round(box.width)} × {Math.round(box.height)}</button>
      {node.locked ? null : HANDLES.map(([handle, label]) => (
        <button key={handle} type="button" className={`visual-resize visual-resize-${handle}`} aria-label={label} onClick={(event) => event.stopPropagation()} onPointerDown={(event) => {
          if (node.locked) return;
          event.preventDefault();
          event.stopPropagation();
          const element = event.currentTarget.closest(".visual-page")?.querySelector(`[data-node="${CSS.escape(selectedId)}"]`);
          if (!(element instanceof HTMLElement)) return;
          const origin = startBox(element);
          const rect = element.getBoundingClientRect();
          const box = { left: rect.left / zoom, top: rect.top / zoom, width: rect.width / zoom, height: rect.height / zoom };
          const startX = event.clientX;
          const startY = event.clientY;
          let moved = false;
          let latest: Record<string, string> | null = null;
          let scheduled = 0;
          const publish = () => {
            if (!latest) return;
            const styles = latest;
            latest = null;
            onResize([{ id: selectedId, styles }], moved);
            moved = true;
          };
          const move = (point: PointerEvent) => {
            const dx = (point.clientX - startX) / zoom;
            const dy = (point.clientY - startY) / zoom;
            const basis = origin.basis || horizontalBasis(element, zoom);
            const size = resizeStyles({ ...origin, ...box }, dx, dy, handle, point.shiftKey, basis);
            delete size.left;
            delete size.top;
            delete size.position;
            writeStyles(element, size);
            const measured = measuredBox(element, zoom);
            const drift = edgeDrift(box, measured, handle);
            const pin = { ...size };
            if (Math.abs(drift.x) > 0.5 || Math.abs(drift.y) > 0.5) {
              pin.position = origin.position === "absolute" || origin.position === "fixed" || origin.position === "sticky" ? origin.position : "relative";
              element.style.position = pin.position;
            }
            if (Math.abs(drift.x) > 0.5) {
              const left = usedOffset(element, "left") + drift.x;
              element.style.left = `${Math.round(left)}px`;
              pin.left = basis > 0 ? percentOf(left, basis) : `${Math.round(left)}px`;
            }
            if (Math.abs(drift.y) > 0.5) {
              const top = usedOffset(element, "top") + drift.y;
              pin.top = `${Math.round(top)}px`;
              element.style.top = pin.top;
            }
            const again = edgeDrift(box, measuredBox(element, zoom), handle);
            if (Math.abs(again.x) > 0.5) {
              const left = usedOffset(element, "left") + again.x;
              element.style.left = `${Math.round(left)}px`;
              pin.left = basis > 0 ? percentOf(left, basis) : `${Math.round(left)}px`;
              pin.position = pin.position ?? "relative";
            }
            if (Math.abs(again.y) > 0.5) {
              const top = usedOffset(element, "top") + again.y;
              pin.top = `${Math.round(top)}px`;
              element.style.top = pin.top;
              pin.position = pin.position ?? "relative";
            }
            placeChrome(frame.current, element, zoom);
            latest = pin;
            if (!scheduled) scheduled = window.requestAnimationFrame(() => { scheduled = 0; publish(); });
          };
          const end = () => {
            if (scheduled) window.cancelAnimationFrame(scheduled);
            publish();
            window.removeEventListener("pointermove", move);
            window.removeEventListener("pointerup", end);
          };
          window.addEventListener("pointermove", move);
          window.addEventListener("pointerup", end);
        }} />
      ))}
      {node.locked ? null : (
        <button type="button" className="visual-rotate" aria-label="Rotate element" onClick={(event) => event.stopPropagation()} onPointerDown={(event) => track(event, (point, start, moved) => {
          const element = frame.current;
          if (!element) return;
          const rect = element.getBoundingClientRect();
          const centerX = rect.left + rect.width / 2;
          const centerY = rect.top + rect.height / 2;
          const startAngle = Math.atan2(event.clientY - centerY, event.clientX - centerX);
          const next = readRotate(start.transform) + (Math.atan2(point.clientY - centerY, point.clientX - centerX) - startAngle) * 180 / Math.PI;
          const snapped = point.shiftKey ? Math.round(next / 15) * 15 : next;
          onResize([{ id: selectedId, styles: { transform: rotateTransform(start.transform, snapped) } }], moved);
        })} />
      )}
    </div>
  );
}

function EmptyPicture({ edit, style, onFile }: {
  edit: { className?: string; onDrop?: (event: DragEvent) => void; onDragOver?: (event: DragEvent) => void };
  style: CSSProperties;
  onFile?: (file: File) => Promise<void>;
}) {
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  async function send(file: File | undefined) {
    if (!file || !onFile) return;
    setBusy(true);
    try {
      await onFile(file);
      setMessage("");
    } catch (error) {
      setMessage(errorMessage(error, "The picture was not saved."));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div
      {...edit}
      className={[edit.className, "visual-picture-empty"].filter(Boolean).join(" ")}
      style={{ ...style, minHeight: 160 }}
      onDragOver={(event) => { event.preventDefault(); edit.onDragOver?.(event); }}
      onDrop={(event) => {
        const file = event.dataTransfer.files?.[0];
        if (file) {
          event.preventDefault();
          event.stopPropagation();
          void send(file);
          return;
        }
        edit.onDrop?.(event);
      }}
    >
      {onFile ? (
        <label className="picture-upload">
          {busy ? "Uploading" : "Upload a picture"}
          <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" aria-label="Upload a picture" onChange={(event) => void send(event.target.files?.[0])} />
        </label>
      ) : null}
      {message ? <span>{message}</span> : null}
    </div>
  );
}

function CarouselFrame({ node, edit, style, mediaEventId, breakpoint, paused, onUploadPicture, onAddPictures, overlay }: {
  node: EditorNode;
  edit: {
    className?: string;
    onPointerDown?: (event: ReactPointerEvent<HTMLElement>) => void;
  };
  style: CSSProperties;
  mediaEventId?: string;
  breakpoint: Breakpoint;
  paused: boolean;
  onUploadPicture?: (nodeId: string, file: File) => Promise<void>;
  onAddPictures?: (parentId: string, files: File[]) => Promise<void>;
  overlay?: ReactNode;
}) {
  const slides = node.children.filter((child) => child.type === "IMAGE");
  const local = propsFor(node, breakpoint);
  const preset = String(local.preset || "minimal");
  const view = Math.min(3, Math.max(1, Number(local.perView) || 1));
  const [index, setIndex] = useState(0);
  const [hovering, setHovering] = useState(false);
  const count = Math.max(slides.length, 1);
  const current = ((index % count) + count) % count;
  useEffect(() => {
    if (paused || hovering || Number(local.autoplay) === 0 || slides.length < 2) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const interval = Math.min(20000, Math.max(2000, Number(local.interval) || 5000));
    const timer = window.setInterval(() => setIndex((value) => value + 1), interval);
    return () => window.clearInterval(timer);
  }, [paused, hovering, local.autoplay, local.interval, slides.length]);
  const start = useRef(0);
  const radius = typeof style.borderRadius === "string" ? style.borderRadius : "";
  const frame = { ...style };
  delete frame.borderRadius;
  delete frame.overflow;
  const picture = radius.trim() ? { borderRadius: radius } : undefined;
  return (
    <section
      {...edit}
      className={`${edit.className ?? ""} prism-carousel is-${preset}`.trim()}
      style={frame}
      aria-roledescription="carousel"
      aria-label={node.name || "Carousel"}
      onMouseEnter={() => setHovering(true)}
      onMouseLeave={() => setHovering(false)}
      onPointerDown={(event) => {
        edit.onPointerDown?.(event);
        if (!paused) start.current = event.clientX;
      }}
      onPointerUp={(event) => {
        if (paused || slides.length < 2) return;
        const delta = event.clientX - start.current;
        if (Math.abs(delta) > 40) setIndex((value) => value + (delta < 0 ? 1 : -1));
      }}
    >
      {overlay}
      <div className="prism-track" style={preset === "fade" ? undefined : { transform: `translateX(-${(current * 100) / view}%)` }}>
        {(slides.length ? slides : [node]).map((slide, position) => {
          const media = String(slide.props.mediaId ?? "");
          const alt = String(slide.props.alt ?? "");
          return (
            <figure key={slide.id} className={position === current ? "prism-slide is-active" : "prism-slide"} style={{ flexBasis: `${100 / view}%`, background: picture ? "transparent" : undefined }}>
              {media ? <img src={mediaSrc(media, mediaEventId)} alt={alt} style={picture} /> : slide.type === "IMAGE" && onUploadPicture ? (
                <label className="picture-upload" onClick={(event) => event.stopPropagation()}>
                  Upload a picture
                  <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" aria-label={`Upload a picture for ${slide.name || "this slide"}`} onChange={(event) => {
                    const file = event.target.files?.[0];
                    event.target.value = "";
                    if (file) void onUploadPicture(slide.id, file);
                  }} />
                </label>
              ) : onAddPictures ? (
                <label className="picture-upload" onClick={(event) => event.stopPropagation()}>
                  Upload a picture
                  <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple aria-label="Upload pictures for the carousel" onChange={(event) => {
                    const files = [...(event.target.files ?? [])];
                    event.target.value = "";
                    if (files.length) void onAddPictures(node.id, files);
                  }} />
                </label>
              ) : <span>Add a picture</span>}
            </figure>
          );
        })}
      </div>
      {slides.length > 1 ? (
        <>
          <button type="button" className="prism-nav is-previous" aria-label="Previous slide" onClick={(event) => { event.stopPropagation(); setIndex((value) => value - 1); }}>‹</button>
          <button type="button" className="prism-nav is-next" aria-label="Next slide" onClick={(event) => { event.stopPropagation(); setIndex((value) => value + 1); }}>›</button>
          <div className="prism-dots">
            {slides.map((slide, position) => (
              <button key={slide.id} type="button" aria-label={`Show slide ${position + 1}`} aria-current={position === current} onClick={(event) => { event.stopPropagation(); setIndex(position); }} />
            ))}
          </div>
        </>
      ) : null}
    </section>
  );
}

function captureGuides(page: HTMLElement, ids: string[], zoom: number) {
  const moving = ids
    .map((id) => page.querySelector(`[data-node="${CSS.escape(id)}"]`))
    .filter((element): element is HTMLElement => element instanceof HTMLElement);
  const starts = new Map<string, Frame>();
  moving.forEach((element) => {
    const id = element.getAttribute("data-node");
    if (id) starts.set(id, pageFrame(page, element, zoom));
  });
  const anchors: Frame[] = [];
  page.querySelectorAll("[data-node]").forEach((node) => {
    if (!(node instanceof HTMLElement)) return;
    if (moving.some((element) => element === node || element.contains(node))) return;
    anchors.push(pageFrame(page, node, zoom));
  });
  const parent = moving[0]?.parentElement;
  const middle = parent ? contentFrame(page, parent, zoom) : undefined;
  return { starts, anchors, middle };
}

function snappedDelta(captured: { starts: Map<string, Frame>; anchors: Frame[]; middle?: Frame }, id: string, dx: number, dy: number) {
  const start = captured.starts.get(id);
  if (!start) return { dx, dy, guides: [] as GuideLine[] };
  const aligned = alignMove(shiftFrame(start, dx, dy), captured.anchors, 6, captured.middle);
  return { dx: dx + aligned.dx, dy: dy + aligned.dy, guides: aligned.guides };
}

function dragStyles(origin: ReturnType<typeof moveOrigin>, dx: number, dy: number) {
  return moveStyles(origin, dx, dy, origin.basis);
}

function writeStyles(element: HTMLElement, styles: Record<string, string>) {
  const target = element.style as unknown as Record<string, string>;
  for (const [key, value] of Object.entries(styles)) target[key] = value;
}

function measuredBox(element: HTMLElement, zoom: number) {
  const box = element.getBoundingClientRect();
  const scale = zoom || 1;
  return { left: box.left / scale, top: box.top / scale, width: box.width / scale, height: box.height / scale };
}

function placeChrome(chrome: HTMLDivElement | null, element: HTMLElement, zoom: number) {
  const page = element.closest(".visual-page");
  if (!chrome || !(page instanceof HTMLElement)) return;
  const pageBox = page.getBoundingClientRect();
  const box = element.getBoundingClientRect();
  const scale = zoom || 1;
  chrome.style.top = `${(box.top - pageBox.top) / scale}px`;
  chrome.style.left = `${(box.left - pageBox.left) / scale}px`;
  chrome.style.width = `${box.width / scale}px`;
  chrome.style.height = `${box.height / scale}px`;
}

function moveOrigin(element: HTMLElement, position: string, zoom: number) {
  return {
    left: usedOffset(element, "left"),
    top: usedOffset(element, "top"),
    width: 0,
    height: 0,
    position,
    basis: horizontalBasis(element, zoom),
  };
}

function horizontalBasis(element: HTMLElement, zoom: number) {
  const parent = element.parentElement;
  const scale = zoom || 1;
  if (!parent) return Math.max(1, element.getBoundingClientRect().width / scale);
  const display = getComputedStyle(parent).display;
  if (display === "grid" || display === "inline-grid") {
    const previous = element.style.width;
    element.style.width = "100%";
    const width = element.getBoundingClientRect().width / scale;
    element.style.width = previous;
    return Math.max(1, width);
  }
  return contentBoxWidth(parent);
}

const SKIPPED_STYLE = new Set<string>(["fontId", "fontRole", ...BACKGROUND_META_KEYS]);

function css(styles: Record<string, string>): CSSProperties {
  const next: Record<string, string> = {};
  let stroke = "";
  let gradient = "";
  for (const [key, value] of Object.entries(styles)) {
    if (!value || SKIPPED_STYLE.has(key) || value.includes("url(") || value.includes("<")) continue;
    if (key === "fontFamily") {
      next.fontFamily = stack(value);
      continue;
    }
    if (key === "textStroke") {
      stroke = value;
      continue;
    }
    if (key === "textGradient") {
      gradient = value;
      continue;
    }
    next[key] = value;
  }
  if (stroke) next.WebkitTextStroke = stroke;
  if (gradient) {
    next.backgroundImage = gradient;
    next.WebkitBackgroundClip = "text";
    next.backgroundClip = "text";
    next.WebkitTextFillColor = "transparent";
  }
  return next as CSSProperties;
}

/** Turn circular text panels into readable rounded cards (editor + published). */
function softenTextEllipse(node: EditorNode, style: CSSProperties): CSSProperties {
  if (node.type === "IMAGE" || node.type === "BUTTON") return style;
  const radius = typeof style.borderRadius === "string" ? style.borderRadius : "";
  if (!isEllipseRadius(radius) || !holdsReadableText(node)) return style;
  const next: CSSProperties = {
    ...style,
    borderRadius: "24px",
    textAlign: "center",
    overflow: "hidden",
  };
  const padding = typeof style.padding === "string" ? style.padding : "";
  if (!padding || thinPadding(padding)) next.padding = "36px 32px";
  if (!style.display) {
    next.display = "flex";
    next.flexDirection = "column";
    next.justifyContent = "center";
    next.alignItems = "center";
    next.gap = "12px";
  }
  return next;
}

function isEllipseRadius(radius: string) {
  const value = radius.trim().toLowerCase();
  if (!value.endsWith("%")) return false;
  const amount = Number.parseFloat(value);
  return Number.isFinite(amount) && amount >= 40;
}

function holdsReadableText(node: EditorNode): boolean {
  if (["HEADING", "PARAGRAPH", "QUOTE", "FAQ_ITEM", "TIMELINE_ITEM"].includes(node.type)) {
    return Boolean(String(node.props.text ?? node.props.title ?? "").trim());
  }
  return node.children.some(holdsReadableText);
}

function thinPadding(padding: string) {
  return padding.split(/\s+/).some((part) => {
    const amount = Number.parseFloat(part);
    if (!Number.isFinite(amount)) return false;
    return part.includes("%") ? amount < 12 : amount < 24;
  });
}

/** Resolve backgroundMediaId to a safe url() and prepare stacking for overlays. */
function applySurface(style: CSSProperties, styles: Record<string, string>, mediaEventId?: string): CSSProperties {
  const next: CSSProperties = { ...style };
  const mediaId = (styles.backgroundMediaId ?? "").trim();
  const src = mediaSrc(mediaId, mediaEventId);
  const overlay = hasOverlay(styles);
  if (src && !styles.textGradient) {
    next.backgroundImage = `url("${src}")`;
    if (!next.backgroundSize) next.backgroundSize = styles.backgroundSize || "cover";
    if (!next.backgroundPosition) next.backgroundPosition = styles.backgroundPosition || "center";
    if (!next.backgroundRepeat) next.backgroundRepeat = styles.backgroundRepeat || "no-repeat";
    if (!next.backgroundAttachment) next.backgroundAttachment = styles.backgroundAttachment || "scroll";
  }
  if (src || overlay) {
    const position = typeof next.position === "string" ? next.position : "";
    if (!position || position === "static") next.position = "relative";
    next.isolation = "isolate";
  }
  return next;
}

function mediaSrc(value: string, eventId?: string) {
  if (!value) return "";
  if (value.startsWith("/") || value.startsWith("http")) return value;
  return eventId ? `/api/v1/events/${eventId}/media/${value}` : `/api/v1/public/media/${value}`;
}
