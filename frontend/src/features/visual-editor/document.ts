export type StyleMap = Record<string, string>;

export type EditorNode = {
  id: string;
  type: string;
  name: string;
  locked?: boolean;
  props: Record<string, string | number>;
  styles: StyleMap;
  responsiveStyles: { tablet: StyleMap; mobile: StyleMap };
  responsiveProps?: { tablet: Record<string, string | number>; mobile: Record<string, string | number> };
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

const SHARED_PROPS = new Set(["text", "href", "alt", "level", "attribution", "target", "label", "title"]);

const LIBRARY: Record<string, { name: string; category: string; props: Record<string, string | number>; styles: StyleMap }> = {
  SECTION: { name: "Section", category: "Layout", props: {}, styles: { display: "flex", flexDirection: "column", gap: "16px", padding: "24px" } },
  CONTAINER: { name: "Container", category: "Layout", props: {}, styles: { display: "flex", flexDirection: "column", gap: "12px" } },
  FLEX: { name: "Row", category: "Layout", props: {}, styles: { display: "flex", flexWrap: "wrap", gap: "12px", alignItems: "center" } },
  GRID: { name: "Grid", category: "Layout", props: {}, styles: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" } },
  GROUP: { name: "Group", category: "Layout", props: {}, styles: { display: "flex", flexDirection: "column", gap: "8px" } },
  CAROUSEL: { name: "Carousel", category: "Media", props: { preset: "minimal", autoplay: 1, interval: 5000, perView: 1 }, styles: { width: "100%" } },
  HEADING: { name: "Heading", category: "Text", props: { text: "Heading", level: 2 }, styles: { fontSize: "36px", fontFamily: "Fraunces" } },
  PARAGRAPH: { name: "Text", category: "Text", props: { text: "Write something" }, styles: { fontSize: "18px" } },
  BUTTON: { name: "Button", category: "Text", props: { text: "Button", href: "" }, styles: { padding: "12px 20px", background: "#121417", color: "#ffffff", borderRadius: "8px" } },
  IMAGE: { name: "Picture", category: "Media", props: { mediaId: "", alt: "" }, styles: { width: "100%", objectFit: "cover" } },
  SPACER: { name: "Spacer", category: "Layout", props: { size: 24 }, styles: {} },
  DIVIDER: { name: "Divider", category: "Layout", props: {}, styles: { height: "1px", background: "#d5d8dc" } },
  QUOTE: { name: "Quote", category: "Text", props: { text: "A short statement", attribution: "" }, styles: { fontSize: "28px", fontFamily: "Fraunces", textAlign: "center", padding: "24px" } },
  COUNTDOWN: { name: "Countdown", category: "Media", props: { target: "", label: "Until the day" }, styles: { display: "flex", flexDirection: "column", gap: "8px", padding: "16px", textAlign: "center" } },
  FAQ_ACCORDION: { name: "FAQ", category: "Content", props: { title: "Questions" }, styles: { display: "flex", flexDirection: "column", gap: "12px", padding: "16px" } },
  FAQ_ITEM: { name: "FAQ item", category: "Content", props: { title: "Question", text: "Answer" }, styles: { display: "flex", flexDirection: "column", gap: "6px", padding: "12px 0" } },
  TIMELINE: { name: "Timeline", category: "Content", props: { title: "Schedule" }, styles: { display: "flex", flexDirection: "column", gap: "16px", padding: "16px" } },
  TIMELINE_ITEM: { name: "Timeline item", category: "Content", props: { title: "Moment", text: "Details" }, styles: { display: "flex", flexDirection: "column", gap: "4px", padding: "8px 0 8px 16px", borderWidth: "0 0 0 2px", borderStyle: "solid", borderColor: "#d5d8dc" } },
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

export function propsFor(node: EditorNode, breakpoint: Breakpoint): Record<string, string | number> {
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
  if (hidden) {
    updateNode(document, elementId, { styles: { display: "none" } }, device);
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

export function updateNode(document: EditorDocument, id: string, patch: { name?: string; locked?: boolean; props?: Record<string, string | number>; styles?: StyleMap }, breakpoint: Breakpoint) {
  const node = findNode(document.root, id);
  if (!node || node.locked && patch.locked !== false && patch.name === undefined) return;
  if (patch.name !== undefined) node.name = patch.name;
  if (patch.locked !== undefined) {
    if (patch.locked) node.locked = true;
    else delete node.locked;
  }
  if (node.locked && patch.locked !== false) return;
  if (patch.props) {
    const shared: Record<string, string | number> = {};
    const local: Record<string, string | number> = {};
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
    if (breakpoint === "desktop") node.styles = { ...node.styles, ...patch.styles };
    else {
      node.responsiveStyles = node.responsiveStyles ?? { tablet: {}, mobile: {} };
      node.responsiveStyles[breakpoint] = { ...node.responsiveStyles[breakpoint], ...patch.styles };
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

export function restack(document: EditorDocument, id: string, where: "forward" | "backward" | "front" | "back", breakpoint: Breakpoint = "desktop") {
  const node = findNode(document.root, id);
  if (!node || node.locked || id === document.root.id) return;
  const current = Number.parseInt(stylesFor(node, breakpoint).zIndex ?? "", 10);
  const base = Number.isFinite(current) ? current : 1;
  const next = where === "front" ? 20 : where === "back" ? 0 : where === "forward" ? Math.min(20, base + 1) : Math.max(0, base - 1);
  const position = stylesFor(node, breakpoint).position;
  updateNode(document, id, { styles: { zIndex: String(next), position: position && position !== "static" ? position : "relative" } }, breakpoint);
}

export function removeNode(document: EditorDocument, id: string) {
  if (document.root.id === id) return;
  removeFrom(document.root, id);
}

export function shiftChild(document: EditorDocument, id: string, direction: -1 | 1) {
  const parent = parentOf(document.root, id);
  if (!parent) return;
  const index = parent.children.findIndex((child) => child.id === id);
  const target = index + direction;
  if (index < 0 || target < 0 || target >= parent.children.length) return;
  moveChild(document, id, parent.id, target);
}

export function moveChild(document: EditorDocument, id: string, parentId: string, index: number) {
  if (id === document.root.id || id === parentId) return;
  const node = detach(document.root, id);
  const parent = findNode(document.root, parentId);
  if (!node || !parent || contains(node, parent.id)) return;
  const at = Math.max(0, Math.min(index, parent.children.length));
  parent.children.splice(at, 0, node);
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
