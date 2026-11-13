import { useEffect } from "react";
import data from "./registry.json";
import { stylesFor, type Breakpoint, type EditorDocument, type EditorNode, type StyleMap } from "../visual-editor/document";

export type FontAxis = { tag: string; min: number; max: number; step: number; default: number };

export type RegistryFont = {
  id: string;
  family: string;
  category: string;
  provider: string;
  weights: number[];
  styles: string[];
  variable: boolean;
  axes: FontAxis[];
  fallback: string;
  preview: string;
  active: boolean;
};

export type TypePreset = {
  id: string;
  name: string;
  headingFontId: string;
  bodyFontId: string;
  accentFontId: string;
  headingWeight: string;
  bodyWeight: string;
  accentWeight: string;
  displaySize: string;
  h1Size: string;
  h2Size: string;
  h3Size: string;
  bodySize: string;
  headingLineHeight: string;
  bodyLineHeight: string;
  headingTracking: string;
  bodyTracking: string;
  headingTransform: string;
};

export type TypographyRole = {
  fontId: string;
  fontFamily: string;
  fontWeight: string;
  fontStyle: string;
  fontSize: string;
  lineHeight: string;
  letterSpacing: string;
  textTransform: string;
};

export type TypographySettings = {
  presetId: string;
  headingFontId: string;
  bodyFontId: string;
  accentFontId: string;
  roles: Record<string, TypographyRole>;
};

export type FontUsage = { id: string; weights: number[]; italic: boolean; variable: boolean };

const registry = data as {
  maxFamilies: number;
  recommendedFamilies: number;
  roles: string[];
  fonts: RegistryFont[];
  presets: TypePreset[];
};

const byId = new Map(registry.fonts.map((font) => [font.id, font]));
const byFamily = new Map(registry.fonts.map((font) => [font.family, font]));
const HEADING = new Set(["display", "hero", "h1", "h2", "h3", "h4"]);
const BODY = new Set(["bodyLarge", "body", "bodySmall", "caption", "label", "button", "navigation"]);
const FILL = ["fontFamily", "fontId", "fontWeight", "fontStyle", "fontSize", "lineHeight", "letterSpacing", "textTransform"] as const;
const GENERIC_SIZES = new Set(["", "64px", "48px", "40px", "36px", "32px", "22px", "18px", "16px"]);
const GENERIC_WEIGHTS = new Set(["", "400", "500", "700"]);
const owners = new Map<string, FontUsage[]>();

export const ROLE_LABELS: Record<string, string> = {
  display: "Display",
  hero: "Hero",
  h1: "Heading 1",
  h2: "Heading 2",
  h3: "Heading 3",
  h4: "Heading 4",
  bodyLarge: "Body large",
  body: "Body",
  bodySmall: "Body small",
  caption: "Caption",
  label: "Label",
  button: "Button",
  navigation: "Navigation",
  accent: "Accent",
};

export const WEIGHT_LABELS: Record<number, string> = {
  100: "Thin",
  200: "Extra Light",
  300: "Light",
  400: "Regular",
  500: "Medium",
  600: "Semi Bold",
  700: "Bold",
  800: "Extra Bold",
  900: "Black",
};

export function fontList() {
  return registry.fonts.filter((font) => font.active);
}

export function fontById(id: string) {
  return byId.get(id);
}

export function fontByFamily(family: string) {
  return byFamily.get(family);
}

export function typeRoles() {
  return registry.roles;
}

export function typePresets() {
  return registry.presets;
}

export function stack(family: string) {
  if (!family || family === "inherit") return "inherit";
  const font = byFamily.get(family);
  if (!font) return "inherit";
  const quoted = font.family.includes(" ") ? `"${font.family}"` : font.family;
  return `${quoted}, ${font.fallback}`;
}

export const TEXT_FONTS: Record<string, string> = Object.fromEntries(fontList().map((font) => [font.family, stack(font.family)]));
export const TEXT_FONT_NAMES = fontList().map((font) => font.family);

export function stylesheet(usages: FontUsage[]) {
  const families: string[] = [];
  for (const usage of usages.slice(0, registry.maxFamilies || 6)) {
    const font = byId.get(usage.id);
    if (!font || font.provider !== "google" || !font.active) continue;
    const query = familyQuery(font, usage);
    if (query) families.push(query);
  }
  if (families.length === 0) return null;
  return `https://fonts.googleapis.com/css2?${families.join("&")}&display=swap`;
}

