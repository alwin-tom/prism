/** Structured text runs — source of truth for inline hyperlinks. */

export type LinkTarget = "_self" | "_blank";

export type RichTextRun = {
  text: string;
  href?: string;
  target?: LinkTarget;
  title?: string;
};

export type MarqueeItem = {
  id: string;
  kind: "text" | "image";
  text?: string;
  richText?: RichTextRun[];
  mediaId?: string;
  alt?: string;
  href?: string;
  target?: LinkTarget;
  title?: string;
};

export type PropValue = string | number | RichTextRun[] | MarqueeItem[];

const SAFE_LINK = /^(https?:\/\/|mailto:|tel:|#|\/)/i;
const DANGEROUS = /^(javascript:|data:|vbscript:)/i;

export function isSafeHref(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed || trimmed.length > 400) return false;
  if (DANGEROUS.test(trimmed)) return false;
  return SAFE_LINK.test(trimmed);
}

export function sanitizeHref(value: string): string {
  const trimmed = value.trim();
  return isSafeHref(trimmed) ? trimmed : "";
}

export function linkRel(target?: string): string | undefined {
  return target === "_blank" ? "noopener noreferrer" : undefined;
}

export function plainFromRuns(runs: RichTextRun[] | undefined): string {
  if (!runs?.length) return "";
  return runs.map((run) => run.text).join("");
}

export function runsFromPlain(text: string): RichTextRun[] {
  return [{ text: text ?? "" }];
}

export function normalizeRuns(runs: unknown, maxText = 4000): RichTextRun[] {
  if (!Array.isArray(runs)) return [];
  const out: RichTextRun[] = [];
  let total = 0;
  for (const item of runs) {
    if (!item || typeof item !== "object") continue;
    const row = item as Record<string, unknown>;
    const text = String(row.text ?? "");
    if (!text) continue;
    if (total + text.length > maxText) {
      const clipped = text.slice(0, Math.max(0, maxText - total));
      if (clipped) out.push(runFromParts(clipped, row));
      break;
    }
    total += text.length;
    out.push(runFromParts(text, row));
    if (out.length >= 40) break;
  }
  return out.length ? out : [];
}

function runFromParts(text: string, row: Record<string, unknown>): RichTextRun {
  const href = sanitizeHref(String(row.href ?? ""));
  const target = row.target === "_blank" ? "_blank" : "_self";
  const title = String(row.title ?? "").slice(0, 120);
  const run: RichTextRun = { text };
  if (href) {
    run.href = href;
    run.target = target;
    if (title) run.title = title;
  }
  return run;
}

/** Prefer richText; fall back to plain text as a single run. */
export function effectiveRuns(props: Record<string, PropValue | undefined>, key = "text"): RichTextRun[] {
  const rich = props.richText;
  if (Array.isArray(rich) && rich.length > 0) {
    return normalizeRuns(rich);
  }
  return runsFromPlain(String(props[key] ?? ""));
}

export function runsHaveLinks(runs: RichTextRun[]): boolean {
  return runs.some((run) => Boolean(run.href));
}

/**
 * Apply or replace a link on [start, end) of the concatenated plain text.
 * Merges adjacent runs with identical link attrs.
 */
export function applyLink(runs: RichTextRun[], start: number, end: number, href: string, target: LinkTarget = "_blank", title = ""): RichTextRun[] {
  const plain = plainFromRuns(runs);
  const a = Math.max(0, Math.min(start, end, plain.length));
  const b = Math.max(a, Math.min(Math.max(start, end), plain.length));
  if (a === b) return mergeRuns(runs);
  const safe = sanitizeHref(href);
  const sliced = sliceRuns(runs, a, b);
  const middle = sliced.map((run) => {
    if (!safe) {
      const next = { ...run };
      delete next.href;
      delete next.target;
      delete next.title;
      return next;
    }
    return {
      ...run,
      href: safe,
      target,
      ...(title ? { title: title.slice(0, 120) } : {}),
    };
  });
  return mergeRuns([...sliceRuns(runs, 0, a), ...middle, ...sliceRuns(runs, b, plain.length)]);
}

export function removeLinksInRange(runs: RichTextRun[], start: number, end: number): RichTextRun[] {
  return applyLink(runs, start, end, "");
}

