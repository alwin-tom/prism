import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Italic, Star, Strikethrough, Underline } from "lucide-react";
import { api, errorMessage } from "../../services/api";
import type { Envelope } from "../../types";
import type { Breakpoint, EditorDocument, EditorNode } from "../visual-editor/document";
import { stylesFor } from "../visual-editor/document";
import { ROLE_LABELS, WEIGHT_LABELS, applyPreset, assignRole, collectFonts, fontByFamily, fontById, fontList, roleFor, stack, toFixedSize, toFluid, typePresets, useFonts, type RegistryFont } from "./fonts";

const FAVOURITES = "prism-font-favourites";
const RECENT = "prism-font-recent";

export function TypographyPanel({ eventId, document, selected, breakpoint, paint, change, mark }: {
  eventId: string;
  document: EditorDocument;
  selected: EditorNode;
  breakpoint: Breakpoint;
  paint: (styles: Record<string, string>) => void;
  change: (apply: (copy: EditorDocument) => void) => void;
  mark: (property: string) => ReactNode;
}) {
  const styles = stylesFor(selected, breakpoint);
  const tokens = document.typography;
  const role = roleFor(selected);
  const token = role && tokens?.roles ? tokens.roles[role] : undefined;
  const family = styles.fontFamily || token?.fontFamily || "";
  const selectedFont = fontByFamily(family) || fontById(styles.fontId || token?.fontId || "");
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("all");
  const [presetId, setPresetId] = useState(tokens?.presetId || "luxury-editorial");
  const [favourites, setFavourites] = useState<string[]>(() => readList(FAVOURITES));
  const [recent, setRecent] = useState<string[]>(() => readList(RECENT));
  const [instruction, setInstruction] = useState("");
  const [preview, setPreview] = useState<{ summary: string; document: EditorDocument } | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [gradient, setGradient] = useState(styles.textGradient || "");
  const [stroke, setStroke] = useState(styles.textStroke || "");
  const fonts = fontList();
  const visible = useMemo(() => filterFonts(fonts, query, category, favourites, recent), [fonts, query, category, favourites, recent]);
  const previewFonts = visible.slice(0, 12).map((font) => ({ id: font.id, weights: [font.weights.includes(500) ? 500 : font.weights[0]], italic: false, variable: false }));
  if (selectedFont && !previewFonts.some((font) => font.id === selectedFont.id)) {
    previewFonts.push({ id: selectedFont.id, weights: [selectedFont.weights.includes(500) ? 500 : selectedFont.weights[0]], italic: false, variable: false });
  }
  useFonts("picker", previewFonts);
  useEffect(() => {
    setGradient(styles.textGradient || "");
    setStroke(styles.textStroke || "");
  }, [selected.id, styles.textGradient, styles.textStroke]);
  const used = collectFonts(document).length;

  function choose(font: RegistryFont) {
    const weight = supportedWeight(font, styles.fontWeight || token?.fontWeight || "400");
    const italic = font.styles.includes("italic") && styles.fontStyle === "italic";
    paint({
      fontId: font.id,
      fontFamily: font.family,
      fontWeight: weight,
      fontStyle: italic ? "italic" : "normal",
    });
    const next = [font.id, ...recent.filter((id) => id !== font.id)].slice(0, 6);
    setRecent(next);
    writeList(RECENT, next);
  }

  function toggleFavourite(id: string) {
    const next = favourites.includes(id) ? favourites.filter((item) => item !== id) : [id, ...favourites];
    setFavourites(next);
    writeList(FAVOURITES, next);
  }

  async function ask() {
    setBusy(true);
    setNote("");
    try {
      const response = await api.post<Envelope<{ summary: string; document: EditorDocument }>>(`/events/${eventId}/editor/typography`, { instruction });
      setPreview(response.data.data);
    } catch (error) {
      setNote(errorMessage(error, "The typography change could not be prepared."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="type-panel">
      <p className="studio-label">Pairing</p>
      <label>Preset
        <select value={presetId} onChange={(event) => setPresetId(event.target.value)}>
          {typePresets().map((preset) => <option key={preset.id} value={preset.id}>{preset.name}</option>)}
        </select>
      </label>
      <button type="button" onClick={() => change((copy) => applyPreset(copy, presetId))}>Apply pairing</button>
      <p className="studio-hint">Text that already uses the current pairing takes the new fonts. A font you chose for one element stays. The words stay as they are.</p>
      {used > 3 ? <p className="studio-hint">This page uses {used} font families. Two or three keeps the page clearer.</p> : null}
      {selected.id === document.root.id && tokens?.roles ? (
        <div className="type-roles">
          {(["display", "h1", "body", "accent"] as const).map((name) => (
            <label key={name}>{ROLE_LABELS[name]}
              <select value={tokens.roles[name]?.fontId || ""} onChange={(event) => change((copy) => assignRole(copy, name, event.target.value))}>
                {fonts.map((font) => <option key={font.id} value={font.id}>{font.family}</option>)}
              </select>
            </label>
          ))}
        </div>
      ) : null}
      <p className="studio-label">Font {mark("fontFamily")}</p>
      <input aria-label="Search fonts" placeholder="Search fonts" value={query} onChange={(event) => setQuery(event.target.value)} />
      <div className="choice-row type-filters" role="group" aria-label="Font category">
        {["all", "sans", "serif", "display", "handwriting", "favourites", "recent"].map((item) => (
          <button key={item} type="button" aria-pressed={category === item} onClick={() => setCategory(item)}>{labelFor(item)}</button>
        ))}
      </div>
      <div className="font-list" role="listbox" aria-label="Font family">
        {visible.slice(0, 12).map((font) => (
          <div key={font.id} className="font-row">
            <button type="button" role="option" aria-selected={selectedFont?.id === font.id} style={{ fontFamily: stack(font.family) }} onClick={() => choose(font)}>
              <span>{font.family}</span>
              <small>{font.provider === "google" ? "Google" : font.provider}</small>
            </button>
            <button type="button" aria-label={favourites.includes(font.id) ? `Unfavourite ${font.family}` : `Favourite ${font.family}`} aria-pressed={favourites.includes(font.id)} onClick={() => toggleFavourite(font.id)}><Star size={14} /></button>
          </div>
        ))}
        {visible.length === 0 ? <p className="studio-hint">No fonts in this filter.</p> : null}
      </div>
      {selectedFont ? <p className="type-preview" style={{ fontFamily: stack(selectedFont.family), fontWeight: styles.fontWeight || token?.fontWeight || "400", fontStyle: styles.fontStyle || "normal", letterSpacing: styles.letterSpacing || token?.letterSpacing || "0em" }}>{selectedFont.preview}</p> : null}
      <label>Weight {mark("fontWeight")}
        <select value={styles.fontWeight || token?.fontWeight || "400"} onChange={(event) => paint({ fontWeight: event.target.value, fontVariationSettings: selectedFont?.variable ? `"wght" ${event.target.value}` : "" })}>
          {weightOptions(selectedFont, styles.fontWeight || token?.fontWeight || "").map((weight) => <option key={weight} value={weight}>{WEIGHT_LABELS[weight] || "Custom"} {weight}</option>)}
        </select>
      </label>
      {selectedFont?.axes.map((axis) => (
        <label key={axis.tag} className="studio-slider">{axis.tag === "wght" ? "Weight axis" : axis.tag === "opsz" ? "Optical size" : axis.tag}
          <input type="range" min={axis.min} max={axis.max} step={axis.step} value={axisValue(styles, axis.tag, axis.default)} onChange={(event) => {
            const next = axisPatch(styles.fontVariationSettings || "", axis.tag, event.target.value);
            paint(axis.tag === "wght" ? { fontVariationSettings: next, fontWeight: event.target.value } : { fontVariationSettings: next });
          }} />
          <span>{axisValue(styles, axis.tag, axis.default)}</span>
        </label>
      ))}
      <div className="choice-row" role="group" aria-label="Text style">
        <button type="button" aria-label="Italic" title="Italic" aria-pressed={styles.fontStyle === "italic"} disabled={!selectedFont?.styles.includes("italic")} onClick={() => paint({ fontStyle: styles.fontStyle === "italic" ? "normal" : "italic" })}><Italic size={15} /></button>
        <button type="button" aria-label="Underline" title="Underline" aria-pressed={styles.textDecoration === "underline"} onClick={() => paint({ textDecoration: styles.textDecoration === "underline" ? "none" : "underline" })}><Underline size={15} /></button>
        <button type="button" aria-label="Strikethrough" title="Strikethrough" aria-pressed={styles.textDecoration === "line-through"} onClick={() => paint({ textDecoration: styles.textDecoration === "line-through" ? "none" : "line-through" })}><Strikethrough size={15} /></button>
      </div>
      <label>Case {mark("textTransform")}
        <select value={styles.textTransform || "none"} onChange={(event) => paint({ textTransform: event.target.value })}>
          <option value="none">As typed</option>
          <option value="uppercase">Uppercase</option>
          <option value="lowercase">Lowercase</option>
          <option value="capitalize">Capitalize</option>
        </select>
      </label>
      <label>Text size {mark("fontSize")}
        <input value={styles.fontSize || ""} placeholder={token?.fontSize || "18px"} onChange={(event) => paint({ fontSize: event.target.value })} />
      </label>
      <div className="choice-row type-filters">
        <button type="button" aria-pressed={(styles.fontSize || "").startsWith("clamp(")} onClick={() => paint({ fontSize: (styles.fontSize || token?.fontSize || "32px").startsWith("clamp(") ? toFixedSize(styles.fontSize || token?.fontSize || "") : toFluid(styles.fontSize || token?.fontSize || "32px") })}>Fluid size</button>
        {breakpoint === "desktop" ? <button type="button" onClick={() => paint({ fontFamily: "", fontId: "", fontWeight: "", fontStyle: "", fontSize: "", lineHeight: "", letterSpacing: "", textTransform: "", fontVariationSettings: "" })}>Use page type</button> : null}
      </div>
      <label>Line height {mark("lineHeight")}
        <input value={styles.lineHeight || ""} placeholder={token?.lineHeight || "1.5"} onChange={(event) => paint({ lineHeight: event.target.value })} />
      </label>
      <label>Letter spacing {mark("letterSpacing")}
        <input value={styles.letterSpacing || ""} placeholder={token?.letterSpacing || "0em"} onChange={(event) => paint({ letterSpacing: event.target.value })} />
      </label>
      <details className="type-advanced">
        <summary>Advanced type</summary>
        <label>Word spacing {mark("wordSpacing")}
          <input value={styles.wordSpacing || ""} placeholder="0em" onChange={(event) => paint({ wordSpacing: event.target.value })} />
        </label>
        <label>Shadow
          <select value={styles.textShadow || ""} onChange={(event) => paint({ textShadow: event.target.value })}>
            <option value="">None</option>
            <option value="0 1px 1px rgba(28,25,21,0.28)">Soft</option>
            <option value="0 2px 0 #1c1915">Hard</option>
          </select>
        </label>
        <label>Gradient
          <input value={gradient} placeholder="linear-gradient(120deg, #1c1915, #1b6c80)" onChange={(event) => setGradient(event.target.value)} onBlur={() => { if (gradient === "" || /^(linear|radial)-gradient\([^)]+\)$/.test(gradient)) paint({ textGradient: gradient }); }} />
        </label>
        <label>Stroke
          <input value={stroke} placeholder="1px #1c1915" onChange={(event) => setStroke(event.target.value)} onBlur={() => { if (stroke === "" || /^\d(?:\.\d)?px (#[0-9a-fA-F]{3,8}|[a-z]{3,20})$/.test(stroke)) paint({ textStroke: stroke }); }} />
        </label>
      </details>
      <p className="studio-label">Ask for a type change</p>
      <textarea aria-label="Typography request" rows={3} value={instruction} placeholder="Make the headings more elegant" onChange={(event) => setInstruction(event.target.value)} />
      <button type="button" disabled={busy || instruction.trim().length < 3} onClick={() => void ask()}>{busy ? "Preparing" : "Preview change"}</button>
      {preview ? (
        <div className="type-confirm">
          <p>{preview.summary}</p>
          <div className="choice-row type-filters">
            <button type="button" onClick={() => { change((copy) => { copy.typography = preview.document.typography; copy.root = preview.document.root; }); setPreview(null); }}>Use this change</button>
            <button type="button" onClick={() => setPreview(null)}>Cancel</button>
          </div>
        </div>
      ) : null}
      {note ? <p className="studio-hint">{note}</p> : null}
    </div>
  );
}

function filterFonts(fonts: RegistryFont[], query: string, category: string, favourites: string[], recent: string[]) {
  const needle = query.trim().toLowerCase();
  return fonts.filter((font) => {
    if (needle && !font.family.toLowerCase().includes(needle) && !font.id.includes(needle)) return false;
    if (category === "favourites") return favourites.includes(font.id);
    if (category === "recent") return recent.includes(font.id);
    if (category !== "all" && font.category !== category) return false;
    return true;
  });
}

function weightOptions(font: RegistryFont | undefined, current: string) {
  const weights = font?.weights?.length ? [...font.weights] : [400, 500, 700];
  const parsed = Number.parseInt(current, 10);
  if (Number.isFinite(parsed) && !weights.includes(parsed)) weights.push(parsed);
  return weights.sort((a, b) => a - b);
}

function supportedWeight(font: RegistryFont, current: string) {
  const parsed = Number.parseInt(current, 10);
  if (!Number.isFinite(parsed)) return String(font.weights.includes(400) ? 400 : font.weights[0]);
  if (font.variable) {
    const axis = font.axes.find((item) => item.tag === "wght");
    if (axis) return String(Math.min(axis.max, Math.max(axis.min, parsed)));
  }
  if (font.weights.includes(parsed)) return String(parsed);
  return String(font.weights.reduce((best, weight) => Math.abs(weight - parsed) < Math.abs(best - parsed) ? weight : best, font.weights[0]));
}

function axisValue(styles: Record<string, string>, tag: string, fallback: number) {
  const match = styles.fontVariationSettings?.match(new RegExp(`"${tag}"\\s+(\\d+)`));
  if (match) return match[1];
  if (tag === "wght" && styles.fontWeight) return styles.fontWeight;
  return String(fallback);
}

function axisPatch(current: string, tag: string, value: string) {
  if (current.includes(`"${tag}"`)) {
    return current.replace(new RegExp(`"${tag}"\\s+\\d+(?:\\.\\d+)?`), `"${tag}" ${value}`);
  }
  return [current, `"${tag}" ${value}`].filter(Boolean).join(", ");
}

function labelFor(category: string) {
  if (category === "all") return "All";
  if (category === "sans") return "Sans";
  if (category === "serif") return "Serif";
  if (category === "display") return "Display";
  if (category === "handwriting") return "Script";
  if (category === "favourites") return "Saved";
  return "Recent";
}

function readList(key: string) {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || "[]");
    return Array.isArray(parsed) ? parsed.filter((item) => typeof item === "string") : [];
  } catch {
    return [];
  }
}

function writeList(key: string, values: string[]) {
  localStorage.setItem(key, JSON.stringify(values));
}
