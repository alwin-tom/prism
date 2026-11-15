import { useEffect, useRef, useState } from "react";
import { Link2, Unlink } from "lucide-react";
import { Button } from "../../components/ui";
import {
  applyLink,
  effectiveRuns,
  isSafeHref,
  plainFromRuns,
  removeLinksInRange,
  syncPlainText,
  type LinkTarget,
  type PropValue,
  type RichTextRun,
} from "./richText";

export type LinkPatch = {
  text?: string;
  richText?: RichTextRun[];
  href?: string;
  linkTarget?: string;
  linkTitle?: string;
  linkColor?: string;
  linkHoverColor?: string;
  linkUnderline?: number;
};

type Mode = "external" | "email" | "tel" | "anchor" | "internal";

function detectMode(href: string): Mode {
  const value = href.trim().toLowerCase();
  if (value.startsWith("mailto:")) return "email";
  if (value.startsWith("tel:")) return "tel";
  if (value.startsWith("#")) return "anchor";
  if (value.startsWith("/")) return "internal";
  return "external";
}

function composeHref(mode: Mode, raw: string): string {
  const value = raw.trim();
  if (!value) return "";
  if (mode === "email") return value.startsWith("mailto:") ? value : `mailto:${value.replace(/^mailto:/i, "")}`;
  if (mode === "tel") return value.startsWith("tel:") ? value : `tel:${value.replace(/^tel:/i, "")}`;
  if (mode === "anchor") return value.startsWith("#") ? value : `#${value.replace(/^#/, "")}`;
  if (mode === "internal") return value.startsWith("/") ? value : `/${value.replace(/^\//, "")}`;
  return value;
}