export function syncPlainText(runs: RichTextRun[], nextPlain: string): RichTextRun[] {
  const current = plainFromRuns(runs);
  if (current === nextPlain) return runs;
  // Preserve a single whole-run link if the text still maps 1:1 to one linked run.
  if (runs.length === 1 && runs[0].href) {
    return [{ ...runs[0], text: nextPlain }];
  }
  // Best-effort: keep non-overlapping linked spans whose text still appears.
  if (!runsHaveLinks(runs)) return runsFromPlain(nextPlain);
  const kept: RichTextRun[] = [];
  let cursor = 0;
  for (const run of runs) {
    if (!run.href) continue;
    const at = nextPlain.indexOf(run.text, cursor);
    if (at < 0) continue;
    if (at > cursor) kept.push({ text: nextPlain.slice(cursor, at) });
    kept.push({ ...run });
    cursor = at + run.text.length;
  }
  if (cursor < nextPlain.length) kept.push({ text: nextPlain.slice(cursor) });
  return kept.length ? mergeRuns(kept) : runsFromPlain(nextPlain);
}

function sliceRuns(runs: RichTextRun[], start: number, end: number): RichTextRun[] {
  if (start >= end) return [];
  const out: RichTextRun[] = [];
  let offset = 0;
  for (const run of runs) {
    const next = offset + run.text.length;
    const from = Math.max(start, offset);
    const to = Math.min(end, next);
    if (from < to) {
      const piece = { ...run, text: run.text.slice(from - offset, to - offset) };
      out.push(piece);
    }
    offset = next;
    if (offset >= end) break;
  }
  return out;
}

export function mergeRuns(runs: RichTextRun[]): RichTextRun[] {
  const out: RichTextRun[] = [];
  for (const run of runs) {
    if (!run.text) continue;
    const last = out[out.length - 1];
    if (
      last
      && last.href === run.href
      && last.target === run.target
      && last.title === run.title
    ) {
      last.text += run.text;
    } else {
      out.push({ ...run });
    }
  }
  return out;
}

export function newMarqueeItem(kind: "text" | "image" = "text"): MarqueeItem {
  const id = `mq-${Math.random().toString(36).slice(2, 10)}`;
  if (kind === "image") return { id, kind: "image", mediaId: "", alt: "" };
  return { id, kind: "text", text: "Announcement", richText: [{ text: "Announcement" }] };
}

export function normalizeMarqueeItems(value: unknown): MarqueeItem[] {
  if (!Array.isArray(value)) return [];
  const out: MarqueeItem[] = [];
  for (const item of value) {
    if (!item || typeof item !== "object") continue;
    const row = item as Record<string, unknown>;
    const id = String(row.id ?? "").toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 40) || newMarqueeItem().id;
    const kind = row.kind === "image" ? "image" : "text";
    if (kind === "image") {
      const mediaId = String(row.mediaId ?? "").toLowerCase();
      out.push({
        id,
        kind: "image",
        mediaId: /^[0-9a-f-]{36}$/.test(mediaId) ? mediaId : "",
        alt: String(row.alt ?? "").slice(0, 180),
        href: sanitizeHref(String(row.href ?? "")) || undefined,
        target: row.target === "_blank" ? "_blank" : "_self",
        title: String(row.title ?? "").slice(0, 120) || undefined,
      });
    } else {
      const richText = normalizeRuns(row.richText, 400);
      const text = richText.length ? plainFromRuns(richText) : String(row.text ?? "").slice(0, 400);
      out.push({
        id,
        kind: "text",
        text,
        richText: richText.length ? richText : runsFromPlain(text),
        href: sanitizeHref(String(row.href ?? "")) || undefined,
        target: row.target === "_blank" ? "_blank" : "_self",
        title: String(row.title ?? "").slice(0, 120) || undefined,
      });
    }
    if (out.length >= 20) break;
  }
  return out;
}

export function speedToDuration(speed: number): number {
  const s = Math.min(100, Math.max(1, Math.round(speed)));
  return Math.round(120 - s + 4);
}

export function durationToSpeed(duration: number): number {
  const d = Math.min(120, Math.max(4, Math.round(duration)));
  return Math.min(100, Math.max(1, 120 - d + 4));
}
