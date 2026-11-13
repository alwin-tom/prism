export type BoxStart = {
  left: number;
  top: number;
  width: number;
  height: number;
  position: string;
  verticalCenter?: boolean;
  horizontalCenter?: boolean;
};
export type Handle = "n" | "s" | "e" | "w" | "ne" | "nw" | "se" | "sw";

export function px(value: string | undefined) {
  const parsed = Number.parseFloat(value ?? "");
  return Number.isFinite(parsed) ? parsed : 0;
}

export function lengthPx(value: string | undefined, basis: number) {
  if (!value) return 0;
  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed)) return 0;
  return value.trim().endsWith("%") ? (parsed / 100) * basis : parsed;
}

export function percentOf(pixels: number, basis: number) {
  const ratio = basis > 0 ? (pixels / basis) * 100 : 0;
  const rounded = Math.round(ratio * 100) / 100;
  return `${rounded}%`;
}

export function preferPercent(value: string, basis: number) {
  const trimmed = value.trim();
  if (!trimmed || trimmed.endsWith("%") || basis <= 0) return trimmed;
  if (/^(auto|none|min-content|max-content|fit-content|inherit|initial|unset)$/i.test(trimmed)) return trimmed;
  const match = /^(-?\d+(?:\.\d+)?)(?:px)?$/.exec(trimmed);
  if (!match) return trimmed;
  return percentOf(Number(match[1]), basis);
}

export function contentBoxWidth(element: HTMLElement) {
  const style = getComputedStyle(element);
  const padding = (Number.parseFloat(style.paddingLeft) || 0) + (Number.parseFloat(style.paddingRight) || 0);
  return Math.max(1, element.clientWidth - padding);
}

