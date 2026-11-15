import { useQuery } from "@tanstack/react-query";
import { Button } from "../../components/ui";
import { api } from "../../services/api";
import type { Envelope } from "../../types";
import { durationToSpeed, newMarqueeItem, normalizeMarqueeItems, speedToDuration, type MarqueeItem, type PropValue } from "./richText";
import { SimpleLinkFields } from "./LinkEditor";

export type MarqueePatch = Record<string, PropValue>;

export function MarqueeField({
  props,
  eventId,
  onChange,
}: {
  props: Record<string, PropValue | undefined>;
  eventId: string;
  onChange: (patch: MarqueePatch) => void;
}) {
  const items = normalizeMarqueeItems(props.items);
  const duration = Number(props.duration ?? 28);
  const speed = durationToSpeed(duration);
  const media = useQuery({
    queryKey: ["media", eventId],
    queryFn: async () => (await api.get<Envelope<{ id: string; mediaType: string; originalFilename: string }[]>>(`/events/${eventId}/media`)).data.data,
    retry: false,
  });
  const pictures = (media.data ?? []).filter((file) => file.mediaType === "IMAGE");

  function setItems(next: MarqueeItem[]) {
    onChange({
      items: next,
      text: next.filter((item) => item.kind === "text").map((item) => item.text ?? "").join(" · "),
    });
  }

  function patchItem(id: string, patch: Partial<MarqueeItem>) {
    setItems(items.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  }

  return (
    <div className="marquee-field">
      <p className="studio-label">Movement</p>
      <label>Direction
        <select value={String(props.direction ?? "left")} onChange={(event) => onChange({ direction: event.target.value })}>
          <option value="left">Left</option>
          <option value="right">Right</option>
          <option value="up">Up</option>
          <option value="down">Down</option>
        </select>
      </label>
      <label>Speed
        <input
          type="range"
          min={1}
          max={100}
          value={speed}
          onChange={(event) => onChange({ duration: speedToDuration(Number(event.target.value)), speed: Number(event.target.value) })}
        />
      </label>
      <label>Duration (seconds)
        <input
          type="number"
          min={4}
          max={120}
          value={duration}
          onChange={(event) => {
            const next = Math.min(120, Math.max(4, Number(event.target.value) || 28));
            onChange({ duration: next, speed: durationToSpeed(next) });
          }}
        />
      </label>
      <label>Item spacing (px)
        <input type="number" min={0} max={160} value={Number(props.gap ?? 32)} onChange={(event) => onChange({ gap: Number(event.target.value) || 0 })} />
      </label>
      <label>Delay (seconds)
        <input type="number" min={0} max={30} step={0.5} value={Number(props.delay ?? 0)} onChange={(event) => onChange({ delay: Number(event.target.value) || 0 })} />
      </label>
      <label className="check-line">Continuous loop
        <input type="checkbox" checked={Number(props.loop ?? 1) !== 0} onChange={(event) => onChange({ loop: event.target.checked ? 1 : 0 })} />
      </label>
      <label className="check-line">Pause on hover
        <input type="checkbox" checked={Number(props.pauseOnHover ?? 1) !== 0} onChange={(event) => onChange({ pauseOnHover: event.target.checked ? 1 : 0 })} />
      </label>
      <label className="check-line">Pause on click
        <input type="checkbox" checked={Number(props.pauseOnClick ?? 0) !== 0} onChange={(event) => onChange({ pauseOnClick: event.target.checked ? 1 : 0 })} />
      </label>
      <label className="check-line">Animation enabled
        <input type="checkbox" checked={Number(props.animationEnabled ?? 1) !== 0} onChange={(event) => onChange({ animationEnabled: event.target.checked ? 1 : 0 })} />
      </label>
      <p className="studio-hint">Respects prefers-reduced-motion. Animation pauses when the marquee is off-screen.</p>

      <p className="studio-label">Items</p>
      <div className="wizard-actions">
        <Button type="button" tone="secondary" onClick={() => setItems([...items, newMarqueeItem("text")])}>Add text</Button>
        <Button type="button" tone="secondary" onClick={() => setItems([...items, newMarqueeItem("image")])}>Add image</Button>
      </div>
      {items.map((item, index) => (
        <div key={item.id} className="marquee-item-editor">
          <div className="wizard-actions">
            <strong>{item.kind === "image" ? "Image" : "Text"} {index + 1}</strong>
            <Button type="button" tone="secondary" disabled={index === 0} onClick={() => {
              const next = [...items];
              [next[index - 1], next[index]] = [next[index], next[index - 1]];
              setItems(next);
            }}>Up</Button>
            <Button type="button" tone="secondary" disabled={index === items.length - 1} onClick={() => {
              const next = [...items];
              [next[index + 1], next[index]] = [next[index], next[index + 1]];
              setItems(next);
            }}>Down</Button>
            <Button type="button" tone="secondary" onClick={() => setItems(items.filter((row) => row.id !== item.id))}>Remove</Button>
          </div>
          {item.kind === "text" ? (
            <label>Text
              <input
                value={item.text ?? ""}
                onChange={(event) => {
                  const text = event.target.value;
                  const linked = item.richText?.[0]?.href;
                  patchItem(item.id, {
                    text,
                    richText: [{ text, ...(linked ? { href: linked, target: item.richText?.[0]?.target, title: item.richText?.[0]?.title } : {}) }],
                  });
                }}
              />
            </label>
          ) : (
            <>
              <label>Picture
                <select value={String(item.mediaId ?? "")} onChange={(event) => patchItem(item.id, { mediaId: event.target.value })}>
                  <option value="">Choose a picture</option>
                  {pictures.map((file) => (
                    <option key={file.id} value={file.id}>{file.originalFilename || file.id}</option>
                  ))}
                </select>
              </label>
              <label>Alt text
                <input value={String(item.alt ?? "")} onChange={(event) => patchItem(item.id, { alt: event.target.value })} />
              </label>
            </>
          )}
          <SimpleLinkFields
            href={String(item.href ?? item.richText?.[0]?.href ?? "")}
            target={item.target ?? item.richText?.[0]?.target}
            title={item.title ?? item.richText?.[0]?.title}
            onChange={(patch) => {
              const href = patch.href ?? item.href ?? "";
              if (item.kind === "text") {
                const text = item.text ?? "";
                patchItem(item.id, {
                  href: href || undefined,
                  target: (patch.linkTarget as "_blank" | "_self") || item.target,
                  title: patch.linkTitle ?? item.title,
                  richText: href
                    ? [{ text, href, target: (patch.linkTarget as "_blank" | "_self") || "_blank", title: patch.linkTitle || undefined }]
                    : [{ text }],
                });
              } else {
                patchItem(item.id, {
                  href: href || undefined,
                  target: (patch.linkTarget as "_blank" | "_self") || item.target,
                  title: patch.linkTitle ?? item.title,
                });
              }
            }}
          />
        </div>
      ))}
    </div>
  );
}
