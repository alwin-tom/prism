import type { CSSProperties, ReactNode } from "react";
import { effectiveRuns, linkRel, type LinkTarget, type PropValue, type RichTextRun } from "./richText";

type LinkStyle = {
  color?: string;
  hoverColor?: string;
  underline?: boolean;
};

export function RichTextContent({
  props,
  textKey = "text",
  editing = false,
  linkStyle,
  className,
}: {
  props: Record<string, PropValue | undefined>;
  textKey?: string;
  editing?: boolean;
  linkStyle?: LinkStyle;
  className?: string;
}) {
  const runs = effectiveRuns(props, textKey);
  return <span className={["visual-rich-text", className].filter(Boolean).join(" ")}>{renderRuns(runs, editing, linkStyle)}</span>;
}

export function renderRuns(runs: RichTextRun[], editing: boolean, linkStyle?: LinkStyle): ReactNode {
  return runs.map((run, index) => {
    if (!run.href) {
      return <span key={index}>{run.text}</span>;
    }
    const style = linkCss(linkStyle);
    if (editing) {
      return (
        <span
          key={index}
          className="visual-link is-editing"
          data-href={run.href}
          title={run.title || run.href}
          style={style}
        >
          {run.text}
        </span>
      );
    }
    const target = (run.target ?? "_self") as LinkTarget;
    return (
      <a
        key={index}
        className="visual-link"
        href={run.href}
        target={target}
        rel={linkRel(target)}
        title={run.title || undefined}
        style={style}
      >
        {run.text}
      </a>
    );
  });
}

function linkCss(linkStyle?: LinkStyle): CSSProperties | undefined {
  if (!linkStyle) return undefined;
  const style: CSSProperties & Record<string, string> = {};
  if (linkStyle.color) style.color = linkStyle.color;
  if (linkStyle.underline === false) style.textDecoration = "none";
  if (linkStyle.underline === true) style.textDecoration = "underline";
  if (linkStyle.hoverColor) style["--link-hover"] = linkStyle.hoverColor;
  return Object.keys(style).length ? style : undefined;
}

export function linkStyleFromProps(props: Record<string, PropValue | undefined>): LinkStyle {
  return {
    color: props.linkColor ? String(props.linkColor) : undefined,
    hoverColor: props.linkHoverColor ? String(props.linkHoverColor) : undefined,
    underline: props.linkUnderline === undefined ? undefined : Number(props.linkUnderline) !== 0,
  };
}

/** Wrap whole element when there is an element href and no conflicting inline anchors. */
export function WholeLink({
  href,
  target,
  title,
  editing,
  hasInlineLinks,
  children,
  className,
  style,
  ...rest
}: {
  href?: string;
  target?: string;
  title?: string;
  editing?: boolean;
  hasInlineLinks: boolean;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
} & Record<string, unknown>) {
  const safe = href && /^(https?:|mailto:|tel:|#|\/)/i.test(href) ? href : "";
  if (!safe || hasInlineLinks) {
    return <span className={className} style={style} {...rest}>{children}</span>;
  }
  if (editing) {
    return (
      <span className={[className, "visual-whole-link is-editing"].filter(Boolean).join(" ")} style={style} data-href={safe} title={title || safe} {...rest}>
        {children}
      </span>
    );
  }
  const blank = target === "_blank";
  return (
    <a
      className={[className, "visual-whole-link"].filter(Boolean).join(" ")}
      style={style}
      href={safe}
      target={blank ? "_blank" : undefined}
      rel={blank ? "noopener noreferrer" : undefined}
      title={title || undefined}
      {...rest}
    >
      {children}
    </a>
  );
}