export function collectFonts(document: EditorDocument) {
  const found = new Map<string, FontUsage>();
  const tokens = resolvedTokens(document);
  const visit = (node: EditorNode) => {
    for (const breakpoint of ["desktop", "tablet", "mobile"] as Breakpoint[]) {
      addUsage(found, presentedStyles(node, breakpoint, tokens));
    }
    node.children.forEach(visit);
  };
  visit(document.root);
  return [...found.values()];
}

export function useFonts(owner: string, usages: FontUsage[]) {
  const key = usages.map((usage) => `${usage.id}:${usage.weights.join(".")}:${usage.italic}:${usage.variable}`).join("|");
  useEffect(() => {
    owners.set(owner, usages);
    publishFonts();
    return () => {
      owners.delete(owner);
      publishFonts();
    };
  }, [owner, key]);
}

export function FontRequest({ owner, families }: { owner: string; families: string[] }) {
  useFonts(owner, families.flatMap((family) => {
    const usage = usageForFamily(family);
    return usage ? [usage] : [];
  }));
  return null;
}

export function usageForFamily(family: string): FontUsage | null {
  const font = byFamily.get(family);
  if (!font || font.provider !== "google") return null;
  const weights = font.weights.filter((weight) => weight === 400 || weight === 500 || weight === 600 || weight === 700);
  return { id: font.id, weights: weights.length > 0 ? weights : [font.weights[0]], italic: font.styles.includes("italic"), variable: false };
}

export function resolvedTokens(document: EditorDocument): TypographySettings {
  const current = document.typography;
  if (current?.roles?.body && current.roles.h1) return current;
  const heading = fontByFamily(firstFamily(document.root, "HEADING") || "Fraunces");
  const body = fontByFamily(firstFamily(document.root, "PARAGRAPH") || heading?.family || "Newsreader");
  return buildTokens("custom", heading?.id || "fraunces", body?.id || heading?.id || "fraunces", "");
}

export function presentedStyles(node: EditorNode, breakpoint: Breakpoint, tokens: TypographySettings) {
  const styles = { ...stylesFor(node, breakpoint) };
  const role = roleFor(node);
  const token = role ? tokens.roles[role] : undefined;
  if (!token) return styles;
  for (const key of FILL) {
    if (!styles[key] && token[key]) styles[key] = token[key];
  }
  return styles;
}

export function assignRole(document: EditorDocument, role: string, fontId: string) {
  const font = fontById(fontId);
  const current = resolvedTokens(document);
  const previous = current.roles[role];
  if (!font || !previous) return;
  const nextRole = { ...previous, fontId: font.id, fontFamily: font.family, fontWeight: snapWeight(font, previous.fontWeight) };
  const next = {
    ...current,
    roles: { ...current.roles, [role]: nextRole },
    headingFontId: role === "display" || role === "h1" ? font.id : current.headingFontId,
    bodyFontId: role === "body" ? font.id : current.bodyFontId,
    accentFontId: role === "accent" ? font.id : current.accentFontId,
  };
  document.typography = next;
  const visit = (node: EditorNode) => {
    if (node.type === "HEADING" || node.type === "PARAGRAPH" || node.type === "BUTTON") {
      const family = node.styles.fontFamily ?? "";
      const id = node.styles.fontId ?? "";
      const sameRole = roleFor(node) === role || node.styles.fontRole === role;
      if (sameRole && (!family || family === previous.fontFamily || id === previous.fontId)) {
        node.styles.fontId = font.id;
        node.styles.fontFamily = font.family;
        node.styles.fontRole = role;
      }
    }
    node.children.forEach(visit);
  };
  visit(document.root);
}

export function applyPreset(document: EditorDocument, presetId: string) {
  const preset = registry.presets.find((item) => item.id === presetId);
  if (!preset) return;
  const previous = resolvedTokens(document);
  const families = new Set(Object.values(previous.roles).map((role) => role.fontFamily));
  const next = buildTokens(presetId, preset.headingFontId, preset.bodyFontId, preset.accentFontId);
  document.typography = next;
  const visit = (node: EditorNode) => {
    if (node.type === "HEADING" || node.type === "PARAGRAPH" || node.type === "BUTTON") {
      const role = roleFor(node);
      const before = previous.roles[role];
      const after = next.roles[role];
      const family = node.styles.fontFamily ?? "";
      const fontId = node.styles.fontId ?? "";
      const follows = !family || families.has(family) || (!!before && fontId === before.fontId);
      if (follows && after) {
        node.styles.fontId = after.fontId;
        node.styles.fontFamily = after.fontFamily;
        node.styles.fontRole = role;
        if (followsValue(node.styles.fontWeight, before?.fontWeight, GENERIC_WEIGHTS)) node.styles.fontWeight = after.fontWeight;
        if (followsValue(node.styles.fontSize, before?.fontSize, GENERIC_SIZES)) node.styles.fontSize = after.fontSize;
        if (followsValue(node.styles.lineHeight, before?.lineHeight, new Set(["", "1.08", "1.65", "1.7"]))) node.styles.lineHeight = after.lineHeight;
        if (followsValue(node.styles.letterSpacing, before?.letterSpacing, new Set(["", "0", "0em", "-0.03em"]))) node.styles.letterSpacing = after.letterSpacing;
        if (followsValue(node.styles.textTransform, before?.textTransform, new Set(["", "none"]))) node.styles.textTransform = after.textTransform;
      }
    }
    node.children.forEach(visit);
  };
  visit(document.root);
}

