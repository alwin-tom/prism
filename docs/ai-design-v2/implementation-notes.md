# Creative Design Intelligence V2 — implementation notes

Audited against the live WebAux codebase (prompt version was `prism-website-6` before this work).

## Findings vs brief

| Claim in brief | Actual code |
|----------------|-------------|
| CAROUSEL absent from registered elements | **Already present** in `DocumentValidator`, `DocumentNormalizer`, `PrismSchemas`, frontend `document.ts`, `VisualPage` carousel, studio inspector. Reconciled as first-class, not removed. |
| `designTokens` discarded | **Confirmed.** `DocumentValidator.sanitize` replaced tokens with empty `{colors,fonts,spacing}` objects. Fixed with allowlisted semantic token sanitization via `DesignSystemTokens`. |
| Generation = plan → document → repair | Confirmed in `WebsiteDocumentGenerator`. V2 adds creative direction, design-system object, visual blueprint, deterministic QA when `PRISM_AI_CREATIVE_V2_ENABLED=true`. |
| Feature flags | `PRISM_AI_WEBSITE_GENERATION_ENABLED`, `PRISM_ADVANCED_EDITOR_ENABLED` unchanged defaults (`false`). New flag `PRISM_AI_CREATIVE_V2_ENABLED` defaults `false`. |
| Schema version | Remains **1**. Creative metadata stored as optional `creative` + expanded `designTokens`; no migration of old docs. |
| Design directions | Existing `DesignDirection` (15 palettes) kept as palette applicator; V2 archetypes (`ArchetypeCatalog`) drive creative choice and map onto directions. |

## Pipeline (V2 on)

```text
CONTEXT → CREATIVE_DIRECTION → DESIGN_SYSTEM (from archetype + direction)
        → VISUAL_BLUEPRINT → DOCUMENT_GENERATION → SANITIZE
        → DETERMINISTIC_QA → optional REPAIR/REFINE → DRAFT_INSTALL
```

When V2 is off, the previous plan→document→repair path runs unchanged. Prompt version becomes `webaux-creative-v2` only when V2 is enabled.

## Questionnaire preferences

When V2 is enabled, optional Design preferences questions are merged into the questionnaire API response (not persisted into older definition JSON): `visualDirection`, `visualRichness`, `motionPreference`. Answers are kept through `effectiveConfiguration` so they reach generation.

## Stitch / vision QA

Stitch MCP is configured (`project-0-prism-stitch`) but long `generate_screen_from_text` calls timed out earlier; V2 runtime does **not** depend on Stitch. Deterministic visual QA always runs under V2; vision screenshot QA is recorded as unavailable (`checksRun` includes `vision-unavailable`). No fabricated Stitch IDs.

## Component strategy

- Primitives + CAROUSEL retained end-to-end.
- Added allowlisted types: `QUOTE`, `COUNTDOWN`, `FAQ_ACCORDION` / `FAQ_ITEM`, `TIMELINE` / `TIMELINE_ITEM`.
- Hero variants remain blueprint/composition patterns composed from SECTION/FLEX/IMAGE/HEADING.
- Studio library, inspector fields, React renderer, and no-JS `PublicHtmlRenderer` updated for the new types.

## Security preserved

Server-side OpenRouter only; media ownership; URL schemes; CSS allowlists; no `url()` backgrounds; answers treated as data; budgets/retries unchanged. Feature flag off by default.

## Design Reference Library

See [design-reference-library.md](./design-reference-library.md). Adds Flyway V6, ~40 original seed patterns, admin library UI, SQL (+ optional pgvector) retrieval, and Design Director stage when `PRISM_AI_DESIGN_LIBRARY_ENABLED=true`.

## Enablement

Local/dev:

```bash
PRISM_AI_WEBSITE_GENERATION_ENABLED=true
PRISM_ADVANCED_EDITOR_ENABLED=true
PRISM_AI_CREATIVE_V2_ENABLED=true
PRISM_AI_DESIGN_LIBRARY_ENABLED=true
```

Production: enable generation and advanced editor first; turn on creative V2 after verifying OpenRouter models accept the additional structured schemas. Enable the design library after pgvector is available and seeds are verified.
