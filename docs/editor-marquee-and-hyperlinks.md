# Marquee and hyperlinks

Both capabilities are always on. There is no feature flag.

## Rich-text hyperlinks

Text elements store plain `props.text` for compatibility and an optional `props.richText` array of runs:

```json
{
  "text": "Discover our events and register today.",
  "richText": [
    { "text": "Discover our events and " },
    { "text": "register today", "href": "https://example.com/register", "target": "_blank", "title": "Register" }
  ],
  "href": "",
  "linkTarget": "_self",
  "linkTitle": "",
  "linkColor": "#0b5fff",
  "linkHoverColor": "#0842c2",
  "linkUnderline": 1
}
```

- Inline links live on runs. Multiple links per element are supported.
- Whole-element `href` is used only when there are no inline linked runs (avoids nested anchors).
- Allowed schemes: `http(s):`, `mailto:`, `tel:`, `#`, `/`. `javascript:` and `data:` are rejected.
- In the editor, links render as non-navigating marks; preview and published sites use real anchors with `rel="noopener noreferrer"` for `_blank`.

Supported element types: `HEADING`, `PARAGRAPH`, `QUOTE`, `FAQ_ITEM`, `TIMELINE_ITEM`, plus marquee text items. `BUTTON` keeps whole-element `href` (and optional `linkTarget`).

Inspector: select text in the Text field, then **Link selection** / **Remove link**.

## Marquee

Element type `MARQUEE` (Media library). Items are stored in `props.items` (not duplicated as editable canvas children). The renderer clones the track visually for seamless looping; screen-reader copies of the clone are `aria-hidden`.

Key props: `direction`, `speed`, `duration`, `gap`, `delay`, `loop`, `pauseOnHover`, `pauseOnClick`, `animationEnabled`, `items`.

Behaviour:

- CSS animation (no third-party library)
- Respects `prefers-reduced-motion` (static list)
- Pauses when off-screen
- Hover / click pause as configured

## Shared surfaces

Editor canvas, preview (`VisualPage`), and no-JS HTML (`PublicHtmlRenderer`) all understand marquee content and rich-text links. Undo/redo uses the existing document history stack (structuredClone of the full document).
