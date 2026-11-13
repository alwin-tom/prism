import { useState } from "react";
import { Button, Field } from "../../components/ui";
import type { EventSite } from "../../types";
import { FRAME_WIDTH, type EditorNode } from "../visual-editor/document";
import { VisualPage } from "../visual-editor/VisualPage";
import { isEmpty } from "./conditions";
import { fillFromInvite } from "./inviteFill";
import type { Question } from "./types";

export const PALETTES = [
  { name: "Ivory", background: "#f7f4ee", ink: "#172b35", accent: "#087f80" },
  { name: "Ink", background: "#172b35", ink: "#f4faff", accent: "#ff925b" },
  { name: "Blush", background: "#fff5f2", ink: "#3b2428", accent: "#e95760" },
  { name: "Leaf", background: "#f3f7f2", ink: "#1d3328", accent: "#2f6b4f" },
  { name: "Sand", background: "#fbf6ee", ink: "#3a2e22", accent: "#c47b3a" },
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
        <div className="palette-row">
          {PALETTES.map((item) => (
            <button key={item.name} type="button" className="palette-swatch" aria-label={item.name} aria-pressed={palette.name === item.name} title={item.name} onClick={() => onPalette(item)}>
              <span style={{ background: item.background }} />
              <span style={{ background: item.accent }} />
              <span style={{ background: item.ink }} />
            </button>
          ))}
        </div>
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
