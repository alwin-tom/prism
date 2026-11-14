import { useState } from "react";
import { Button, Field } from "../../components/ui";
import type { EventSite } from "../../types";
import { FRAME_WIDTH, type EditorNode } from "../visual-editor/document";
import { VisualPage } from "../visual-editor/VisualPage";
import { isEmpty } from "./conditions";
import { fillFromInvite } from "./inviteFill";
import type { Question } from "./types";

/** 60% background · 30% ink · 10% accent */
export const PALETTES = [
  { name: "Ivory", background: "#f7f4ee", ink: "#172b35", accent: "#087f80" },
  { name: "Ink", background: "#172b35", ink: "#f4faff", accent: "#ff925b" },
  { name: "Blush", background: "#fff5f2", ink: "#3b2428", accent: "#e95760" },
  { name: "Leaf", background: "#f3f7f2", ink: "#1d3328", accent: "#2f6b4f" },
  { name: "Sand", background: "#fbf6ee", ink: "#3a2e22", accent: "#c47b3a" },
  { name: "Pearl", background: "#f8fafc", ink: "#0f172a", accent: "#0ea5a0" },
  { name: "Champagne", background: "#faf6f0", ink: "#3f2a1d", accent: "#b8860b" },
  { name: "Sky", background: "#f0f7fb", ink: "#163247", accent: "#2b6cb0" },
  { name: "Slate", background: "#eef2f4", ink: "#1e293b", accent: "#475569" },
  { name: "Rosewood", background: "#faf3f4", ink: "#4a1d28", accent: "#a82735" },
  { name: "Olive", background: "#f4f5ef", ink: "#2c3320", accent: "#6b7c3a" },
  { name: "Coast", background: "#eef8f7", ink: "#134e4a", accent: "#0f766e" },
  { name: "Midnight", background: "#0b1220", ink: "#e8eef7", accent: "#38bdf8" },
  { name: "Ember", background: "#1a1210", ink: "#f8efe8", accent: "#e07a3a" },
  { name: "Orchid", background: "#fbf5fa", ink: "#3b1f36", accent: "#9d4edd" },
  { name: "Citrus", background: "#fffbeb", ink: "#292524", accent: "#ca8a04" },
  { name: "Clay", background: "#f6f0ea", ink: "#3b2f2a", accent: "#9a5b42" },
  { name: "Navy", background: "#eef2f7", ink: "#0b1c33", accent: "#1d4e89" },
  { name: "Forest", background: "#0f1a14", ink: "#e7f0ea", accent: "#34d399" },
  { name: "Snow", background: "#ffffff", ink: "#111827", accent: "#dc2626" },
] as const;

export type Palette = (typeof PALETTES)[number];

export function DraftSide({ questions, answers, palette, onPalette, event, applying, onApply, onFill }: {
  questions: Question[];
  answers: Record<string, unknown>;
  palette: Palette;
  onPalette: (palette: Palette) => void;
  event?: EventSite;
  applying: boolean;
  onApply: () => void;
  onFill: (patch: Record<string, string>) => void;
}) {
  const [invite, setInvite] = useState("");
  const [notice, setNotice] = useState("");
  const title = firstAnswer(answers, questions, /name|couple|host|title|person|who/) || event?.eventName || "Your website";
  const when = firstAnswer(answers, questions, /date|when|day/);
  const venue = firstAnswer(answers, questions, /venue|place|location|where/);
  const document = event?.editorDocument;
  const showPage = document ? hasText(document.root) : false;
  const scale = 300 / FRAME_WIDTH.desktop;

  function readInvite() {
    const patch = fillFromInvite(invite, questions);
    const empty = Object.fromEntries(Object.entries(patch).filter(([key]) => isEmpty(answers[key])));
    const count = Object.keys(empty).length;
    if (count === 0) {
      setNotice(Object.keys(patch).length === 0 ? "Nothing in that note matched an open question." : "Those answers are already filled.");
      return;
    }
    onFill(empty);
    setNotice(`Filled ${count} empty ${count === 1 ? "answer" : "answers"}. Check them before you continue.`);
  }

  return (
    <aside className="wizard-side">
      <section className="panel wizard-card">
        <h2>Preview</h2>
        <article className="draft-card" style={{ background: palette.background, color: palette.ink }}>
          <p style={{ color: palette.accent }}>Live from your answers</p>
          <strong>{title}</strong>
          {when ? <span>{when}</span> : null}
          {venue ? <span>{venue}</span> : null}
        </article>
        {showPage && document && event ? (
          <div className="draft-page">
            <div style={{ width: FRAME_WIDTH.desktop, transform: `scale(${scale})`, transformOrigin: "top left" }}>
              <VisualPage document={document} breakpoint="desktop" mediaEventId={event.id} />
            </div>
          </div>
        ) : null}
      </section>
      <section className="panel wizard-card">
        <h2>Palette</h2>
        <p className="studio-hint palette-ratio-hint">60% surface · 30% text · 10% accent</p>
        <div className="palette-row" role="list">
          {PALETTES.map((item) => (
            <button
              key={item.name}
              type="button"
              className="palette-swatch"
              role="listitem"
              aria-label={`${item.name}: 60% ${item.background}, 30% ${item.ink}, 10% ${item.accent}`}
              aria-pressed={palette.name === item.name}
              title={`${item.name} · 60/30/10`}
              onClick={() => onPalette(item)}
            >
              <span className="swatch-60" style={{ background: item.background }} title="60% surface" />
              <span className="swatch-30" style={{ background: item.ink }} title="30% text" />
              <span className="swatch-10" style={{ background: item.accent }} title="10% accent" />
            </button>
          ))}
        </div>
        <p className="palette-selected"><span>{palette.name}</span> · surface {palette.background} · text {palette.ink} · accent {palette.accent}</p>
        <Button type="button" tone="secondary" disabled={!event || applying} onClick={onApply}>{applying ? "Saving" : "Use this palette"}</Button>
        <p className="studio-hint">The palette is saved on the site. The generated page can still choose its own type.</p>
      </section>
      <section className="panel wizard-card">
        <h2>Smart fill</h2>
        <Field label="Paste an invite" hint="Names, a date, a time, and a venue are copied into empty answers. Nothing is sent to a model.">
          <textarea rows={5} value={invite} onChange={(item) => setInvite(item.target.value)} />
        </Field>
        <Button type="button" tone="secondary" onClick={readInvite}>Fill empty answers</Button>
        {notice ? <p className="studio-hint">{notice}</p> : null}
      </section>
    </aside>
  );
}

function firstAnswer(answers: Record<string, unknown>, questions: Question[], pattern: RegExp) {
  const question = questions.find((item) => pattern.test(`${item.key} ${item.label}`.toLowerCase()) && !isEmpty(answers[item.key]) && typeof answers[item.key] !== "object");
  return question ? String(answers[question.key]) : "";
}

function hasText(node: EditorNode): boolean {
  const text = node.props?.text;
  if (typeof text === "string" && text.trim() !== "") return true;
  return node.children.some(hasText);
}
