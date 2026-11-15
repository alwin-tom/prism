# Advanced visual editor

WebAux keeps the template editor. The visual builder is a permanent second editing mode, stored as its own document.

## Why a separate document

The template configuration is a sanitised list of sections. A page builder needs a tree: a section contains a container, which contains a heading and a button. That tree does not fit the section schema, and adding hundreds of fields to it would break every published site.

`event_page_documents.document_json` is the tree. It is tied to one event revision. The live site reads the document attached to `published_revision_id`.

Editor chrome (selection, zoom, the open panel) is not stored in the document.

## Foundations that were not adopted

GrapesJS stores HTML. Its document is not a component tree, and publishing would mean sanitising arbitrary markup. Puck stores React component configs and renders through React. The public site, the admin review, and the no-JavaScript HTML fallback would all have to depend on that runtime. WebAux keeps a JSON tree and a registry both the studio and the public renderer understand.

## Document, schema 1

```json
{
  "schemaVersion": 1,
  "documentId": "uuid",
  "page": { "title": "", "description": "" },
  "breakpoints": { "desktop": 1200, "tablet": 768 },
  "designTokens": { "colors": {}, "fonts": {}, "spacing": {} },
  "root": {
    "id": "page",
    "type": "SECTION",
    "name": "Page",
    "props": {},
    "styles": {},
    "responsiveStyles": { "tablet": {}, "mobile": {} },
    "children": []
  }
}
```

Desktop styles are the base. Tablet and mobile objects override only the keys set for that width. The backend rebuilds the tree through an allowlist. An unknown type, style, or field is rejected. Text is plain. Links allow `http`, `https`, `mailto`, `tel`, `#`, and a site path. `javascript:` is rejected.

Registered types: `SECTION`, `CONTAINER`, `FLEX`, `GRID`, `GROUP`, `CAROUSEL`, `HEADING`, `PARAGRAPH`, `BUTTON`, `IMAGE`, `SPACER`, `DIVIDER`.

A sideways move stores `left` as a percentage of the parent on every screen. For a picture, that percentage is measured from the parent's left edge, and the picture's width is stored as a percentage too, so a move on desktop, tablet, or mobile keeps the same fraction of the row.

`CAROUSEL` stores its slides as child `IMAGE` nodes. Presentation lives in scalar props: `preset` (`minimal`, `cards`, `hero`, `coverflow`, `fade`, `editorial`), `autoplay` (`0` or `1`), `interval` (milliseconds), and `perView` (`1` to `3`). Changing the preset does not replace the slides. Tablet shows at most two slides and mobile shows one. Autoplay pauses on hover and does not run when the visitor prefers reduced motion.

Style values stay on the existing `styles` and `responsiveStyles` maps. `background` may be a hex colour or a `linear-gradient` with no `url()`. `background`, `boxShadow`, `filter`, and `border` may be up to 180 characters. Other style values stay at 80. `url(`, `expression(`, `javascript`, `<`, and `;` are still rejected. Shape presets write `borderRadius` and `overflow`. Shadow presets write `boxShadow`. Stacking writes `zIndex` and sets `position` to `relative` when the element was static. Existing documents that omit the new keys still render.

## What this slice includes

- Migration for `event_page_documents`, `editor_document_versions`, and `editor_templates`.
- Document read, explicit conversion from the current template, save, autosave, history, restore, and validate.
- Optimistic `version` on save. A stale write is rejected.
- A draft revision is required. A submitted revision is not edited in place.
- When a draft revision is cloned, its visual document is copied with it. The previous revision keeps its own copy.
- Studio at `/app/events/:id/studio`: add, select, drag onto a container, colour pickers, text and flex alignment, width and height fields, edge and corner resize, layers, undo, save, and a draft preview.
- Public and review previews render the document when the revision has one, with no selection chrome.

## Still to build

These controls are not in the editor yet, so they are not offered as empty buttons: rich text, video, audio, HTML embed, maps, countdown, RSVP, schedule, couple and family blocks, galleries beyond the carousel, before/after, masonry, lightbox, accordion, tabs, marquee, modal, sticky navigation, the remaining carousel presets, a multi-stop gradient editor, per-side borders, clip-path, multiple shadows, filters and blend modes, scale, skew and flip, smart guides, typography presets, scroll animations, and natural-language edits of one element. RSVP in the tree must keep using the existing RSVP API. The phase log is `docs/advanced-editor-implementation.md`.