export function RichTextLinkField({
  props,
  onChange,
  allowWholeElement = true,
}: {
  props: Record<string, PropValue | undefined>;
  onChange: (patch: LinkPatch) => void;
  allowWholeElement?: boolean;
}) {
  const areaRef = useRef<HTMLTextAreaElement>(null);
  const runs = effectiveRuns(props);
  const plain = plainFromRuns(runs);
  const [mode, setMode] = useState<Mode>("external");
  const [url, setUrl] = useState("");
  const [target, setTarget] = useState<LinkTarget>("_blank");
  const [title, setTitle] = useState("");
  const [error, setError] = useState("");
  const [selection, setSelection] = useState<{ start: number; end: number }>({ start: 0, end: 0 });

  useEffect(() => {
    setError("");
  }, [props.richText, props.text]);

  function rememberSelection() {
    const el = areaRef.current;
    if (!el) return;
    setSelection({ start: el.selectionStart, end: el.selectionEnd });
  }

  function applyInline() {
    const href = composeHref(mode, url);
    if (!isSafeHref(href)) {
      setError("Use https://, http://, mailto:, tel:, #, or a path starting with /.");
      return;
    }
    let { start, end } = selection;
    if (start === end) {
      start = 0;
      end = plain.length;
    }
    if (start === end) {
      setError("Select text to link, or type text first.");
      return;
    }
    const next = applyLink(runs, start, end, href, target, title);
    onChange({ text: plainFromRuns(next), richText: next });
    setError("");
  }

  function clearInline() {
    let { start, end } = selection;
    if (start === end) {
      start = 0;
      end = plain.length;
    }
    const next = removeLinksInRange(runs, start, end);
    onChange({ text: plainFromRuns(next), richText: next });
  }

  const wholeHref = String(props.href ?? "");
  const linkedCount = runs.filter((run) => run.href).length;

  return (
    <div className="link-editor">
      <label>Text
        <textarea
          ref={areaRef}
          value={plain}
          onSelect={rememberSelection}
          onKeyUp={rememberSelection}
          onMouseUp={rememberSelection}
          onChange={(event) => {
            const nextPlain = event.target.value;
            const next = syncPlainText(runs, nextPlain);
            onChange({ text: nextPlain, richText: next });
          }}
        />
      </label>
      <p className="studio-hint">Select words in the text, then apply a link. Multiple links can live in one block.</p>
      <div className="link-editor-row">
        <label>Link type
          <select value={mode} onChange={(event) => setMode(event.target.value as Mode)}>
            <option value="external">External URL</option>
            <option value="internal">Internal path</option>
            <option value="anchor">Page anchor</option>
            <option value="email">Email</option>
            <option value="tel">Telephone</option>
          </select>
        </label>
        <label>Open in
          <select value={target} onChange={(event) => setTarget(event.target.value as LinkTarget)}>
            <option value="_self">Same tab</option>
            <option value="_blank">New tab</option>
          </select>
        </label>
      </div>
      <label>URL
        <input
          value={url}
          placeholder={mode === "email" ? "hello@example.com" : mode === "tel" ? "+123456789" : mode === "anchor" ? "contact" : "https://"}
          onChange={(event) => setUrl(event.target.value)}
        />
      </label>
      <label>Link title (optional)
        <input value={title} onChange={(event) => setTitle(event.target.value)} />
      </label>
      <div className="wizard-actions">
        <Button type="button" tone="secondary" onClick={applyInline}><Link2 size={14} /> Link selection</Button>
        <Button type="button" tone="secondary" onClick={clearInline}><Unlink size={14} /> Remove link</Button>
      </div>
      {linkedCount > 0 ? <p className="studio-hint">{linkedCount} inline {linkedCount === 1 ? "link" : "links"} in this text.</p> : null}
      {error ? <p className="error-line">{error}</p> : null}

      <label className="studio-label">Link colour
        <input type="color" value={String(props.linkColor || "#0b5fff")} onChange={(event) => onChange({ linkColor: event.target.value })} />
      </label>
      <label className="studio-label">Hover colour
        <input type="color" value={String(props.linkHoverColor || "#0842c2")} onChange={(event) => onChange({ linkHoverColor: event.target.value })} />
      </label>
      <label className="check-line">Underline links
        <input
          type="checkbox"
          checked={props.linkUnderline === undefined ? true : Number(props.linkUnderline) !== 0}
          onChange={(event) => onChange({ linkUnderline: event.target.checked ? 1 : 0 })}
        />
      </label>

      {allowWholeElement ? (
        <>
          <p className="studio-label">Whole-element link</p>
          <p className="studio-hint">Used when there are no inline links. Inline links always take priority.</p>
          <label>Element URL
            <input
              value={wholeHref}
              onChange={(event) => {
                const next = event.target.value;
                onChange({ href: next });
                setMode(detectMode(next));
              }}
              placeholder="https:// or /path or #section"
            />
          </label>
          <label>Element opens in
            <select
              value={String(props.linkTarget ?? "_self")}
              onChange={(event) => onChange({ linkTarget: event.target.value })}
            >
              <option value="_self">Same tab</option>
              <option value="_blank">New tab</option>
            </select>
          </label>
          <label>Element link title
            <input value={String(props.linkTitle ?? "")} onChange={(event) => onChange({ linkTitle: event.target.value })} />
          </label>
          {wholeHref && !isSafeHref(wholeHref) ? <p className="error-line">That whole-element link is not allowed.</p> : null}
        </>
      ) : null}
    </div>
  );
}

export function SimpleLinkFields({
  href,
  target,
  title,
  onChange,
}: {
  href: string;
  target?: string;
  title?: string;
  onChange: (patch: { href?: string; linkTarget?: string; linkTitle?: string }) => void;
}) {
  return (
    <>
      <label>Link
        <input value={href} onChange={(event) => onChange({ href: event.target.value })} placeholder="https:// or /path" />
      </label>
      <label>Open in
        <select value={target ?? "_self"} onChange={(event) => onChange({ linkTarget: event.target.value })}>
          <option value="_self">Same tab</option>
          <option value="_blank">New tab</option>
        </select>
      </label>
      <label>Link title
        <input value={title ?? ""} onChange={(event) => onChange({ linkTitle: event.target.value })} />
      </label>
    </>
  );
}