export function toFluid(value: string) {
  const parsed = Number.parseFloat(value);
  const pixels = Number.isFinite(parsed) && value.includes("px") ? parsed : 32;
  const max = Math.max(1, pixels / 16);
  const min = Math.max(0.85, Math.round(max * 0.62 * 100) / 100);
  const vw = Math.min(8, Math.max(2, Math.round(max * 1.35 * 10) / 10));
  return `clamp(${trim(min)}rem, ${trim(vw)}vw, ${trim(max)}rem)`;
}

export function toFixedSize(value: string) {
  const rem = value.match(/,\s*([\d.]+)rem\s*\)\s*$/);
  if (rem) return `${Math.round(Number(rem[1]) * 16)}px`;
  const pixels = value.match(/([\d.]+)px/);
  if (pixels) return `${Math.round(Number(pixels[1]))}px`;
  return "18px";
}

export function roleFor(node: EditorNode) {
  const explicit = String(node.styles.fontRole ?? "");
  if (explicit && registry.roles.includes(explicit)) return explicit;
  if (node.type === "HEADING") {
    const level = Number(node.props.level ?? 2);
    if (level <= 1) return "h1";
    if (level === 2) return "h2";
    if (level === 3) return "h3";
    return "h4";
  }
  if (node.type === "BUTTON") return "button";
  if (node.type === "PARAGRAPH") return "body";
  return "";
}

function buildTokens(presetId: string, headingId: string, bodyId: string, accentId: string): TypographySettings {
  const preset = registry.presets.find((item) => item.id === presetId);
  let heading = fontById(headingId) || fontById(preset?.headingFontId || "") || fontById("fraunces");
  let body = fontById(bodyId) || fontById(preset?.bodyFontId || "") || fontById("manrope");
  if (!heading || !body) {
    throw new Error("The font registry is missing a fallback font.");
  }
  if (body.category === "handwriting") {
    body = fontById(preset && fontById(preset.bodyFontId)?.category !== "handwriting" ? preset.bodyFontId : "manrope") || body;
  }
  const accent = fontById(accentId) || (presetId === "custom" ? undefined : fontById(preset?.accentFontId || ""));
  const roles: Record<string, TypographyRole> = {};
  for (const role of registry.roles) {
    roles[role] = roleStyle(role, heading, body, accent, preset);
  }
  return {
    presetId: preset?.id || "custom",
    headingFontId: heading.id,
    bodyFontId: body.id,
    accentFontId: accent?.id || "",
    roles,
  };
}

function roleStyle(role: string, heading: RegistryFont, body: RegistryFont, accent: RegistryFont | undefined, preset?: TypePreset): TypographyRole {
  const headingRole = HEADING.has(role);
  const font = role === "accent" ? accent || heading : BODY.has(role) ? body : heading;
  let weight = role === "accent" ? preset?.accentWeight || "400" : headingRole ? preset?.headingWeight || "500" : preset?.bodyWeight || "400";
  if ((role === "h3" || role === "h4") && (font.weights.includes(500) || font.variable)) weight = "500";
  if ((role === "button" || role === "label" || role === "navigation") && (font.weights.includes(600) || font.variable)) weight = "600";
  const size = role === "display" || role === "hero" || role === "accent"
    ? preset?.displaySize || "64px"
    : role === "h1" ? preset?.h1Size || "64px"
      : role === "h2" ? preset?.h2Size || "36px"
        : role === "h3" || role === "h4" ? preset?.h3Size || "22px"
          : role === "bodySmall" || role === "caption" ? "0.875rem"
            : role === "label" || role === "navigation" || role === "button" ? "0.95rem"
              : preset?.bodySize || "18px";
  const line = role === "button" ? "1.2" : headingRole || role === "accent" ? preset?.headingLineHeight || "1.08" : preset?.bodyLineHeight || "1.65";
  const tracking = headingRole ? preset?.headingTracking || "-0.03em" : "0em";
  const transform = role === "display" || role === "hero" || role === "h1" ? preset?.headingTransform || "none" : "none";
  return {
    fontId: font.id,
    fontFamily: font.family,
    fontWeight: snapWeight(font, weight),
    fontStyle: "normal",
    fontSize: size,
    lineHeight: line,
    letterSpacing: tracking,
    textTransform: transform,
  };
}

