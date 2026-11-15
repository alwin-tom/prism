import type { StyleMap } from "./document";

/** Style keys stored as plain strings (no CSS url()) and resolved at render time. */
export const BACKGROUND_META_KEYS = [
  "backgroundMediaId",
  "backgroundOverlayType",
  "backgroundOverlayColor",
  "backgroundOverlayOpacity",
  "backgroundOverlayAngle",
  "backgroundOverlayEnd",
] as const;

export const BACKGROUND_CSS_KEYS = [
  "backgroundSize",
  "backgroundPosition",
  "backgroundRepeat",
  "backgroundAttachment",
] as const;

export const SIZE_OPTIONS = ["cover", "contain", "auto", "custom"] as const;
export const POSITION_OPTIONS = ["center", "top", "bottom", "left", "right", "custom"] as const;
export const REPEAT_OPTIONS = ["no-repeat", "repeat", "repeat-x", "repeat-y"] as const;
export const ATTACHMENT_OPTIONS = ["scroll", "fixed", "local"] as const;
export const OVERLAY_TYPES = ["none", "solid", "gradient"] as const;

export type SizeOption = (typeof SIZE_OPTIONS)[number];
export type PositionOption = (typeof POSITION_OPTIONS)[number];
export type RepeatOption = (typeof REPEAT_OPTIONS)[number];
export type AttachmentOption = (typeof ATTACHMENT_OPTIONS)[number];
export type OverlayType = (typeof OVERLAY_TYPES)[number];

const LAYOUT_TYPES = new Set([
  "SECTION", "CONTAINER", "FLEX", "GRID", "GROUP", "CAROUSEL", "MARQUEE",
  "HEADING", "PARAGRAPH", "BUTTON", "QUOTE", "COUNTDOWN",
  "FAQ_ACCORDION", "FAQ_ITEM", "TIMELINE", "TIMELINE_ITEM",
]);

export function supportsBackgroundImage(type: string) {
  return LAYOUT_TYPES.has(type);
}

export function readBackground(styles: StyleMap) {
  const mediaId = (styles.backgroundMediaId ?? "").trim();
  const sizeRaw = (styles.backgroundSize ?? "cover").trim() || "cover";
  const positionRaw = (styles.backgroundPosition ?? "center").trim() || "center";
  const sizeMode: SizeOption = SIZE_OPTIONS.includes(sizeRaw as SizeOption)
    ? sizeRaw as SizeOption
    : sizeRaw === "cover" || sizeRaw === "contain" || sizeRaw === "auto"
      ? sizeRaw
      : "custom";
  const positionMode: PositionOption = POSITION_OPTIONS.includes(positionRaw as PositionOption)
    ? positionRaw as PositionOption
    : ["center", "top", "bottom", "left", "right"].includes(positionRaw)
      ? positionRaw as PositionOption
      : "custom";
  const overlayType = (OVERLAY_TYPES.includes((styles.backgroundOverlayType ?? "none") as OverlayType)
    ? styles.backgroundOverlayType
    : "none") as OverlayType;
  return {
    mediaId,
    sizeMode,
    sizeCustom: sizeMode === "custom" ? sizeRaw : "100% 100%",
    positionMode,
    positionCustom: positionMode === "custom" ? positionRaw : "50% 50%",
    repeat: (REPEAT_OPTIONS.includes((styles.backgroundRepeat ?? "no-repeat") as RepeatOption)
      ? styles.backgroundRepeat
      : "no-repeat") as RepeatOption,
    attachment: (ATTACHMENT_OPTIONS.includes((styles.backgroundAttachment ?? "scroll") as AttachmentOption)
      ? styles.backgroundAttachment
      : "scroll") as AttachmentOption,
    overlayType,
    overlayColor: styles.backgroundOverlayColor || "#000000",
    overlayOpacity: clampPercent(styles.backgroundOverlayOpacity, 45),
    overlayAngle: clampAngle(styles.backgroundOverlayAngle, 180),
    overlayEnd: styles.backgroundOverlayEnd || styles.backgroundOverlayColor || "#000000",
  };
}

