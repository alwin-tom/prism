import type { CanvasElement, SiteCanvas, SiteConfiguration } from "../../types";

const alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";

export function elementId() {
  let id = "el";
  for (let index = 0; index < 10; index += 1) id += alphabet[Math.floor(Math.random() * alphabet.length)];
  return id;
}

export function sectionsToCanvas(configuration: SiteConfiguration): SiteCanvas {
  const elements: CanvasElement[] = [];
  let y = 28;
  const ink = configuration.theme.ink;
  const push = (partial: Partial<CanvasElement> & Pick<CanvasElement, "type" | "w" | "h">) => {
    const element = createElement(partial, ink, y, elements.length + 1);
    elements.push(element);
    y += element.h + 18;
  };
  for (const section of configuration.sections) {
    const properties = section.properties;
    const read = (key: string) => String(properties[key] ?? "");
    const picture = (value: string) => (value && !value.startsWith("/placeholders/") ? value : "");
    switch (section.type) {
      case "HERO": {
        const image = picture(read("backgroundImage"));
        if (image) push({ type: "image", w: 34, h: 200, mediaId: image, x: 60 });
        push({ type: "text", text: read("title") || configuration.event.name, w: 70, h: 96, fontSize: 54, weight: "display" });
        push({ type: "text", text: read("subtitle") || configuration.event.subtitle, w: 70, h: 44, fontSize: 20 });
        break;
      }
      case "TEXT_BLOCK":
        if (read("heading")) push({ type: "text", text: read("heading"), w: 70, h: 56, fontSize: 32, weight: "display" });
        if (read("body")) push({ type: "text", text: read("body"), w: 70, h: 88, fontSize: 18 });
        break;
      case "IMAGE":
        if (picture(read("mediaId"))) push({ type: "image", w: 42, h: 220, mediaId: picture(read("mediaId")) });
        if (read("caption")) push({ type: "text", text: read("caption"), w: 50, h: 40, fontSize: 16 });
        break;
      case "IMAGE_GALLERY":
        for (const id of Array.isArray(properties.mediaIds) ? properties.mediaIds.map(String) : []) {
          if (picture(id)) push({ type: "image", w: 30, h: 180, mediaId: picture(id) });
        }
        break;
      case "EVENT_DETAILS":
        push({ type: "text", text: read("heading") || "Details", w: 50, h: 48, fontSize: 32, weight: "display" });
        push({ type: "text", text: [configuration.event.venueName, configuration.event.venueAddress, configuration.event.description].filter(Boolean).join("\n"), w: 60, h: 110, fontSize: 18 });
        break;
      case "EVENT_SCHEDULE": {
        const rows = Array.isArray(properties.items) ? properties.items as Record<string, unknown>[] : [];
        const body = rows.map((row) => [row.time, row.title, row.detail].filter(Boolean).join(" — ")).join("\n");
        push({ type: "text", text: read("heading") || "Schedule", w: 50, h: 48, fontSize: 32, weight: "display" });
        if (body) push({ type: "text", text: body, w: 70, h: Math.min(280, 36 + rows.length * 28), fontSize: 18 });
        break;
      }
      case "LOCATION_MAP":
        push({ type: "text", text: read("heading") || "Place", w: 50, h: 48, fontSize: 32, weight: "display" });
        push({ type: "text", text: [configuration.event.venueName, configuration.event.venueAddress, read("note")].filter(Boolean).join("\n"), w: 60, h: 96, fontSize: 18 });
        break;
      case "COUNTDOWN":
        push({ type: "text", text: read("label") || "Until the day", w: 50, h: 48, fontSize: 28, weight: "display" });
        break;
      case "RSVP_FORM":
        if (configuration.rsvp.enabled) push({ type: "rsvp", text: read("heading") || "Reply", w: 42, h: 340, fontSize: 28, weight: "display" });
        break;
      case "FOOTER":
        push({ type: "text", text: read("note") || configuration.event.name, w: 70, h: 80, fontSize: 42, weight: "display" });
        break;
      default:
        break;
    }
  }
  if (elements.length === 0) {
    push({ type: "text", text: configuration.event.name || "Title", w: 70, h: 90, fontSize: 54, weight: "display" });
  }
  return { height: Math.max(720, y + 36), elements };
}

export function createElement(partial: Partial<CanvasElement> & Pick<CanvasElement, "type">, ink: string, y = 40, z = 1): CanvasElement {
  return {
    id: partial.id ?? elementId(),
    type: partial.type,
    x: partial.x ?? 8,
    y: partial.y ?? y,
    w: partial.w ?? 40,
    h: partial.h ?? 80,
    z: partial.z ?? z,
    text: partial.text ?? "",
    fontSize: partial.fontSize ?? 18,
    color: partial.color ?? ink,
    align: partial.align ?? "left",
    fill: partial.fill ?? "",
    mediaId: partial.mediaId ?? "",
    weight: partial.weight ?? "regular",
    font: partial.font ?? "",
    bold: partial.bold ?? false,
    italic: partial.italic ?? false,
    underline: partial.underline ?? false,
    borderWidth: partial.borderWidth ?? 0,
    borderColor: partial.borderColor ?? "",
    radius: partial.radius ?? 0,
    shadow: partial.shadow ?? 0,
    href: partial.href ?? "",
  };
}

export function ensureCanvas(configuration: SiteConfiguration) {
  if (!configuration.canvas) configuration.canvas = sectionsToCanvas(configuration);
  return configuration.canvas;
}

export function growCanvas(canvas: SiteCanvas) {
  const lowest = canvas.elements.reduce((max, element) => Math.max(max, element.y + element.h), 0);
  if (canvas.elements.length === 0) {
    canvas.height = Math.max(canvas.height, 320);
    return;
  }
  canvas.height = Math.min(12000, lowest + 50);
}

export function orderCanvas(elements: CanvasElement[], id: string, direction: "front" | "back") {
  const order = elements.map((element, index) => ({ element, index }));
  order.sort((left, right) => left.element.z - right.element.z || left.index - right.index);
  const at = order.findIndex((item) => item.element.id === id);
  const to = at + (direction === "front" ? 1 : -1);
  if (at < 0 || to < 0 || to >= order.length) return;
  const [moved] = order.splice(at, 1);
  order.splice(to, 0, moved);
  order.forEach((item, index) => {
    item.element.z = index + 1;
  });
}

export function rememberHistory<T>(past: T[], future: T[], current: T, mode?: "coalesce") {
  if (mode === "coalesce") return;
  past.push(structuredClone(current));
  if (past.length > 100) past.shift();
  future.length = 0;
}

export function undoHistory<T>(past: T[], future: T[], current: T) {
  const previous = past.pop();
  if (!previous) return null;
  future.push(structuredClone(current));
  return previous;
}

export function redoHistory<T>(past: T[], future: T[], current: T) {
  const next = future.pop();
  if (!next) return null;
  past.push(structuredClone(current));
  return next;
}
