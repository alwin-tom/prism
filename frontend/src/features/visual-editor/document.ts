import type { MarqueeItem, PropValue, RichTextRun } from "./richText";
import { newMarqueeItem } from "./richText";

export type StyleMap = Record<string, string>;
export type { PropValue, RichTextRun, MarqueeItem };

export type EditorNode = {
  id: string;
  type: string;
  name: string;
  locked?: boolean;
  props: Record<string, PropValue>;
  styles: StyleMap;
  responsiveStyles: { tablet: StyleMap; mobile: StyleMap };
  responsiveProps?: { tablet: Record<string, PropValue>; mobile: Record<string, PropValue> };
  children: EditorNode[];
};

export type EditorDocument = {
  schemaVersion: 1;
  documentId: string;
  page: { title: string; description: string };
  breakpoints: { desktop: number; tablet: number };
  designTokens: {
    colors: Record<string, string>;
    fonts: Record<string, string>;
    spacing: Record<string, string>;
    shape?: Record<string, string>;
    image?: Record<string, string>;
    motion?: Record<string, string>;
    meta?: Record<string, string>;
  };
  creative?: { archetypeId?: string; rationale?: string; promptVersion?: string; motionStrategy?: string; visualComplexity?: string };
  typography?: import("../typography/fonts").TypographySettings;
  root: EditorNode;
};

export type Breakpoint = "desktop" | "tablet" | "mobile";

export const FRAME_WIDTH: Record<Breakpoint, number> = { desktop: 1440, tablet: 768, mobile: 390 };

export const RESPONSIVE_BREAKPOINTS = { mobileMax: 767, tabletMax: 1023, desktopMin: 1024 } as const;

export function viewportDevice(): Breakpoint {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return "desktop";
  if (window.matchMedia(`(max-width: ${RESPONSIVE_BREAKPOINTS.mobileMax}px)`).matches) return "mobile";
  if (window.matchMedia(`(max-width: ${RESPONSIVE_BREAKPOINTS.tabletMax}px)`).matches) return "tablet";
  return "desktop";
}

const SHARED_PROPS = new Set([
  "text", "href", "alt", "level", "attribution", "target", "label", "title",
  "richText", "linkTarget", "linkTitle", "linkColor", "linkHoverColor", "linkUnderline",
  "items", "direction", "speed", "duration", "gap", "delay", "loop", "pauseOnHover", "pauseOnClick", "animationEnabled",
]);