export function backgroundPatch(partial: {
  mediaId?: string;
  sizeMode?: SizeOption;
  sizeCustom?: string;
  positionMode?: PositionOption;
  positionCustom?: string;
  repeat?: RepeatOption;
  attachment?: AttachmentOption;
  overlayType?: OverlayType;
  overlayColor?: string;
  overlayOpacity?: number;
  overlayAngle?: number;
  overlayEnd?: string;
  clear?: boolean;
}): StyleMap {
  if (partial.clear) {
    return {
      backgroundMediaId: "",
      backgroundSize: "",
      backgroundPosition: "",
      backgroundRepeat: "",
      backgroundAttachment: "",
      backgroundOverlayType: "",
      backgroundOverlayColor: "",
      backgroundOverlayOpacity: "",
      backgroundOverlayAngle: "",
      backgroundOverlayEnd: "",
    };
  }
  const next: StyleMap = {};
  if (partial.mediaId !== undefined) next.backgroundMediaId = partial.mediaId.trim();
  if (partial.sizeMode !== undefined) {
    next.backgroundSize = partial.sizeMode === "custom"
      ? sanitizeSize(partial.sizeCustom ?? "100% 100%")
      : partial.sizeMode;
  }
  if (partial.sizeCustom !== undefined && (partial.sizeMode === "custom" || partial.sizeMode === undefined)) {
    next.backgroundSize = sanitizeSize(partial.sizeCustom);
  }
  if (partial.positionMode !== undefined) {
    next.backgroundPosition = partial.positionMode === "custom"
      ? sanitizePosition(partial.positionCustom ?? "50% 50%")
      : partial.positionMode;
  }
  if (partial.positionCustom !== undefined && (partial.positionMode === "custom" || partial.positionMode === undefined)) {
    next.backgroundPosition = sanitizePosition(partial.positionCustom);
  }
  if (partial.repeat !== undefined) next.backgroundRepeat = partial.repeat;
  if (partial.attachment !== undefined) next.backgroundAttachment = partial.attachment;
  if (partial.overlayType !== undefined) next.backgroundOverlayType = partial.overlayType;
  if (partial.overlayColor !== undefined) next.backgroundOverlayColor = partial.overlayColor;
  if (partial.overlayOpacity !== undefined) next.backgroundOverlayOpacity = String(clampPercent(partial.overlayOpacity, 45));
  if (partial.overlayAngle !== undefined) next.backgroundOverlayAngle = String(clampAngle(partial.overlayAngle, 180));
  if (partial.overlayEnd !== undefined) next.backgroundOverlayEnd = partial.overlayEnd;
  return next;
}

export function hasOverlay(styles: StyleMap) {
  const type = styles.backgroundOverlayType ?? "none";
  return type === "solid" || type === "gradient";
}

export function overlayCss(styles: StyleMap): { background: string; opacity: number } | null {
  if (!hasOverlay(styles)) return null;
  const color = styles.backgroundOverlayColor || "#000000";
  const end = styles.backgroundOverlayEnd || color;
  const angle = clampAngle(styles.backgroundOverlayAngle, 180);
  const opacity = clampPercent(styles.backgroundOverlayOpacity, 45) / 100;
  const background = (styles.backgroundOverlayType ?? "") === "gradient"
    ? `linear-gradient(${angle}deg, ${color}, ${end})`
    : color;
  return { background, opacity };
}

function clampPercent(value: string | number | undefined, fallback: number) {
  const parsed = typeof value === "number" ? value : Number.parseFloat(String(value ?? ""));
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(100, Math.max(0, Math.round(parsed)));
}

function clampAngle(value: string | number | undefined, fallback: number) {
  const parsed = typeof value === "number" ? value : Number.parseFloat(String(value ?? ""));
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(360, Math.max(0, Math.round(parsed)));
}

function sanitizeSize(value: string) {
  const cleaned = value.trim().slice(0, 40);
  return cleaned || "100% 100%";
}

function sanitizePosition(value: string) {
  const cleaned = value.trim().slice(0, 40);
  return cleaned || "50% 50%";
}