function snapWeight(font: RegistryFont, raw: string) {
  const wanted = raw === "bold" ? 700 : raw === "normal" || raw === "" ? 400 : Number.parseInt(raw, 10) || 400;
  const axis = font.axes.find((item) => item.tag === "wght");
  if (font.variable && axis) return String(Math.min(axis.max, Math.max(axis.min, wanted)));
  return String(font.weights.reduce((best, weight) => Math.abs(weight - wanted) < Math.abs(best - wanted) ? weight : best, font.weights[0] || 400));
}

function firstFamily(node: EditorNode, type: string): string {
  if (node.type === type && node.styles.fontFamily && node.styles.fontFamily !== "inherit") return node.styles.fontFamily;
  for (const child of node.children) {
    const found = firstFamily(child, type);
    if (found) return found;
  }
  return "";
}

function followsValue(current: string | undefined, previous: string | undefined, generic: Set<string>) {
  const value = current ?? "";
  return !value || value === previous || generic.has(value);
}

function addUsage(found: Map<string, FontUsage>, styles: StyleMap) {
  const font = fontByFamily(styles.fontFamily || "") || fontById(styles.fontId || "");
  if (!font) return;
  const current = found.get(font.id) ?? { id: font.id, weights: [], italic: false, variable: false };
  const weight = Number.parseInt(styles.fontWeight || "400", 10);
  if (Number.isFinite(weight) && !current.weights.includes(weight)) current.weights.push(weight);
  if (styles.fontStyle === "italic") current.italic = true;
  if (styles.fontVariationSettings || (Number.isFinite(weight) && !font.weights.includes(weight))) current.variable = true;
  found.set(font.id, current);
}

function familyQuery(font: RegistryFont, usage: FontUsage) {
  const name = font.family.replaceAll(" ", "+");
  const italic = usage.italic && font.styles.includes("italic");
  const axis = font.axes.find((item) => item.tag === "wght");
  const variable = font.variable && axis && (usage.variable || usage.weights.some((weight) => !font.weights.includes(weight)));
  if (variable && axis) {
    const opsz = font.axes.find((item) => item.tag === "opsz");
    const range = `${axis.min}..${axis.max}`;
    if (opsz && italic) return `family=${name}:ital,opsz,wght@0,${opsz.min}..${opsz.max},${range};1,${opsz.min}..${opsz.max},${range}`;
    if (opsz) return `family=${name}:opsz,wght@${opsz.min}..${opsz.max},${range}`;
    if (italic) return `family=${name}:ital,wght@0,${range};1,${range}`;
    return `family=${name}:wght@${range}`;
  }
  const requested = usage.weights.filter((weight) => font.weights.includes(weight)).sort((a, b) => a - b);
  const weights = requested.length > 0 ? requested : [font.weights[0]];
  if (italic) return `family=${name}:ital,wght@${weights.flatMap((weight) => [`0,${weight}`, `1,${weight}`]).join(";")}`;
  return `family=${name}:wght@${weights.join(";")}`;
}

function publishFonts() {
  const merged = new Map<string, FontUsage>();
  for (const list of owners.values()) {
    for (const usage of list) {
      const current = merged.get(usage.id) ?? { id: usage.id, weights: [], italic: false, variable: false };
      for (const weight of usage.weights) {
        if (!current.weights.includes(weight)) current.weights.push(weight);
      }
      current.italic = current.italic || usage.italic;
      current.variable = current.variable || usage.variable;
      merged.set(usage.id, current);
    }
  }
  const url = stylesheet([...merged.values()]);
  const existing = document.getElementById("prism-fonts");
  if (!url) {
    existing?.remove();
    return;
  }
  const link = existing instanceof HTMLLinkElement ? existing : document.createElement("link");
  link.id = "prism-fonts";
  link.rel = "stylesheet";
  if (link.href !== url) link.href = url;
  link.onerror = () => link.remove();
  if (!link.parentElement) document.head.appendChild(link);
}

function trim(value: number) {
  return (Math.round(value * 100) / 100).toString();
}