export function usedOffset(element: HTMLElement, axis: "left" | "top") {
  const parsed = Number.parseFloat(getComputedStyle(element)[axis]);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function centeredOn(element: HTMLElement, axis: "x" | "y"): boolean {
  const parent = element.parentElement;
  if (!parent) return false;
  const parentStyle = getComputedStyle(parent);
  const selfStyle = getComputedStyle(element);
  if (axis === "x" && selfStyle.marginLeft === "auto" && selfStyle.marginRight === "auto") return true;
  if (axis === "y" && selfStyle.marginTop === "auto" && selfStyle.marginBottom === "auto") return true;
  const display = parentStyle.display;
  const align = selfStyle.alignSelf === "auto" ? parentStyle.alignItems : selfStyle.alignSelf;
  if (display === "flex" || display === "inline-flex") {
    const column = parentStyle.flexDirection.startsWith("column");
    if (axis === "y") return column ? parentStyle.justifyContent === "center" : align === "center";
    return column ? align === "center" : parentStyle.justifyContent === "center";
  }
  if (display === "grid" || display === "inline-grid") {
    if (axis === "y") return align === "center";
    const justify = selfStyle.justifySelf === "auto" ? parentStyle.justifyItems : selfStyle.justifySelf;
    return justify === "center";
  }
  return false;
}

export type ResizeBox = { left: number; top: number; width: number; height: number };

export function resizedFrame(start: ResizeBox, dx: number, dy: number, handle: Handle, shift: boolean): ResizeBox & { lock: boolean } {
  const west = handle.includes("w");
  const east = handle.includes("e");
  const north = handle.includes("n");
  const south = handle.includes("s");
  const corner = (east || west) && (north || south);
  const lock = corner || shift;
  const ratio = start.width / Math.max(1, start.height);
  let width = start.width;
  let height = start.height;
  if (east) width = start.width + dx;
  if (west) width = start.width - dx;
  if (south) height = start.height + dy;
  if (north) height = start.height - dy;
  if (lock) {
    const scaleX = width / Math.max(1, start.width);
    const scaleY = height / Math.max(1, start.height);
    const scale = corner
      ? Math.abs(scaleX - 1) >= Math.abs(scaleY - 1) ? scaleX : scaleY
      : east || west ? scaleX : scaleY;
    width = start.width * Math.max(0, scale);
    height = start.height * Math.max(0, scale);
  }
  width = Math.max(24, Math.round(width));
  height = Math.max(16, Math.round(lock ? width / ratio : height));
  const left = west ? start.left + start.width - width : start.left;
  const top = north ? start.top + start.height - height : start.top;
  return { left, top, width, height, lock };
}

export function edgeDrift(start: ResizeBox, measured: ResizeBox, handle: Handle) {
  const west = handle.includes("w");
  const north = handle.includes("n");
  return {
    x: west ? start.left + start.width - (measured.left + measured.width) : start.left - measured.left,
    y: north ? start.top + start.height - (measured.top + measured.height) : start.top - measured.top,
  };
}

export function resizeStyles(start: BoxStart, dx: number, dy: number, handle: Handle, shift: boolean, basis = 0): Record<string, string> {
  const frame = resizedFrame(start, dx, dy, handle, shift);
  const west = handle.includes("w");
  const east = handle.includes("e");
  const north = handle.includes("n");
  const south = handle.includes("s");
  const out: Record<string, string> = {};
  if (frame.lock) {
    out.width = horizontal(frame.width, basis);
    out.height = `${frame.height}px`;
    out.aspectRatio = `${Math.max(1, Math.round(start.width))} / ${Math.max(1, Math.round(start.height))}`;
    out.minWidth = "0";
    out.minHeight = "0";
  } else if (east || west) {
    out.width = horizontal(frame.width, basis);
    out.minWidth = "0";
  }
  if ((north || south) && !frame.lock) {
    out.height = `${frame.height}px`;
    out.aspectRatio = "auto";
    out.minHeight = "0";
  }
  if (west) {
    out.position = placed(start.position);
    out.left = horizontal(frame.left, basis);
  }
  if (north) {
    out.position = placed(start.position);
    out.top = `${Math.round(frame.top)}px`;
  }
  return out;
}

export function moveStyles(start: BoxStart, dx: number, dy: number, basis = 0): Record<string, string> {
  const position = start.position === "absolute" || start.position === "fixed" || start.position === "sticky" ? start.position : "relative";
  return {
    position,
    left: horizontal(start.left + dx, basis),
    top: `${Math.round(start.top + dy)}px`,
  };
}

export function readRotate(transform: string | undefined) {
  const match = /rotate\((-?\d+)deg\)/.exec(transform ?? "");
  return match ? Number(match[1]) : 0;
}

export function rotateTransform(existing: string | undefined, degrees: number) {
  const rest = (existing ?? "").replace(/rotate\(-?\d+deg\)/g, "").trim();
  const next = `rotate(${Math.round(degrees)}deg)`;
  return rest ? `${next} ${rest}` : next;
}

export type Frame = { left: number; top: number; right: number; bottom: number };
export type GuideLine = { axis: "x" | "y"; at: number; start: number; end: number };

export function pageFrame(page: HTMLElement, element: HTMLElement, zoom: number): Frame {
  const pageBox = page.getBoundingClientRect();
  const box = element.getBoundingClientRect();
  const scale = zoom || 1;
  return {
    left: (box.left - pageBox.left) / scale,
    top: (box.top - pageBox.top) / scale,
    right: (box.right - pageBox.left) / scale,
    bottom: (box.bottom - pageBox.top) / scale,
  };
}

export function contentFrame(page: HTMLElement, element: HTMLElement, zoom: number): Frame {
  const frame = pageFrame(page, element, zoom);
  const style = getComputedStyle(element);
  return {
    left: frame.left + (Number.parseFloat(style.paddingLeft) || 0),
    top: frame.top + (Number.parseFloat(style.paddingTop) || 0),
    right: frame.right - (Number.parseFloat(style.paddingRight) || 0),
    bottom: frame.bottom - (Number.parseFloat(style.paddingBottom) || 0),
  };
}

export function shiftFrame(frame: Frame, dx: number, dy: number): Frame {
  return { left: frame.left + dx, top: frame.top + dy, right: frame.right + dx, bottom: frame.bottom + dy };
}

export function alignMove(moving: Frame, anchors: Frame[], gap = 6, middle?: Frame): { dx: number; dy: number; guides: GuideLine[] } {
  const x = nearest(axisPoints(moving, "x"), anchors, "x", gap, middle);
  const y = nearest(axisPoints(moving, "y"), anchors, "y", gap, middle);
  const snapped = shiftFrame(moving, x.delta, y.delta);
  return {
    dx: x.delta,
    dy: y.delta,
    guides: [
      ...merge(x.hits.map((hit) => pad({ axis: "x", at: hit.at, start: Math.min(snapped.top, hit.anchor.top), end: Math.max(snapped.bottom, hit.anchor.bottom) }))),
      ...merge(y.hits.map((hit) => pad({ axis: "y", at: hit.at, start: Math.min(snapped.left, hit.anchor.left), end: Math.max(snapped.right, hit.anchor.right) }))),
    ],
  };
}

function axisPoints(frame: Frame, axis: "x" | "y") {
  if (axis === "x") return [frame.left, (frame.left + frame.right) / 2, frame.right];
  return [frame.top, (frame.top + frame.bottom) / 2, frame.bottom];
}

function nearest(movingPoints: number[], anchors: Frame[], axis: "x" | "y", gap: number, middle?: Frame) {
  const targets = middle ? [middle, ...anchors] : anchors;
  const middleCenter = middle ? centerOf(middle, axis) : null;
  let best = 0;
  let found = false;
  for (const anchor of targets) {
    for (const from of movingPoints) {
      for (const to of axisPoints(anchor, axis)) {
        if (middleCenter != null && isOffMiddle(anchor, to, axis, middleCenter)) continue;
        const delta = to - from;
        if (Math.abs(delta) > gap) continue;
        if (!found || Math.abs(delta) < Math.abs(best)) {
          found = true;
          best = delta;
        }
      }
    }
  }
  if (!found) return { delta: 0, hits: [] as { at: number; anchor: Frame }[] };
  const hits: { at: number; anchor: Frame }[] = [];
  for (const anchor of targets) {
    for (const from of movingPoints) {
      for (const to of axisPoints(anchor, axis)) {
        if (middleCenter != null && isOffMiddle(anchor, to, axis, middleCenter)) continue;
        if (Math.abs(to - from - best) < 0.2) hits.push({ at: to, anchor });
      }
    }
  }
  return { delta: best, hits };
}

function centerOf(frame: Frame, axis: "x" | "y") {
  return axis === "x" ? (frame.left + frame.right) / 2 : (frame.top + frame.bottom) / 2;
}

function isOffMiddle(anchor: Frame, point: number, axis: "x" | "y", middleCenter: number) {
  const center = centerOf(anchor, axis);
  return Math.abs(point - center) < 0.5 && Math.abs(center - middleCenter) > 0.5 && Math.abs(center - middleCenter) <= 16;
}

function merge(lines: GuideLine[]) {
  const kept: GuideLine[] = [];
  for (const line of lines) {
    const match = kept.find((item) => item.axis === line.axis && Math.abs(item.at - line.at) < 0.5);
    if (!match) {
      kept.push({ ...line });
      continue;
    }
    match.start = Math.min(match.start, line.start);
    match.end = Math.max(match.end, line.end);
  }
  return kept;
}

function pad(line: GuideLine): GuideLine {
  return { ...line, start: line.start - 6, end: line.end + 6 };
}

function placed(position: string) {
  return position === "absolute" || position === "fixed" || position === "sticky" || position === "relative" ? position : "relative";
}

function horizontal(pixels: number, basis: number) {
  return basis > 0 ? percentOf(pixels, basis) : `${Math.round(pixels)}px`;
}
