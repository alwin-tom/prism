import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { linkRel, normalizeMarqueeItems, type MarqueeItem, type PropValue } from "./richText";
import { renderRuns, linkStyleFromProps } from "./RichTextContent";
import { effectiveRuns } from "./richText";

function mediaSrc(mediaId: string, eventId?: string) {
  if (!mediaId || !eventId) return "";
  return `/api/v1/events/${eventId}/media/${mediaId}/content`;
}

function ItemFace({
  item,
  editing,
  mediaEventId,
  linkStyle,
}: {
  item: MarqueeItem;
  editing: boolean;
  mediaEventId?: string;
  linkStyle: ReturnType<typeof linkStyleFromProps>;
}) {
  if (item.kind === "image") {
    const src = mediaSrc(String(item.mediaId ?? ""), mediaEventId);
    const img = src
      ? <img src={src} alt={item.alt ?? ""} draggable={false} className="marquee-image" />
      : <span className="marquee-image is-empty">Image</span>;
    if (!item.href || editing) {
      return <span className="marquee-item" data-kind="image">{img}</span>;
    }
    return (
      <a className="marquee-item" data-kind="image" href={item.href} target={item.target === "_blank" ? "_blank" : undefined} rel={linkRel(item.target)} title={item.title}>
        {img}
      </a>
    );
  }
  const runs = item.richText?.length ? effectiveRuns({ richText: item.richText, text: item.text ?? "" }) : effectiveRuns({ text: item.text ?? "" });
  const body = <span className="marquee-item" data-kind="text">{renderRuns(runs, editing, linkStyle)}</span>;
  if (item.href && !runs.some((run) => run.href) && !editing) {
    return (
      <a className="marquee-item-link" href={item.href} target={item.target === "_blank" ? "_blank" : undefined} rel={linkRel(item.target)} title={item.title}>
        {body}
      </a>
    );
  }
  return body;
}

export function MarqueeFrame({
  props,
  style,
  edit,
  overlay,
  mediaEventId,
  editing,
}: {
  props: Record<string, PropValue | undefined>;
  style: CSSProperties;
  edit: Record<string, unknown>;
  overlay?: ReactNode;
  mediaEventId?: string;
  editing: boolean;
}) {
  const items = useMemo(() => {
    const parsed = normalizeMarqueeItems(props.items);
    return parsed.length ? parsed : [{ id: "mq-default", kind: "text" as const, text: String(props.text ?? "Announcement"), richText: [{ text: String(props.text ?? "Announcement") }] }];
  }, [props.items, props.text]);
  const direction = String(props.direction ?? "left").toLowerCase();
  const duration = Math.min(120, Math.max(4, Number(props.duration ?? 28)));
  const gap = Math.min(160, Math.max(0, Number(props.gap ?? 32)));
  const delay = Math.min(30, Math.max(0, Number(props.delay ?? 0)));
  const loop = Number(props.loop ?? 1) !== 0;
  const pauseOnHover = Number(props.pauseOnHover ?? 1) !== 0;
  const pauseOnClick = Number(props.pauseOnClick ?? 0) !== 0;
  const animationEnabled = Number(props.animationEnabled ?? 1) !== 0;
  const linkStyle = linkStyleFromProps(props);
  const [paused, setPaused] = useState(false);
  const [reduced, setReduced] = useState(false);
  const [inView, setInView] = useState(true);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const sync = () => setReduced(mq.matches);
    sync();
    mq.addEventListener?.("change", sync);
    return () => mq.removeEventListener?.("change", sync);
  }, []);

  useEffect(() => {
    const node = rootRef.current;
    if (!node || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting), { threshold: 0.05 });
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const vertical = direction === "up" || direction === "down";
  const animate = animationEnabled && !reduced && !editing && inView && !paused;
  const axis = vertical ? "Y" : "X";
  const sign = direction === "left" || direction === "up" ? -1 : 1;

  const group = (
    <div className="marquee-group" style={{ gap }}>
      {items.map((item) => (
        <ItemFace key={item.id} item={item} editing={editing} mediaEventId={mediaEventId} linkStyle={linkStyle} />
      ))}
    </div>
  );

  return (
    <div
      {...edit}
      ref={rootRef}
      className={["visual-marquee", pauseOnHover ? "pause-hover" : "", vertical ? "is-vertical" : "is-horizontal"].filter(Boolean).join(" ")}
      style={style}
      data-kind="MARQUEE"
      onClick={(event) => {
        if (!pauseOnClick || editing) return;
        event.stopPropagation();
        setPaused((value) => !value);
      }}
    >
      {overlay}
      {!animate ? (
        <div className="marquee-static" role="list">
          {items.map((item) => (
            <div key={item.id} role="listitem">
              <ItemFace item={item} editing={editing} mediaEventId={mediaEventId} linkStyle={linkStyle} />
            </div>
          ))}
        </div>
      ) : (
        <div className="marquee-viewport">
          <div
            className="marquee-track"
            style={{
              gap,
              animationName: `marquee-shift-${axis}${sign < 0 ? "neg" : "pos"}`,
              animationDuration: `${duration}s`,
              animationTimingFunction: "linear",
              animationIterationCount: loop ? "infinite" : 1,
              animationDelay: `${delay}s`,
              animationPlayState: paused ? "paused" : "running",
            }}
          >
            <div className="marquee-copy">{group}</div>
            <div className="marquee-copy" aria-hidden="true">{group}</div>
          </div>
        </div>
      )}
    </div>
  );
}
