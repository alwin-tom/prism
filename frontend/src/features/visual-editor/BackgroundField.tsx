import { useQuery } from "@tanstack/react-query";
import { api } from "../../services/api";
import type { Envelope } from "../../types";
import {
  ATTACHMENT_OPTIONS,
  OVERLAY_TYPES,
  POSITION_OPTIONS,
  REPEAT_OPTIONS,
  SIZE_OPTIONS,
  backgroundPatch,
  overlayCss,
  readBackground,
  type AttachmentOption,
  type OverlayType,
  type PositionOption,
  type RepeatOption,
  type SizeOption,
} from "./background";
import type { StyleMap } from "./document";

type Picture = { id: string; mediaType: string; url: string; originalFilename: string };
type Generated = { id: string; mediaId: string | null; status: string; assetKey: string; purpose: string | null; url: string | null };

export function BackgroundField({
  eventId,
  styles,
  onChange,
  onUpload,
}: {
  eventId: string;
  styles: StyleMap;
  onChange: (patch: StyleMap, coalesce?: boolean) => void;
  onUpload: (file: File) => Promise<string>;
}) {
  const current = readBackground(styles);
  const media = useQuery({
    queryKey: ["media", eventId],
    queryFn: async () => (await api.get<Envelope<Picture[]>>(`/events/${eventId}/media`)).data.data,
    retry: false,
  });
  const features = useQuery({
    queryKey: ["features"],
    queryFn: async () => (await api.get<Envelope<{ imageGenerationEnabled?: boolean }>>("/config/features")).data.data,
  });
  const generated = useQuery({
    queryKey: ["generated-assets", eventId],
    enabled: features.data?.imageGenerationEnabled === true,
    queryFn: async () => (await api.get<Envelope<Generated[]>>(`/events/${eventId}/generated-assets`)).data.data,
    retry: false,
  });
  const pictures = (media.data ?? []).filter((file) => file.mediaType === "IMAGE");
  const generatedReady = (generated.data ?? []).filter((asset) => asset.status === "COMPLETED" && asset.mediaId);
  const previewUrl = pictures.find((file) => file.id === current.mediaId)?.url
    ?? generatedReady.find((asset) => asset.mediaId === current.mediaId)?.url
    ?? "";
  const overlay = overlayCss({
    backgroundOverlayType: current.overlayType,
    backgroundOverlayColor: current.overlayColor,
    backgroundOverlayOpacity: String(current.overlayOpacity),
    backgroundOverlayAngle: String(current.overlayAngle),
    backgroundOverlayEnd: current.overlayEnd,
  });

  function paint(partial: Parameters<typeof backgroundPatch>[0], coalesce = false) {
    onChange(backgroundPatch(partial), coalesce);
  }

  return (
    <div className="studio-background">
      <p className="studio-label">Background image</p>
      <div
        className="studio-bg-preview"
        style={{
          backgroundColor: styles.background?.includes("gradient") ? undefined : (styles.background || "#f4f4f5"),
          backgroundImage: previewUrl ? `url("${previewUrl}")` : undefined,
          backgroundSize: current.sizeMode === "custom" ? current.sizeCustom : current.sizeMode,
          backgroundPosition: current.positionMode === "custom" ? current.positionCustom : current.positionMode,
          backgroundRepeat: current.repeat,
        }}
      >
        {overlay ? <span className="visual-bg-overlay" aria-hidden style={{ background: overlay.background, opacity: overlay.opacity }} /> : null}
      </div>
      <label className="picture-upload">
        {current.mediaId ? "Replace background" : "Upload background"}
        <input
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          aria-label={current.mediaId ? "Replace background image" : "Upload background image"}
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = "";
            if (!file) return;
            void onUpload(file).then((mediaId) => paint({
              mediaId,
              sizeMode: current.sizeMode === "custom" ? "custom" : current.sizeMode || "cover",
              sizeCustom: current.sizeCustom,
              positionMode: current.positionMode === "custom" ? "custom" : current.positionMode || "center",
              positionCustom: current.positionCustom,
              repeat: current.repeat || "no-repeat",
              attachment: current.attachment || "scroll",
            }));
          }}
        />
      </label>
      {current.mediaId ? (
        <button type="button" onClick={() => paint({ clear: true })}>Remove background image</button>
      ) : null}
      {generatedReady.length > 0 ? (
        <>
          <p className="studio-label">Generated</p>
          <div className="picture-grid">
            {generatedReady.map((asset) => (
              <button
                key={asset.id}
                type="button"
                aria-label={asset.purpose || asset.assetKey}
                aria-pressed={asset.mediaId === current.mediaId}
                onClick={() => asset.mediaId && paint({ mediaId: asset.mediaId, sizeMode: "cover", positionMode: "center", repeat: "no-repeat", attachment: "scroll" })}
              >
                {asset.url ? <img src={asset.url} alt="" /> : null}
              </button>
            ))}
          </div>
        </>
      ) : null}
      {pictures.length > 0 ? (
        <div className="picture-grid">
          {pictures.map((file) => (
            <button
              key={file.id}
              type="button"
              aria-label={file.originalFilename || "Use this picture"}
              aria-pressed={file.id === current.mediaId}
              onClick={() => paint({ mediaId: file.id, sizeMode: current.mediaId ? current.sizeMode : "cover", positionMode: current.mediaId ? current.positionMode : "center", repeat: current.repeat || "no-repeat", attachment: current.attachment || "scroll" })}
            >
              <img src={file.url} alt="" />
            </button>
          ))}
        </div>
      ) : null}
      {current.mediaId ? (
        <>
          <label>Size
            <select value={current.sizeMode} onChange={(event) => paint({ sizeMode: event.target.value as SizeOption, sizeCustom: current.sizeCustom })}>
              {SIZE_OPTIONS.map((option) => <option key={option} value={option}>{labelize(option)}</option>)}
            </select>
          </label>
          {current.sizeMode === "custom" ? (
            <label>Custom size
              <input value={current.sizeCustom} placeholder="100% 100%" onChange={(event) => paint({ sizeMode: "custom", sizeCustom: event.target.value })} />
            </label>
          ) : null}
          <label>Position
            <select value={current.positionMode} onChange={(event) => paint({ positionMode: event.target.value as PositionOption, positionCustom: current.positionCustom })}>
              {POSITION_OPTIONS.map((option) => <option key={option} value={option}>{labelize(option)}</option>)}
            </select>
          </label>
          {current.positionMode === "custom" ? (
            <label>Custom position
              <input value={current.positionCustom} placeholder="50% 50%" onChange={(event) => paint({ positionMode: "custom", positionCustom: event.target.value })} />
            </label>
          ) : null}
          <label>Repeat
            <select value={current.repeat} onChange={(event) => paint({ repeat: event.target.value as RepeatOption })}>
              {REPEAT_OPTIONS.map((option) => <option key={option} value={option}>{labelize(option)}</option>)}
            </select>
          </label>
          <label>Attachment
            <select value={current.attachment} onChange={(event) => paint({ attachment: event.target.value as AttachmentOption })}>
              {ATTACHMENT_OPTIONS.map((option) => <option key={option} value={option}>{labelize(option)}</option>)}
            </select>
          </label>
        </>
      ) : null}
      <p className="studio-label">Overlay</p>
      <label>Type
        <select value={current.overlayType} onChange={(event) => paint({ overlayType: event.target.value as OverlayType })}>
          {OVERLAY_TYPES.map((option) => <option key={option} value={option}>{labelize(option)}</option>)}
        </select>
      </label>
      {current.overlayType !== "none" ? (
        <>
          <label>Overlay colour
            <span className="color-row">
              <input type="color" aria-label="Overlay colour" value={toHex(current.overlayColor)} onChange={(event) => paint({ overlayColor: event.target.value }, true)} />
              <input value={current.overlayColor} onChange={(event) => paint({ overlayColor: event.target.value })} />
            </span>
          </label>
          <label>Overlay opacity
            <span className="studio-slider">
              <input
                type="range"
                min={0}
                max={100}
                aria-label="Overlay opacity"
                value={current.overlayOpacity}
                onChange={(event) => paint({ overlayOpacity: Number(event.target.value) }, true)}
              />
              <span>{current.overlayOpacity}%</span>
            </span>
          </label>
          {current.overlayType === "gradient" ? (
            <>
              <label>Gradient angle
                <span className="studio-slider">
                  <input
                    type="range"
                    min={0}
                    max={360}
                    aria-label="Overlay gradient angle"
                    value={current.overlayAngle}
                    onChange={(event) => paint({ overlayAngle: Number(event.target.value) }, true)}
                  />
                  <span>{current.overlayAngle}°</span>
                </span>
              </label>
              <label>End colour
                <span className="color-row">
                  <input type="color" aria-label="Overlay end colour" value={toHex(current.overlayEnd)} onChange={(event) => paint({ overlayEnd: event.target.value }, true)} />
                  <input value={current.overlayEnd} onChange={(event) => paint({ overlayEnd: event.target.value })} />
                </span>
              </label>
            </>
          ) : null}
        </>
      ) : null}
    </div>
  );
}

function labelize(value: string) {
  return value.replace(/-/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function toHex(value: string) {
  return /^#[0-9a-f]{6}$/i.test(value) ? value : "#000000";
}