const LIBRARY: Record<string, { name: string; category: string; props: Record<string, PropValue>; styles: StyleMap }> = {
  SECTION: { name: "Section", category: "Layout", props: {}, styles: { display: "flex", flexDirection: "column", gap: "16px", padding: "24px" } },
  CONTAINER: { name: "Container", category: "Layout", props: {}, styles: { display: "flex", flexDirection: "column", gap: "12px" } },
  FLEX: { name: "Row", category: "Layout", props: {}, styles: { display: "flex", flexWrap: "wrap", gap: "12px", alignItems: "center" } },
  GRID: { name: "Grid", category: "Layout", props: {}, styles: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" } },
  GROUP: { name: "Group", category: "Layout", props: {}, styles: { display: "flex", flexDirection: "column", gap: "8px" } },
  CAROUSEL: { name: "Carousel", category: "Media", props: { preset: "minimal", autoplay: 1, interval: 5000, perView: 1 }, styles: { width: "100%" } },
  HEADING: { name: "Heading", category: "Text", props: { text: "Heading", level: 2, href: "", richText: [{ text: "Heading" }] }, styles: { fontSize: "36px", fontFamily: "Fraunces" } },
  PARAGRAPH: { name: "Text", category: "Text", props: { text: "Write something", href: "", richText: [{ text: "Write something" }] }, styles: { fontSize: "18px" } },
  BUTTON: { name: "Button", category: "Text", props: { text: "Button", href: "" }, styles: { padding: "12px 20px", background: "#121417", color: "#ffffff", borderRadius: "8px" } },
  IMAGE: { name: "Picture", category: "Media", props: { mediaId: "", alt: "" }, styles: { width: "100%", objectFit: "cover" } },
  SPACER: { name: "Spacer", category: "Layout", props: { size: 24 }, styles: {} },
  DIVIDER: { name: "Divider", category: "Layout", props: {}, styles: { height: "1px", background: "#d5d8dc" } },
  QUOTE: { name: "Quote", category: "Text", props: { text: "A short statement", attribution: "", href: "", richText: [{ text: "A short statement" }] }, styles: { fontSize: "28px", fontFamily: "Fraunces", textAlign: "center", padding: "24px" } },
  COUNTDOWN: { name: "Countdown", category: "Media", props: { target: "", label: "Until the day" }, styles: { display: "flex", flexDirection: "column", gap: "8px", padding: "16px", textAlign: "center" } },
  FAQ_ACCORDION: { name: "FAQ", category: "Content", props: { title: "Questions" }, styles: { display: "flex", flexDirection: "column", gap: "12px", padding: "16px" } },
  FAQ_ITEM: { name: "FAQ item", category: "Content", props: { title: "Question", text: "Answer", richText: [{ text: "Answer" }] }, styles: { display: "flex", flexDirection: "column", gap: "6px", padding: "12px 0" } },
  TIMELINE: { name: "Timeline", category: "Content", props: { title: "Schedule" }, styles: { display: "flex", flexDirection: "column", gap: "16px", padding: "16px" } },
  TIMELINE_ITEM: { name: "Timeline item", category: "Content", props: { title: "Moment", text: "Details", richText: [{ text: "Details" }] }, styles: { display: "flex", flexDirection: "column", gap: "4px", padding: "8px 0 8px 16px", borderWidth: "0 0 0 2px", borderStyle: "solid", borderColor: "#d5d8dc" } },
  MARQUEE: {
    name: "Marquee",
    category: "Media",
    props: {
      text: "Announcement",
      direction: "left",
      speed: 50,
      duration: 28,
      gap: 32,
      delay: 0,
      loop: 1,
      pauseOnHover: 1,
      pauseOnClick: 0,
      animationEnabled: 1,
      items: [newMarqueeItem("text")],
    },
    styles: { width: "100%", overflow: "hidden", padding: "12px 0", background: "#f4f6f8" },
  },
};

export const CAROUSEL_PRESETS = ["minimal", "cards", "hero", "coverflow", "fade", "editorial"] as const;

export const SHADOW_PRESETS: Record<string, string> = {
  None: "",
  Subtle: "0 1px 2px rgba(18,20,23,0.08)",
  Soft: "0 8px 24px rgba(18,20,23,0.12)",
  Medium: "0 12px 32px rgba(18,20,23,0.18)",
  Floating: "0 18px 40px rgba(18,20,23,0.22)",
};

export const ELEMENT_LIBRARY = Object.entries(LIBRARY).map(([type, item]) => ({ type, name: item.name, category: item.category }));

export function createNode(type: string): EditorNode {
  const item = LIBRARY[type] ?? LIBRARY.CONTAINER;
  return {
    id: newId(),
    type: LIBRARY[type] ? type : "CONTAINER",
    name: item.name,
    props: { ...item.props },
    styles: { ...item.styles },
    responsiveStyles: { tablet: {}, mobile: {} },
    responsiveProps: { tablet: {}, mobile: {} },
    children: [],
  };
}

export function stylesFor(node: EditorNode, breakpoint: Breakpoint): StyleMap {
  if (breakpoint === "desktop") return node.styles;
  return { ...node.styles, ...(node.responsiveStyles?.[breakpoint] ?? {}) };
}

export function propsFor(node: EditorNode, breakpoint: Breakpoint): Record<string, PropValue> {
  if (breakpoint === "desktop") return node.props;
  return { ...node.props, ...(node.responsiveProps?.[breakpoint] ?? {}) };
}

export function getEffectiveProperty(node: EditorNode, property: string, device: Breakpoint): string {
  const props = propsFor(node, device);
  if (property in stylesFor(node, device)) return stylesFor(node, device)[property] ?? "";
  if (property in props) return String(props[property] ?? "");
  return "";
}

export function hasOverride(node: EditorNode, property: string, device: Breakpoint): boolean {
  if (device === "desktop") return false;
  return property in (node.responsiveStyles?.[device] ?? {}) || property in (node.responsiveProps?.[device] ?? {});
}

export function setResponsiveProperty(document: EditorDocument, elementId: string, property: string, value: string, device: Breakpoint) {
  updateNode(document, elementId, { styles: { [property]: value } }, device);
}

export function removeResponsiveOverride(document: EditorDocument, elementId: string, properties: string[], device: Breakpoint) {
  const node = findNode(document.root, elementId);
  if (!node || device === "desktop") return;
  const styles = node.responsiveStyles?.[device];
  const props = node.responsiveProps?.[device];
  for (const property of properties) {
    if (styles) delete styles[property];
    if (props) delete props[property];
  }
}

export function setDeviceVisibility(document: EditorDocument, elementId: string, device: Breakpoint, hidden: boolean) {
  const node = findNode(document.root, elementId);
  if (!node) return;
  // Visibility can change while locked so Layers can hide without unlocking.
  if (hidden) {
    if (device === "desktop") {
      node.styles.display = "none";
      return;
    }
    node.responsiveStyles = node.responsiveStyles ?? { tablet: {}, mobile: {} };
    node.responsiveStyles[device] = { ...node.responsiveStyles[device], display: "none" };
    return;
  }
  if (device !== "desktop") {
    removeResponsiveOverride(document, elementId, ["display"], device);
    return;
  }
  if (node.styles.display === "none") delete node.styles.display;
  const fallback = LIBRARY[node.type]?.styles.display;
  if (fallback && !node.styles.display) node.styles.display = fallback;
}

export function findNode(node: EditorNode, id: string): EditorNode | null {
  if (node.id === id) return node;
  for (const child of node.children) {
    const found = findNode(child, id);
    if (found) return found;
  }
  return null;
}

export function addChild(document: EditorDocument, parentId: string, type: string): EditorNode {
  const parent = findNode(document.root, parentId) ?? document.root;
  const node = createNode(type);
  node.styles.zIndex = String(parent.children.length);
  parent.children.push(node);
  return node;
}

export const INSERT_BLOCKS = [
  { id: "note", name: "Welcome note" },
  { id: "split", name: "Picture and text" },
  { id: "reply", name: "Reply button" },
] as const;

export function insertBlock(document: EditorDocument, parentId: string, block: string): EditorNode {
  const parent = findNode(document.root, parentId) ?? document.root;
  const group = createNode("GROUP");
  const named = INSERT_BLOCKS.find((item) => item.id === block);
  group.name = named?.name ?? "Block";
  if (block === "split") {
    group.styles = { display: "flex", flexWrap: "wrap", gap: "16px", alignItems: "center" };
    const image = createNode("IMAGE");
    image.styles = { width: "46%", objectFit: "cover" };
    const paragraph = createNode("PARAGRAPH");
    paragraph.props.text = "A few words beside the picture.";
    paragraph.styles = { ...paragraph.styles, flex: "1", minWidth: "180px" };
    group.children = [image, paragraph];
  } else if (block === "reply") {
    const paragraph = createNode("PARAGRAPH");
    paragraph.props.text = "Let people know how to reply.";
    const button = createNode("BUTTON");
    button.props.text = "Send a reply";
    button.props.href = "#rsvp";
    group.children = [paragraph, button];
  } else {
    const heading = createNode("HEADING");
    heading.props.text = "You are invited";
    const paragraph = createNode("PARAGRAPH");
    paragraph.props.text = "Add the date, the place, and a line for your guests.";
    group.children = [heading, paragraph];
  }
  parent.children.push(group);
  return group;
}

export function updateNode(document: EditorDocument, id: string, patch: { name?: string; locked?: boolean; props?: Record<string, PropValue>; styles?: StyleMap }, breakpoint: Breakpoint) {
  const node = findNode(document.root, id);
  if (!node || node.locked && patch.locked !== false && patch.name === undefined) return;
  if (patch.name !== undefined) node.name = patch.name;
  if (patch.locked !== undefined) {
    if (patch.locked) node.locked = true;
    else delete node.locked;
  }
  if (node.locked && patch.locked !== false) return;
  if (patch.props) {
    const shared: Record<string, PropValue> = {};
    const local: Record<string, PropValue> = {};
    for (const [key, value] of Object.entries(patch.props)) {
      if (breakpoint === "desktop" || SHARED_PROPS.has(key)) shared[key] = value;
      else local[key] = value;
    }
    if (Object.keys(shared).length > 0) node.props = { ...node.props, ...shared };
    if (breakpoint !== "desktop" && Object.keys(local).length > 0) {
      node.responsiveProps = node.responsiveProps ?? { tablet: {}, mobile: {} };
      node.responsiveProps[breakpoint] = { ...node.responsiveProps[breakpoint], ...local };
    }
  }
  if (patch.styles) {
    if (breakpoint === "desktop") {
      node.styles = { ...node.styles, ...patch.styles };
      for (const [key, value] of Object.entries(patch.styles)) {
        if (value === "") delete node.styles[key];
      }
    } else {
      node.responsiveStyles = node.responsiveStyles ?? { tablet: {}, mobile: {} };
      node.responsiveStyles[breakpoint] = { ...node.responsiveStyles[breakpoint], ...patch.styles };
      for (const [key, value] of Object.entries(patch.styles)) {
        if (value === "") delete node.responsiveStyles[breakpoint][key];
      }
    }
  }
}

export function placeAcross(document: EditorDocument, id: string, styles: StyleMap, breakpoint: Breakpoint) {
  updateNode(document, id, { styles }, breakpoint);
}

export function parentOf(root: EditorNode, id: string): EditorNode | null {
  for (const child of root.children) {
    if (child.id === id) return root;
    const found = parentOf(child, id);
    if (found) return found;
  }
  return null;
}

export function duplicateNode(document: EditorDocument, id: string): EditorNode | null {
  const parent = parentOf(document.root, id);
  const node = findNode(document.root, id);
  if (!parent || !node) return null;
  const copy = cloneNode(node);
  parent.children.splice(parent.children.findIndex((child) => child.id === id) + 1, 0, copy);
  return copy;
}

export function insertCopy(document: EditorDocument, parentId: string, source: EditorNode) {
  const parent = findNode(document.root, parentId) ?? document.root;
  const copy = cloneNode(source);
  parent.children.push(copy);
  return copy;
}

export function groupNodes(document: EditorDocument, ids: string[]) {
  const unique = [...new Set(ids)].filter((id) => id !== document.root.id);
  const parent = unique.map((id) => parentOf(document.root, id)).find((item) => item) ?? null;
  if (!parent || unique.length < 2 || unique.some((id) => parentOf(document.root, id)?.id !== parent.id)) return null;
  const chosen = parent.children.filter((child) => unique.includes(child.id));
  const group = createNode("GROUP");
  const index = parent.children.findIndex((child) => child.id === chosen[0].id);
  parent.children = parent.children.filter((child) => !unique.includes(child.id));
  group.children = chosen;
  parent.children.splice(index, 0, group);
  return group;
}

export function ungroupNode(document: EditorDocument, id: string) {
  const parent = parentOf(document.root, id);
  const node = findNode(document.root, id);
  if (!parent || !node || node.type !== "GROUP") return;
  const index = parent.children.findIndex((child) => child.id === id);
  parent.children.splice(index, 1, ...node.children);
}

const STACK_MIN = -100;
const STACK_MAX = 100;

/** Explicit z-index for one element (clamped). Does not renumber siblings. */
export function setStackIndex(document: EditorDocument, id: string, value: number, breakpoint: Breakpoint = "desktop") {
  const node = findNode(document.root, id);
  if (!node || node.locked || id === document.root.id) return;
  writeStackStyles(node, clampStack(value), breakpoint);
}

/**
 * Sibling-relative stacking. Renumbers peers to 0..n-1 so values stay bounded,
 * then moves the target within that range (front / forward / backward / back).
 */
export function restack(document: EditorDocument, id: string, where: "forward" | "backward" | "front" | "back", breakpoint: Breakpoint = "desktop") {
  const parent = parentOf(document.root, id);
  const node = findNode(document.root, id);
  if (!parent || !node || node.locked || id === document.root.id) return;
  const siblings = parent.children;
  const ranked = [...siblings].sort((left, right) => {
    const delta = stackValue(left, breakpoint) - stackValue(right, breakpoint);
    if (delta !== 0) return delta;
    return siblings.indexOf(left) - siblings.indexOf(right);
  });
  const from = ranked.findIndex((child) => child.id === id);
  if (from < 0) return;
  ranked.splice(from, 1);
  const to = where === "front"
    ? ranked.length
    : where === "back"
      ? 0
      : where === "forward"
        ? Math.min(ranked.length, from + 1)
        : Math.max(0, from - 1);
  ranked.splice(to, 0, node);
  ranked.forEach((child, index) => writeStackStyles(child, index, breakpoint));
}

/** After DOM reorder within a parent, keep z-index aligned to sibling order. */
export function normalizeSiblingStack(document: EditorDocument, parentId: string, breakpoint: Breakpoint = "desktop") {
  const parent = findNode(document.root, parentId);
  if (!parent) return;
  parent.children.forEach((child, index) => writeStackStyles(child, index, breakpoint));
}

function stackValue(node: EditorNode, breakpoint: Breakpoint) {
  const raw = Number.parseInt(stylesFor(node, breakpoint).zIndex ?? "", 10);
  return Number.isFinite(raw) ? raw : 0;
}

function clampStack(value: number) {
  if (!Number.isFinite(value)) return 0;
  return Math.min(STACK_MAX, Math.max(STACK_MIN, Math.round(value)));
}

function writeStackStyles(node: EditorNode, zIndex: number, breakpoint: Breakpoint) {
  const position = stylesFor(node, breakpoint).position;
  const styles: StyleMap = {
    zIndex: String(clampStack(zIndex)),
    position: position && position !== "static" ? position : "relative",
  };
  if (breakpoint === "desktop") {
    node.styles = { ...node.styles, ...styles };
    return;
  }
  node.responsiveStyles = node.responsiveStyles ?? { tablet: {}, mobile: {} };
  node.responsiveStyles[breakpoint] = { ...node.responsiveStyles[breakpoint], ...styles };
}

export function removeNode(document: EditorDocument, id: string) {
  if (document.root.id === id) return;
  removeFrom(document.root, id);
}

export function shiftChild(document: EditorDocument, id: string, direction: -1 | 1, breakpoint: Breakpoint = "desktop") {
  const parent = parentOf(document.root, id);
  if (!parent) return;
  const index = parent.children.findIndex((child) => child.id === id);
  const target = index + direction;
  if (index < 0 || target < 0 || target >= parent.children.length) return;
  moveChild(document, id, parent.id, target, breakpoint);
}

export function moveChild(document: EditorDocument, id: string, parentId: string, index: number, breakpoint: Breakpoint = "desktop") {
  if (id === document.root.id || id === parentId) return;
  const previousParent = parentOf(document.root, id);
  const node = detach(document.root, id);
  const parent = findNode(document.root, parentId);
  if (!node || !parent || contains(node, parent.id)) return;
  const at = Math.max(0, Math.min(index, parent.children.length));
  parent.children.splice(at, 0, node);
  if (previousParent && previousParent.id !== parent.id) {
    normalizeSiblingStack(document, previousParent.id, breakpoint);
  }
  normalizeSiblingStack(document, parent.id, breakpoint);
}

function removeFrom(node: EditorNode, id: string): boolean {
  const index = node.children.findIndex((child) => child.id === id);
  if (index >= 0) {
    node.children.splice(index, 1);
    return true;
  }
  return node.children.some((child) => removeFrom(child, id));
}

function detach(node: EditorNode, id: string): EditorNode | null {
  const index = node.children.findIndex((child) => child.id === id);
  if (index >= 0) return node.children.splice(index, 1)[0];
  for (const child of node.children) {
    const found = detach(child, id);
    if (found) return found;
  }
  return null;
}

function contains(node: EditorNode, id: string): boolean {
  return node.id === id || node.children.some((child) => contains(child, id));
}

function cloneNode(node: EditorNode): EditorNode {
  const copy = structuredClone(node);
  copy.id = newId();
  copy.children = node.children.map(cloneNode);
  return copy;
}

function newId() {
  const bytes = crypto.getRandomValues(new Uint8Array(4));
  return `n${Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("")}`;
}
