# Advanced editor implementation

WebAux keeps the template editor. The visual editor is a second mode on the same event revision. `PRISM_ADVANCED_EDITOR_ENABLED` defaults to false. `GET /api/v1/config/features` returns `{ "advancedEditorEnabled": true|false }`. Editor writes check the flag. A published page still renders a saved visual document when the flag is off.

## Architecture

The React tree is not the website. `event_page_documents.document_json` is a versioned tree tied to one revision. The studio, the draft preview, admin review, and the public site all read that tree. Selection, zoom, and the open panel stay in editor state.

Publishing still follows the existing revision, review, and payment flow. Saving a draft does not change `published_revision_id`.

GrapesJS and Puck were not adopted. WebAux owns the document and the renderer.

## Document schema

Schema 1. Desktop styles are the base. `responsiveStyles.tablet` and `responsiveStyles.mobile` override only the keys set for that width.

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
    "locked": false,
    "props": {},
    "styles": {},
    "responsiveStyles": { "tablet": {}, "mobile": {} },
    "children": []
  }
}
```

`locked` is stored only when true. The backend rebuilds the tree through an allowlist and rejects unknown types, fields, fonts, links, and transforms. Text is plain. Links allow `http`, `https`, `mailto`, `tel`, `#`, and a site path.

Registered types: `SECTION`, `CONTAINER`, `FLEX`, `GRID`, `GROUP`, `HEADING`, `PARAGRAPH`, `BUTTON`, `IMAGE`, `SPACER`, `DIVIDER`.

Position defaults to normal flow. Dragging an element sets `position: relative` and `left` / `top`, so it stays in the document and can still sit in a flex or grid parent. Choosing "Free on the page" sets `position: absolute`.

## Component registry

`frontend/src/features/visual-editor/document.ts` lists the elements the studio can insert. `VisualPage` renders the same tree for editing and for the public site. Editor chrome is drawn only when `onSelect` is passed.

## Editor state

The studio component holds the document, selection, breakpoint, zoom, undo and redo stacks, and save status. Autosave waits 1.5 seconds after the last change and sends the current version number. A stale write is rejected. A continuous drag or resize is one history step.

## API

- `GET /api/v1/config/features`
- `GET` and `PUT /api/v1/events/{id}/editor/document`
- `POST /api/v1/events/{id}/editor/convert`
- `POST /api/v1/events/{id}/editor/autosave`
- `GET /api/v1/events/{id}/editor/history`
- `POST /api/v1/events/{id}/editor/restore/{versionId}`
- `POST /api/v1/events/{id}/editor/validate`
- `GET /api/v1/admin/events/{id}/editor/preview`

Only a draft revision can be edited. Converting a template is explicit and does not remove the original configuration.

## Keyboard shortcuts

- Ctrl/Cmd + Z, Shift + Z: undo, redo
- Ctrl/Cmd + S: save
- Ctrl/Cmd + C / V: copy and paste an element
- Ctrl/Cmd + D: duplicate
- Ctrl/Cmd + G: group the selection
- Ctrl/Cmd + Shift + G: ungroup
- Delete / Backspace: delete
- Arrow keys: move 1px, Shift + arrow moves 10px
- Escape: select the page
- Shift or Ctrl/Cmd + click: add or remove from the selection

Shortcuts do not run while a field is focused.

## Completed

- Feature flag, document storage, validation, history, and explicit template conversion.
- Studio shell, element library, layers, colour, type, alignment, and draft preview.
- Eight resize handles, move, rotation, X/Y, width, height, and proportional resize with Shift.
- Duplicate, copy, paste, copy style, paste style, group, ungroup, and lock.
- Font family, weight, italic, underline, uppercase, line height, and letter spacing.
- Autosave with a version check.
- Public and no-JavaScript rendering of the saved tree, without editor handles.

## Remaining

Rich text, video, gradients, galleries, carousels, accordions, tabs, modals, countdown, RSVP inside the tree, maps, animations, hover actions, reusable sections, a full grid editor, smart guides, and rulers. RSVP must keep using the existing RSVP API.

## Known limitations

- Free movement uses `left` and `top` on a relative or absolute element. Reordering inside a flex row is done by grouping and by the layer tree, not by dropping one sibling onto another.
- Rotation is a `rotate()` transform. Scale and skew are accepted by the validator and are not yet in the inspector.
- One page is stored per revision. Extra pages are not a separate product object yet.
- The inspector edits the primary selection. A drag moves every selected element by the same distance.
