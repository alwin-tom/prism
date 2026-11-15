# Creative Design Intelligence V2 — implementation notes

Audited against the live WebAux codebase (prompt version was `prism-website-6` before this work).

## Findings vs brief

| Claim in brief | Actual code |
|----------------|-------------|
| CAROUSEL absent from registered elements | **Already present** in `DocumentValidator`, `DocumentNormalizer`, `PrismSchemas`, frontend `document.ts`, `VisualPage` carousel, studio inspector. Reconciled as first-class, not removed. |
| `designTokens` discarded | **Confirmed.** `DocumentValidator.sanitize` replaced tokens with empty `{colors,fonts,spacing}` objects. Fixed with allowlisted semantic token sanitization via `DesignSystemTokens`. |
| Generation = plan → document → repair | Confirmed in `WebsiteDocumentGenerator`. Production path always runs Creative Design Intelligence V2 (creative direction, design-system object, visual blueprint, deterministic QA). Legacy plan→document path remains for tests only. |
| Feature flags | Website generation, creative V2, design library, and design embeddings are always on (no enable flags). Visual editor is always on. Optional embedding provider/model still configure hybrid retrieval. |
| Schema version | Remains **1**. Creative metadata stored as optional `creative` + expanded `designTokens`; no migration of old docs. |
| Design directions | Existing `DesignDirection` (15 palettes) kept as palette applicator; V2 archetypes (`ArchetypeCatalog`) drive creative choice and map onto directions. |

## Pipeline

```text
CONTEXT → CREATIVE_DIRECTION → DESIGN_SYSTEM (from archetype + direction)
        → VISUAL_BLUEPRINT → DOCUMENT_GENERATION → SANITIZE
        → DETERMINISTIC_QA → optional REPAIR/REFINE → DRAFT_INSTALL
```

Prompt version is always `webaux-creative-v2`. The legacy plan→document→repair path is retained only for unit tests via `WebsiteDocumentGenerator.legacyForTests`.

## Questionnaire preferences

Optional Design preferences questions are merged into the questionnaire API response (not persisted into older definition JSON): `visualDirection`, `visualRichness`, `motionPreference`. Answers are kept through `effectiveConfiguration` so they reach generation.

## Stitch / vision QA

Stitch MCP is configured (`project-0-prism-stitch`) but long `generate_screen_from_text` calls timed out earlier; V2 runtime does **not** depend on Stitch. Deterministic visual QA always runs; vision screenshot QA is recorded as unavailable (`checksRun` includes `vision-unavailable`). No fabricated Stitch IDs.

## Component strategy

- Primitives + CAROUSEL retained end-to-end.
- Added allowlisted types: `QUOTE`, `COUNTDOWN`, `FAQ_ACCORDION` / `FAQ_ITEM`, `TIMELINE` / `TIMELINE_ITEM`.
- Hero variants remain blueprint/composition patterns composed from SECTION/FLEX/IMAGE/HEADING.
- Studio library, inspector fields, React renderer, and no-JS `PublicHtmlRenderer` updated for the new types.

## Security preserved

Server-side OpenRouter only; media ownership; URL schemes; CSS allowlists; no `url()` backgrounds; answers treated as data; budgets/retries unchanged.

## Design Reference Library

See [design-reference-library.md](./design-reference-library.md). Adds Flyway V6, ~40 original seed patterns, admin library UI, SQL (+ optional pgvector) retrieval, and Design Director during generation when a retriever bean is present.

## Enablement

Website generation and creative V2 always run when OpenRouter is configured. Optional hybrid embeddings need provider/model/API key. Production needs pgvector available and design-library seeds verified.
